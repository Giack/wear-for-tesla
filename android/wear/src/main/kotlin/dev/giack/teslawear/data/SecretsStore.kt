package dev.giack.teslawear.data

import android.content.Context
import dev.giack.teslawear.protocol.EcdhKey
import dev.giack.teslawear.protocol.JvmEcdhKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Keys and Fleet API credentials imported from the Mac (`setup.sh push-watch`). Stored encrypted with a
 * Keystore AES key; the EC scalars are decrypted in memory for ECDH (the Keystore cannot import a bare EC
 * scalar without a certificate chain).
 */
class SecretsStore(context: Context) {
    private val file = EncryptedJsonFile(context.filesDir.resolve("secrets.bin"), KeystoreCipher("secrets"))

    /** What Fleet mode needs: the user's own Developer app and Cloud key. */
    data class Fleet(
        val vin: String,
        val clientId: String,
        val clientSecret: String,
        val cloudKey: EcdhKey,
        /** Domain publishing the Cloud key (for the `tesla.com/_ak/<domain>` pairing link); null on old imports. */
        val domain: String?,
    )

    fun fleet(): Fleet? {
        val json = file.read() ?: return null
        fun s(k: String) = json[k]?.jsonPrimitive?.content.orEmpty()
        if (FLEET_FIELDS.any { s(it).isEmpty() }) return null
        return Fleet(s("vin"), s("client_id"), s("client_secret"), JvmEcdhKey(unhex(s("key")), unhex(s("pub"))), s("domain").ifEmpty { null })
    }

    /** BLE key imported from the Mac (paired with `setup.sh ble-check`), if any. Never the Cloud key. */
    fun importedBleKey(): EcdhKey? {
        val json = file.read() ?: return null
        val key = json["ble_key"]?.jsonPrimitive?.content.orEmpty()
        val pub = json["ble_pub"]?.jsonPrimitive?.content.orEmpty()
        return if (key.isEmpty() || pub.isEmpty()) null else JvmEcdhKey(unhex(key), unhex(pub))
    }

    /** Opaque options for the Personal build's Official renders (optional, ADR-0002). */
    fun renderOptions(): String? = file.read()?.get("render_options")?.jsonPrimitive?.content?.ifEmpty { null }

    /** Validates and stores the JSON written by `setup.sh push-watch`. */
    fun import(json: JsonObject) {
        val missing = FLEET_FIELDS.filter { json[it]?.jsonPrimitive?.content.isNullOrEmpty() }
        require(missing.isEmpty()) { "campi mancanti: $missing" }
        require(json.getValue("vin").jsonPrimitive.content.length == 17) { "VIN non valido" }
        JvmEcdhKey(unhex(json.getValue("key").jsonPrimitive.content), unhex(json.getValue("pub").jsonPrimitive.content))
        write(json.filterKeys { it in FLEET_FIELDS + "domain" + KEPT_FIELDS })
    }

    /** Deletes the Fleet credentials; an imported BLE key and render options stay. */
    fun forgetFleet() {
        val kept = file.read()?.filter { (k, v) -> k in KEPT_FIELDS && v.jsonPrimitive.content.isNotEmpty() }.orEmpty()
        if (kept.isEmpty()) file.delete() else write(kept)
    }

    /** Deletes everything imported from the Mac (Fleet credentials, imported BLE key, render options). */
    fun forgetAll() = file.delete()

    private fun write(fields: Map<String, kotlinx.serialization.json.JsonElement>) =
        file.write(JsonObject(fields.mapValues { JsonPrimitive(it.value.jsonPrimitive.content) }))

    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private companion object {
        val FLEET_FIELDS = listOf("key", "pub", "vin", "client_id", "client_secret")
        /** Survive [forgetFleet]: they are not Fleet credentials. */
        val KEPT_FIELDS = listOf("ble_key", "ble_pub", "render_options")
    }
}
