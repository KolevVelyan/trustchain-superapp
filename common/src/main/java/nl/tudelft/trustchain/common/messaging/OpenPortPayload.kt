package nl.tudelft.trustchain.common.messaging

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.SERIALIZED_LONG_SIZE
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeLong
import nl.tudelft.ipv8.messaging.serializeLong

class OpenPortPayload(val port: Int, var dataSize: Int = 0): Serializable {
    override fun serialize(): ByteArray {
        return serializeLong(port.toLong()) + serializeLong(dataSize.toLong())
    }

    companion object Deserializer : Deserializable<OpenPortPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<OpenPortPayload, Int> {
            var localOffset = offset

            val port = deserializeLong(buffer, localOffset)
            localOffset += SERIALIZED_LONG_SIZE
            val dataSize = deserializeLong(buffer, localOffset)
            localOffset += SERIALIZED_LONG_SIZE

            return Pair(OpenPortPayload(port.toInt(), dataSize.toInt()), localOffset)
        }
    }

}
