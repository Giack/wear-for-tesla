package dev.giack.teslawear.protocol

import java.io.ByteArrayOutputStream

/** Tag-length-value metadata serialization used to bind signatures to context (protocol.md "Metadata serialization"). */
class MetadataBuilder {
    private val out = ByteArrayOutputStream()
    private var last = -1

    fun add(tag: Int, value: ByteArray?): MetadataBuilder {
        if (value == null) return this
        require(tag >= last) { "metadata tags must be added in increasing order" }
        require(value.size <= 255) { "metadata values can't be more than 255 bytes long" }
        last = tag
        out.write(tag)
        out.write(value.size)
        out.write(value)
        return this
    }

    fun addUInt32(tag: Int, value: Long): MetadataBuilder = add(tag, uint32(value))

    /** Serialized metadata terminated by the TAG_END (0xFF) byte. */
    fun serialize(): ByteArray = out.toByteArray() + byteArrayOf(0xFF.toByte())

    companion object {
        fun uint32(value: Long): ByteArray = byteArrayOf(
            (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte(),
        )
    }
}
