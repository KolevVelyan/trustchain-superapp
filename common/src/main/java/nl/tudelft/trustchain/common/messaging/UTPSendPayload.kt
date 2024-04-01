package nl.tudelft.trustchain.common.messaging

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.SERIALIZED_LONG_SIZE
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeLong
import nl.tudelft.ipv8.messaging.serializeLong

class UTPSendPayload(var dataSize: Int = 0): Serializable {
    override fun serialize(): ByteArray {
        return serializeLong(dataSize.toLong())
    }

    companion object Deserializer : Deserializable<UTPSendPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<UTPSendPayload, Int> {
            var localOffset = offset

            val dataSize = deserializeLong(buffer, localOffset)
            localOffset += SERIALIZED_LONG_SIZE

            return Pair(UTPSendPayload(dataSize.toInt()), localOffset)
        }
    }

}
