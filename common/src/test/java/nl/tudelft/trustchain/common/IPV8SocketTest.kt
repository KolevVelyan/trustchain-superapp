package nl.tudelft.trustchain.common

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
import java.lang.Error
import java.net.DatagramPacket
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

        // Run the socket recieve thread

        // Interrupt the thread and assert that IOException is thrown
    }

    @Test
    fun recieveThrowsErrorOnEmptyQueue() {
        // Create the socket
//        val socketTest = mockedSocket;
//        val semaphore = mockk<Semaphore>();

        // Release the semaphore

        // Check that an error is thrown
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
        // Mock the community

        // Make sure that the community endpoint is called with the right send payload

        // Make sure that the status function is called

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
        assertTrue(true);
    }

    @Test
    fun getCommunity() {
        // Test the socket
        val testSocket = IPV8Socket(mockedCommunity);

        // Assert that the community is correct
        assertEquals(mockedCommunity, testSocket.community);
    }
}
