package nl.tudelft.trustchain.debug

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.debug.databinding.FragmentUtpSendBinding
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Arrays


class UTPSendDialogFragment(private val otherPeer: Peer,
                            private val community: Community,
                            private val utpDialogListener: UTPDialogListener)
        : DialogFragment(), UTPDataFragment {
    private var binding: FragmentUtpSendBinding? = null

    private var sender: UTPSender? = null
    private var chosenVote: String = ""
    private var availableVotes : Array<String> = emptyArray()
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = layoutInflater.inflate(R.layout.fragment_utp_send, null)
        binding = FragmentUtpSendBinding.bind(dialogView)

        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setTitle("Send Data through UTP")
            .setCancelable(false)
            .setNeutralButton("Close") { _, _ -> }


        sender = UTPSender(this@UTPSendDialogFragment, community)


        lifecycleScope.launchWhenCreated {
            while (isActive) {
                updateView()
                delay(100)
            }
        }

        binding!!.btnSend.setOnClickListener {
            if (sender!!.isSending()) {
                Toast.makeText(
                    requireContext(),
                    "Already sending. Wait for previous send to finish!",
                    Toast.LENGTH_SHORT
                ).show()
            } else if (validateDataSize()) {
                val thread = Thread {
                    startSender()
                }
                thread.start()
            }
        }

        return dialogBuilder.create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        val nSender = sender
        if (nSender != null && nSender.isSending()) {
            // _sender.cancelSend()
            Toast.makeText(requireContext(), "UTP transfer to ${otherPeer.address} has been stopped", Toast.LENGTH_SHORT).show()
        }

        utpDialogListener.onUTPDialogDismissed()
    }

    override fun debugInfo(info: String, toast: Boolean, reset: Boolean) {
        if (reset) {
            setTextToResult(info)
        } else {
            appendTextToResult(info)
        }
    }

    override fun newDataReceived(success: Boolean, data: ByteArray, source: IPv4Address, msg: String) {
        appendTextToResult("Unexpectedly received data. SHOULD NOT HAPPEN!")
    }

    override fun newDataSent(success: Boolean, destinationAddress: String, msg: String) {
        if (!success || destinationAddress == "") {
            appendTextToResult(msg)
            return
        }

        appendTextToResult("Sent data to $destinationAddress")
    }

    private fun validateDataSize(): Boolean {
        val context: Context = requireContext()

        if (binding!!.dataSize.text.toString().isEmpty() && binding!!.dataSize.isEnabled) {
            Toast.makeText(context, "Invalid data size", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun startSender() {
        val byteData: ByteArray

        if (chosenVote == ARG_CUSTOM_SIZE || chosenVote.isEmpty()) {
            val dataSizeText = binding!!.dataSize.text.toString()

            // should not occur if validated beforehand
            if (dataSizeText.isEmpty()) throw IllegalArgumentException("invalid data size")

            byteData = ByteArray(dataSizeText.toInt() * 1024)
            Arrays.fill(
                byteData,
                0x6F.toByte()
            ) // currently data is just the character "o" over and over
        } else {
            byteData = readCsvToByteArray(chosenVote)
        }

        sender?.sendData(otherPeer, byteData)
    }

    private fun getFileNames(): Array<String> {
        val csvFileNames = mutableListOf<String>()
        try {
            // List all files in the "app/assets" folder
            val files = requireContext().assets.list("") ?: return emptyArray()
            for (file in files) {
                // Check if the file is a CSV file
                if (file.endsWith(".csv")) {
                    csvFileNames.add(file)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        // add CUSTOM_DATA_SIZE to the list of available votes
        csvFileNames.add(ARG_CUSTOM_SIZE)

        return csvFileNames.toTypedArray()
    }

    private fun updateVoteFiles() {
        val voteFiles = getFileNames()
        if (!voteFiles.contentEquals(availableVotes)) {
            // peers have changed need to update
            availableVotes = voteFiles
            val autoComplete: AutoCompleteTextView = binding!!.autoCompleteVotes
            val context: Context = requireContext()
            val adapter = ArrayAdapter(context, R.layout.vote_item, availableVotes)

            autoComplete.setAdapter(adapter)
            autoComplete.onItemClickListener = AdapterView.OnItemClickListener {
                    adapterView, _, i, _ ->

                val itemSelected = adapterView.getItemAtPosition(i)
                chosenVote = itemSelected.toString()

                if (chosenVote == ARG_CUSTOM_SIZE || chosenVote.isEmpty()) {
                    binding!!.dataSize.isEnabled = true
                    binding!!.dataSize.setText("0")
                } else {
                    binding!!.dataSize.isEnabled = false
                    val dataSize = readCsvToByteArray(chosenVote).size
                    binding!!.dataSize.setText((dataSize / 1024).toString())
                }

            }
        }
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

    private fun readCsvToByteArray(fileName: String): ByteArray {
        val inputStream = requireContext().assets.open(fileName)
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var length: Int
        while (inputStream.read(buffer).also { length = it } != -1) {
            outputStream.write(buffer, 0, length)
        }
        inputStream.close()
        return outputStream.toByteArray()
    }

    private fun updatePeerDetails() {
        binding!!.peerId.text = PeerListAdapter.getSplitMID(otherPeer.mid)
        binding!!.lanAddress.text = otherPeer.lanAddress.toString()
        binding!!.wanAddress.text = otherPeer.wanAddress.toString()
    }

    private fun updateView() {
        updateVoteFiles()
        updatePeerDetails()
//        binding!!.txtDebug.text = "Debug Info: ${otherPeer}"
    }
    companion object {
        const val TAG = "UTPSendDialogFragment"
        const val ARG_CUSTOM_SIZE = "Custom Data Size"
    }
}
