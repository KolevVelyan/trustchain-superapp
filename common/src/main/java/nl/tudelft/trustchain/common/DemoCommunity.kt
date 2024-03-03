package nl.tudelft.trustchain.common

import kotlinx.coroutines.flow.MutableSharedFlow
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.logger
import nl.tudelft.ipv8.messaging.Address
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.payload.IntroductionRequestPayload
import nl.tudelft.ipv8.messaging.payload.IntroductionResponsePayload
import nl.tudelft.ipv8.messaging.payload.PuncturePayload
import nl.tudelft.ipv8.messaging.payload.PunctureRequestPayload
import nl.tudelft.ipv8.messaging.udp.UdpEndpoint
import java.util.Date

class DemoCommunity : Community() {
    override val serviceId = "02313685c1912a141279f8248fc8db5899c5df5a"

    val discoveredAddressesContacted: MutableMap<IPv4Address, Date> = mutableMapOf()

    val lastTrackerResponses = mutableMapOf<IPv4Address, Date>()

    val punctureChannel = MutableSharedFlow<Pair<Address, PuncturePayload>>(0, 10000)

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
        id: Int
    ) {
        val payload = PuncturePayload(myEstimatedLan, myEstimatedWan, id)
        val packet = serializePacket(MessageId.OPEN_PORT, payload, sign = false)
        endpoint.fileEndpoint?.send(address, packet)
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
        val payload = packet.getPayload(PunctureRequestPayload.Deserializer)
        if (packet.source is IPv4Address) {
            val target = if (payload.wanWalkerAddress.ip == myEstimatedWan.ip) {
                // They are on the same LAN, puncture should not be needed, but send it just in case
                payload.lanWalkerAddress
            } else {
                payload.wanWalkerAddress
            }

            val packet_ = createPortOpenResponse(myEstimatedLan, myEstimatedWan, payload.identifier)
            endpoint.fileEndpoint?.send(target, packet_)
        }
    }

    private fun createPortOpenResponse(lanWalker: IPv4Address, wanWalker: IPv4Address, identifier: Int): ByteArray {
        val payload = PuncturePayload(lanWalker, wanWalker, identifier)
        return serializePacket(MessageId.OPEN_PORT_RESPONSE, payload)
    }

    private fun onOpenPortResponse(packet: Packet) {
        System.out.print("Helloooo" + packet.toString());
    }
}
