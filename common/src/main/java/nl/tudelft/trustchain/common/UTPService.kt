package nl.tudelft.trustchain.common

import nl.tudelft.ipv8.Community
import java.net.InetSocketAddress
import net.utp4j.channels.UtpServerSocketChannel
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class UTPService(val socketAddress: InetSocketAddress, val community: Community) : Thread() {
    var server: UtpServerSocketChannel = UtpServerSocketChannel.open()

    init {
        server.socket = IPv8Socket(community)
        server.bind(socketAddress)
    }
    protected fun setup() {

    }

    override fun run() {
        setup()

        while(true){
            val acceptFuture = server.accept()

            acceptFuture.block()
            val channel = acceptFuture.channel

            thread {
                val buffer = ByteBuffer.allocate(1000)
                val readFuture = channel.read(buffer)
                readFuture.block()

                buffer.rewind()
                val data = ByteArray(buffer.remaining())
                buffer.get(data)

                System.out.println(data.decodeToString())
                channel.close()
            }
        }
    }
}
