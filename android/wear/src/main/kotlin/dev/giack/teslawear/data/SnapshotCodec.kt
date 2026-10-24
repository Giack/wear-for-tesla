package dev.giack.teslawear.data

import dev.giack.teslawear.core.Channel
import dev.giack.teslawear.core.Snapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

object SnapshotCodec {
    fun encode(s: Snapshot): String = buildJsonObject {
        s.batteryPct?.let { put("battery", JsonPrimitive(it)) }
        s.rangeKm?.let { put("range", JsonPrimitive(it)) }
        s.locked?.let { put("locked", JsonPrimitive(it)) }
        s.climateOn?.let { put("climate", JsonPrimitive(it)) }
        s.insideTempC?.let { put("inside", JsonPrimitive(it)) }
        s.setTempC?.let { put("set", JsonPrimitive(it)) }
        s.chargePortOpen?.let { put("port", JsonPrimitive(it)) }
        s.charging?.let { put("charging", JsonPrimitive(it)) }
        s.windowsOpen?.let { put("windows", JsonPrimitive(it)) }
        s.trunkOpen?.let { put("trunk", JsonPrimitive(it)) }
        s.asleep?.let { put("asleep", JsonPrimitive(it)) }
        put("at", JsonPrimitive(s.updatedAtMs))
        s.checkedAtMs?.let { put("checked", JsonPrimitive(it)) }
        put("channel", JsonPrimitive(s.channel.name))
    }.toString()

    fun decode(text: String): Snapshot? = runCatching {
        val o = Json.parseToJsonElement(text).jsonObject
        Snapshot(
            batteryPct = o.int("battery"), rangeKm = o.int("range"), locked = o.bool("locked"),
            climateOn = o.bool("climate"), insideTempC = o.float("inside"), setTempC = o.float("set"),
            chargePortOpen = o.bool("port"), charging = o.bool("charging"), windowsOpen = o.bool("windows"),
            trunkOpen = o.bool("trunk"), asleep = o.bool("asleep"), updatedAtMs = o.getValue("at").jsonPrimitive.long,
            checkedAtMs = o["checked"]?.jsonPrimitive?.longOrNull,
            channel = Channel.valueOf(o.getValue("channel").jsonPrimitive.content),
        )
    }.getOrNull()

    private fun JsonObject.int(k: String) = this[k]?.jsonPrimitive?.intOrNull
    private fun JsonObject.float(k: String) = this[k]?.jsonPrimitive?.floatOrNull
    private fun JsonObject.bool(k: String) = this[k]?.jsonPrimitive?.booleanOrNull
}
