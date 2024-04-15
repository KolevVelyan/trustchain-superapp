package nl.tudelft.trustchain.common

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.EndpointListener
import nl.tudelft.ipv8.peerdiscovery.Network
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IPV8SocketTest {
    private var marketCommunity = spyk(DemoCommunity(), recordPrivateCalls = true)
    private val myPeer = mockk<Peer>()
    private val socket = mockk<IPV8Socket>()
    private val endpoint = mockk<EndpointAggregator>()
    private val network = mockk<Network>(relaxed = true)
    @Before
    fun setUp() {

        every { marketCommunity.getPeers() } returns ArrayList<Peer>();
        every { marketCommunity.myPeer } returns myPeer
        every { marketCommunity.endpoint } returns endpoint
        every { marketCommunity.network } returns network
        every { endpoint.send(any<Peer>(), any()) } just runs
        every { endpoint.addListener(any<EndpointListener>()) } just runs
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

        // Call the send function with a null value

        // Assert the
    }

    @Test
    fun onPacketNotForUs() {
        // Create the community

        // Create the socket

        // Put a packet received, which is not for us

        // Assert that the handler and received is not called
    }

    @Test
    fun onPacketForUs() {
        // Create the community

        // Create the socket

        // Put a packet received, which is not for us

        // Assert that the handler and received is called

        // Assert that the semaphore is released and the message queue is populated
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
        val testSocket = IPV8Socket(marketCommunity);

        // Assert that the community is correct
        assertEquals(marketCommunity, testSocket.community);
    }
}
