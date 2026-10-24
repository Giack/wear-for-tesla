package dev.giack.teslawear.setup

import dev.giack.teslawear.R
import dev.giack.teslawear.ble.AndroidBleLink
import dev.giack.teslawear.core.setup.Setup
import dev.giack.teslawear.diag.Diag
import dev.giack.teslawear.protocol.Enrollment
import dev.giack.teslawear.ui.UiText
import dev.giack.teslawear.ui.text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Pairs the watch's BLE key: request over Bluetooth, Keycard tap, then checks until the car accepts it. */
class BleKeyPairing(private val link: AndroidBleLink, private val setup: Setup) {
    /** Runs the whole pairing; [prompt] shows what the user must do now. Returns the final message. */
    suspend fun pair(prompt: (UiText) -> Unit): UiText = try {
        link.requestKey()
        setup.bleKeyRequested()
        prompt(text(R.string.pairing_keycard_prompt))
        // Each check logs its own result ("BLE key: …").
        waitForPairing()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Diag.w("BLE key request failed", e)
        text(R.string.pairing_request_failed, e.message.orEmpty())
    }

    private suspend fun waitForPairing(): UiText {
        repeat(CHECKS) {
            delay(INTERVAL_MS)
            when (link.verifyKey()) {
                Enrollment.PAIRED -> {
                    setup.bleKeyPaired()
                    return text(R.string.pairing_done)
                }
                Enrollment.INSUFFICIENT_PRIVILEGES -> return text(R.string.pairing_insufficient_privileges)
                Enrollment.NOT_PAIRED, Enrollment.OUT_OF_RANGE, Enrollment.NO_REPLY -> Unit // keep waiting for the Keycard
            }
        }
        return when (link.verifyKey()) {
            Enrollment.OUT_OF_RANGE -> text(R.string.pairing_out_of_range)
            Enrollment.NO_REPLY -> text(R.string.pairing_no_reply)
            else -> text(R.string.pairing_not_yet)
        }
    }

    private companion object {
        const val CHECKS = 30
        const val INTERVAL_MS = 3_000L
    }
}
