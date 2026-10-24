package dev.giack.teslawear.diag

import android.content.Context
import android.util.Log
import dev.giack.teslawear.TeslaConfig.LOG_TAG
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The newest [maxLines] diagnostic lines, kept in a file so a test at the car leaves evidence
 * without adb. The file grows to twice the limit before it is rewritten, so most adds are appends.
 */
class DiagLog(private val file: File, private val maxLines: Int = 300, private val nowMs: () -> Long = System::currentTimeMillis) {
    private val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
    private var fileLines = 0
    private val lines: ArrayDeque<String> by lazy {
        val stored = runCatching { file.readLines() }.getOrDefault(emptyList())
        fileLines = stored.size
        ArrayDeque(stored.takeLast(maxLines))
    }

    @Synchronized fun add(message: String) {
        val line = "${time.format(Date(nowMs()))} | ${message.replace("\n", " ⏎ ")}"
        lines.addLast(line)
        while (lines.size > maxLines) lines.removeFirst()
        runCatching {
            if (fileLines >= 2 * maxLines) {
                file.writeText(lines.joinToString("\n", postfix = "\n"))
                fileLines = lines.size
            } else {
                file.appendText(line + "\n")
                fileLines++
            }
        }
    }

    @Synchronized fun tail(n: Int): List<String> = lines.toList().takeLast(n)
}

/** App-wide diagnostics: every line goes to logcat and to the on-watch log shown in Setup. */
object Diag {
    @Volatile private var log: DiagLog? = null

    fun init(context: Context) { log = DiagLog(File(context.filesDir, "diag.log")) }

    fun i(message: String) {
        Log.i(LOG_TAG, message)
        log?.add(message)
    }

    fun w(message: String, e: Throwable? = null) {
        Log.w(LOG_TAG, message, e)
        log?.add("⚠ $message" + (e?.let { " — ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }

    fun tail(n: Int): List<String> = log?.tail(n).orEmpty()
}
