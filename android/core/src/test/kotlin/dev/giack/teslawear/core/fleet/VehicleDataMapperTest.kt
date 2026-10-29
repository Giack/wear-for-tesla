package dev.giack.teslawear.core.fleet

import dev.giack.teslawear.core.Channel
import dev.giack.teslawear.core.setup.CarModel
import kotlin.test.Test
import kotlin.test.assertEquals

class VehicleDataMapperTest {
    @Test fun vehicleDataMapsFixture() {
        val s = VehicleDataMapper.map(fixture("vehicle_data_online.json"), nowMs = 42)
        assertEquals(78, s.batteryPct)
        assertEquals(312, s.rangeKm)
        assertEquals(true, s.locked)
        assertEquals(false, s.climateOn)
        assertEquals(19.0f, s.insideTempC)
        assertEquals(21.5f, s.setTempC)
        assertEquals(false, s.chargePortOpen)
        assertEquals(false, s.charging)
        assertEquals(false, s.windowsOpen)
        assertEquals(false, s.trunkOpen)
        assertEquals(42, s.updatedAtMs)
        assertEquals(Channel.CLOUD, s.channel)
    }

    @Test fun frunkMapsFromFt() {
        val s = VehicleDataMapper.map(fixture("vehicle_data_open.json").replace("\"ft\":0", "\"ft\":1"), nowMs = 0)
        assertEquals(true, s.frunkOpen)
    }

    @Test fun openStatesMap() {
        val s = VehicleDataMapper.map(fixture("vehicle_data_open.json"), nowMs = 0)
        assertEquals(false, s.locked)
        assertEquals(true, s.climateOn)
        assertEquals(true, s.chargePortOpen)
        assertEquals(true, s.charging)
        assertEquals(true, s.windowsOpen)
        assertEquals(true, s.trunkOpen)
        assertEquals(false, s.frunkOpen)
    }

    @Test fun vehicleConfigBecomesTheCarProfile() {
        val s = VehicleDataMapper.map(fixture("vehicle_data_config.json"), nowMs = 0)
        assertEquals(FleetCarConfig(CarModel.MODEL_Y, paint = "RED", wheels = "Helix20"), s.carConfig)
    }

    @Test fun teslaPaintNamesMapToGenericPaints() {
        val cases = mapOf(
            "PearlWhite" to "WHITE", "SolidBlack" to "BLACK", "StealthGrey" to "GREY", "MidnightSilver" to "GREY",
            "QuicksilverMetallic" to "SILVER", "DeepBlue" to "BLUE", "RedMulticoat" to "RED", "UltraRed" to "RED",
            "Lunar" to "Lunar",
        )
        cases.forEach { (tesla, generic) -> assertEquals(generic, VehicleDataMapper.genericPaint(tesla), tesla) }
    }

    @Test fun unsupportedModelsLeaveTheProfileAlone() {
        val json = fixture("vehicle_data_config.json").replace("\"modely\"", "\"models\"")
        assertEquals(null, VehicleDataMapper.map(json, nowMs = 0).carConfig)
    }
}
