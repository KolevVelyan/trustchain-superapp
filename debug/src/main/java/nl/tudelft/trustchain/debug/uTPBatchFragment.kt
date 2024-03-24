package nl.tudelft.trustchain.debug

import android.content.Context
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.utp4j.channels.UtpServerSocketChannel
import net.utp4j.channels.UtpSocketChannel
import net.utp4j.channels.UtpSocketState.CLOSED
import net.utp4j.channels.impl.UtpSocketChannelImpl
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.common.OnOpenPortResponseListener
import nl.tudelft.trustchain.common.ui.BaseFragment
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.debug.databinding.FragmentUtpbatchBinding
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.Arrays
import java.time.Duration

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class uTPBatchFragment : BaseFragment(R.layout.fragment_utpbatch) {
    private val binding by viewBinding(FragmentUtpbatchBinding::bind)

    private var sender: UTPSender? = null
    private var receiver: UTPReceiver? = null

    private val CUSTOM_DATA_SIZE = "Custom Data Size"

    private var availablePeers = HashMap<IPv4Address, Peer>()
    private var chosenPeer: Peer? = null

    private var availableVotes : Array<String> = emptyArray()
    private var chosenVote: String = ""

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        sender = UTPSender(this)
        receiver = UTPReceiver(this)
        getDemoCommunity().addListener(receiver!!)


        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        lifecycleScope.launchWhenCreated {
            while (isActive) {
                updateView()
                delay(100)
            }
        }
        lifecycleScope.launchWhenCreated {
            getDemoCommunity().punctureChannel.collect { (peer, payload) ->
                Log.i(
                    "PunctureFragment",
                    "Received puncture from $peer on port ${payload.identifier}"
                )
            }
        }

        binding.btnSend.setOnClickListener {
            if (receiver!!.isReceiving()) {
                Toast.makeText(requireContext(), "Cannot send while receiving.", Toast.LENGTH_SHORT).show()
            } else if (validateInput()) {
                val thread = Thread {
                    val senderPort = 8093
                    val senderIP = getDemoCommunity().myEstimatedWan.ip
                    val byteData: ByteArray

                    if (chosenVote == CUSTOM_DATA_SIZE || chosenVote.isEmpty()) {
                        val dataSizeText = binding.dataSize.text.toString()

                        // should not occur if validated beforehand
                        if (dataSizeText.isEmpty()) throw IllegalArgumentException("invalid data size")

                        byteData = ByteArray(dataSizeText.toInt() * 1024)
                        Arrays.fill(
                            byteData,
                            0x6F.toByte()
                        ) // currently data is just the character "o" over and over
                    } else {
                        byteData = readCsvToByteArray(chosenVote)
                    }

                    sender?.setUpSender(byteData, senderPort, senderIP)
                }
                thread.start()
            }
        }

        binding.btnConnect.setOnClickListener {
            if (validateInput(false)) {
                val senderPort: Int = 8093
                val peerToPuncture = chosenPeer ?: throw IllegalArgumentException("invalid peer")
                val senderWan = peerToPuncture.wanAddress
                puncturePortOfSender(senderWan, senderPort)
            }
        }

        binding.dataSize.doOnTextChanged { text, _, _, _ ->
            if (!text.isNullOrEmpty() && binding.dataSize.isEnabled) {
                getDemoCommunity().senderDataSize = text.toString().toInt() * 1024
            }
        }

    }

    private fun puncturePortOfSender(addr: IPv4Address, port: Int) {
        // try to puncture the uTP sender port
        setTextToResult("Puncturing port $port of $addr")
        getDemoCommunity().openPort(addr, port)
    }



    private fun validateInput(includeData: Boolean = true): Boolean {
        val context: Context = requireContext()


        if (chosenPeer == null) {
            Toast.makeText(context, "Invalid peer", Toast.LENGTH_SHORT).show()
            return false
        }

        if (includeData && binding.dataSize.text.toString().isEmpty() && binding.dataSize.isEnabled) {
            Toast.makeText(context, "Invalid data size", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    fun setTextToResult(text: String) {
        appendTextToResult(text, false)
    }

    fun appendTextToResult(text: String, newline: Boolean = true) {
        activity?.runOnUiThread {
            var oldText = binding.txtResult.text.toString() + "\n"
            if (binding.txtResult.text.isEmpty() || !newline) {
                oldText = ""
            }
            val currentTime = LocalDateTime.now()
            val formattedTime = currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
            binding.txtResult.text = oldText + formattedTime + " | " + text
        }
    }

    private fun updateAvailablePeers()  {
        // get current peers
        val currPeers: List<Peer> = getDemoCommunity().getPeers()
        val newPeersMap = HashMap<IPv4Address, Peer>()
        for (peer in currPeers) {
            newPeersMap[peer.wanAddress] = peer
        }

        if (newPeersMap != availablePeers) {
            // peers have changed need to update
            availablePeers = newPeersMap
            val autoComplete: AutoCompleteTextView = binding.autoCompleteTxt
            val context: Context = requireContext()
            val adapter = ArrayAdapter(context, R.layout.peer_item, availablePeers.keys.toList())

            autoComplete.setAdapter(adapter)
            autoComplete.onItemClickListener = AdapterView.OnItemClickListener {
                    adapterView, view, i, l ->

                val itemSelected = adapterView.getItemAtPosition(i)
                chosenPeer = availablePeers[itemSelected]
            }
        }
    }

    private fun getFileNames(): Array<String> {
        val csvFileNames = mutableListOf<String>()
        try {
            // List all files in the "app/assets" folder
            val files = requireContext().assets.list("") ?: return emptyArray()
            for (file in files) {
                // Check if the file is a CSV file
                if (file.endsWith(".csv")) {
                    csvFileNames.add(file)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        // add CUSTOM_DATA_SIZE to the list of available votes
        csvFileNames.add(CUSTOM_DATA_SIZE)

        return csvFileNames.toTypedArray()
    }

    private fun updateVoteFiles() {
        val voteFiles = getFileNames()
        if (!voteFiles.contentEquals(availableVotes)) {
            // peers have changed need to update
            availableVotes = voteFiles
            val autoComplete: AutoCompleteTextView = binding.autoCompleteVotes
            val context: Context = requireContext()
            val adapter = ArrayAdapter(context, R.layout.vote_item, availableVotes)

            autoComplete.setAdapter(adapter)
            autoComplete.onItemClickListener = AdapterView.OnItemClickListener {
                    adapterView, _, i, _ ->

                val itemSelected = adapterView.getItemAtPosition(i)
                chosenVote = itemSelected.toString()

                if (chosenVote == CUSTOM_DATA_SIZE || chosenVote.isEmpty()) {
                    binding.dataSize.isEnabled = true
                    binding.dataSize.setText("0")
                    getDemoCommunity().senderDataSize = 0
                } else {
                    binding.dataSize.isEnabled = false
                    val dataSize = readCsvToByteArray(chosenVote).size
                    getDemoCommunity().senderDataSize = dataSize
                    binding.dataSize.setText((dataSize / 1024).toString())
                }

            }
        }
    }

    private fun readCsvToByteArray(fileName: String): ByteArray {
        val inputStream = requireContext().assets.open(fileName)
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var length: Int
        while (inputStream.read(buffer).also { length = it } != -1) {
            outputStream.write(buffer, 0, length)
        }
        inputStream.close()
        return outputStream.toByteArray()
    }

    private fun updateView() {
        updateAvailablePeers()
        updateVoteFiles()
    }
}


