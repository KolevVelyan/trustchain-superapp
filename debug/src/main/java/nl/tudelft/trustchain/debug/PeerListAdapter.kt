package nl.tudelft.trustchain.debug

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import nl.tudelft.ipv8.Peer
import java.util.Date

class PeerListAdapter(
    private val context: Context,
    resource: Int,
    peerConnectionList: List<Peer?>,
    private val incoming: Boolean,
    private val peersUTPExchange: HashMap<Peer, UTPExchange>
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

        val lastRequest = peer.lastRequest
        if (lastRequest != null) {
            if (Date().time - lastRequest.time < 200) {
                animate(holder.mSentIndicator)
            }
        }

        val lastResponse = peer.lastResponse
        if (lastResponse != null) {
            if (Date().time - lastResponse.time < 200) {
                animate(holder.mReceivedIndicator)
            }
        }

        val lastUTPReceive = peersUTPExchange[peer]?.lastUTPReceive
        if (lastUTPReceive != null && incoming) {
            val msSinceLastReceived = Date().time - lastUTPReceive.time

            if (msSinceLastReceived > 20 * 1000) {
                holder.mStatusIndicator!!.background = ContextCompat.getDrawable(context, R.drawable.peer_indicator_red)
            } else if (msSinceLastReceived > 10 * 1000) {
                holder.mStatusIndicator!!.background = ContextCompat.getDrawable(context, R.drawable.peer_indicator_yellow)
            } else {
                holder.mStatusIndicator!!.background = ContextCompat.getDrawable(context, R.drawable.peer_indicator_green)
            }
        }

        val lastUTPSent = peersUTPExchange[peer]?.lastUTPSent
        if (lastUTPSent != null && !incoming) {
            val msSinceLastSent = Date().time - lastUTPSent.time

            if (msSinceLastSent > 20 * 1000) {
                holder.mStatusIndicator!!.background = ContextCompat.getDrawable(context, R.drawable.peer_indicator_red)
            } else if (msSinceLastSent > 10 * 1000) {
                holder.mStatusIndicator!!.background = ContextCompat.getDrawable(context, R.drawable.peer_indicator_yellow)
            } else {
                holder.mStatusIndicator!!.background = ContextCompat.getDrawable(context, R.drawable.peer_indicator_green)
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

    companion object {
        fun getSplitMID(mid: String): String {
            return mid.substring(0, mid.length / 2) +
                "\n" + mid.substring(mid.length / 2)
        }
    }
}
