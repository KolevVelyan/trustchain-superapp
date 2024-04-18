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
import net.utp4j.channels.UtpServerSocketChannel
import net.utp4j.channels.UtpSocketChannel
import net.utp4j.channels.futures.UtpAcceptFuture
import net.utp4j.channels.futures.UtpCloseFuture
import net.utp4j.channels.futures.UtpWriteFuture
import net.utp4j.channels.impl.UtpServerSocketChannelImpl
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.messaging.Address
import nl.tudelft.trustchain.common.DemoCommunity
import nl.tudelft.trustchain.common.IPV8Socket
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

class UTPSenderTest {
    private lateinit var mockedDataFragment: UTPDataFragment
    private lateinit var mockedCommunity: Community
    private var mockedIsSending: Boolean = false
    private lateinit var mockedSocket: IPV8Socket
    private lateinit var mockedServer: UtpServerSocketChannel
    private lateinit var mockedChannel: UtpSocketChannel
    private lateinit var mockedFuture: UtpAcceptFuture

    protected fun createSender(): UTPSender {
        return UTPSender(
            mockedDataFragment,
            mockedCommunity, mockedIsSending, mockedSocket,
            mockedServer, mockedChannel, mockedFuture
        );
    }

    private fun statusFunction(
        a: Boolean,
        b: Int,
        c: Int,
        d: Int
    ) {
        print("" + a + b + c + d);
    }

    @Before
    fun setUp() {
        this.mockedDataFragment = mockk<UTPDataFragment>()
        this.mockedCommunity = spyk(DemoCommunity(), recordPrivateCalls = true)
        this.mockedSocket = mockk<IPV8Socket>()
        this.mockedServer = mockk<UtpServerSocketChannel>()
        this.mockedChannel = mockk<UtpSocketChannel>()
        this.mockedFuture = mockk<UtpAcceptFuture>()

        setUpSend();
    }

    @Test
    fun stopConnection() {
        // Create the sender
        val testSender = createSender()

        // Stop the connection
        testSender.stopConnection()

        // Assertions
        verify(exactly = 1) { mockedChannel.close() }
        verify(exactly = 1) { mockedFuture.unblock() }
    }

    @Test
    fun sendWhileSending() {
        // Make the future return null
        every { mockedChannel.write(any()) } just Awaits;

        // Create the sender
        val testSender = createSender()

        // Run the function
        val t = Thread {
            testSender.sendData(validPeer(), validData())
        }
        t.start()
        Thread.sleep(1000)

        // Make sure that the sender is still sending
        assertEquals(true, testSender.isSending());


        // Mock the data fragment
        val successAnswer = slot<Boolean>()
        val destinationAddress = slot<String>()
        val msgAnswer = slot<String>()
        every {
            mockedDataFragment.newDataSent(
                capture(successAnswer),
                capture(destinationAddress),
                capture(msgAnswer)
            )
        } just runs;

        // Run the send again
        testSender.sendData(validPeer(), validData());


        // Assertions
        assertEquals(false, successAnswer.captured)
        assertEquals("", destinationAddress.captured)
        assertEquals(true, msgAnswer.captured.contains("Already sending"))
    }

    @Test
    fun sendInvalidAddress() {
        // Create an invalid peer
        val invalidPeer = mockk<Peer>()
        every { invalidPeer.address.toString() } returns "0.0.0.0"

        // Generate sender
        val testSender = createSender()

        // Mock the data fragment
        val successAnswer = slot<Boolean>()
        val destinationAddress = slot<String>()
        val msgAnswer = slot<String>()
        every {
            mockedDataFragment.newDataSent(
                capture(successAnswer),
                capture(destinationAddress),
                capture(msgAnswer)
            )
        } just runs;

        // Try to send
        testSender.sendData(invalidPeer, ByteArray(100));

        // Check the answer
        assertEquals(false, successAnswer.captured)
        assertEquals("", destinationAddress.captured)
        assertEquals(true, msgAnswer.captured.contains("Invalid peer address:"))
    }

    @Test
    fun sendInvalidData() {
        // Create an valid peer
        val validPeer = mockk<Peer>()
        every { validPeer.address.toString() } returns "1.2.3.4"

        // Create invalid data
        val invalidData = ByteArray(0)

        // Generate sender
        val testSender = createSender()

        // Mock the data fragment
        val successAnswer = slot<Boolean>()
        val destinationAddress = slot<String>()
        val msgAnswer = slot<String>()
        every {
            mockedDataFragment.newDataSent(
                capture(successAnswer),
                capture(destinationAddress),
                capture(msgAnswer)
            )
        } just runs;

        // Try to send
        testSender.sendData(validPeer, invalidData);

        // Check the answer
        assertEquals(false, successAnswer.captured)
        assertEquals(validPeer.address.toString(), destinationAddress.captured)
        assertEquals(true, msgAnswer.captured.contains("No data to send"))
    }

