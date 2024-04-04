package nl.tudelft.trustchain.debug

import android.content.Context
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.common.ui.BaseFragment
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.debug.databinding.FragmentNetworkDebuggerBinding
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class NetworkDebuggerFragment : BaseFragment(R.layout.fragment_network_debugger), UTPDataFragment {
    private val binding by viewBinding(FragmentNetworkDebuggerBinding::bind)

    private var receiver: UTPReceiver? = null

    private var peerList: List<Peer> = emptyList()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        receiver = UTPReceiver(this, getDemoCommunity())
//        getDemoCommunity().addListener(receiver!!)

        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        lifecycleScope.launchWhenCreated {
            while (isActive) {
                updateView()
                delay(100)
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

    override fun newDataReceived(success: Boolean, data: ByteArray, source: IPv4Address, msg: String) {
        if (!success) {
            appendTextToResult(msg)
            return
        }

        // update peer's last received time for UTP
        val peer = peerList.find { it.wanAddress.ip == source.ip }
        if (peer != null) {
            Log.i("UTP", "Received data from known ${source.ip}")
        } else {
            Log.e("UTP", "Received data from unknown peer")
        }


        val dataSize = data.size
        val dataSizeInKB = dataSize / 1024
        appendTextToResult("Received data from $source with size $dataSizeInKB KB")
    }

    override fun newDataSent(success: Boolean, destinationAddress: String, msg: String) {
        appendTextToResult("Unexpectedly sending data. SHOULD NOT HAPPEN!")
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
            binding.txtResult.text = "$oldText$formattedTime | $text"
        }
    }

    private fun updatePeerList()  {
        peerList = getDemoCommunity().getPeers()
        val context: Context = requireContext()

        val peerListAdapter = PeerListAdapter(context, R.layout.peer_connection_list_item, peerList)
        binding.peerConnectionListView.adapter = peerListAdapter

        binding.peerConnectionListView.setOnItemClickListener { _, _, position, _ ->
            val peer = peerList[position]

            if (peer.address.ip == "0.0.0.0") {
                Toast.makeText(context, "Not allowed to send data to ${peer.address.ip}", Toast.LENGTH_SHORT).show()
            } else {
                val utpDialog = UTPSendDialogFragment(peer, getDemoCommunity()) as DialogFragment
                utpDialog.show(parentFragmentManager, UTPSendDialogFragment.TAG)
            }
        }

    }

//    private fun showInfoDialog() {
//        val dialogView = LayoutInflater.from(context).inflate(R.layout.info_popup_layout, null)
//
//        val dialogBuilder = AlertDialog.Builder(context)
//            .setView(dialogView)
//            .setPositiveButton("OK") { dialog, _ ->
//                dialog.dismiss()
//            }
//
//        val alertDialog = dialogBuilder.create()
//        alertDialog.show()
//    }

    private fun updateMyDetails() {
        val ipv8 = getIpv8()
        val demo = getDemoCommunity()
        binding.lanAddress.text = demo.myEstimatedLan.toString()
        binding.wanAddress.text = demo.myEstimatedWan.toString()
        binding.connectionType.text = demo.network.wanLog
            .estimateConnectionType().value
        binding.peerId.text = PeerListAdapter.getSplitMID(ipv8.myPeer.mid)
    }

    private fun updateView() {
        updatePeerList()
        updateMyDetails()
    }
}


