package nl.tudelft.trustchain.debug

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.debug.databinding.FragmentUtpReceiveBinding
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/**
 * Dialog fragment that receives data through UTP
 */
class UTPReceiveDialogFragment(private val otherPeer: Peer,
                               private val community: Community,
                               private val dataSize: Int?)
        : DialogFragment(), UTPDataFragment {
    private var binding: FragmentUtpReceiveBinding? = null // binding for the fragments view

    // the receiver object that receives the data
    private var receiver = UTPReceiver(this@UTPReceiveDialogFragment, community)

    private var updateSpeed: Boolean = true

    private var initialPacketTime: LocalDateTime = LocalDateTime.now()
    private var lastSentTime: LocalDateTime = LocalDateTime.now()
    private var lastReceivedTime: LocalDateTime = LocalDateTime.now()

    private var totalDataSent: Int = 0
    private var totalDataReceived: Int = 0

    private var sentSeqNum: Int = 0
    private var sentAckNum: Int = 0
    private var receivedSeqNum: Int = 0
    private var receivedAckNum: Int = 0

    private var sentPackets: Int = 0
    private var receivedPackets: Int = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // inflate the view to get the correct layout
        val dialogView = layoutInflater.inflate(R.layout.fragment_utp_receive, null)
        binding = FragmentUtpReceiveBinding.bind(dialogView)

        // make the dialog
        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setTitle("Receive Data through UTP")
            .setCancelable(false)
            .setNeutralButton("Close") { _, _ -> }

        lifecycleScope.launchWhenCreated {
            // start receiver in new thread
            val thread = Thread {
                receiver.receiveData(otherPeer.address, dataSize)
            }
            thread.start()

            while (isActive) {
                updateView()
                delay(100)
            }
        }

        return dialogBuilder.create()
    }

    // This method is called when the dialog is dismissed
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // when closing the dialog if the receiver is still receiving, inform user that the transfer has been stopped
        if (receiver.isReceiving()) {
            Toast.makeText(requireContext(), "UTP transfer from ${otherPeer.address} has been stopped", Toast.LENGTH_SHORT).show()
        }
    }

    // Get debug information from the receiver
    override fun debugInfo(info: String, toast: Boolean, reset: Boolean) {
        if (reset) {
            setTextToResult(info)
        } else {
            appendTextToResult(info)
        }
    }

    // Receive new data form a sender
    override fun newDataReceived(success: Boolean, data: ByteArray, source: IPv4Address, msg: String) {
        // if unsuccessful, display the error message
        if (!success) {
            appendTextToResult(msg)
            return
        }

        // otherwise display the data
        val dataSize = data.size
        appendTextToResult("Received ${dataSize/1024}Kb from ${source}:\n${convertDataToUTF8(data)}")
    }

    // Handle confirmation of the data being sent (should not happen as we have only started a receiver)
    override fun newDataSent(success: Boolean, destinationAddress: String, msg: String) {
        appendTextToResult("Unexpectedly sending data. SHOULD NOT HAPPEN!")
    }

    override fun receiveSpeedUpdate(isSentPacket: Boolean, packetSize: Int, seqNum: Int, ackNum: Int) {
        updateSpeed = true

        if (isSentPacket) {
            sentPackets++
            if (seqNum > sentSeqNum) {
                sentSeqNum = seqNum // always 0 as the receiver only sends 1 packet on connection
            }
            if (ackNum > sentAckNum) {
                sentAckNum = ackNum // up to which packet the receiver has received
            }
            totalDataSent += packetSize
            lastSentTime = LocalDateTime.now()
        } else {
            receivedPackets++
            if (seqNum > receivedSeqNum) {
                receivedSeqNum = seqNum // last packet the sender sent
            }
            if (ackNum > receivedAckNum) {
                receivedAckNum = ackNum // should become 1 when connected and stay 1 as the sender acknowledges only the initial connection packet
            }
            totalDataReceived += packetSize
            lastReceivedTime = LocalDateTime.now()
        }
    }

    // Convert data to UTF-8 string (max 200 characters)
    private fun convertDataToUTF8(data: ByteArray): String {
        val maxCharToPrint = 555
        val utf8String = String(data, Charsets.UTF_8)

        return if (utf8String.length > maxCharToPrint) utf8String.substring(0, maxCharToPrint) else utf8String
    }

    // Update the output text and reset it
    private fun setTextToResult(text: String) {
        appendTextToResult(text, false)
    }

    // Append the output text to the result (if newline is false, reset the text)
    private fun appendTextToResult(text: String, newline: Boolean = true) {
        activity?.runOnUiThread {
            var oldText = binding!!.txtResult.text.toString() + "\n"
            if (binding!!.txtResult.text.isEmpty() || !newline) {
                oldText = ""
            }
            val currentTime = LocalDateTime.now()
            // add time in front of the text
            val formattedTime = currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
            binding!!.txtResult.text = "$oldText$formattedTime | $text"
        }
    }

    // Update the peer details on the screen
    private fun updatePeerDetails() {
        val currBinding = binding ?: return

        currBinding.peerId.text = PeerListAdapter.getSplitMID(otherPeer.mid)
        currBinding.lanAddress.text = otherPeer.lanAddress.toString()
        currBinding.wanAddress.text = otherPeer.wanAddress.toString()

        // update status indicator
        val statusIndicator = PeerListAdapter.getStatusIndicator(otherPeer.lastResponse, requireContext())
        if (statusIndicator != null) {
            currBinding.statusIndicator.background = statusIndicator
        }
    }

    // Update the data speed on the screen
    private fun updateDataSpeed() {
        if (!updateSpeed) {return}

        val currBinding = binding ?: return

        val totalSendDiff = lastSentTime.toLocalTime().toNanoOfDay() - initialPacketTime.toLocalTime().toNanoOfDay()
        val totalSendDiffSec = totalSendDiff / 1_000_000_000.0

        val totalRcvdDiff = lastReceivedTime.toLocalTime().toNanoOfDay() - initialPacketTime.toLocalTime().toNanoOfDay()
        val totalRcvdDiffSec = totalRcvdDiff / 1_000_000_000.0

        val kBSent = totalDataSent.toDouble() / 1000.0
        val kBReceived = totalDataReceived.toDouble() / 1000.0

        val avgKBSent = kBSent / totalSendDiffSec
        val avgKBReceived = kBReceived / totalRcvdDiffSec

        val dataSpeed ="Sent ${String.format("%.2f", kBSent)}KB (${String.format("%.2f", avgKBSent)}KB/s) [S:$sentSeqNum, A:$sentAckNum, TP:$sentPackets]\nRcvd ${String.format("%.2f", kBReceived)}KB (${String.format("%.2f", avgKBReceived)}KB/s) [S:$receivedSeqNum, A:$receivedAckNum, TP:$receivedPackets]"

        currBinding.txtDataSpeed.text = dataSpeed

        if (dataSize != null) {
            val totalPackets = ceil(dataSize.toDouble() / 1452.0).toInt()
            currBinding.txtTotalExpect.text = "[$sentAckNum/$totalPackets]"
        }


        updateSpeed = false
    }

    private fun updateView() {
        updatePeerDetails()
        updateDataSpeed()
    }
    companion object {
        const val TAG = "UTPReceiveDialogFragment"
    }
}
