package nl.tudelft.trustchain.common

import nl.tudelft.ipv8.Community
import java.net.DatagramPacket
import java.net.SocketAddress

class UTPSender(val peer: SocketAddress, val community: Community) {

    fun send(data: ByteArray) {
        val socket = IPv8Socket(community);

        val packet = DatagramPacket(data, data.size, peer)
        socket.send(packet)
    }
}
