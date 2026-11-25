package dev.giack.teslawear.complication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The complication path stays a stored-snapshot read: no new permission, no refresh, no wake. */
class ComplicationPolicyTest {
    private val module = File(".").absoluteFile.let { if (File(it, "src/main").exists()) it else File(it, "wear") }

    @Test fun manifestAddsProvidersWithoutNewPermissions() {
        val xml = module.resolve("src/main/AndroidManifest.xml").readText()
        val permissions = Regex("""uses-permission android:name="android\.permission\.([^"]+)"""")
            .findAll(xml).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf("INTERNET", "BLUETOOTH_SCAN", "ACCESS_FINE_LOCATION", "BLUETOOTH_CONNECT"),
            permissions,
        )
        val providers = listOf(
            "LockStatusComplicationService" to "@string/complication_lock_status_label",
            "LockCommandComplicationService" to "@string/complication_command_lock_label",
            "ClimateCommandComplicationService" to "@string/complication_command_climate_label",
            "SentryCommandComplicationService" to "@string/complication_command_sentry_label",
        )
        providers.forEach { (service, label) ->
            val block = xml.substringAfter("android:name=\".complication.$service\"").substringBefore("</service>")
            assertTrue(service, block.contains("android:label=\"$label\""))
            assertTrue(service, block.contains("BIND_COMPLICATION_PROVIDER"))
            assertTrue(service, block.contains("SHORT_TEXT,MONOCHROMATIC_IMAGE,SMALL_IMAGE"))
            assertTrue(service, block.contains("UPDATE_PERIOD_SECONDS\" android:value=\"0\""))
        }
        assertFalse(xml.contains("uses-permission android:name=\"com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER\""))
    }

    @Test fun complicationSourcesOnlyReadTheStoredSnapshot() {
        val root = module.resolve("src/main/kotlin/dev/giack/teslawear/complication")
        val banned = listOf("wake_up", "wakeUp", "RefreshTrigger", ".refresh(", "FleetApi", "vehicle_data", "vehicleData")
        val hits = root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                val code = line.substringBefore("//")
                banned.firstOrNull { code.contains(it) }?.let { "${file.name}:${i + 1} $it" }
            }
        }.toList()
        assertEquals(emptyList<String>(), hits)
        val sources = root.walkTopDown().filter { it.name == "ComplicationServices.kt" }.single().readText()
        assertTrue(sources.contains("readComplicationSnapshot"))
        assertFalse(sources.contains("provisioned()"))
        // One import, and one call in each of the four provider request paths.
        assertEquals(5, Regex("readComplicationSnapshot").findAll(sources).count())
    }
}
