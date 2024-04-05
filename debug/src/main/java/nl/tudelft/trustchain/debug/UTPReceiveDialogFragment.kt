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


class UTPReceiveDialogFragment(private val otherPeer: Peer,
                               private val community: Community,
                               private val dataSize: Int?)
        : DialogFragment(), UTPDataFragment {
    private var binding: FragmentUtpReceiveBinding? = null

    private var receiver: UTPReceiver? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = layoutInflater.inflate(R.layout.fragment_utp_receive, null)
        binding = FragmentUtpReceiveBinding.bind(dialogView)

        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setTitle("Receive Data through UTP")
            .setCancelable(false)
            .setNeutralButton("Close") { _, _ -> }


        receiver = UTPReceiver(this@UTPReceiveDialogFragment, community)

        lifecycleScope.launchWhenCreated {
            val thread = Thread {
                receiver!!.receiveData(otherPeer.address, dataSize)
            }
            thread.start()

            while (isActive) {
                updateView()
                delay(100)
            }
        }

        return dialogBuilder.create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        val nReceiver = receiver

        if (nReceiver != null && nReceiver.isReceiving()) {
            Toast.makeText(requireContext(), "UTP transfer from ${otherPeer.address} has been stopped", Toast.LENGTH_SHORT).show()
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
            var oldText = binding!!.txtResult.text.toString() + "\n"
            if (binding!!.txtResult.text.isEmpty() || !newline) {
                oldText = ""
            }
            val currentTime = LocalDateTime.now()
            val formattedTime = currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
            binding!!.txtResult.text = "$oldText$formattedTime | $text"
        }
    }

    private fun updatePeerDetails() {
        binding!!.peerId.text = PeerListAdapter.getSplitMID(otherPeer.mid)
        binding!!.lanAddress.text = otherPeer.lanAddress.toString()
        binding!!.wanAddress.text = otherPeer.wanAddress.toString()
    }

    private fun updateView() {
        updatePeerDetails()
    }
    companion object {
        const val TAG = "UTPReceiveDialogFragment"
    }
}
