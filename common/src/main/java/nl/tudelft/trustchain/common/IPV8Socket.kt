package nl.tudelft.trustchain.common

import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.EndpointListener
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeRaw
import nl.tudelft.ipv8.util.hexToBytes
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Date
import java.util.concurrent.Semaphore

class IPV8Socket(val community: Community) : DatagramSocket(), EndpointListener {
    val UTP_RAW_DATA = 254
    val prefix: ByteArray =
        ByteArray(0) + Community.PREFIX_IPV8 + Community.VERSION + community.serviceId.hexToBytes();

    // create a semaphore to block the receive method until the community.endpoint receives a packet and notifies the IPV8Socket listener
    val readSemaphore = Semaphore(0)
    var curData: UTPPayload? = null;
    var curAddr: IPv4Address? = null;
    init {
        community.endpoint.addListener(this)
    }

//    override fun bind(addr: SocketAddress?) {}

//    override fun connect(addr: SocketAddress?) {
//        super.connect(addr)
//    }

//    override fun connect(address: InetAddress?, port: Int) {
//        super.connect(address, port)
//    }

    override fun receive(p: DatagramPacket?) {
        // block on the semaphore until the community.endpoint receives a packet and notifies the IPV8Socket listener
        try {
            readSemaphore.acquire()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        // set the address, port and data of the p
        p?.socketAddress = InetSocketAddress(InetAddress.getByName(curAddr!!.ip), curAddr!!.port)
        p?.length = curData!!.payload.size
        p?.data = curData!!.payload
    }

    override fun send(datagramPacket: DatagramPacket?) {
        // encapsulate the UTP packet in IPV8 and send it
        if (datagramPacket == null)
            throw Error("Sorry, I cannot send without a packet")

        // serialize DatagramPacket to byte array
        val payload = UTPPayload(datagramPacket.data)
        val packet = this.community.serializePacket(UTP_RAW_DATA, payload, sign = false)

        // define the peer from  the address, port in the datagram
        val peer = IPv4Address(datagramPacket.address.hostAddress!!, datagramPacket.port)

        community.endpoint.send(peer, packet)
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

        if (msgId == UTP_RAW_DATA) {
            // we got a UTP_RAW_DATA packet, deserialize it to get the UTPPayload object with the DatagramPacket
            curData = packet.getPayload(UTPPayload.Deserializer);
            curAddr = sourceAddress as IPv4Address;
            readSemaphore.release()
        }
//        else if (msgId == 252) {
//            readSemaphore.release()
//        }
    }

    override fun onEstimatedLanChanged(address: IPv4Address) {}
}


data class UTPPayload(
    val payload: ByteArray,
) : Serializable {
    override fun serialize(): ByteArray {
        return payload.copyOfRange(0, payload.size)
    }

    companion object Deserializer : Deserializable<UTPPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<UTPPayload, Int> {
            var localOffset = 0;
            val (payload, payloadLen) = deserializeRaw(buffer, localOffset + offset)
            localOffset += payloadLen

            return Pair(UTPPayload(payload), buffer.size);
        }
    }
}
