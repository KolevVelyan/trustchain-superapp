package nl.tudelft.trustchain.debug

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import nl.tudelft.ipv8.Peer
import java.util.Date

/**
 * Created by jaap on 5/4/16.
 */
class PeerListAdapter(
    private val context: Context,
    resource: Int,
    peerConnectionList: List<Peer?>,
    private val incoming: Boolean
) :
    ArrayAdapter<Peer?>(context, resource, peerConnectionList) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val holder: ViewHolder
        if (convertView == null) {
            val inflater =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            convertView = inflater.inflate(R.layout.peer_connection_list_item, parent, false)
            holder = ViewHolder()
            holder.mStatusIndicator =
                convertView!!.findViewById<View>(R.id.status_indicator) as TextView?
            holder.mPeerId = convertView.findViewById<View>(R.id.peer_id) as TextView?
            holder.mDestinationAddress =
                convertView.findViewById<View>(R.id.peer_ip) as TextView?
            holder.mReceivedIndicator =
                convertView.findViewById<View>(R.id.received_indicator) as TextView?
            holder.mSentIndicator = convertView.findViewById<View>(R.id.sent_indicator) as TextView?
            convertView.setTag(holder)
        } else {
            holder = convertView.tag as ViewHolder
        }
        val peer: Peer? = getItem(position)
        holder.mPeerId!!.text = peer?.getSplitMID()
//         temporary fix
        holder.mStatusIndicator!!.setTextColor(Color.GREEN)

//        if (peer.hasReceivedData()) {
//            if (peer.isAlive()) {
//                holder.mStatusIndicator!!.setTextColor(context.resources.getColor(R.color.colorStatusConnected))
//            } else {
//                holder.mStatusIndicator!!.setTextColor(context.resources.getColor(R.color.colorStatusCantConnect))
//            }
//        } else {
//            if (peer.isAlive()) {
//                holder.mStatusIndicator!!.setTextColor(context.resources.getColor(R.color.colorStatusConnecting))
//            } else {
//                holder.mStatusIndicator!!.setTextColor(context.resources.getColor(R.color.colorStatusCantConnect))
//            }
//        }
        holder.mDestinationAddress!!.text = peer?.wanAddress.toString()
        val lastRequest = peer?.lastRequest
        if (lastRequest != null) {
            if (Date().time - lastRequest.time < 200) {
                animate(holder.mSentIndicator)
            }
        }
        val lastResponse = peer?.lastResponse
        if (lastResponse != null) {
            if (Date().time - lastResponse.time < 200) {
                animate(holder.mReceivedIndicator)
            }
        }
        return convertView
    }

    private fun animate(view: View?) {
        view!!.alpha = 1f
        view.animate().alpha(0f).setDuration(500).start()
    }

    internal class ViewHolder {
        var mPeerId: TextView? = null
        var mDestinationAddress: TextView? = null
        var mStatusIndicator: TextView? = null
        var mReceivedIndicator: TextView? = null
        var mSentIndicator: TextView? = null
    }
}
