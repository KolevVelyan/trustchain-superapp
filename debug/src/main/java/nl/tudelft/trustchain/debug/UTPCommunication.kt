package nl.tudelft.trustchain.debug

import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import net.utp4j.channels.UtpSocketChannel
import net.utp4j.channels.UtpSocketState
import net.utp4j.channels.impl.UtpSocketChannelImpl
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.trustchain.common.OnOpenPortResponseListener
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.time.Duration
import java.time.LocalDateTime

open class UTPCommunication {
    fun convertDataToUTF8(data: ByteArray): String {
        val utf8String = String(data, Charsets.UTF_8)

        return if (utf8String.length > 1000) utf8String.substring(0, 1000) else utf8String
    }

    fun calculateTimeStats(startTime: LocalDateTime, dataAmount: Int): String {
        val endTime = LocalDateTime.now()
        val duration: Duration = Duration.between(startTime, endTime)

        // calculate time stats
        val milliseconds: Long = duration.toMillis() % 1000
        val seconds: Long = duration.seconds % 60
        val minutes: Long = duration.toMinutes() % 60

        // speed in Kb per second
        val speed: Double = ((dataAmount).toDouble() / (duration.toMillis()).toDouble()) * (1000.0 / 1024.0)

        var result = "$minutes:$seconds.$milliseconds"
        if (!speed.isInfinite() && !speed.isNaN() && speed != 0.0) {
            result += " (${String.format("%.3f", speed)} Kb/s)"
        }

        return result
    }
}

// Subclass for sending data
class UTPReceiver(
    val activity: FragmentActivity?,
    val uTPBatchFragment: uTPBatchFragment
) : UTPCommunication(), OnOpenPortResponseListener {

    private var isReceiving: Boolean = false

    override fun onOpenPortResponse(source: IPv4Address, dataSize: Int?) {
        try{
            receiveData(source, dataSize)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun receiveData(source: IPv4Address, dataSize: Int?) {
        if (isReceiving) {
            return
        }

        isReceiving = true

        try {
            setUpReceiver(source, dataSize)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isReceiving = false
        }

    }

    private fun setUpReceiver(sender: IPv4Address, dataSize: Int?) {
        val receiverPort: Int = 9999
        try {
            if (dataSize == 0 || dataSize == null) {
                throw IllegalArgumentException("Invalid data size received from sender")
            }

            // socket is defined by the sender's ip and chosen sender port
            val socket = InetSocketAddress(InetAddress.getByName(sender.ip), sender.port)

            // instantiate client to receive data
            val c = UtpSocketChannelImpl()
            try {
                c.dgSocket = DatagramSocket(receiverPort)
                c.state = UtpSocketState.CLOSED
            } catch (exp: IOException) {
                throw IOException("Could not open UtpSocketChannel: ${exp.message}")
            }
            val channel: UtpSocketChannel = c
            uTPBatchFragment.setTextToResult("Starting receiver on port $receiverPort")


            val cFut = channel.connect(socket) // connect to sender
            cFut.block() // block until connection is established

            val startTime = LocalDateTime.now()
            uTPBatchFragment.appendTextToResult("Connected to sender (${socket.toString()})")

            // Allocate space in buffer and start receiving
            val buffer = ByteBuffer.allocate(dataSize)
            val readFuture = channel.read(buffer)
            readFuture.block() // block until all data is received

            // Rewind the buffer to make sure you're reading from the beginning
            buffer.rewind()

            // Convert the buffer to a byte array
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val timeStats = calculateTimeStats(startTime, dataSize)
            uTPBatchFragment.appendTextToResult("Received ${data.size/1024} Kb of data in $timeStats")

            uTPBatchFragment.appendTextToResult("Received data: \n${convertDataToUTF8(data)}")

            channel.close()
            uTPBatchFragment.appendTextToResult("Channel closed")

        } catch (e: Exception) {
            e.printStackTrace(System.err)
            uTPBatchFragment.appendTextToResult("Error: ${e.message}")
        }
    }

    public fun isReceiving(): Boolean {
        return isReceiving
    }




}
