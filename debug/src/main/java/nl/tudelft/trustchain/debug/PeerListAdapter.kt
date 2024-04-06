package nl.tudelft.trustchain.debug

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import nl.tudelft.ipv8.Peer
import java.util.Date

/**
 * Adapter for the list of peers in the network.
 */
class PeerListAdapter(
    private val context: Context,
    resource: Int,
    peerConnectionList: List<Peer?>,
) :
    ArrayAdapter<Peer?>(context, resource, peerConnectionList) {
    override fun getView(position: Int, convertViewVar: View?, parent: ViewGroup): View {
        var convertView = convertViewVar
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
            convertView.tag = holder
        } else {
            holder = convertView.tag as ViewHolder
        }



        val peer: Peer? = getItem(position)
        holder.mPeerId!!.text = getSplitMID(peer?.mid!!)
        holder.mDestinationAddress!!.text = peer.wanAddress.toString()

//        val lastRequest = peer.lastRequest
//        if (lastRequest != null) {
//            if (Date().time - lastRequest.time < 200) {
//                animate(holder.mSentIndicator)
//            }
//        }
//
//        val lastResponse = peer.lastResponse
//        if (lastResponse != null) {
//            if (Date().time - lastResponse.time < 200) {
//                animate(holder.mReceivedIndicator)
//            }
//        }

        val statusIndicator = getStatusIndicator(peer.lastResponse, context)
        if (statusIndicator != null) {
            holder.mStatusIndicator!!.background = statusIndicator
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

    companion object {
        fun getSplitMID(mid: String): String {
            return mid.substring(0, mid.length / 2) +
                "\n" + mid.substring(mid.length / 2)
        }

        fun getStatusIndicator(lastOccurrence: Date?, context: Context): Drawable? {
            if (lastOccurrence == null) {
                return null
            }

            val msSinceLastOccurrence = Date().time - lastOccurrence.time
            return if (msSinceLastOccurrence > 20 * 1000) {
                ContextCompat.getDrawable(context, R.drawable.peer_indicator_red)
            } else if (msSinceLastOccurrence > 10 * 1000) {
                ContextCompat.getDrawable(context, R.drawable.peer_indicator_yellow)
            } else {
                ContextCompat.getDrawable(context, R.drawable.peer_indicator_green)
            }
        }
    }
}
