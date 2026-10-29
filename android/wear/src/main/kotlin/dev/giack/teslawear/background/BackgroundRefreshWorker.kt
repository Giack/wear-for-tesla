package dev.giack.teslawear.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.giack.teslawear.core.Channel
import dev.giack.teslawear.core.RefreshTrigger
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.diag.Diag
import dev.giack.teslawear.graph
import java.util.concurrent.TimeUnit

/**
 * Keeps the stored state current while the app is closed, twice an hour: Bluetooth to the known car,
 * else one Fleet API read (Fleet mode only). Never wakes the car; each run is written to the log.
 */
class BackgroundRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = applicationContext.graph
        val provisioned = graph.provisioned() ?: return Result.success()
        val before = graph.snapshots.load()
        val after = runCatching { provisioned.refresher.refresh(RefreshTrigger.BACKGROUND) }
            .onFailure { Diag.w("Background refresh failed", it) }
            .getOrNull()
        Diag.i("Background: ${backgroundOutcome(before, after)}")
        return Result.success()
    }

    companion object {
        private const val NAME = "background-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackgroundRefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** One line for the log saying what a background run achieved. */
fun backgroundOutcome(before: Snapshot?, after: Snapshot?): String = when {
    after == null || after == before -> "not near, cloud skipped"
    after.updatedAtMs != before?.updatedAtMs ->
        if (after.channel == Channel.BLE) "read over Bluetooth" else "read over Fleet API"
    after.asleep == true -> "car asleep, not woken"
    else -> "not near, cloud skipped"
}
