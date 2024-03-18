package nl.tudelft.trustchain.debug

import android.R.attr.port
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.utp4j.channels.UtpServerSocketChannel
import net.utp4j.channels.UtpSocketChannel
import net.utp4j.channels.UtpSocketState.CLOSED
import net.utp4j.channels.impl.UtpSocketChannelImpl
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.common.ui.BaseFragment
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.debug.databinding.FragmentUtpbatchBinding
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.Arrays
import java.util.Date

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class uTPBatchFragment : BaseFragment(R.layout.fragment_utpbatch) {
    private val binding by viewBinding(FragmentUtpbatchBinding::bind)

    private var sent = 0
    private var received = 0

    private var availablePeers = HashMap<IPv4Address, Peer>()
    private var chosenPeer: Peer? = null

    private var availableVotes : Array<String> = emptyArray()
    private var chosenVote: String = ""


    private val receivedMap = mutableMapOf<String, Int>()
    private val firstMessageTimestamps = mutableMapOf<String, Date>()
    private var firstSentMessageTimestamp: Date? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

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
                received++
                receivedMap[peer.toString()] = (receivedMap[peer.toString()] ?: 0) + 1
                if (firstMessageTimestamps[peer.toString()] == null) {
                    firstMessageTimestamps[peer.toString()] = Date()
                }
            }
        }

        binding.btnSend.setOnClickListener {
            if (sendReceiveValidateInput()) {
                val senderPort: Int = 8093

                setUpSender(senderPort)
            }
        }

        binding.btnConnect.setOnClickListener {
            if (sendReceiveValidateInput()) {
                val senderPort: Int = 8093
                val senderWan = getChosenPeer().wanAddress
                puncturePortOfSender(senderWan, senderPort)
            }
        }

        binding.btnReceive.setOnClickListener {
           if (sendReceiveValidateInput()) {
               do {
               } while (getDemoCommunity().serverWanPort == null)

               val receiverPort: Int = 9999
               val senderPort: Int = getDemoCommunity().serverWanPort!!

               setUpReceiver(receiverPort, senderPort)

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
        if (chosenVote == "") {
            transferAmount = getDataSize() * 1024
            // data to send
            byteData = ByteArray(transferAmount)
            Arrays.fill(
                byteData,
                0xAF.toByte()
            ) // currently data is just the byte 0xAF over and over
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
                val channel = acceptFuture.channel

                // send data on newly established channel (with client/receiver)
                val out = ByteBuffer.allocate(transferAmount)
                out.put(byteData)
                val fut = channel.write(out)
                fut.block() // block until all data is sent
                appendTextToResult("Sent all $transferAmount bytes of data")

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

    private fun setUpReceiver(receiverPort: Int, senderPort: Int) {
        var transferAmount = getDemoCommunity().receivedDataSize
//        if (chosenVote == "") {
//            transferAmount = getDataSize() * 1024
//        } else {
//            val byteData = readCsvToByteArray(chosenVote)
//            transferAmount = byteData.size
//            transferAmount = 3351188
//        }

        try {
            setTextToResult("Starting receiver on port $receiverPort for sender port $senderPort")

            // socket is defined by the sender's ip and chosen sender port
            val socket = InetSocketAddress(InetAddress.getByName(getChosenPeer().wanAddress.ip), senderPort)

            appendTextToResult("Socket of sender (${socket.toString()}) set up")


            // instantiate client to receive data
            val c = UtpSocketChannelImpl()
            try {
                c.dgSocket = DatagramSocket(receiverPort)
                c.state = CLOSED
            } catch (exp: IOException) {
                throw IOException("Could not open UtpSocketChannel: ${exp.message}")
            }
            val channel: UtpSocketChannel = c
            appendTextToResult("Channel set up on port $receiverPort")



            val cFut = channel.connect(socket) // connect to server/sender
            cFut.block() // block until connection is established
            appendTextToResult("Connected to sender")

            // Allocate space in buffer and start receiving
            val buffer = ByteBuffer.allocate(transferAmount)
            val readFuture = channel.read(buffer)
            readFuture.block() // block until all data is received
            appendTextToResult("Received all $transferAmount bytes of data")

            // Rewind the buffer to make sure you're reading from the beginning
            buffer.rewind()

            // Convert the buffer to a byte array
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            appendTextToResult("Received data: ${converDataToHex(data)}")

            channel.close()
            appendTextToResult("Channel closed")

        } catch (e: Exception) {
            e.printStackTrace(System.err)
            appendTextToResult("Error: ${e.message}")
        }
    }

    private fun sendReceiveValidateInput(): Boolean {
        val context: Context = requireContext()

        if (chosenPeer == null) {
            Toast.makeText(context, "Invalid peer", Toast.LENGTH_SHORT).show()
            return false
        }

        if (binding.dataSize.text.toString().isEmpty()) {
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

    private fun converDataToHex(data: ByteArray): String {
        var maxBytes = 3 * 1024
        val hexString = buildString {
            for (byte in data) {
                if (maxBytes <= 0) {
                    append("...")
                    break
                }
                append("%02X".format(byte))
                maxBytes--
            }
        }

        return hexString
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
                getDemoCommunity().currentDataSize = readCsvToByteArray(chosenVote).size
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
}
