package nl.tudelft.trustchain.common.messaging

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.SERIALIZED_UINT_SIZE
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeUInt
import nl.tudelft.ipv8.messaging.deserializeUShort
import nl.tudelft.ipv8.messaging.serializeUInt
import nl.tudelft.ipv8.messaging.serializeUShort

class OpenPortPayload(val port: Int, var dataSize: Int = 0): Serializable {
    override fun serialize(): ByteArray {
        return serializeUInt(port.toUInt()) + serializeUInt(dataSize.toUInt())
    }

    companion object Deserializer : Deserializable<OpenPortPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<OpenPortPayload, Int> {
            var localOffset = offset

            val port = deserializeUInt(buffer, localOffset)
            localOffset += SERIALIZED_UINT_SIZE
            val dataSize = deserializeUInt(buffer, localOffset)
            localOffset += SERIALIZED_UINT_SIZE

            return Pair(OpenPortPayload(port.toInt(), dataSize.toInt()), localOffset)
        }
    }

}
