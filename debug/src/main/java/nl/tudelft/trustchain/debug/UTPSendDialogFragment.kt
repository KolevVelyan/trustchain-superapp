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

/**
 * Dialog fragment that sends data through UTP
 */
class UTPSendDialogFragment(private val otherPeer: Peer, private val community: Community)
        : DialogFragment(), UTPDataFragment {
    private var binding: FragmentUtpSendBinding? = null // binding for the fragments view

    // the sender object that sends the data
    private var sender = UTPSender(this@UTPSendDialogFragment, community)

    private var chosenVote: String = "" // the type of data/vote the user wants to send
    private var availableVotes : Array<String> = emptyArray() // the list of available votes

    private var initialPacketTime: LocalDateTime = LocalDateTime.now()
    private var lastPacketTime: LocalDateTime = LocalDateTime.now()
    private var currentPacketTime: LocalDateTime = LocalDateTime.now()
    private var lastDataSent: Long = 0
    private var totalDataSent: Long = 0
    private var lastDataReceived: Long = 0
    private var totalDataReceived: Long = 0

    // This method is called when the dialog is created
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // inflate the view to get the correct layout
        val dialogView = layoutInflater.inflate(R.layout.fragment_utp_send, null)
        binding = FragmentUtpSendBinding.bind(dialogView)

        // make the dialog
        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setTitle("Send Data through UTP")
            .setCancelable(false)
            .setNeutralButton("Close") { _, _ -> }

        lifecycleScope.launchWhenCreated {
            while (isActive) {
                updateView()
                delay(100)
            }
        }


        // handle click of Send button
        binding!!.btnSend.setOnClickListener {
            // do not send if already sending
            if (sender.isSending()) {
                Toast.makeText(
                    requireContext(),
                    "Already sending. Wait for previous send to finish!",
                    Toast.LENGTH_SHORT
                ).show()
            } else if (validateDataSize()) {
                // start sender in new thread
                val thread = Thread {
                    startSender()
                }
                thread.start()
            }
        }

        return dialogBuilder.create()
    }

    // This method is called when the dialog is dismissed
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // when closing the dialog if the sender is still sending, inform user that the transfer has been stopped
        if (sender.isSending()) {
            Toast.makeText(requireContext(), "UTP transfer to ${otherPeer.address} has been stopped", Toast.LENGTH_SHORT).show()
        }
    }

    // Get debug information from the sender
    override fun debugInfo(info: String, toast: Boolean, reset: Boolean) {
        if (reset) {
            setTextToResult(info)
        } else {
            appendTextToResult(info)
        }
    }

    // Get new data received from the sender (should not happen as we have only started a sender)
    override fun newDataReceived(success: Boolean, data: ByteArray, source: IPv4Address, msg: String) {
        appendTextToResult("Unexpectedly received data. SHOULD NOT HAPPEN!")
    }

    // Handle confirmation of the data being sent
    override fun newDataSent(success: Boolean, destinationAddress: String, msg: String) {
        // if unsuccessful or no destination address, display error message
        if (!success || destinationAddress == "") {
            appendTextToResult(msg)
            return
        }

        // if successful, display success message
        appendTextToResult("Sent data to $destinationAddress")
    }

    override fun receiveSpeedUpdate(dataSent: Long, dataReceived: Long) {
        if (initialPacketTime == null) {
            initialPacketTime = LocalDateTime.now()
        }
        currentPacketTime = LocalDateTime.now()
        lastDataSent = dataSent - totalDataSent
        lastDataReceived = dataReceived - totalDataReceived
    }

    // Validate the data size entered by the user
    private fun validateDataSize(): Boolean {
        val context: Context = requireContext()

        if (binding!!.dataSize.text.toString().isEmpty() && binding!!.dataSize.isEnabled) {
            Toast.makeText(context, "Invalid data size", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    // Send the chosen data to the {otherPeer}
    private fun startSender() {
        val byteData: ByteArray

        // get data based on the user's choice
        if (chosenVote == ARG_CUSTOM_SIZE || chosenVote.isEmpty()) {
            val dataSizeText = binding!!.dataSize.text.toString()

            // if user chose custom data size then fill the data with "o"
            try {
                byteData = ByteArray(dataSizeText.toInt() * 1024)
                Arrays.fill(
                    byteData,
                    0x6F.toByte()
                )
            } catch (e: Throwable) {
                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Data size too big. Please choose a smaller size.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
        } else {
            // get the data from the chosen vote/file
            byteData = readCsvToByteArray(chosenVote)
        }

        // send the data to the other peer
        sender.sendData(otherPeer, byteData)
    }

    // Get the list of available votes/files
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

    // Update the list of available votes/files
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

    // Get the content of the chosen vote/file
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
        if (currentPacketTime == lastPacketTime) return

        val currBinding = binding ?: return

//        if (lastPacketTime == null) {
//            return
//        }

        val timeDifference = currentPacketTime!!.toLocalTime().toNanoOfDay() - lastPacketTime!!.toLocalTime().toNanoOfDay()
        val timeDifferenceSeconds = timeDifference / 1_000_000_000.0

        val sentDataSpeed = lastDataSent / timeDifferenceSeconds
        val receivedDataSpeed = lastDataReceived / timeDifferenceSeconds

        val totalDifference = currentPacketTime!!.toLocalTime().toNanoOfDay() - initialPacketTime!!.toLocalTime().toNanoOfDay()
        val totalDifferenceSeconds = totalDifference / 1_000_000_000.0

        val avgSentDataSpeed = totalDataSent / totalDifferenceSeconds
        val avgReceivedDataSpeed = totalDataReceived / totalDifferenceSeconds

        // since the last update we have sent lastDataSent bytes
        // and received lastDataReceived bytes in timeDifferenceSeconds seconds
        val dataSpeed ="\nSent $totalDataSent Bytes (${String.format("%.3f", avgSentDataSpeed)} B/s) \nReceived $totalDataReceived (${String.format("%.3f", avgReceivedDataSpeed)} B/s)"
        currBinding.txtDataSpeed.text = dataSpeed


        if (currentPacketTime != lastPacketTime) {
            lastPacketTime = currentPacketTime
            totalDataSent += lastDataSent
            totalDataReceived += lastDataReceived
        }
    }

    private fun updateView() {
        updateVoteFiles()
        updatePeerDetails()
        updateDataSpeed()
    }

    companion object {
        const val TAG = "UTPSendDialogFragment"
        const val ARG_CUSTOM_SIZE = "Custom Data Size"
    }
}
