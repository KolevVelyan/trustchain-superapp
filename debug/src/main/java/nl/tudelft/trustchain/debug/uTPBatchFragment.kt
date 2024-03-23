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

    private var receiver: ReceiveUTP? = null

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

        receiver = ReceiveUTP(activity, this)
        getDemoCommunity().addListener(receiver!!)

        updateAvailablePeers()

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
            if (sendReceiveValidateInput()) {
                val senderPort: Int = 8093

                setUpSender(senderPort)
            }
        }

        binding.btnConnect.setOnClickListener {
            if (sendReceiveValidateInput(false)) {
                val senderPort: Int = 8093
                val senderWan = getChosenPeer().wanAddress
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
        // try to puncture the uTP server port
        setTextToResult("Puncturing port $port of $addr")
        getDemoCommunity().openPort(addr, port)
    }

    private fun setUpSender(senderPort: Int) {
        var transferAmount: Int = 0
        var byteData: ByteArray = ByteArray(0)
        if (chosenVote == CUSTOM_DATA_SIZE || chosenVote.isEmpty()) {
            transferAmount = getDataSize() * 1024
            // data to send
            byteData = ByteArray(transferAmount)
            Arrays.fill(
                byteData,
                0x6F.toByte()
            ) // currently data is just the character "o" over and over
        } else {
            byteData = readCsvToByteArray(chosenVote)
            transferAmount = byteData.size
        }


        try {
            setTextToResult("Starting sender on port $senderPort")


            // socket is defined by the sender's ip and chosen port
            val socket = InetSocketAddress(
                InetAddress.getByName(getDemoCommunity().myEstimatedLan.ip),
                senderPort
            )
            appendTextToResult("Socket ${socket.toString()} set up")

            // instantiate server to send data (it waits for client to through socket first)
            try {
                // socket is defined by the sender's ip and chosen port
                val server = UtpServerSocketChannel.open()
                server.bind(socket)

                appendTextToResult("Server bound to socket")
                // wait until someone connects to server and get new channel
                val acceptFuture = server.accept()
                appendTextToResult("Waiting for client to connect")
                acceptFuture.block()


                appendTextToResult("Client has connected to server")
                val startTime = LocalDateTime.now()
                val channel = acceptFuture.channel

                // send data on newly established channel (with client/receiver)
                val out = ByteBuffer.allocate(transferAmount)
                out.put(byteData)
                val fut = channel.write(out)
                fut.block() // block until all data is sent
                val timeStats = calculateTimeStats(startTime, transferAmount)
                appendTextToResult("Sent all ${transferAmount/1024} Kb of data in $timeStats")

                channel.close()
                server.close()
                appendTextToResult("Channel and server closed")

            } catch (e: java.lang.Exception) {
                e.printStackTrace(System.err)
                appendTextToResult("Error: ${e.message}")
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace(System.err)
            appendTextToResult("Error: ${e.message}")
        }
    }

    private fun sendReceiveValidateInput(includeData: Boolean = true): Boolean {
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

    private fun getDataSize(): Int {
        val dataSizeText = binding.dataSize.text.toString()

        // should not occur if validated beforehand
        if (dataSizeText.isEmpty()) throw IllegalArgumentException("invalid data size")

        return dataSizeText.toInt()
    }

    private fun getChosenPeer(): Peer {
        // should not occur if validated beforehand
        if (chosenPeer == null) throw IllegalArgumentException("invalid peer")

        return chosenPeer as Peer
    }

    private fun convertDataToUTF8(data: ByteArray): String {
        val utf8String = String(data, Charsets.UTF_8)

        return if (utf8String.length > 1000) utf8String.substring(0, 1000) else utf8String
    }

    private fun setTextToResult(text: String) {
        appendTextToResult(text, false)
    }

    private fun appendTextToResult(text: String, newline: Boolean = true) {
        var oldText = binding.txtResult.text.toString() + "\n"
        if (binding.txtResult.text.isEmpty() || !newline) {
            oldText = ""
        }
        val currentTime = LocalDateTime.now()
        val formattedTime = currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
        binding.txtResult.text = oldText + formattedTime + " | " + text
    }

    private fun calculateTimeStats(startTime: LocalDateTime, dataAmount: Int): String {
        val endTime = LocalDateTime.now()
        val duration: Duration = Duration.between(startTime, endTime)

        // calculate time stats
        val miliseconds: Long = duration.toMillis() % 1000
        val seconds: Long = duration.getSeconds() % 60
        val minutes: Long = duration.toMinutes() % 60

        // speed in Kb per second
        var speed: Double = (dataAmount / 1024).toDouble() / (duration.toMillis() / 1000).toDouble()

        if (speed.isInfinite()) {
            speed = 10000.0; // 10 Mb/s
        }

        val result = "$minutes:$seconds.$miliseconds (${String.format("%.3f", speed)} Kb/s)"
        return result
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

    fun getFileNames(folderPath: String): Array<String> {
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
        val voteFiles = getFileNames("")
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

    fun readCsvToByteArray(fileName: String): ByteArray {
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
 //        binding.txtResult.text = "Available Peers: ${availablePeers.keys} \nData Size: ${binding.dataSize.text} \nChosen Peer: $chosenPeer"
    }

    class ReceiveUTP(
        val activity: FragmentActivity?,
        val uTPBatchFragment: uTPBatchFragment
    ) : OnOpenPortResponseListener {

        var isReceiving: Boolean = false



        override fun onOpenPortResponse(source: IPv4Address, dataSize: Int?) {
            try{
                receiveData(source, dataSize)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }

        private fun receiveData(source: IPv4Address, dataSize: Int?) {
            if (isReceiving) {
                return
            }

            isReceiving = true

            try {
                if (uTPBatchFragment.sendReceiveValidateInput(false)) {
                    val receiverPort: Int = 9999
                    val senderPort: Int = source.port

                    setUpReceiver(receiverPort, source, dataSize)

                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isReceiving = false
            }

        }

        private fun setUpReceiver(receiverPort: Int, sender: IPv4Address, dataSize: Int?) {
            try {
                if (dataSize == 0 || dataSize == null) {
                    throw IllegalArgumentException("Invalid data size received from server")
                }

                // socket is defined by the sender's ip and chosen sender port
                val socket = InetSocketAddress(InetAddress.getByName(sender.ip), sender.port)

                // instantiate client to receive data
                val c = UtpSocketChannelImpl()
                try {
                    c.dgSocket = DatagramSocket(receiverPort)
                    c.state = CLOSED
                } catch (exp: IOException) {
                    throw IOException("Could not open UtpSocketChannel: ${exp.message}")
                }
                val channel: UtpSocketChannel = c
                activity?.runOnUiThread {
                    uTPBatchFragment.appendTextToResult("Starting receiver on port $receiverPort for sender port ${sender.port}")
                }


                val cFut = channel.connect(socket) // connect to server/sender
                cFut.block() // block until connection is established

                val startTime = LocalDateTime.now()
                activity?.runOnUiThread {
                    uTPBatchFragment.appendTextToResult("Connected to sender (${socket.toString()})")
                }

                // Allocate space in buffer and start receiving
                val buffer = ByteBuffer.allocate(dataSize)
                val readFuture = channel.read(buffer)
                readFuture.block() // block until all data is received

                // Rewind the buffer to make sure you're reading from the beginning
                buffer.rewind()

                // Convert the buffer to a byte array
                val data = ByteArray(buffer.remaining())
                buffer.get(data)
                val timeStats = uTPBatchFragment.calculateTimeStats(startTime, dataSize)
                activity?.runOnUiThread {
                    uTPBatchFragment.appendTextToResult("Received all ${data.size/1024} Kb of data in $timeStats")
                }



                activity?.runOnUiThread {
                    uTPBatchFragment.appendTextToResult("Received data: \n${uTPBatchFragment.convertDataToUTF8(data)}")
                }

                channel.close()
                activity?.runOnUiThread {
                    uTPBatchFragment.appendTextToResult("Channel closed")
                }

            } catch (e: Exception) {
                e.printStackTrace(System.err)
                activity?.runOnUiThread {
                    uTPBatchFragment.appendTextToResult("Error: ${e.message}")
                }
            }
        }


    }
}


