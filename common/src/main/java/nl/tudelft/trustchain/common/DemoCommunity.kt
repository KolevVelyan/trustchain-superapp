package nl.tudelft.trustchain.common

import kotlinx.coroutines.flow.MutableSharedFlow
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.messaging.Address
import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.EndpointListener
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.SERIALIZED_UINT_SIZE
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeRaw
import nl.tudelft.ipv8.messaging.deserializeUInt
import nl.tudelft.ipv8.messaging.payload.IntroductionResponsePayload
import nl.tudelft.ipv8.messaging.payload.PuncturePayload
import nl.tudelft.ipv8.messaging.serializeUInt
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.trustchain.common.messaging.OpenPortPayload
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketAddress
import java.util.Date

class DemoCommunity : Community() {
    override val serviceId = "02313685c1912a141279f8248fc8db5899c5df5a"

    val discoveredAddressesContacted: MutableMap<IPv4Address, Date> = mutableMapOf()

    val lastTrackerResponses = mutableMapOf<IPv4Address, Date>()

    val punctureChannel = MutableSharedFlow<Pair<Address, PuncturePayload>>(0, 10000)

    var serverWanPort: Int? = null
    var senderDataSize: Int? = null
    var receivedDataSize: Int? = null

    // Retrieve the trustchain community
    private fun getTrustChainCommunity(): TrustChainCommunity {
        return IPv8Android.getInstance().getOverlay()
            ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    override fun walkTo(address: IPv4Address) {
        super.walkTo(address)

        discoveredAddressesContacted[address] = Date()
    }

    override fun onIntroductionResponse(
        peer: Peer,
        payload: IntroductionResponsePayload
    ) {
        super.onIntroductionResponse(peer, payload)

        if (peer.address in DEFAULT_ADDRESSES) {
            lastTrackerResponses[peer.address] = Date()
        }
    }

    object MessageId {
        const val PUNCTURE_TEST = 251
        const val OPEN_PORT = 252
        const val OPEN_PORT_RESPONSE = 253
        const val UTP_RAW_DATA = 69
    }

    fun sendPuncture(
        address: IPv4Address,
        id: Int
    ) {
        val payload = PuncturePayload(myEstimatedLan, myEstimatedWan, id)
        val packet = serializePacket(MessageId.PUNCTURE_TEST, payload, sign = false)
        endpoint.send(address, packet)
    }

    fun openPort(
        address: IPv4Address,
        portToOpen: Int
    ) {
        val s = IPv8Socket(this)
        val d = DatagramPacket("Hello World".toByteArray(), "Hello World".toByteArray().size)
        val sa = InetAddress.getByName(address.ip)
        System.out.print(sa);
        d.address = InetAddress.getByName(address.ip);
        d.port = address.port;

        System.out.print(portToOpen)
        s.send(d);
    }

    // RECEIVE MESSAGE
    init {
        messageHandlers[MessageId.PUNCTURE_TEST] = ::onPunctureTest
        messageHandlers[MessageId.OPEN_PORT] = ::onOpenPort
        messageHandlers[MessageId.OPEN_PORT_RESPONSE] = ::onOpenPortResponse
        messageHandlers[MessageId.UTP_RAW_DATA] = ::onUTPPacket
    }

    private fun onUTPPacket(packet: Packet) {
        System.out.print(packet)

    }

    private fun onPunctureTest(packet: Packet) {
        val payload = packet.getPayload(PuncturePayload.Deserializer)
        punctureChannel.tryEmit(Pair(packet.source, payload))
    }

    private fun onOpenPort(packet: Packet) {
        val payload = packet.getPayload(OpenPortPayload.Deserializer)
        payload.dataSize = senderDataSize ?: 0
        if (packet.source is IPv4Address) {
            sendData(
                serializePacket(MessageId.OPEN_PORT_RESPONSE, payload, sign = false),
                (packet.source as IPv4Address).ip,
                (packet.source as IPv4Address).port,
                payload.port
            )
        }
    }

    private fun sendData(data: ByteArray, serverIp: String, serverPort: Int, clientPort: Int) {
        try {
            val address = InetAddress.getByName(serverIp)
            val socket = DatagramSocket(clientPort)

            val packet = DatagramPacket(data, data.size, address, serverPort)
            socket.send(packet)

            // Close the socket after sending data
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onOpenPortResponse(packet: Packet) {
        this.serverWanPort = (packet.source as IPv4Address).port
        this.receivedDataSize = packet.getPayload(OpenPortPayload.Deserializer).dataSize
    }
}

class IPv8Socket(val community: Community) : DatagramSocket(), EndpointListener {
    val BUFFER_SIZE = 1234;

    //    val myLan: IPv4Address = IPv4Address().;
    val prefix: ByteArray =
        ByteArray(0) + Community.PREFIX_IPV8 + Community.VERSION + community.serviceId.hexToBytes();

    init {
        community.endpoint.addListener(this)
    }

    override fun bind(addr: SocketAddress?) {}

    override fun connect(addr: SocketAddress?) {
        super.connect(addr)
    }

    override fun connect(address: InetAddress?, port: Int) {
        super.connect(address, port)
    }

    override fun receive(p: DatagramPacket?) {
        super.receive(p)
    }

    override fun send(p: DatagramPacket?) {
        if (p == null)
            throw Error("Sorry, I cannot send without a packet")

        val source = p.address.toString().replace("/", "")

        val address: IPv4Address = IPv4Address(source, p.port)
        val payload: UTPPayload = UTPPayload(p.port.toUInt(), p);

        val data =
            community.serializePacket(DemoCommunity.MessageId.UTP_RAW_DATA, payload, sign = false)
        community.endpoint.send(address, data);
    }

    override fun onPacket(packet: Packet) {
        val sourceAddress = packet.source
        val data = packet.data

        val probablePeer = community.network.getVerifiedByAddress(sourceAddress)
        if (probablePeer != null) {
            probablePeer.lastResponse = Date()
        }

        val packetPrefix = data.copyOfRange(0, this.prefix.size)
        if (!packetPrefix.contentEquals(prefix)) {
            // logger.debug("prefix not matching")
            return
        }

        val msgId = data[prefix.size].toUByte().toInt()

        if (msgId == DemoCommunity.MessageId.UTP_RAW_DATA) {
            val payload = packet.getPayload(UTPPayload.Deserializer)

            receive(payload.payload)
        }
    }

    override fun onEstimatedLanChanged(address: IPv4Address) {}
}

data class UTPPayload(
    val port: UInt,
    val payload: DatagramPacket,
) : Serializable {
    override fun serialize(): ByteArray {
        return serializeUInt(port) + payload.data
    }

    companion object Deserializer : Deserializable<UTPPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<UTPPayload, Int> {
            var local_offset = 0;
            val port = deserializeUInt(buffer, local_offset + offset)
            local_offset += SERIALIZED_UINT_SIZE
            val (payload, payload_len) = deserializeRaw(buffer, local_offset + offset)
            local_offset += payload_len + local_offset

            return Pair(UTPPayload(port, DatagramPacket(payload, payload_len)), buffer.size);
        }
    }
}
