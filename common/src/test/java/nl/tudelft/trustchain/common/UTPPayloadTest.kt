package nl.tudelft.trustchain.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.DatagramPacket


class UTPPayloadTest {

    @Test
    fun serialize() {
        val testData = ByteArray(50);
        val testPacket = DatagramPacket(testData, testData.size);
        val testUTPPayload = UTPPayload(testPacket);

        assertArrayEquals(testData, testUTPPayload.serialize());
    }

    @Test
    fun getPayload() {
        val testData = ByteArray(50);
        val testPacket = DatagramPacket(testData, testData.size);
        val testUTPPayload = UTPPayload(testPacket);

        assertEquals(testPacket, testUTPPayload.payload);
    }

    @Test
    fun fullTest() {
        val testData = ByteArray(50);
        val testPacket = DatagramPacket(testData, testData.size);
        val testUTPPayload = UTPPayload(testPacket);

        val serialized = testUTPPayload.serialize();
        val (deserialized, _) = UTPPayload.deserialize(serialized, 0);


        assertEquals(testUTPPayload.payload.address, deserialized.payload.address);
        assertEquals(testUTPPayload.payload.port, deserialized.payload.port);
        assertArrayEquals(testUTPPayload.payload.data, deserialized.payload.data);
    }
    @Test
    fun deserialize() {
        val data = ByteArray(50);

        val (deserialized, len) = UTPPayload.deserialize(data, 0);

        assertArrayEquals(DatagramPacket(ByteArray(50), 50).data, deserialized.payload.data)
        assertEquals(50, len)
    }
}
