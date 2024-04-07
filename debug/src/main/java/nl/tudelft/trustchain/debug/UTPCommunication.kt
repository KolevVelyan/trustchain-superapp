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
import java.util.Date


/**
 * Class that oversees the communication of UTP data between two peers.

 */
open class UTPCommunication {
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

/**
 * Class that oversees the receiving of UTP data from a peer.
 */
class UTPReceiver(
    private val uTPDataFragment: UTPDataFragment,
    community: Community
) : UTPCommunication() {
    private var isReceiving: Boolean = false
    private var socket: IPV8Socket = IPV8Socket(community);

    fun isReceiving(): Boolean {
        return isReceiving
    }

    // This method should be called when a UTP send request is received
    fun receiveData(sender: IPv4Address, dataSize: Int?) {
        // handle invalid input
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

        // try to receive data
        try {
            // define socket that will be used by UTP to send data
            // socket is defined by the sender's ip and port
            val socket = InetSocketAddress(InetAddress.getByName(sender.ip), sender.port)

            // get the updates from the custom IPV8 socket
            this.socket.statusFunction = uTPDataFragment::receiveSpeedUpdate

            // instantiate client to receive data on the custom IPV8 socket
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

            // allocate space in buffer and start receiving
            val buffer = ByteBuffer.allocate(dataSize)
            val readFuture = channel.read(buffer)
            readFuture.block() // block until all data is received

            // rewind the buffer to make sure you're reading from the beginning
            buffer.rewind()

            // convert the buffer to a byte array and retrieve data
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            uTPDataFragment.debugInfo("Received all of data")

            channel.close()
            uTPDataFragment.debugInfo("Channel closed")

            uTPDataFragment.newDataReceived(true, data, sender)
        } catch (e: Exception) {
            e.printStackTrace(System.err)
            uTPDataFragment.newDataReceived(false, byteArrayOf(), IPv4Address("0.0.0.0", 0), "Error: ${e.message}")
        } finally {
            isReceiving = false
        }
    }
}

/**
 * Class that oversees the sending of UTP data to a peer.
 */
class UTPSender(
    private val uTPDataFragment: UTPDataFragment,
    private val community: Community
) : UTPCommunication() {
    private var isSending: Boolean = false
    private var socket: IPV8Socket = IPV8Socket(community);

    fun isSending(): Boolean {
        return isSending
    }

    // Method to send data to a peer using UTP
    fun sendData(peerToSend: Peer, dataToSend: ByteArray) {
        // handle invalid input
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

        // try to send the data to the chosen peer
        uTPDataFragment.debugInfo("Trying to send data to ${peerToSend.address.toString()}", reset = true)

        // send request to receiver to ask if we can send our data
        val payload = UTPSendPayload(dataToSend.size)
        val packet = community.serializePacket(DemoCommunity.MessageId.UTP_SEND_REQUEST, payload, sign = false)
        community.endpoint.send(peerToSend.address, packet)

        this.socket.statusFunction = uTPDataFragment::receiveSpeedUpdate

        try {
            // use the custom IPV8 socket to send the data
            val server = UtpServerSocketChannel.open()
            server.bind(socket)

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
                uTPDataFragment.debugInfo("Sent all ${dataToSend.size/1024} Kb of data")

                channel.close()
                uTPDataFragment.debugInfo("Socket closed")

                // tell listening fragment that the data has been sent successfully
                uTPDataFragment.newDataSent(success=true, destinationAddress=channel.remoteAdress.toString())
            } catch (e: java.lang.Exception) {
                e.printStackTrace(System.err)
                uTPDataFragment.newDataSent(false, "","Error: ${e.message}")
            } finally {
                server.close()
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace(System.err)
            uTPDataFragment.newDataSent(false, "","Error: ${e.message}")
        } finally {
            isSending = false
        }
    }
}

/**
 * Interface that should be implemented by the fragment that wants to receive UTP data.
 * It is used to give information back to the fragment.
 */
interface UTPDataFragment {
    // Relate back to fragment some information during the transfer
    fun debugInfo(info: String, toast: Boolean = false, reset: Boolean = false)

    // Inform the fragment that new data has been received
    // Or if success is false then the data could not be received and the {msg} gives the reason
    fun newDataReceived(success: Boolean, data: ByteArray, source: IPv4Address, msg: String = "")

    // Inform the fragment that new data has been sent
    // Or if success is false then the data could not be sent and the {msg} gives the reason
    fun newDataSent(success: Boolean, destinationAddress: String = "", msg: String = "")


    fun receiveSpeedUpdate(isSentPacket: Boolean, packetSize: Int, seqNum: Int, ackNum: Int)
}
