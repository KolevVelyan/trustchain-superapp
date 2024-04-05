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
import java.util.concurrent.SynchronousQueue

class IPV8Socket(val community: Community) : DatagramSocket(), EndpointListener {
    val UTP_RAW_DATA = 254
    val prefix: ByteArray =
        ByteArray(0) + Community.PREFIX_IPV8 + Community.VERSION + community.serviceId.hexToBytes();

    // create a semaphore to block the receive method until the community.endpoint receives a packet and notifies the IPV8Socket listener
    val readSemaphore = Semaphore(0);
    var messageQueue = ConcurrentLinkedQueue<Pair<IPv4Address, UTPPayload>>();
    val peerMap = HashMap<IPv4Address, Peer>();

    lateinit var statusFunction: (Long, Long) -> Unit;
    private var dataSent: Long = 0;
    private var dataRecieved: Long = 0;

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
            throw IOException("Testing Haha")
        }
        val packet = messageQueue.poll();
        val address = packet?.first;
        val payload = packet?.second;

        // set the address, port and data of the p
        p?.socketAddress = InetSocketAddress(InetAddress.getByName(address!!.ip), address.port)
        p?.address = InetAddress.getByName(address.ip);
        p?.length = payload!!.payload.size
        p?.data = payload.payload

        // Update recieved tracker
        dataRecieved += payload.payload.size;
        statusFunction(dataSent, dataRecieved);
    }

    override fun send(datagramPacket: DatagramPacket?) {
        // encapsulate the UTP packet in IPV8 and send it
        if (datagramPacket == null)
            throw Error("Sorry, I cannot send without a packet")

        // serialize DatagramPacket to byte array
        val payload = UTPPayload(datagramPacket.data)
        val packet = this.community.serializePacket(UTP_RAW_DATA, payload, sign = false)

        // define the peer from  the address, port in the datagram
        val address = IPv4Address(datagramPacket.address.hostAddress!!, datagramPacket.port);
        var peer : Peer?;

        if(peerMap.contains(address)){
            peer = peerMap[address];
        }
        else {
            peer = community.getPeers().find { p: Peer -> p.address == address };

            while(peer == null) {
                peer = community.getPeers().find { p: Peer -> p.address == IPv4Address(datagramPacket.address.hostAddress!!, datagramPacket.port) };
            }
            peerMap[address] = peer;
        }

        community.endpoint.send(peer!!, packet)

        // Udpate status
        dataSent += packet.size;
        statusFunction(dataSent, dataRecieved);
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
            val payload = packet.getPayload(UTPPayload.Deserializer);
            val address = sourceAddress as IPv4Address;
            messageQueue.add(Pair(address, payload));
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
