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
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.Arrays
import java.util.Date


class uTPBatchFragment : BaseFragment(R.layout.fragment_utpbatch) {
    private val binding by viewBinding(FragmentUtpbatchBinding::bind)

    private var sent = 0
    private var received = 0

    private var availablePeers = HashMap<IPv4Address, Peer>()
    private var chosenPeer: Peer? = null


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
                val transferAmount: Int = getDataSize() * 1024 * 1024
                val senderPort: Int = 8091

                try {
                    // data to send
                    val bulk = ByteArray(transferAmount)
                    Arrays.fill(
                        bulk,
                        0xAF.toByte()
                    ) // currently data is just the byte 0xAF over and over

                    // socket is defined by the sender's ip and chosen port
                    val socket = InetSocketAddress(
                        InetAddress.getByName(getDemoCommunity().myEstimatedLan.ip),
                        senderPort
                    )

                    // instantiate server to send data (it waits for client to through socket first)
                    try {
                        // socket is defined by the sender's ip and chosen port
                        val server = UtpServerSocketChannel.open()
                        server.bind(socket)

                        // wait until someone connects to server and get new channel
                        val acceptFuture = server.accept()
                        acceptFuture.block()
                        val channel = acceptFuture.channel

                        // send data on newly established channel (with client/receiver)
                        val out = ByteBuffer.allocate(bulk.size)
                        out.put(bulk)
                        val fut = channel.write(out)
                        fut.block() // block until all data is sent


                        channel.close()
                        server.close()
                    } catch (e: java.lang.Exception) {
                        e.printStackTrace(System.err)
                    }
                } catch (e: java.lang.Exception) {
                    e.printStackTrace(System.err)
                }
            }
        }

        fun getAvailablePort(): Int {
            var port = 4444
            while (port < 65535) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(InetAddress.getByName(getDemoCommunity().myEstimatedLan.ip), port), 5)
                    socket.close()
                    return port
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                port++
            }
            throw Exception("There are no available ports.")
        }

        binding.btnReceive.setOnClickListener {
           if (sendReceiveValidateInput()) {
                val transferAmount: Int = getDataSize() * 1024 * 1024
                val senderPort: Int = getChosenPeer().wanAddress.port
                val receiverPort: Int = 9999 //getAvailablePort()// getDemoCommunity().myEstimatedLan.port

                // try to puncture the uTP server port
                getDemoCommunity().openPort(getDemoCommunity().myEstimatedLan, 8091)

                try {
                    // data to send
                    val bulk = ByteArray(transferAmount)
                    Arrays.fill(bulk, 0xAF.toByte()) // currently data is just the byte 0xAF over and over

                    // socket is defined by the sender's ip and chosen port
                    val socket = InetSocketAddress(InetAddress.getByName(getChosenPeer().wanAddress.ip), 8091)

                    // instantiate client to receive data
                    val c = UtpSocketChannelImpl()
                    try {
                        c.dgSocket = DatagramSocket(receiverPort)
                        c.state = CLOSED
                    } catch (exp: IOException) {
                        throw IOException("Could not open UtpSocketChannel: ${exp.message}") //145,94.218.166:50797
                    }
                    val channel: UtpSocketChannel = c
                    do{} while (getDemoCommunity().serverWanPort == null)


                    val cFut = channel.connect(socket) // connect to server/sender
                    cFut.block() // block until connection is established

                    // Allocate space in buffer and start receiving
                    val buffer = ByteBuffer.allocate(bulk.size)
                    val readFuture = channel.read(buffer)
                    readFuture.block() // block until all data is received

                    channel.close()
                } catch (e: Exception) {
                    e.printStackTrace(System.err)
                }
            }
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


    private fun updateView() {
        updateAvailablePeers()
        binding.txtResult.text = "Available Peers: ${availablePeers.keys} \nData Size: ${binding.dataSize.text} \nChosen Peer: $chosenPeer"
    }
}