    private fun validPeer(): Peer {
        val validPeer = mockk<Peer>();
        val validAddress = IPv4Address("1.2.3.4", 5678);
        every { validPeer.address } returns validAddress
        return validPeer
    }

    private fun validData(): ByteArray {
        val validData = ByteArray(100);
        return validData
    }

    protected fun setUpSend() {
        // Prepare uTP Fragment
        every { mockedDataFragment.debugInfo(any(), any()) } just runs;
        every { mockedDataFragment.debugInfo(any(), any(), any()) } just runs;
        every { mockedDataFragment.newDataSent(any(), any(), any()) } just runs;

        // Prepare endpoint
        every { mockedCommunity.myPeer } returns mockk<Peer>()
        every { mockedCommunity.myPeer.lamportTimestamp } returns 0u;
        every { mockedCommunity.myPeer.updateClock(any()) } just runs;
        every { mockedCommunity.myPeer.key } returns mockk<PublicKey>();
        every { mockedCommunity.endpoint.send(any<Address>(), any()) } just runs;
        every { mockedCommunity.serviceId } returns "";
        every { mockedCommunity.endpoint.addListener(any()) } just runs;

        // Prepare channel
        mockkConstructor(UtpServerSocketChannelImpl::class)
        every { anyConstructed<UtpServerSocketChannelImpl>().bind(any<IPV8Socket>()) } just runs;
        every { anyConstructed<UtpServerSocketChannelImpl>().accept() } returns mockedFuture;
        every { anyConstructed<UtpServerSocketChannelImpl>().close() } just runs;
        every { mockedServer.accept() } returns mockedFuture
        every { mockedFuture.block() } just runs
        every { mockedFuture.unblock() } just runs;
        every { mockedFuture.channel } returns mockedChannel
        every { mockedSocket.statusFunction } returns this::statusFunction;
        every { mockedSocket.statusFunction = any() } just runs;

        // Prepare the send
        val mockedWriteFuture = mockk<UtpWriteFuture>()
        every { mockedChannel.write(any()) } returns mockedWriteFuture
        every { mockedChannel.remoteAdress } returns InetSocketAddress.createUnresolved(
            "1.2.3.4",
            5678
        );
        every { mockedWriteFuture.block() } just runs;
        val mockedCloseFuture = mockk<UtpCloseFuture>()
        every { mockedChannel.close() } returns mockedCloseFuture;
        every { mockedServer.close() } just runs;
    }

    @Test
    fun sendSuccess() {
        // Create the sender
        val testSender = createSender()

        // Run the function
        testSender.sendData(validPeer(), validData())

        // Assertions
        verify(exactly = 1) { mockedChannel.write(any()) }
        verify(exactly = 1) { anyConstructed<UtpServerSocketChannelImpl>().close() }
        assertEquals(false, testSender.isSending())
    }

    @Test
    fun sendStopConnection() {
        // Make the future return null
        every { anyConstructed<UtpServerSocketChannelImpl>().accept() } returns null;

        // Create the sender
        val testSender = createSender()

        // Run the function
        val ret = testSender.sendData(validPeer(), validData())

        // Assertions
        assertEquals(Unit, ret);
    }

    @Test
    fun sendCatch1() {
        // Make the future return null
        every { anyConstructed<UtpServerSocketChannelImpl>().bind(any<IPV8Socket>()) } throws Exception();

        // Capture the status
        val successAnswer = slot<Boolean>()
        val destinationAddress = slot<String>()
        val msgAnswer = slot<String>()
        every {
            mockedDataFragment.newDataSent(
                capture(successAnswer),
                capture(destinationAddress),
                capture(msgAnswer)
            )
        } just runs;

        // Create the sender
        val testSender = createSender()

        // Run the function
        testSender.sendData(validPeer(), validData())

        // Assertions
        assertEquals(false, successAnswer.captured)
        assertEquals("", destinationAddress.captured)
        assertEquals(true, msgAnswer.captured.contains("Error"))
        assertEquals(false, testSender.isSending());

    }

    @Test
    fun sendCatch2() {
        // Make the future return null
        every { anyConstructed<UtpServerSocketChannelImpl>().accept() } throws Exception();

        // Capture the status
        val successAnswer = slot<Boolean>()
        val destinationAddress = slot<String>()
        val msgAnswer = slot<String>()
        every {
            mockedDataFragment.newDataSent(
                capture(successAnswer),
                capture(destinationAddress),
                capture(msgAnswer)
            )
        } just runs;

        // Create the sender
        val testSender = createSender()

        // Run the function
        testSender.sendData(validPeer(), validData())

        // Assertions
        assertEquals(false, successAnswer.captured)
        assertEquals("", destinationAddress.captured)
        assertEquals(true, msgAnswer.captured.contains("Error"))
        assertEquals(false, testSender.isSending());

    }
}
