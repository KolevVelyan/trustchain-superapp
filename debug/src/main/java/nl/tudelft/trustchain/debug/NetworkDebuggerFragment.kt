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
import nl.tudelft.ipv8.keyvault.Key
import nl.tudelft.trustchain.common.ui.BaseFragment
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.debug.databinding.FragmentNetworkDebuggerBinding
import nl.tudelft.trustchain.common.OnUTPSendRequestListener


class NetworkDebuggerFragment : BaseFragment(R.layout.fragment_network_debugger), OnUTPSendRequestListener {
    private val binding by viewBinding(FragmentNetworkDebuggerBinding::bind)
    private var receivingDialog: UTPReceiveDialogFragment? = null // last dialog used to receive data from a peer
    private var peerList: List<Peer> = emptyList() // list of peers in the community

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        // add yourself to listeners to receive UTP send requests
        getDemoCommunity().addListener(this)

        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        lifecycleScope.launchWhenCreated {
            while (isActive) {
                updateView()
                delay(100)
            }
        }
    }

    // This method is called when a UTP send request is received
    // It opens a new dialog to receive the data of the sender
    override fun onUTPSendRequest(sender: IPv4Address, dataSize: Int?) {
        // find the peer that sent the request
        val peer = peerList.find { it.address.ip == sender.ip }
        val myKey = getDemoCommunity().myPeer.key

        // if sender is not in peers then display warning to user and ignore request
        if (peer == null) {
            activity?.runOnUiThread {
                Toast.makeText(
                    requireContext(),
                    "Request to get the data of an unknown peer (${sender.ip}:${sender.port}) was received. Ignoring.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            Log.e("UTP", "Received send request from unknown peer ${sender.ip}:${sender.port}")
            return
        }

        // if sender is one of the peers that are known but have not been discovered yet, display warning to user and ignore request
        if (peer.key == myKey) {
            activity?.runOnUiThread {
                Toast.makeText(
                    requireContext(),
                    "Not allowed to receive data from peers in discovery phase",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        // close any open receiving dialogs
        receivingDialog?.dismiss()

        if (peer.address.ip == "0.0.0.0") {
            activity?.runOnUiThread {
                Toast.makeText(
                    context,
                    "Not allowed to receive data from ${peer.address.ip}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            try {
                // open a new dialog to receive the data
                val utpDialog = UTPReceiveDialogFragment(peer, getDemoCommunity(), dataSize)
                utpDialog.show(parentFragmentManager, UTPReceiveDialogFragment.TAG)
                receivingDialog = utpDialog
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(UTPReceiveDialogFragment.TAG, "Error: ${e.message}")
            }
        }
    }

    // This method updates the peer list and handles clicking a peer by creating a UTPSendDialogFragment
    private fun updatePeerList()  {
        val context: Context = requireContext()

        // update the peer list based on the peers in the community
        peerList = getDemoCommunity().getPeers()

        // add walkable peers to peer list with their IPv4 address and my peer's key
        val walkablePeers = getDemoCommunity().getWalkableAddresses()
        val myPeerKey = getDemoCommunity().myPeer.key // use my key to create a peer object for the list
        for (ipv4address in walkablePeers) {
                peerList += Peer(myPeerKey, ipv4address)
        }

        val peerListAdapter = PeerListAdapter(context, R.layout.peer_connection_list_item, myPeerKey, peerList)
        binding.peerConnectionListView.adapter = peerListAdapter

        // handle click on peer (opens UTP send dialog)
        binding.peerConnectionListView.setOnItemClickListener { _, _, position, _ ->
            val peer = peerList[position]
            val myKey = getDemoCommunity().myPeer.key

            if (peer.key == myKey) {
                activity?.runOnUiThread {
                    Toast.makeText(
                        context,
                        "Not allowed to send data to peers in discovery phase",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else if (peer.address.ip == "0.0.0.0") {
                activity?.runOnUiThread {
                    Toast.makeText(
                        context,
                        "Not allowed to send data to ${peer.address.ip}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                try {
                    // open a new dialog to send data to the clicked peer
                    val utpDialog = UTPSendDialogFragment(peer, getDemoCommunity()) as DialogFragment
                    utpDialog.show(parentFragmentManager, UTPSendDialogFragment.TAG)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e(UTPSendDialogFragment.TAG, "Error: ${e.message}")
                }
            }
        }

    }

    // Update details on screen of the user's peer
    private fun updateMyDetails() {
        activity?.runOnUiThread {
            val ipv8 = getIpv8()
            val demo = getDemoCommunity()
            binding.lanAddress.text = demo.myEstimatedLan.toString()
            binding.wanAddress.text = demo.myEstimatedWan.toString()
            binding.connectionType.text = demo.network.wanLog
                .estimateConnectionType().value
            binding.peerId.text = PeerListAdapter.getSplitMID(ipv8.myPeer.mid)
        }
    }

    private fun updateView() {
        // continuously update the peer list and the user's details
        updatePeerList()
        updateMyDetails()
    }
}


