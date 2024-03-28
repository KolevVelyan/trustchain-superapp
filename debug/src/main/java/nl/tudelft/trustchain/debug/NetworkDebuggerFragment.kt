package nl.tudelft.trustchain.debug


import android.content.Context
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.view.View
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.common.ui.BaseFragment
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.debug.databinding.FragmentNetworkDebuggerBinding


class NetworkDebuggerFragment : BaseFragment(R.layout.fragment_network_debugger) {
    private val binding by viewBinding(FragmentNetworkDebuggerBinding::bind)

    private var peerList: List<Peer> = emptyList()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        lifecycleScope.launchWhenCreated {
            while (isActive) {
                updateView()
                delay(100)
            }
        }

    }

    private fun updatePeerList()  {
        peerList = getDemoCommunity().getPeers()

        val context: Context = requireContext()
//        val incomingPeerAdapter = PeerListAdapter(context, R.layout.peer_connection_list_item, peerList, true)
//        binding.incomingPeerConnectionListView.adapter = incomingPeerAdapter
    }

    private fun updateView() {
        updatePeerList()

        val ipv8 = getIpv8()
        val demo = getDemoCommunity()
        binding.lanAddress.text = demo.myEstimatedLan.toString()
        binding.wanAddress.text = demo.myEstimatedWan.toString()
        binding.connectionType.text = demo.network.wanLog
            .estimateConnectionType().value
        binding.peerId.text = PeerListAdapter.getSplitMID(ipv8.myPeer.mid)
    }
}
