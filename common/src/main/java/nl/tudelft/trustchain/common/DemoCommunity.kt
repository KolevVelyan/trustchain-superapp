package nl.tudelft.trustchain.common

import kotlinx.coroutines.flow.MutableSharedFlow
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.messaging.Address
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.payload.IntroductionResponsePayload
import nl.tudelft.ipv8.messaging.payload.PuncturePayload
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

    private val listeners = mutableListOf<OnOpenPortResponseListener>()

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
        val payload = OpenPortPayload(portToOpen)
        val packet = serializePacket(MessageId.OPEN_PORT, payload, sign = false)
        endpoint.send(address, packet)
    }

    // RECEIVE MESSAGE
    init {
        messageHandlers[MessageId.PUNCTURE_TEST] = ::onPunctureTest
        messageHandlers[MessageId.OPEN_PORT] = ::onOpenPort
        messageHandlers[MessageId.OPEN_PORT_RESPONSE] = ::onOpenPortResponse
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

    fun addListener(listener: OnOpenPortResponseListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: OnOpenPortResponseListener) {
        listeners.remove(listener)
    }

    private fun onOpenPortResponse(packet: Packet) {
        this.serverWanPort = (packet.source as IPv4Address).port
        this.receivedDataSize = packet.getPayload(OpenPortPayload.Deserializer).dataSize

        listeners.forEach { it.onOpenPortResponse(packet.source as IPv4Address, packet.getPayload(OpenPortPayload.Deserializer).dataSize) }
    }

}

interface OnOpenPortResponseListener {
    fun onOpenPortResponse(source: IPv4Address, dataSize: Int?)

}

interface UTPDataFragment {
    fun debugInfo(info: String, toast: Boolean = false, reset: Boolean = false) {
    }

    fun newDataReceived(data: ByteArray, source: IPv4Address) {
    }

    fun newDataSent(success: Boolean, destinationAddress: String = "", msg: String = "") {
    }

}
