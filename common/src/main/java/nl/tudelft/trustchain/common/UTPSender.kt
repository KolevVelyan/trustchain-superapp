package nl.tudelft.trustchain.common

import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.Peer
import java.net.DatagramPacket
import java.net.SocketAddress

class UTPSender(val peer: Peer, val community: Community) {
    public val socket = IPv8Socket(community, peer);

    fun send(data: ByteArray) {
        val packet = DatagramPacket(data, data.size)
        socket.send(packet)
    }
}
