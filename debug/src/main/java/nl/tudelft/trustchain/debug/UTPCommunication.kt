package nl.tudelft.trustchain.debug

import net.utp4j.channels.UtpServerSocketChannel
import net.utp4j.channels.UtpSocketChannel
import net.utp4j.channels.UtpSocketState
import net.utp4j.channels.futures.UtpAcceptFuture
import net.utp4j.channels.futures.UtpBlockableFuture
import net.utp4j.channels.impl.UtpSocketChannelImpl
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.common.DemoCommunity
import nl.tudelft.trustchain.common.IPV8Socket
import nl.tudelft.trustchain.common.messaging.UTPSendPayload
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer


/**
 * Class that oversees the communication of UTP data between two peers.

 */
open class UTPCommunication {
    // add common methods for sender and receiver here

    companion object {
        // 1472 bytes is the maximum size of a UTP packet
        // 31 bytes is the size of the IPV8 unsigned header
        // 20 bytes is the size of the UTP header
        const val MAX_UTP_PACKET = 1472.0 - 31.0 - 20.0
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
    private var socket: IPV8Socket = IPV8Socket(community)
    private var channel: UtpSocketChannel? = null
    private var connectFuture: UtpBlockableFuture? = null

    fun isReceiving(): Boolean {
        return isReceiving
    }

    fun stopConnection() {
        synchronized(this) {
            connectFuture?.unblock() // unblock if no one has connected
            connectFuture = null // reset future
            channel?.close() // close channel
        }
    }

    // This method should be called when a UTP send request is received
    fun receiveData(sender: IPv4Address, dataSize: Int?) {
        // handle invalid input
        if (isReceiving) {
            uTPDataFragment.newDataReceived(
                false,
                byteArrayOf(),
                IPv4Address("0.0.0.0", 0),
                "Already receiving. Wait for previous receive to finish!"
            )
            return
        }
        if (sender.ip.isEmpty() || sender.ip == "0.0.0.0") {
            uTPDataFragment.newDataReceived(
                false,
                byteArrayOf(),
                sender,
                "Invalid sender address: ${sender.ip}. Stopping receiver."
            )
            return
        }
        if (dataSize == 0 || dataSize == null) {
            uTPDataFragment.newDataReceived(
                false,
                byteArrayOf(),
                sender,
                "Invalid data size received from sender. Stopping receiver."
            )
            return
        }
        isReceiving = true

        // try to receive data
        try {
            // define socket that will be used by UTP to send data
            // socket is defined by the sender's ip and port
            val socket = InetSocketAddress(InetAddress.getByName(sender.ip), sender.port)

            // define function that should be called by IPV8 socket to give info about transfer
            this.socket.statusFunction = uTPDataFragment::receiveSpeedUpdate

            // instantiate client to receive data on the custom IPV8 socket
            val c = UtpSocketChannelImpl()
            try {
                c.dgSocket = this.socket
                c.state = UtpSocketState.CLOSED
            } catch (exp: IOException) {
                throw IOException("Could not open UtpSocketChannel: ${exp.message}")
            }
            channel = c
            uTPDataFragment.debugInfo(
                "Starting receiver for ${sender.ip}:${sender.port}",
                reset = true
            )

            connectFuture = channel!!.connect(socket) // connect to sender
            uTPDataFragment.debugInfo("Waiting for sender to connect...")
            connectFuture?.block() // block until connection is established

            // can only be null if stopConnection was called (so no need to continue)
            if (connectFuture == null) {
                return
            }

            uTPDataFragment.debugInfo("Connected to sender (${socket.toString()})")

            // allocate space in buffer and start receiving
            val buffer = ByteBuffer.allocate(dataSize)
            val readFuture = channel!!.read(buffer)
            readFuture.block() // block until all data is received

            val totalReceived = buffer.position() // in bytes
            uTPDataFragment.debugInfo("Received $totalReceived/$dataSize bytes of data")

            // rewind the buffer to make sure you're reading from the beginning
            buffer.rewind()

            // convert the buffer to a byte array and retrieve data
            val data = ByteArray(totalReceived)
            buffer.get(data)

            channel?.close()
            uTPDataFragment.debugInfo("Channel closed")

            // tell listening fragment that the data has been received
            // depending on the size of the data the transfer can be successful or not
            if (totalReceived == 0) {
                uTPDataFragment.newDataReceived(false, byteArrayOf(), sender, "No data sent")
            } else if (totalReceived != dataSize) {
                uTPDataFragment.newDataReceived(false, data, sender, "Received $totalReceived bytes instead of $dataSize")
            } else {
                uTPDataFragment.newDataReceived(true, data, sender)
            }
        } catch (e: Exception) {
            e.printStackTrace(System.err)
            uTPDataFragment.newDataReceived(
                false,
                byteArrayOf(),
                IPv4Address("0.0.0.0", 0),
                "Error: ${e.message}"
            )
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
    private var socket: IPV8Socket = IPV8Socket(community)
    private var server: UtpServerSocketChannel? = null
    private var channel: UtpSocketChannel? = null
    private var acceptFuture: UtpAcceptFuture? = null

    constructor(
        uTPDataFragment: UTPDataFragment,
        community: Community,
        isSending: Boolean,
        socket: IPV8Socket,
        server: UtpServerSocketChannel,
        channel: UtpSocketChannel,
        future: UtpAcceptFuture
    ) : this(uTPDataFragment, community) {
        this.isSending = isSending;
        this.socket = socket;
        this.server = server;
        this.channel = channel;
        this.acceptFuture = future;
    }

    fun isSending(): Boolean {
        return isSending
    }

    fun stopConnection() {
        synchronized(this) {
            acceptFuture?.unblock() // unblock if no one has connected
            acceptFuture = null // reset future
            channel?.close() // close channel if connection is established
            server?.close() // close the server
        }
    }

    // Method to send data to a peer using UTP
    fun sendData(peerToSend: Peer, dataToSend: ByteArray) {
        // handle invalid input
        if (isSending) {
            uTPDataFragment.newDataSent(
                false,
                "",
                "Already sending. Wait for previous send to finish!"
            )
            return
        }
        if (peerToSend.address.toString().isEmpty() || peerToSend.address.toString() == "0.0.0.0"){
            uTPDataFragment.newDataSent(false, "","Invalid peer address: ${peerToSend.address.toString()}. Stopping sender")
            return
        }
        if (dataToSend.isEmpty()) {
            uTPDataFragment.newDataSent(false, peerToSend.address.toString(),"No data to send. Stopping sender")
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
            server = UtpServerSocketChannel.open()
            server?.bind(socket)

            try {
                // wait until someone connects to socket and get new channel
                acceptFuture = server!!.accept()
                uTPDataFragment.debugInfo("Waiting for receiver to connect...")
                acceptFuture?.block() // block until connection is established

                // can only be null if stopConnection was called (so no need to continue)
                if (acceptFuture == null) {
                    return
                }

                uTPDataFragment.debugInfo("Client has connected")
                channel = acceptFuture!!.channel

                // send data on newly established channel (with client/receiver)
                val out = ByteBuffer.allocate(dataToSend.size)
                out.put(dataToSend)
                val fut = channel!!.write(out)
                fut.block() // block until all data is sent

                val totalSent = out.position() // in bytes
                uTPDataFragment.debugInfo("Sent $totalSent/${dataToSend.size} bytes of data")

                channel?.close()
                uTPDataFragment.debugInfo("Channel closed")

                // tell listening fragment that the data has been sent
                // depending on the size of the data the transfer can be successful or not
                if (totalSent == 0) {
                    uTPDataFragment.newDataSent(false, "","No data sent")
                } else if (totalSent != dataToSend.size) {
                    uTPDataFragment.newDataSent(false, "","Sent $totalSent bytes instead of ${dataToSend.size}")
                } else {
                    uTPDataFragment.newDataSent(
                        success = true,
                        destinationAddress = channel?.remoteAdress.toString()
                    )
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace(System.err)
                uTPDataFragment.newDataSent(false, "", "Error: ${e.message}")
            } finally {
                server?.close()
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace(System.err)
            uTPDataFragment.newDataSent(false, "", "Error: ${e.message}")
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

    // Inform the fragment about every packet sent or received through IPV8 socket
    fun receiveSpeedUpdate(isSentPacket: Boolean, packetSize: Int, seqNum: Int, ackNum: Int)
}
