package nl.tudelft.trustchain.debug

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.common.ui.BaseFragment
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.debug.databinding.FragmentUtpbatchBinding
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Arrays

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

data class UTPExchange(
    val lastUTPReceive: Date? = null,
    val lastUTPSent: Date? = null,
)


class uTPBatchFragment : BaseFragment(R.layout.fragment_utpbatch), UTPDataFragment {
    private val binding by viewBinding(FragmentUtpbatchBinding::bind)

    private var sender: UTPSender? = null
    private var receiver: UTPReceiver? = null

    private var peerDropdown: PeerDropdown? = null

    private val CUSTOM_DATA_SIZE = "Custom Data Size"

    private var availableVotes : Array<String> = emptyArray()
    private var chosenVote: String = ""

    private var peerList: List<Peer> = emptyList()

    private var peersUTPExchange: HashMap<Peer, UTPExchange> = hashMapOf()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        sender = UTPSender(this, getDemoCommunity())
        receiver = UTPReceiver(this, getDemoCommunity())
        getDemoCommunity().addListener(receiver!!)

        peerDropdown = PeerDropdown(requireContext())


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
            } else if (sender!!.isSending()) {
                Toast.makeText(
                    requireContext(),
                    "Already sending. Wait for previous send to finish!",
                    Toast.LENGTH_SHORT
                ).show()
            } else if (validateInput()) {
                // neither receiving nor sending so start sending
                val thread = Thread {
                    startSender()
                }
                thread.start()
            }
        }

        binding.infoButton.setOnClickListener{
            showInfoDialog()
        }

        binding.dataSize.doOnTextChanged { text, _, _, _ ->
            if (!text.isNullOrEmpty() && binding.dataSize.isEnabled) {
                getDemoCommunity().senderDataSize = text.toString().toInt() * 1024
            }
        }

    }

    override fun debugInfo(info: String, toast: Boolean, reset: Boolean) {
        if (reset) {
            setTextToResult(info)
        } else {
            appendTextToResult(info)
        }
    }

    override fun newDataReceived(data: ByteArray, source: IPv4Address) {
        // update peer's last received time for UTP
        val peer = peerList.find { it.wanAddress.ip == source.ip }
        if (peer != null) {
            peersUTPExchange[peer] = UTPExchange(lastUTPReceive = Date(), lastUTPSent = peersUTPExchange[peer]?.lastUTPSent)
        } else {
            Log.e("uTPBatchFragment", "Received data from unknown peer")
        }


        val dataSize = data.size
        val dataSizeInKB = dataSize / 1024
        appendTextToResult("Received data from $source with size $dataSizeInKB KB")
    }

    override fun newDataSent(success: Boolean, destinationAddress: String, msg: String) {
        if (success) {
            if (destinationAddress != "") {
                val destinationIP: String = destinationAddress.split(":")[0].removePrefix("/")
                val destinationPort: String = destinationAddress.split(":")[1]
                appendTextToResult("Sent data to $destinationIP:$destinationPort")

                // update peer's last sent time for UTP
                val peer = peerList.find { it.wanAddress.ip == destinationIP }
                if (peer != null) {
                    peersUTPExchange[peer] = UTPExchange(lastUTPSent = Date(), lastUTPReceive = peersUTPExchange[peer]?.lastUTPReceive)
                } else {
                    Log.e("uTPBatchFragment", "Sent data to unknown peer")
                }



            } else {
                appendTextToResult(msg)
            }
        } else {
            appendTextToResult(msg)
        }
    }

    private fun validateInput(includeData: Boolean = true): Boolean {
        val context: Context = requireContext()


        if (peerDropdown?.getChosenPeer() == null) {
            Toast.makeText(context, "Invalid peer", Toast.LENGTH_SHORT).show()
            return false
        }

        if (includeData && binding.dataSize.text.toString().isEmpty() && binding.dataSize.isEnabled) {
            Toast.makeText(context, "Invalid data size", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun startSender() {
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

        val peerToSend = peerDropdown?.getChosenPeer() ?: throw IllegalArgumentException("invalid peer")


        sender?.sendData(peerToSend, byteData)
    }

    private fun setTextToResult(text: String) {
        appendTextToResult(text, false)
    }

    private fun appendTextToResult(text: String, newline: Boolean = true) {
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

    private fun updatePeerList()  {
        peerList = getDemoCommunity().getPeers()

        // add new peers to UTP list
        for (peer in peerList) {
            if (!peersUTPExchange.containsKey(peer)) {
                peersUTPExchange[peer] = UTPExchange()
            }
        }

        // remove old peers from UTP list
        val keys = peersUTPExchange.keys
        for (key in keys) {
            if (!peerList.contains(key)) {
                peersUTPExchange.remove(key)
            }
        }

        val context: Context = requireContext()

        // incoming peers have non null UTP response time in peersUTPExchange get peer from peerList
        val incomingPeers = peerList.filter { peersUTPExchange[it]?.lastUTPReceive != null }
        val incomingPeerAdapter = PeerListAdapter(context, R.layout.peer_connection_list_item, incomingPeers, true, peersUTPExchange)
        binding.incomingPeerConnectionListView.adapter = incomingPeerAdapter

        // outgoing peers have non null UTP sent time in peersUTPExchange get peer from peerList
        val outgoingPeers = peerList.filter { peersUTPExchange[it]?.lastUTPSent != null }
        val outgoingPeerAdapter = PeerListAdapter(context, R.layout.peer_connection_list_item, outgoingPeers, false, peersUTPExchange)
        binding.outgoingPeerConnectionListView.adapter = outgoingPeerAdapter

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
        peerDropdown?.updateAvailablePeers(getDemoCommunity().getPeers(), binding.autoCompleteTxt)
        updateVoteFiles()
        updatePeerList()
    }

    private fun showInfoDialog() {
        val ipv8 = getIpv8()
        val demo = getDemoCommunity()

        val dialogView = LayoutInflater.from(context).inflate(R.layout.info_popup_layout, null)

        val lan = dialogView.findViewById<TextView>(R.id.lan_address)
        val wan = dialogView.findViewById<TextView>(R.id.wan_address)
        val conn_type = dialogView.findViewById<TextView>(R.id.connection_type)
        val peer_id = dialogView.findViewById<TextView>(R.id.peer_id)
        val green = dialogView.findViewById<TextView>(R.id.green)

        lan.text = demo.myEstimatedLan.toString()
        wan.text = demo.myEstimatedWan.toString()
        conn_type.text = demo.network.wanLog.estimateConnectionType().value
        peer_id.text = PeerListAdapter.getSplitMID(ipv8.myPeer.mid)

        val dialogBuilder = AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }

        val alertDialog = dialogBuilder.create()
        alertDialog.show()
    }
}


