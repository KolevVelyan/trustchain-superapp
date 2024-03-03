package nl.tudelft.trustchain.common.messaging

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.SERIALIZED_USHORT_SIZE
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeUShort
import nl.tudelft.ipv8.messaging.serializeUShort

class OpenPortPayload(val port: Int): Serializable {
    override fun serialize(): ByteArray {
        return serializeUShort(port)
    }

    companion object Deserializer : Deserializable<OpenPortPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<OpenPortPayload, Int> {
            var localOffset = offset

            val port = deserializeUShort(buffer, offset + localOffset)
            localOffset += SERIALIZED_USHORT_SIZE

            return Pair(OpenPortPayload(port), localOffset)
        }
    }

}
