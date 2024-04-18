package nl.tudelft.trustchain.debug

import io.mockk.Awaits
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import net.utp4j.channels.futures.UtpConnectFuture
import net.utp4j.channels.impl.UtpSocketChannelImpl
import net.utp4j.channels.impl.read.UtpReadFutureImpl
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.EndpointListener
import nl.tudelft.ipv8.peerdiscovery.Network
import nl.tudelft.trustchain.common.DemoCommunity
import nl.tudelft.trustchain.common.IPV8Socket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class UTPReceiverTest {
    private var mockedCommunity = spyk(DemoCommunity(), recordPrivateCalls = true)
    private var mockedUTPDataFragment = mockk<UTPDataFragment>()
    private val myPeer = mockk<Peer>()
    private val mockedSocket = mockk<IPV8Socket>()
    private val endpoint = mockk<EndpointAggregator>()
    private val network = mockk<Network>(relaxed = true)
    private val msgIdUTPRawData = 254
    @Before
    fun setUp() {
        every { mockedCommunity.getPeers() } returns ArrayList<Peer>();
        every { mockedCommunity.myPeer } returns myPeer
        every { mockedCommunity.endpoint } returns endpoint
        every { mockedCommunity.network } returns network
        every { mockedCommunity.claimGlobalTime() } returns 1u;
        every { myPeer.lamportTimestamp } returns 0u;
        every { myPeer.updateClock(any()) } just runs;
        every { myPeer.key } returns mockk<PublicKey>();
        every { endpoint.send(any<Peer>(), any()) } just runs
        every { endpoint.addListener(any<EndpointListener>()) } just runs
        every {mockedUTPDataFragment.debugInfo(any<String>(), any<Boolean>(), any<Boolean>())} just runs

    }

    @Test
    fun isReceivingFalse() {
        // create a utp receiver with the mocked objects
        val utpReceiver = UTPReceiver(mockedUTPDataFragment, mockedCommunity)
        // check if the isReceiving method returns false
        assertEquals(utpReceiver.isReceiving(), false)
    }

    @Test
    fun isReceivingTrue() {
        mockkConstructor(UtpSocketChannelImpl::class)
        var mockConnFut = mockk<UtpConnectFuture>()
        var mockReadFut = mockk<UtpReadFutureImpl>()

        // mock connect method
        every { anyConstructed<UtpSocketChannelImpl>().connect(any()) } returns mockConnFut
        every { anyConstructed<UtpSocketChannelImpl>().read(any()) } returns mockReadFut
        // await and release immediately
        every { anyConstructed<UtpSocketChannelImpl>().close() } just Awaits

        every { mockedUTPDataFragment.newDataReceived(any(), any(), any(), any()) } just runs

        // mock block
        every { mockConnFut.block() } just runs
        // mock utpreadfuture block
        every { mockReadFut.block() } just runs


        // create a utp receiver with the mocked objects
        val utpReceiver = UTPReceiver(mockedUTPDataFragment, mockedCommunity)
        // create a thread to run the receiveData method
        val t = thread {
            utpReceiver.receiveData(IPv4Address("1.2.3.4", 1234), 10)
        }
        // wait for the thread to start
        Thread.sleep(1000)


        // check if the isReceiving method returns true
        assertEquals(utpReceiver.isReceiving(), true)

        // kill thread t
        t.interrupt()
    }

    @Test
    fun stopConnection() {
        mockkConstructor(UtpSocketChannelImpl::class)
        var mockConnFut = mockk<UtpConnectFuture>()
        var mockReadFut = mockk<UtpReadFutureImpl>()

        // mock connect method
        every { anyConstructed<UtpSocketChannelImpl>().connect(any()) } returns mockConnFut
        every { anyConstructed<UtpSocketChannelImpl>().read(any()) } returns mockReadFut
        // await and release immediately
        every { anyConstructed<UtpSocketChannelImpl>().close() } just Awaits

        every { mockedUTPDataFragment.newDataReceived(any(), any(), any(), any()) } just runs

        // mock block
        every { mockConnFut.block() } just runs
        // mock unblock for connect future
        every { mockConnFut.unblock() } just runs

        // mock utpreadfuture block
        every { mockReadFut.block() } just runs



        // create a utp receiver with the mocked objects
        val utpReceiver = UTPReceiver(mockedUTPDataFragment, mockedCommunity)
        // create a thread to run the receiveData method
        val t1 = thread {
            utpReceiver.receiveData(IPv4Address("1.2.3.4", 1234), 10)
        }

        // wait for the thread to start
        Thread.sleep(1000)

        val t2 = thread {
            utpReceiver.stopConnection()
        }
        Thread.sleep(1000)

        verify(exactly = 2) { anyConstructed<UtpSocketChannelImpl>().close() }
        verify(exactly = 1) { mockConnFut.unblock() }

        // stop the threads
        t1.interrupt()
        t2.interrupt()
    }

    @Test
    fun receiveData() {
        // we test for socket.read() functionality indirectly on the IPV8 testsuite.
        // continue by mocking everything else and verify the correct amount of calls

        mockkConstructor(UtpSocketChannelImpl::class)
        var mockConnFut = mockk<UtpConnectFuture>()
        var mockReadFut = mockk<UtpReadFutureImpl>()

        // mock connect method
        every { anyConstructed<UtpSocketChannelImpl>().connect(any()) } returns mockConnFut
        every { anyConstructed<UtpSocketChannelImpl>().read(any()) } answers  {
            (it.invocation.args[0] as ByteBuffer).put(ByteArray(10))
            mockReadFut
        }
        // await and release immediately
        every { anyConstructed<UtpSocketChannelImpl>().close() } returns mockk()
        var msg = slot<String>()
        every { mockedUTPDataFragment.newDataReceived(any(), any(), any(), capture(msg)) } just runs

        // mock block
        every { mockConnFut.block() } just runs
        // mock unblock for connect future
        every { mockConnFut.unblock() } just runs

        // mock utpreadfuture block
        every { mockReadFut.block() } just runs

        // create a utp receiver with the mocked objects
        val utpReceiver = UTPReceiver(mockedUTPDataFragment, mockedCommunity)
        // create a thread to run the receiveData method
        utpReceiver.receiveData(IPv4Address("1.2.3.4", 1234), 10)


        verify(exactly = 1) { anyConstructed<UtpSocketChannelImpl>().connect(any()) }
        verify(exactly = 1) { anyConstructed<UtpSocketChannelImpl>().read(any()) }
        verify(exactly = 1) { anyConstructed<UtpSocketChannelImpl>().close() }
        verify(exactly = 1) { mockConnFut.block() }
        verify(exactly = 1) { mockReadFut.block() }
        verify(exactly = 1) { mockedUTPDataFragment.newDataReceived(any(), any(), any(), any()) }
        assertNotEquals("Received 9 bytes instead of 10", msg.captured)
        assertNotEquals("No data sent", msg.captured)

    }

    @Test
    fun receiveDataSenderIPIsEmpty() {
        // we test for socket.read() functionality indirectly on the IPV8 testsuite.
        // continue by mocking everything else and verify the correct amount of calls

        mockkConstructor(UtpSocketChannelImpl::class)
        var mockConnFut = mockk<UtpConnectFuture>()
        var mockReadFut = mockk<UtpReadFutureImpl>()

        // mock connect method
        every { anyConstructed<UtpSocketChannelImpl>().connect(any()) } returns mockConnFut
        every { anyConstructed<UtpSocketChannelImpl>().read(any()) } returns mockReadFut
        // await and release immediately
        every { anyConstructed<UtpSocketChannelImpl>().close() } returns mockk()

        var errorMsg = slot<String>();
        every { mockedUTPDataFragment.newDataReceived(any(), any(), any(), capture(errorMsg)) } just runs

        // mock block
        every { mockConnFut.block() } just runs
        // mock unblock for connect future
        every { mockConnFut.unblock() } just runs

        // mock utpreadfuture block
        every { mockReadFut.block() } just runs

        // create a utp receiver with the mocked objects
        val utpReceiver = UTPReceiver(mockedUTPDataFragment, mockedCommunity)
        // create a thread to run the receiveData method
        utpReceiver.receiveData(IPv4Address("", 1234), 10)

        assertEquals("Invalid sender address: . Stopping receiver.", errorMsg.captured)

        // do the same for 0.0.0.0
        utpReceiver.receiveData(IPv4Address("0.0.0.0", 1234), 10)

        assertEquals("Invalid sender address: 0.0.0.0. Stopping receiver.", errorMsg.captured)
    }

    @Test
    fun receiveDataWhileReceivingData() {
        // we test for socket.read() functionality indirectly on the IPV8 testsuite.
        // continue by mocking everything else and verify the correct amount of calls

        mockkConstructor(UtpSocketChannelImpl::class)
        var mockConnFut = mockk<UtpConnectFuture>()
        var mockReadFut = mockk<UtpReadFutureImpl>()

        // mock connect method
        every { anyConstructed<UtpSocketChannelImpl>().connect(any()) } returns mockConnFut
        every { anyConstructed<UtpSocketChannelImpl>().read(any()) } returns mockReadFut
        // await and release immediately
        every { anyConstructed<UtpSocketChannelImpl>().close() } just Awaits

        var errorMsg = slot<String>();
        every { mockedUTPDataFragment.newDataReceived(any(), any(), any(), capture(errorMsg)) } just runs

        // mock block
        every { mockConnFut.block() } just runs
        // mock unblock for connect future
        every { mockConnFut.unblock() } just runs

        // mock utpreadfuture block
        every { mockReadFut.block() } just runs

        // create a utp receiver with the mocked objects
        val utpReceiver = UTPReceiver(mockedUTPDataFragment, mockedCommunity)

        val t1 = thread {
            utpReceiver.receiveData(IPv4Address("1.2.3.4", 1234), 10)
        }

        // wait for the thread to start
        Thread.sleep(1000)

        assertEquals(true, utpReceiver.isReceiving())

        // create a thread to run the receiveData method
        utpReceiver.receiveData(IPv4Address("1.2.3.4", 1234), 10)

        assertEquals("Already receiving. Wait for previous receive to finish!", errorMsg.captured)

        // stop the threads
        t1.interrupt()
    }

    @Test
    fun receiveDataInvalidDatasize() {
        // we test for socket.read() functionality indirectly on the IPV8 testsuite.
        // continue by mocking everything else and verify the correct amount of calls

        mockkConstructor(UtpSocketChannelImpl::class)
        var mockConnFut = mockk<UtpConnectFuture>()
        var mockReadFut = mockk<UtpReadFutureImpl>()

        // mock connect method
        every { anyConstructed<UtpSocketChannelImpl>().connect(any()) } returns mockConnFut
        every { anyConstructed<UtpSocketChannelImpl>().read(any()) } returns mockReadFut
        // await and release immediately
        every { anyConstructed<UtpSocketChannelImpl>().close() } returns mockk()

        var errorMsg = slot<String>();
        every { mockedUTPDataFragment.newDataReceived(any(), any(), any(), capture(errorMsg)) } just runs

        // mock block
        every { mockConnFut.block() } just runs
        // mock unblock for connect future
        every { mockConnFut.unblock() } just runs

        // mock utpreadfuture block
        every { mockReadFut.block() } just runs

        // create a utp receiver with the mocked objects
        val utpReceiver = UTPReceiver(mockedUTPDataFragment, mockedCommunity)
        // create a thread to run the receiveData method
        utpReceiver.receiveData(IPv4Address("1.2.3.4", 1234), 0)

        assertEquals("Invalid data size received from sender. Stopping receiver.", errorMsg.captured)

        // Do the same with null datasize
        utpReceiver.receiveData(IPv4Address("1.2.3.4", 1234), null)

        assertEquals("Invalid data size received from sender. Stopping receiver.", errorMsg.captured)
    }

    @Test
    fun receiveDataFailedRead() {
        // we test for socket.read() functionality indirectly on the IPV8 testsuite.
        // continue by mocking everything else and verify the correct amount of calls

        mockkConstructor(UtpSocketChannelImpl::class)
        var mockConnFut = mockk<UtpConnectFuture>()
        var mockReadFut = mockk<UtpReadFutureImpl>()

        // mock connect method
        every { anyConstructed<UtpSocketChannelImpl>().connect(any()) } returns mockConnFut
        every { anyConstructed<UtpSocketChannelImpl>().read(any()) } returns mockReadFut
        // await and release immediately
        every { anyConstructed<UtpSocketChannelImpl>().close() } returns mockk()

        var errorMsg = slot<String>();
        every { mockedUTPDataFragment.newDataReceived(any(), any(), any(), capture(errorMsg)) } just runs

        // mock block
        every { mockConnFut.block() } just runs
        // mock unblock for connect future
        every { mockConnFut.unblock() } just runs

        // mock utpreadfuture block
        every { mockReadFut.block() } throws Exception("Failed to read")

        // create a utp receiver with the mocked objects
        val utpReceiver = UTPReceiver(mockedUTPDataFragment, mockedCommunity)
        // create a thread to run the receiveData method
        utpReceiver.receiveData(IPv4Address("1.2.3.4", 1234), 10)

        assertEquals("Error: Failed to read", errorMsg.captured)
    }

    @Test
    fun receiveDataConnectFutureNull() {
        // we test for socket.read() functionality indirectly on the IPV8 testsuite.
        // continue by mocking everything else and verify the correct amount of calls

        mockkConstructor(UtpSocketChannelImpl::class)
        var mockConnFut = mockk<UtpConnectFuture>()
        var mockReadFut = mockk<UtpReadFutureImpl>()

        // mock connect method
        every { anyConstructed<UtpSocketChannelImpl>().connect(any()) } returns null

        // create a utp receiver with the mocked objects
        val utpReceiver = UTPReceiver(mockedUTPDataFragment, mockedCommunity)
        // create a thread to run the receiveData method
        assertEquals(Unit, utpReceiver.receiveData(IPv4Address("1.2.3.4", 1234), 10))
    }


    @Test
    fun receiveDataFailedBind() {
        // we test for socket.read() functionality indirectly on the IPV8 testsuite.
        // continue by mocking everything else and verify the correct amount of calls

        mockkConstructor(UtpSocketChannelImpl::class)
        var mockConnFut = mockk<UtpConnectFuture>()
        var mockReadFut = mockk<UtpReadFutureImpl>()

        // mock connect method
        every { anyConstructed<UtpSocketChannelImpl>().connect(any()) } returns null
        every { anyConstructed<UtpSocketChannelImpl>().dgSocket = any() } throws IOException("Failed to bind")

        var errorMsg = slot<String>();
        every { mockedUTPDataFragment.newDataReceived(any(), any(), any(), capture(errorMsg)) } just runs

        // create a utp receiver with the mocked objects
        val utpReceiver = UTPReceiver(mockedUTPDataFragment, mockedCommunity)
        // create a thread to run the receiveData method
        assertEquals(Unit, utpReceiver.receiveData(IPv4Address("1.2.3.4", 1234), 10))

        assertEquals("Error: Could not open UtpSocketChannel: Failed to bind", errorMsg.captured)
    }

    @Test
    fun receiveDataZeroReceived() {
    // we test for socket.read() functionality indirectly on the IPV8 testsuite.
        // continue by mocking everything else and verify the correct amount of calls

        mockkConstructor(UtpSocketChannelImpl::class)
        mockkConstructor(ByteBuffer::class)
        var mockConnFut = mockk<UtpConnectFuture>()
        var mockReadFut = mockk<UtpReadFutureImpl>()

        // mock connect method
        every { anyConstructed<UtpSocketChannelImpl>().connect(any()) } returns mockConnFut
        every { anyConstructed<UtpSocketChannelImpl>().read(any()) } returns mockReadFut
        // await and release immediately
        every { anyConstructed<UtpSocketChannelImpl>().close() } returns mockk()
        every { anyConstructed<ByteBuffer>().position() } returns 0

        var errorMsg = slot<String>();
        every { mockedUTPDataFragment.newDataReceived(any(), any(), any(), capture(errorMsg)) } just runs

        // mock block
        every { mockConnFut.block() } just runs
        // mock unblock for connect future
        every { mockConnFut.unblock() } just runs

        // mock utpreadfuture block
        every { mockReadFut.block() } just runs

        // create a utp receiver with the mocked objects
        val utpReceiver = UTPReceiver(mockedUTPDataFragment, mockedCommunity)
        // create a thread to run the receiveData method
        utpReceiver.receiveData(IPv4Address("1.2.3.4", 1234), 10)

        assertEquals("No data sent", errorMsg.captured)
    }

    @Test
    fun receiveDataDiffDataSize() {
        // we test for socket.read() functionality indirectly on the IPV8 testsuite.
        // continue by mocking everything else and verify the correct amount of calls

        // mock the line "val buffer = ByteBuffer.allocate(dataSize)"

        mockkConstructor(UtpSocketChannelImpl::class)
        var mockConnFut = mockk<UtpConnectFuture>()
        var mockReadFut = mockk<UtpReadFutureImpl>()

        // mock connect method
        every { anyConstructed<UtpSocketChannelImpl>().connect(any()) } returns mockConnFut
        every { anyConstructed<UtpSocketChannelImpl>().read(any<ByteBuffer>()) } answers {
            (it.invocation.args[0] as ByteBuffer).put(ByteArray(9))
            mockReadFut
        }

        // await and release immediately

        var errorMsg = slot<String>();
        every { mockedUTPDataFragment.newDataReceived(any(), any(), any(), capture(errorMsg)) } just runs

        // mock block
        every { mockConnFut.block() } just runs
        // mock unblock for connect future
        every { mockConnFut.unblock() } just runs

        // mock utpreadfuture block
        every { mockReadFut.block() } just runs

        // create a utp receiver with the mocked objects
        val utpReceiver = UTPReceiver(mockedUTPDataFragment, mockedCommunity)
        // create a thread to run the receiveData method
        utpReceiver.receiveData(IPv4Address("1.2.3.4", 1234), 10)

        assertEquals("Received 9 bytes instead of 10", errorMsg.captured)
    }


}
