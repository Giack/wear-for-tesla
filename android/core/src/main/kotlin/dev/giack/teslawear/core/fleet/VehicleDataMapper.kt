package dev.giack.teslawear.core.fleet

import dev.giack.teslawear.core.Channel
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.core.setup.CarModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/** What the car looks like according to Fleet mode; paint is a generic paint name when recognised. */
data class FleetCarConfig(val model: CarModel, val paint: String, val wheels: String)

/** Maps Fleet API `vehicle_data` JSON to a [Snapshot]. */
object VehicleDataMapper {
    private const val KM_PER_MILE = 1.609344

    fun map(json: String, nowMs: Long): Snapshot {
        val response = Json.parseToJsonElement(json).jsonObject.getValue("response").jsonObject
        val charge = response.obj("charge_state")
        val climate = response.obj("climate_state")
        val vehicle = response.obj("vehicle_state")
        val windows = listOf("fd_window", "fp_window", "rd_window", "rp_window").mapNotNull { vehicle?.int(it) }
        return Snapshot(
            batteryPct = charge?.int("battery_level"),
            rangeKm = charge?.double("battery_range")?.let { (it * KM_PER_MILE).roundToInt() },
            locked = vehicle?.bool("locked"),
            climateOn = climate?.bool("is_climate_on"),
            insideTempC = climate?.double("inside_temp")?.toFloat(),
            setTempC = climate?.double("driver_temp_setting")?.toFloat(),
            chargePortOpen = charge?.bool("charge_port_door_open"),
            charging = charge?.string("charging_state")?.let { it == "Charging" },
            windowsOpen = if (windows.isEmpty()) null else windows.any { it != 0 },
            trunkOpen = vehicle?.int("rt")?.let { it != 0 },
            frunkOpen = vehicle?.int("ft")?.let { it != 0 },
            // vehicle_data only answers while the car is awake.
            asleep = false,
            updatedAtMs = nowMs,
            channel = Channel.CLOUD,
            carConfig = response.obj("vehicle_config")?.let(::carConfig),
        )
    }

    private fun carConfig(config: JsonObject): FleetCarConfig? {
        val model = when (config.string("car_type")) {
            "modely" -> CarModel.MODEL_Y
            "model3" -> CarModel.MODEL_3
            else -> return null // no Car illustration for other models yet
        }
        return FleetCarConfig(model, genericPaint(config.string("exterior_color").orEmpty()), config.string("wheel_type").orEmpty())
    }

    /** Tesla paint name (e.g. `UltraRed`) to a generic paint name; unknown names are kept as they are. */
    fun genericPaint(tesla: String): String = when {
        "Red" in tesla -> "RED"
        "White" in tesla -> "WHITE"
        "Black" in tesla -> "BLACK"
        "Blue" in tesla -> "BLUE"
        "Quicksilver" in tesla -> "SILVER"
        "Grey" in tesla || "Gray" in tesla || "MidnightSilver" in tesla -> "GREY"
        "Silver" in tesla -> "SILVER"
        else -> tesla
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.double(key: String) = this[key]?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.bool(key: String) = this[key]?.jsonPrimitive?.booleanOrNull
    private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.content
}
