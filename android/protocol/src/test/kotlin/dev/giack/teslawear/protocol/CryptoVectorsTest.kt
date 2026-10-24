package dev.giack.teslawear.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CryptoVectorsTest {
    private val k = DocVectors.k

    @Test fun clientPublicBytesMatchDoc() = assertEquals(
        "04b2b6bc68c2da0665ce656815594996c62394edd8bea905fe781a754fe6a845a714330902f225e9269d466e05b349981fda9d85cc23c6fb444aa73b629105dc6e",
        JvmEcdhKey.fromPem(DocVectors.CLIENT_PEM).publicBytes.hex())

    @Test fun ecdhDerivesDocKey() = assertEquals(k.hex(),
        SessionCrypto.deriveKey(JvmEcdhKey.fromPem(DocVectors.CLIENT_PEM).sharedSecretX(DocVectors.vehiclePublic)).hex())

    @Test fun ecdhIsSymmetric() {
        val client = JvmEcdhKey.fromPem(DocVectors.CLIENT_PEM)
        val vehicle = JvmEcdhKey.fromPem(DocVectors.VEHICLE_PEM)
        assertEquals(client.sharedSecretX(vehicle.publicBytes).hex(), vehicle.sharedSecretX(client.publicBytes).hex())
    }

    @Test fun generatedKeysAgree() {
        val a = JvmEcdhKey.generate()
        val b = JvmEcdhKey.generate()
        assertEquals(65, a.publicBytes.size)
        assertEquals(a.sharedSecretX(b.publicBytes).hex(), b.sharedSecretX(a.publicBytes).hex())
    }

    @Test fun sessionInfoSubkey() = assertEquals(
        "fceb679ee7bca756fcd441bf238bf2f338629b41d9eb9c67be1b32c9672ce300",
        SessionCrypto.subkey(k, "session info").hex())

    @Test fun metadataSerializationMatchesDoc() = assertEquals(
        DocVectors.GCM_METADATA,
        MetadataBuilder().add(0, byteArrayOf(5)).add(1, byteArrayOf(3)).add(2, DocVectors.VIN.toByteArray())
            .add(3, DocVectors.epoch).addUInt32(4, 2655).addUInt32(5, 7).addUInt32(7, 2)
            .serialize().hex())

    @Test fun metadataSkipsNullValues() = assertEquals("0201aaff",
        MetadataBuilder().add(1, null).add(2, byteArrayOf(0xaa.toByte())).serialize().hex())

    @Test fun aesGcmMatchesDoc() {
        val aad = SessionCrypto.sha256(unhex(DocVectors.GCM_METADATA))
        val (ct, tag) = SessionCrypto.aesGcmSeal(k, unhex("dbf79447fa156674dae1caed"), unhex("120452020801"), aad)
        assertEquals("38038e8c0f2e", ct.hex())
        assertEquals("c228e0ff64991481db3a7bbc133696c5", tag.hex())
        assertEquals("120452020801", SessionCrypto.aesGcmOpen(k, unhex("dbf79447fa156674dae1caed"), ct, tag, aad).hex())
    }

    @Test fun metadataRejectsOutOfOrderTags() {
        assertFailsWith<IllegalArgumentException> { MetadataBuilder().add(2, byteArrayOf(1)).add(1, byteArrayOf(1)) }
    }

    @Test fun metadataRejectsLongValues() {
        assertFailsWith<IllegalArgumentException> { MetadataBuilder().add(2, ByteArray(256)) }
    }
}
