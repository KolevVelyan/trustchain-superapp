package nl.tudelft.trustchain.common

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.messaging.Address
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.EndpointListener
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.peerdiscovery.Network
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.DatagramPacket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.lang.Error
import java.net.InetAddress
import java.net.InetSocketAddress

class IPV8SocketTest {
    private var mockedCommunity = spyk(DemoCommunity(), recordPrivateCalls = true)
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
    }
    private fun statusFunction(
        a: Boolean,
        b: Int,
        c: Int,
        d: Int
    ) {
print("" + a+ b+ c+ d);
    }

    @After
    fun tearDown() {
    }

    @Test
    fun receiveWaitsForSemaphore() {
        // Run test for 5 seconds and assume that the thread is still blocked

    }



    @Test
    fun receiveThrowsIOExceptionWhenInterrupted() {
        // Create the socket

        // Create a mock Semaphore
        val mockSemaphore = mockk<Semaphore>()
        every { mockSemaphore.acquire() } throws InterruptedException()
        var testSocket = IPV8Socket(mockedCommunity)
        testSocket.readSemaphore = mockSemaphore

        // Interrupt the thread and assert that IOException is thrown
        assertThrows(IOException::class.java) {
            testSocket.receive(null)
        }
    }

    @Test
    fun recieveThrowsErrorOnEmptyQueue() {
        // Create a mock Semaphore
        val mockSemaphore = mockk<Semaphore>()
        every { mockSemaphore.acquire() } just runs

        // mock the queue
        val mockQueue = mockk<ConcurrentLinkedQueue<Pair<IPv4Address, UTPPayload>>>()
        every { mockQueue.poll() } returns null

        var testSocket = IPV8Socket(mockedCommunity)
        testSocket.readSemaphore = mockSemaphore
        testSocket.messageQueue = mockQueue

        // Interrupt the thread and assert that IOException is thrown
        assertThrows(Error::class.java) {
            testSocket.receive(null)
        }
    }

    @Test
    fun receiveReadsFromQueueOncePerSemaphore() {
        // Create the socket

        // Populate the message queue

        // Release the semaphore

        // Check that the queue was popped only once
    }

    @Test
    fun sendCorrect() {
        // Create the datagram packet
        val testData = ByteArray(50);
        val testSource = InetAddress.getByName("1.2.3.4")
        val testPort = 5678;
        val datagramPacket = DatagramPacket(testData, testData.size);
        datagramPacket.address = testSource;
        datagramPacket.port = testPort;

        // Create the socket
        val testSocket = IPV8Socket(mockedCommunity);
        testSocket.statusFunction = this::statusFunction;
        val targetAddress = IPv4Address(datagramPacket.address.hostAddress!!, datagramPacket.port)

        val capturedAddress = slot<Address>();
        val capturedPayload = slot<ByteArray>();

        every { mockedCommunity.myPeer.key } returns mockk<PublicKey>();
        every { endpoint.send(capture(capturedAddress), capture(capturedPayload)) } just runs;

        // run the function
        testSocket.send(datagramPacket);

        // Assertions
        assertEquals(targetAddress, capturedAddress.captured);

        val serializedPacket = mockedCommunity.serializePacket(msgIdUTPRawData, UTPPayload(datagramPacket), sign = false)
        assertArrayEquals(serializedPacket, capturedPayload.captured)


    }

    @Test
    fun sendWhenPeerNotDiscoveredYet() {
        // Mock the community

        // Make sure that the community endpoint is called with the right send payload

        // Make sure that the status function is called

    }

    @Test
    fun sendDatagramNull() {
        // Create the socket
        val testSocket = IPV8Socket(mockedCommunity);
        // Call the send function with a null value
        assertThrows(Error::class.java) {
            testSocket.send(null)
        }
    }

    @Test
    fun onPacketNotForUs() {
        // Create the socket
        val testSocket = IPV8Socket(mockedCommunity);

        // Put a packet received, which is not for us
        val testPacket = mockk<Packet>();
        val mockedPeer = mockk<Peer>();
        val mockedData = ByteArray(22);

        // Mock all needed calls
        every { testPacket.source } returns mockk<Address>()
        every { testPacket.data } returns mockedData;
        every { network.getVerifiedByAddress(any()) } returns mockedPeer;
        every { mockedPeer.lastResponse =  any() } just runs;

        // Assert that the handler and received is not called
        testSocket.onPacket(testPacket);
    }

    @Test
    fun onPacketForUs() {
        // Create all needed data
        val fakePayload = UTPPayload(DatagramPacket(ByteArray(30), 30));

        val mockedPacket = mockk<Packet>()
        val mockedSource = mockk<IPv4Address>();
        val mockedData = mockedCommunity.serializePacket(msgIdUTPRawData, fakePayload, sign = false)


        // Mock all needed calls
        every { network.getVerifiedByAddress(any()) } returns null;
        every { mockedPacket.data } returns mockedData;
        every { mockedPacket.source } returns mockedSource;
        every { mockedSource.ip } returns "1.2.3.4";
        every { mockedSource.port } returns 5678;

        // Create the socket and call the onPacket
        val testSocket = IPV8Socket(mockedCommunity);
        testSocket.statusFunction = this::statusFunction;
        val testPacket = Packet(mockedSource, mockedData);
        testSocket.onPacket(testPacket);

        val recievedDatagram = DatagramPacket(ByteArray(150), 150);
        testSocket.receive(recievedDatagram);

        assertEquals(InetSocketAddress(InetAddress.getByName(mockedSource.ip), mockedSource.port), recievedDatagram.socketAddress)

        assertArrayEquals(fakePayload.payload.data, recievedDatagram.data)
    }

    /**
     * This should not do anything by design
     */
    @Test
    fun onEstimatedLanChanged() {
        // Test the socket
        val testSocket = IPV8Socket(mockedCommunity);
        val mockedAddress = mockk<IPv4Address>();

        testSocket.onEstimatedLanChanged(mockedAddress);
    }

    @Test
    fun getCommunity() {
        // Test the socket
        val testSocket = IPV8Socket(mockedCommunity);

        // Assert that the community is correct
        assertEquals(mockedCommunity, testSocket.community);
    }
}
