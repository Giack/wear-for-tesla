package dev.giack.teslawear.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import dev.giack.teslawear.diag.Diag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** What a scan saw; shown by the setup diagnostics. */
data class ScanReport(
    val permissionsOk: Boolean,
    val bluetoothOn: Boolean,
    val locationOn: Boolean,
    val devicesSeen: Int,
    val teslaNames: Set<String>,
    val found: Boolean,
)

/**
 * Finds the car by its advertised local name ("S" + SHA1(VIN)[:8] + "C"). Scans without a system
 * filter and matches the name itself, so a name carried only in the scan response still matches.
 */
class BleScanner(private val context: Context) {
    val requiredPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    fun permissionsOk(): Boolean =
        requiredPermissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    suspend fun find(localName: String, timeoutMs: Long): BluetoothDevice? = scan(localName, timeoutMs).first

    /** Full-length scan for the diagnostics; also returns the car if it was seen. */
    suspend fun diagnose(localName: String, timeoutMs: Long): Pair<BluetoothDevice?, ScanReport> =
        scan(localName, timeoutMs, stopOnMatch = false)

    @SuppressLint("MissingPermission")
    private suspend fun scan(localName: String, timeoutMs: Long, stopOnMatch: Boolean = true): Pair<BluetoothDevice?, ScanReport> {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val locationOn = context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true
        val perms = permissionsOk()
        val btOn = adapter?.isEnabled == true
        val scanner = adapter?.takeIf { perms && btOn }?.bluetoothLeScanner ?: run {
            Diag.w("BLE scan not started: permissions=$perms bluetooth=$btOn")
            return null to ScanReport(perms, btOn, locationOn, 0, emptySet(), false)
        }
        val seen = mutableSetOf<String>()
        val teslaNames = mutableSetOf<String>()
        val match = CompletableDeferred<BluetoothDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                seen += result.device.address
                val name = result.scanRecord?.deviceName ?: return
                if (name.length == 18 && name.startsWith("S") && name.endsWith("C")) teslaNames += name
                if (name == localName) match.complete(result.device)
            }
            override fun onScanFailed(errorCode: Int) {
                Diag.w("BLE scan failed: $errorCode")
                match.completeExceptionally(IllegalStateException("scan failed $errorCode"))
            }
        }
        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        // Only a scan failure becomes "not found": cancellation must keep propagating.
        val device = try {
            if (stopOnMatch) withTimeoutOrNull(timeoutMs) { try { match.await() } catch (_: IllegalStateException) { null } }
            else { kotlinx.coroutines.delay(timeoutMs); if (match.isCompleted) runCatching { match.getCompleted() }.getOrNull() else null }
        } finally {
            runCatching { scanner.stopScan(callback) }
        }
        Diag.i("BLE scan ${timeoutMs / 1000.0} s for $localName: ${seen.size} devices, tesla=$teslaNames, found=${device != null}")
        return device to ScanReport(perms, btOn, locationOn, seen.size, teslaNames, device != null)
    }
}
