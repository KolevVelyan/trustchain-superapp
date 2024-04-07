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
        appendTextToResult("Received ${dataSize/1024}Kb from ${source}:")
        appendTextToResult(convertDataToUTF8(data))
    }

    // Handle confirmation of the data being sent (should not happen as we have only started a receiver)
    override fun newDataSent(success: Boolean, destinationAddress: String, msg: String) {
        appendTextToResult("Unexpectedly sending data. SHOULD NOT HAPPEN!")
    }

    override fun receiveSpeedUpdate(dataSent: Long, dataReceived: Long) {
        binding!!.txtDataSpeed.text = "Sent: $dataSent B/s\nReceived: $dataReceived B/s"
    }

    // Convert data to UTF-8 string (max 200 characters)
    private fun convertDataToUTF8(data: ByteArray): String {
        val maxCharToPrint = 200
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

    private fun updateView() {
        updatePeerDetails()
    }
    companion object {
        const val TAG = "UTPReceiveDialogFragment"
    }
}
