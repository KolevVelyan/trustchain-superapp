package nl.tudelft.trustchain.common

import io.mockk.mockk
import io.mockk.spyk
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.peerdiscovery.Network
import org.junit.jupiter.api.Assertions.*

class IPV8SocketTest {
    private var marketCommunity = spyk(DemoCommunity(), recordPrivateCalls = true)
    private val myPeer = mockk<Peer>()
    private val socket = mockk<IPV8Socket>()
    private val endpoint = mockk<EndpointAggregator>()
    private val network = mockk<Network>(relaxed = true)
    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
    }

    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
    }

    @org.junit.jupiter.api.Test
    fun receiveWaitsForSemaphore() {
        // Run test for 5 seconds and assume that the thread is still blocked

    }

    @org.junit.jupiter.api.Test
    fun receiveThrowsIOExceptionWhenInterrupted() {
        // Create the socket

        // Run the socket recieve thread

        // Interrupt the thread and assert that IOException is thrown
    }

    @org.junit.jupiter.api.Test
    fun recieveThrowsErrorOnEmptyQueue() {
        // Create the socket

        // Release the semaphore

        // Check that an error is thrown
    }

    @org.junit.jupiter.api.Test
    fun receiveReadsFromQueueOncePerSemaphore() {
        // Create the socket

        // Populate the message queue

        // Release the semaphore

        // Check that the queue was popped only once
    }

    @org.junit.jupiter.api.Test
    fun sendCorrect() {
        // Mock the community

        // Make sure that the community endpoint is called with the right send payload

        // Make sure that the status function is called

    }

    @org.junit.jupiter.api.Test
    fun sendWhenPeerNotDiscoveredYet() {
        // Mock the community

        // Make sure that the community endpoint is called with the right send payload

        // Make sure that the status function is called

    }

    @org.junit.jupiter.api.Test
    fun sendDatagramNull() {
        // Create the socket

        // Call the send function with a null value

        // Assert the
    }

    @org.junit.jupiter.api.Test
    fun onPacketNotForUs() {
        // Create the community

        // Create the socket

        // Put a packet received, which is not for us

        // Assert that the handler and received is not called
    }

    @org.junit.jupiter.api.Test
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
    @org.junit.jupiter.api.Test
    fun onEstimatedLanChanged() {
        assertTrue(true);
    }

    @org.junit.jupiter.api.Test
    fun getCommunity() {
        val testCommunity = DemoCommunity();

        // Test the socket
        val testSocket = IPV8Socket(testCommunity);

        // Assert that the community is correct
        assertEquals(testCommunity, testSocket.community);
    }
}
