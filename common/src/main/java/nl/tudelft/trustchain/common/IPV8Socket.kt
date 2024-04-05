package nl.tudelft.trustchain.common

import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.EndpointListener
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeRaw
import nl.tudelft.ipv8.util.hexToBytes
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Date
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore

/**
 * Represents a socket implementation for the IPV8 transport protocol.
 *
 * @property community The community associated with the socket.
 */
class IPV8Socket(val community: Community) : DatagramSocket(), EndpointListener {
    // Message ID for the URT raw data (used in the IPv8 header)
    private val UTP_RAW_DATA = 254

    // Prefix for the IPV8 protocol
    private val prefix: ByteArray =
        ByteArray(0) + Community.PREFIX_IPV8 + Community.VERSION + community.serviceId.hexToBytes()

    // Semaphore to block the receive method until a packet is received
    // and IPV8Socket listener is notified
    private val readSemaphore = Semaphore(0)

    // Queue to store received messages
    private var messageQueue = ConcurrentLinkedQueue<Pair<IPv4Address, UTPPayload>>()

    // Map to store peers, who the socket has sent to
    private val peerMap = HashMap<IPv4Address, Peer>()

    // Function to update status when data is sent or received
    lateinit var statusFunction: (Long, Long) -> Unit

    // Count of data sent and received
    private var dataSent: Long = 0
    private var dataReceived: Long = 0

    init {
        // Registering IPV8Socket as a listener for community's endpoint
        community.endpoint.addListener(this)
    }

    /**
     * Receives a DatagramPacket from the socket.
     * Blocks until a packet is received.
     *
     * @param p The DatagramPacket to store the received data.
     * @throws IOException If an I/O error occurs.
     */
    override fun receive(p: DatagramPacket?) {
        // Blocking until a packet is received
        try {
            readSemaphore.acquire()
        } catch (e: InterruptedException) {
            // In case it is interrupted, throw an IO Exception
            throw IOException("Socket interrupted")
        }

        // Polling message queue for a received packet
        val tuple = messageQueue.poll() ?: throw Error("Polling from an empty message queue. This is most probably because " +
            "the semaphore was acquired, but the queue is still empty! Please check whether the semaphore is correctly configured!")
        val address = tuple.first
        val packet: DatagramPacket = tuple.second.payload

        // Setting address, port, and data of DatagramPacket
        p?.socketAddress = InetSocketAddress(InetAddress.getByName(address.ip), address.port)
        p?.address = InetAddress.getByName(address.ip)
        p?.length = packet.data.size
        p?.data = packet.data

        // Updating received tracker and invoking status function
        dataReceived += packet.data.size
        statusFunction(dataSent, dataReceived)
    }

    /**
     * Sends a DatagramPacket through the socket.
     * Encapsulates the UTP packet in IPV8 before sending.
     *
     * @param datagramPacket The DatagramPacket to be sent.
     * @throws Error If datagramPacket is null.
     */
    override fun send(datagramPacket: DatagramPacket?) {
        // if packet is null, raise an error
        if (datagramPacket == null)
            throw Error("Sorry, I cannot send without a packet")

        // Serializing DatagramPacket to byte array
        val payload = UTPPayload(datagramPacket)
        val packet = this.community.serializePacket(UTP_RAW_DATA, payload, sign = false)

        // Defining the peer from the address and port in the datagram
        val address = IPv4Address(datagramPacket.address.hostAddress!!, datagramPacket.port)
        var peer : Peer?

        // Checking if the peer exists in the map, else finding it from the community
        if(peerMap.contains(address)){
            peer = peerMap[address]
        }
        else {
            peer = community.getPeers().find { p: Peer -> p.address == address }

            while(peer == null) {
                peer = community.getPeers().find { p: Peer -> p.address == IPv4Address(datagramPacket.address.hostAddress!!, datagramPacket.port) }
            }
            peerMap[address] = peer
        }

        // Sending packet through community's endpoint
        community.endpoint.send(peer!!, packet)

        // Updating sent and receive counters
        dataSent += packet.size
        statusFunction(dataSent, dataReceived)
    }

    /**
     * Listens for incoming packets from the IPv8 socket and handles them accordingly.
     *
     * @param packet The incoming packet.
     */
    override fun onPacket(packet: Packet) {
        // Handling received packet
        val sourceAddress = packet.source
        val data = packet.data

        // Updating last response time for peer
        val probablePeer = community.network.getVerifiedByAddress(sourceAddress)
        if (probablePeer != null) {
            probablePeer.lastResponse = Date()
        }

        // Checking packet prefix
        val packetPrefix = data.copyOfRange(0, this.prefix.size)
        if (!packetPrefix.contentEquals(prefix)) {
            // Prefix mismatch
            // logger.debug("prefix not matching")
            return
        }

        val msgId = data[prefix.size].toUByte().toInt()

        if (msgId == UTP_RAW_DATA) {
            // Handling UTP_RAW_DATA packet
            val payload = packet.getPayload(UTPPayload.Deserializer)
            val address = sourceAddress as IPv4Address
            messageQueue.add(Pair(address, payload))
            readSemaphore.release()
        }
    }

    /**
     * Notifies when the estimated LAN has changed.
     * This function is not implemented.
     *
     * @param address The IPv4Address of the estimated LAN.
     */
    override fun onEstimatedLanChanged(address: IPv4Address) {
        // Not implemented
    }
}

/**
 * Represents a payload for the UTP protocol encapsulated in a DatagramPacket.
 *
 * @property payload The DatagramPacket containing the UTP payload.
 */
data class UTPPayload(
    val payload: DatagramPacket,
) : Serializable {
    /**
     * Serializes the UTP payload to a byte array.
     *
     * @return The serialized byte array representing the UTP payload.
     */
    override fun serialize(): ByteArray {
        return payload.data.copyOfRange(0, payload.data.size)
    }

    /**
     * Companion object serving as a deserializer for UTPPayload.
     */
    companion object Deserializer : Deserializable<UTPPayload> {
        /**
         * Deserializes the byte array buffer to reconstruct a UTPPayload object.
         *
         * @param buffer The byte array buffer containing the serialized UTP payload.
         * @param offset The offset in the buffer to start deserialization from.
         * @return A pair containing the deserialized UTPPayload object and the new offset in the buffer.
         */
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<UTPPayload, Int> {
            // Deserialize the raw payload and its length
            val (payload, payloadLen) = deserializeRaw(buffer, offset)

            // Reconstruct UTPPayload object with the payload and its length
            return Pair(UTPPayload(DatagramPacket(payload, payloadLen)), buffer.size)
        }
    }
}
