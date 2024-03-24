package nl.tudelft.trustchain.common

import net.utp4j.channels.UtpServerSocketChannel
import net.utp4j.channels.impl.UtpServerSocketChannelImpl
import net.utp4j.channels.impl.recieve.UtpRecieveRunnable
import nl.tudelft.ipv8.Community
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class UTPService(val socketAddress: InetSocketAddress, val community: Community) : Thread() {
    var server: UtpServerSocketChannel = UtpServerSocketChannel.open()

    init {
        server.bind(IPv8Socket(community, null))

        super.setDaemon(true)
        super.setName("UTP Service")
    }
    protected fun setup() {

    }

    override fun run() {
        setup()

        while(true){
            val acceptFuture = server.accept()

            acceptFuture.block()

            thread {
                val buffer = ByteBuffer.allocate(   1000)
                val channel = acceptFuture.channel
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
