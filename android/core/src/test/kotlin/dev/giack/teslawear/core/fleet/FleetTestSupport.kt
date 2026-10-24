package dev.giack.teslawear.core.fleet

import dev.giack.teslawear.core.usage.UsageCounts
import dev.giack.teslawear.core.usage.UsageMeter
import dev.giack.teslawear.core.usage.UsageStore

class MemoryTokenStore(var tokens: Tokens? = null) : TokenStore {
    override fun load() = tokens
    override fun save(t: Tokens) { tokens = t }
    override fun clear() { tokens = null }
}

class MemoryUsageStore(var counts: UsageCounts? = null) : UsageStore {
    override fun load() = counts
    override fun save(c: UsageCounts) { counts = c }
}

fun fixture(name: String): String =
    requireNotNull(FleetTestSupport::class.java.getResource("/fleet/$name")) { "missing fixture $name" }.readText()

object FleetTestSupport

fun meterAt(nowMs: Long = 1_760_000_000_000L) = UsageMeter(MemoryUsageStore(), nowMs = { nowMs })

class MemoryRegionStore(private var url: okhttp3.HttpUrl? = null) : RegionStore {
    override fun load() = url
    override fun save(url: okhttp3.HttpUrl) { this.url = url }
}
