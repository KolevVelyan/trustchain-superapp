package nl.tudelft.trustchain.debug

import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.utp4j.channels.UtpServerSocketChannel
import net.utp4j.channels.UtpSocketChannel
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.trustchain.common.ui.BaseFragment
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.debug.databinding.FragmentUtpbatchBinding
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date


class uTPBatchFragment : BaseFragment(R.layout.fragment_utpbatch) {
    private val binding by viewBinding(FragmentUtpbatchBinding::bind)

    private var sent = 0
    private var received = 0



    private val receivedMap = mutableMapOf<String, Int>()
    private val firstMessageTimestamps = mutableMapOf<String, Date>()
    private var firstSentMessageTimestamp: Date? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        var items = listOf("adff", "ffff", "ffff", "ffff", "ffff")
        var autoComplete: AutoCompleteTextView = view.findViewById(R.id.auto_complete_txt)
        var adapter = ArrayAdapter(view.context, R.layout.peer_item, items)

        autoComplete.setAdapter(adapter)
        autoComplete.onItemClickListener = AdapterView.OnItemClickListener {
                adapterView, view, i, l ->


            val itemSelected = adapterView.getItemAtPosition(i)
            Log.i("HELLO", itemSelected.toString())

            Toast.makeText(view.context, "Item: $itemSelected", Toast.LENGTH_SHORT).show()
        }




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
//            val address = binding.edtAddress.text.toString().split(":")
//            if (address.size == 2) {
//                val ip = address[0]
//                val port = address[1].toIntOrNull() ?: MIN_PORT
//
//                lifecycleScope.launchWhenCreated {
//                    firstSentMessageTimestamp = Date()
//                    if (binding.sweep.isChecked) {
//                        punctureAll(ip, false)
//                        delay(30000)
//                        punctureAll(ip, true)
//                    } else {
//                        punctureSingle(ip, port)
//                    }
//                }
//            }
//
//            try {
//                // stub data to send
//                val bulk = ByteArray(10 * 1024)
//                Arrays.fill(bulk, 0xAF.toByte())
//                // 1752 bytes per packets
//
//                val local = InetSocketAddress(InetAddress.getByName("145.94.188.69"), 12350)
//
//                // The Server.
//                try {
//                    val server = UtpServerSocketChannel.open()
//                    server.bind(local)
//                    val acceptFuture = server.accept()
//                    acceptFuture.block()
//                    val channel = acceptFuture.channel
//                    val out = ByteBuffer.allocate(bulk.size)
//                    out.put(bulk)
//                    // Send data
//                    val fut = channel.write(out)
//                    fut.block()
//                    channel.close()
//                    server.close()
//                } catch (e: java.lang.Exception) {
//                    e.printStackTrace(System.err)
//                }
//            } catch (e: java.lang.Exception) {
//                e.printStackTrace(System.err)
//            }
        }

        binding.btnReceive.setOnClickListener {
//            val address = binding.edtAddress.text.toString().split(":")
//
//            try {
//                // stub data to send
//                val bulk = ByteArray(10 * 1024)
//                Arrays.fill(bulk, 0xAF.toByte())
//
//                val local = InetSocketAddress(InetAddress.getLocalHost(), 12350)
//
//                // The Client.
//                val channel = UtpSocketChannel.open()
//                val cFut = channel.connect(local)
//                cFut.block()
//                val buffer = ByteBuffer.allocate(bulk.size)
//                val readFuture = channel.read(buffer)
//                readFuture.block()
//                channel.close()
//            } catch (e: Exception) {
//                e.printStackTrace(System.err)
//            }
        }
    }

    /*
    private suspend fun punctureMultiple(ip: String, port: Int) {
        Log.d("PunctureFragment", "Puncture multiple with initial port: $ip $port")
        val minPort = max(port - SEARCH_BREADTH/2, MIN_PORT)
        val maxPort = min(port + SEARCH_BREADTH/2, MAX_PORT)
        for (i in  minPort .. maxPort) {
            val ipv4 = IPv4Address(ip, i)
            getDemoCommunity().sendPuncture(ipv4, i)
            sent++
            updateView()

            if (i % 10 == 0) {
                delay(40)
            }
        }
    }
     */

    private suspend fun punctureAll(
        ip: String,
        slow: Boolean
    ) = with(Dispatchers.Default) {
        for (i in MIN_PORT..MAX_PORT) {
            val ipv4 = IPv4Address(ip, i)
            getDemoCommunity().sendPuncture(ipv4, i)
            sent++

            if (i % 1000 == 0) {
                delay(if (slow) 30000L else 1L)
            }
        }
    }

    private suspend fun punctureSingle(
        ip: String,
        port: Int
    ) {
        while (true) {
            val ipv4 = IPv4Address(ip, port)
            getDemoCommunity().sendPuncture(ipv4, port)
            sent++

            delay(1000)
        }
    }

    private fun updateView() {
        val df = SimpleDateFormat.getTimeInstance(SimpleDateFormat.MEDIUM)
        binding.txtResult.text = getDemoCommunity().getPeers().toString()
    }

    companion object {
        const val MIN_PORT = 1024
        const val MAX_PORT = 65535
        const val SEARCH_BREADTH = 1000
    }
}
