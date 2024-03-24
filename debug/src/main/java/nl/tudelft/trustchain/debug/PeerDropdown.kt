package nl.tudelft.trustchain.debug

import android.content.Context
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer

/**
 * Class to handle the dropdown of available peers.
 *
 * Usage: 1. Create a PeerDropdown instance with the context of the activity.
 *        2. Call updateAvailablePeers with the current list of peers and the AutoCompleteTextView
 *        in UpdateView of the activity to update the list dynamically.
 *        3. Call getChosenPeer to get the selected peer.
 */
class PeerDropdown(
    private val context: Context,
) {
    private var availablePeers = HashMap<IPv4Address, Peer>()
    private var chosenPeer: Peer? = null


    fun updateAvailablePeers(currPeers: List<Peer>, autoComplete: AutoCompleteTextView)  {
        // get current peers
        val newPeersMap = HashMap<IPv4Address, Peer>()
        for (peer in currPeers) {
            newPeersMap[peer.wanAddress] = peer
        }

        if (newPeersMap != availablePeers) {
            // peers have changed need to update
            availablePeers = newPeersMap
            val adapter = ArrayAdapter(context, R.layout.peer_item, availablePeers.keys.toList())

            autoComplete.setAdapter(adapter)
            autoComplete.onItemClickListener = AdapterView.OnItemClickListener {
                    adapterView, _, i, _ ->
                chosenPeer = availablePeers[adapterView.getItemAtPosition(i)]
            }
        }
    }

    fun getChosenPeer(): Peer? {
        return chosenPeer
    }
}
