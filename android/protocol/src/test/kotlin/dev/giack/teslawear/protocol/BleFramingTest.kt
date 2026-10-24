package dev.giack.teslawear.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BleFramingTest {
    @Test fun localNameMatchesDoc() = assertEquals("S1a87a5a75f3df858C", BleFraming.vehicleLocalName("5YJS0000000000000"))

    @Test fun frameAddsBigEndianLength() = assertEquals("0003aabbcc", BleFraming.frame(unhex("aabbcc")).hex())

    @Test fun chunksSplitByBlockLength() {
        val chunks = BleFraming.chunks(ByteArray(45), 20)
        assertEquals(listOf(20, 20, 5), chunks.map { it.size })
    }

    @Test fun reassemblesAcrossChunks() {
        val msg = ByteArray(300) { it.toByte() }
        val chunks = BleFraming.chunks(BleFraming.frame(msg), 20)
        val r = BleFraming.Reassembler()
        chunks.dropLast(1).forEach { assertNull(r.push(it)) }
        assertEquals(msg.hex(), r.push(chunks.last())!!.hex())
    }

    @Test fun reassemblerStartsFreshAfterAMessage() {
        val r = BleFraming.Reassembler()
        r.push(BleFraming.frame(unhex("01")))
        assertEquals("0203", r.push(BleFraming.frame(unhex("0203")))!!.hex())
    }

    @Test fun rejectsOversizeLength() {
        assertFailsWith<IllegalStateException> { BleFraming.Reassembler().push(unhex("0401")) }
    }
}
