package dev.giack.teslawear.core.fleet

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Hands out a valid access token, refreshing it shortly before expiry. */
class TokenProvider(
    private val store: TokenStore,
    private val oauth: OAuthClient,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    val hasTokens: Boolean get() = store.load() != null

    suspend fun accessToken(): String = mutex.withLock {
        val tokens = store.load() ?: throw AuthRequiredException()
        if (tokens.expiresAtMs - nowMs() > REFRESH_MARGIN_MS) return@withLock tokens.access
        val fresh = try {
            oauth.refresh(tokens.refresh)
        } catch (e: AuthRequiredException) {
            store.clear()
            throw e
        }
        store.save(fresh)
        fresh.access
    }

    fun save(tokens: Tokens) = store.save(tokens)

    /** The server rejected our access token: refresh now regardless of its local expiry. */
    suspend fun forceRefresh(): String = mutex.withLock {
        val tokens = store.load() ?: throw AuthRequiredException()
        val fresh = try {
            oauth.refresh(tokens.refresh)
        } catch (e: AuthRequiredException) {
            store.clear()
            throw e
        }
        store.save(fresh)
        fresh.access
    }

    fun clear() = store.clear()

    private companion object {
        const val REFRESH_MARGIN_MS = 60_000L
    }
}
