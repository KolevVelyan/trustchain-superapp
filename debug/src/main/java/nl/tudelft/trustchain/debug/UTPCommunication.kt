package nl.tudelft.trustchain.debug

import net.utp4j.channels.UtpServerSocketChannel
import net.utp4j.channels.UtpSocketChannel
import net.utp4j.channels.UtpSocketState
import net.utp4j.channels.futures.UtpBlockableFuture
import net.utp4j.channels.impl.UtpSocketChannelImpl
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.trustchain.common.DemoCommunity
import nl.tudelft.trustchain.common.IPV8Socket
import java.io.IOException
import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.common.messaging.UTPSendPayload
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.time.Duration
import java.time.LocalDateTime

open class UTPCommunication {
    fun convertDataToUTF8(data: ByteArray): String {
        val maxCharToPrint = 200
        val utf8String = String(data, Charsets.UTF_8)

        return if (utf8String.length > maxCharToPrint) utf8String.substring(0, maxCharToPrint) else utf8String
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

    fun timeout(fut: UtpBlockableFuture): Boolean {
        val connection =  Thread() {
            try {
                fut.block() // block until connection is established
            } catch (_: java.lang.Exception) {
            }
        }

        connection.start()
        connection.join(30000)
        if (connection.isAlive) { // If thread is still alive after join
            connection.interrupt(); // Interrupt the thread
            return true
        }

        return false
    }
}

// Subclass for receiving data
class UTPReceiver(
    private val uTPDataFragment: UTPDataFragment,
    community: Community
) : UTPCommunication() {
    private var isReceiving: Boolean = false
    private var socket: IPV8Socket = IPV8Socket(community);

    fun isReceiving(): Boolean {
        return isReceiving
    }

    fun receiveData(sender: IPv4Address, dataSize: Int?) {
        if (isReceiving) {
            uTPDataFragment.newDataReceived(false, byteArrayOf(), IPv4Address("0.0.0.0", 0), "Already receiving. Wait for previous receive to finish!")
            return
        }

        if (sender.ip.isEmpty() || sender.ip == "0.0.0.0") {
            uTPDataFragment.newDataReceived(false, byteArrayOf(), sender, "Invalid sender address: ${sender.ip}. Stopping receiver.")
            return
        }

        if (dataSize == 0 || dataSize == null) {
            uTPDataFragment.newDataReceived(false, byteArrayOf(), sender, "Invalid data size received from sender. Stopping receiver.")
            return
        }

        isReceiving = true

        try {
            if (dataSize == 0) {
                throw IllegalArgumentException("Invalid data size received from sender")
            }

            // socket is defined by the sender's ip and chosen sender port
            val socket = InetSocketAddress(InetAddress.getByName(sender.ip), sender.port)

            // instantiate client to receive data
            val c = UtpSocketChannelImpl()
            try {
                c.dgSocket = this.socket
                c.state = UtpSocketState.CLOSED
            } catch (exp: IOException) {
                throw IOException("Could not open UtpSocketChannel: ${exp.message}")
            }
            val channel: UtpSocketChannel = c
            uTPDataFragment.debugInfo("Starting receiver for ${sender.ip}:${sender.port}", reset = true)


            val cFut = channel.connect(socket) // connect to sender
            cFut.block() // block until connection is established
//            if (timeout(cFut))  {
//                uTPDataFragment.debugInfo("Timeout - Couldn't establish a connection with the sender")
//                return
//            }


            val startTime = LocalDateTime.now()
            uTPDataFragment.debugInfo("Connected to sender (${socket.toString()})")

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
            uTPDataFragment.debugInfo("Received ${data.size/1024} Kb of data in $timeStats")

            uTPDataFragment.debugInfo("Received data: ${convertDataToUTF8(data)}")

            channel.close()
            uTPDataFragment.debugInfo("Channel closed")

            uTPDataFragment.newDataReceived(true, data, sender)
        } catch (e: Exception) {
            e.printStackTrace(System.err)
            uTPDataFragment.debugInfo("Error: ${e.message}")
        } finally {
            isReceiving = false
        }
    }
}

// Subclass for sending data
class UTPSender(
    private val uTPDataFragment: UTPDataFragment,
    private val community: Community
) : UTPCommunication() {
    private var isSending: Boolean = false
    private var socket: IPV8Socket = IPV8Socket(community);

    fun isSending(): Boolean {
        return isSending
    }

    fun sendData(peerToSend: Peer, dataToSend: ByteArray) {
        if (isSending) {
            uTPDataFragment.newDataSent(false, "","Already sending. Wait for previous send to finish!")
            return
        }

        if (peerToSend.address.toString().isEmpty() || peerToSend.address.toString() == "0.0.0.0"){
            uTPDataFragment.newDataSent(false, "","Invalid peer address: ${peerToSend.address.toString()}. Stopping sender.")
            return
        }

        if (dataToSend.isEmpty()) {
            uTPDataFragment.newDataSent(false, peerToSend.address.toString(),"No data to send. Stopping sender.")
            return
        }

        isSending = true

        uTPDataFragment.debugInfo("Trying to send data to ${peerToSend.address.toString()}", reset = true)

        // send request to receiver to ask if we can send our data
        val payload = UTPSendPayload(dataToSend.size)
        val packet = community.serializePacket(DemoCommunity.MessageId.UTP_SEND_REQUEST, payload, sign = false)
        community.endpoint.send(peerToSend.address, packet)

        try {
            // instantiate socket to send data (it waits for client to through socket first)
            val server = UtpServerSocketChannel.open()
            try {
                server.bind(socket)
            } catch (e: java.lang.Exception) {
                e.printStackTrace(System.err)
                uTPDataFragment.debugInfo("Error: ${e.message}")
            }

            try {
                // wait until someone connects to socket and get new channel
                val acceptFuture = server.accept()
                uTPDataFragment.debugInfo("Waiting for client to connect...")


                acceptFuture.block() // block until connection is established
//                if (timeout(acceptFuture))  {
//                    uTPDataFragment.debugInfo("Timeout - Couldn't establish a connection with the receiver")
//                    return
//                }

                uTPDataFragment.debugInfo("Client has connected")
                val startTime = LocalDateTime.now()
                val channel = acceptFuture.channel

                // send data on newly established channel (with client/receiver)
                val out = ByteBuffer.allocate(dataToSend.size)
                out.put(dataToSend)
                val fut = channel.write(out)
                fut.block() // block until all data is sent
                val timeStats = calculateTimeStats(startTime, dataToSend.size)
                uTPDataFragment.debugInfo("Sent all ${dataToSend.size/1024} Kb of data in $timeStats")

                uTPDataFragment.newDataSent(success=true, destinationAddress=channel.remoteAdress.toString())

                channel.close()
                uTPDataFragment.debugInfo("Socket closed")
            } catch (e: java.lang.Exception) {
                e.printStackTrace(System.err)
                uTPDataFragment.debugInfo("Error: ${e.message}")
            } finally {
                server.close()
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace(System.err)
            uTPDataFragment.debugInfo("Error: ${e.message}")
        } finally {
            isSending = false
        }
    }
}

interface UTPDataFragment {
    fun debugInfo(info: String, toast: Boolean = false, reset: Boolean = false)

    fun newDataReceived(success: Boolean, data: ByteArray, source: IPv4Address, msg: String = "")

    fun newDataSent(success: Boolean, destinationAddress: String = "", msg: String = "")

}

interface UTPDialogListener {
    fun onUTPDialogDismissed()
}
