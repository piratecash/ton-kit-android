package io.horizontalsystems.tonkit.api

import okhttp3.Response

internal object RetryAfterParser {
    const val HTTP_TOO_MANY_REQUESTS = 429
    private const val RETRY_AFTER_HEADER = "Retry-After"
    private const val DEFAULT_RETRY_AFTER_SEC = 5L
    private const val MIN_RETRY_AFTER_SEC = 1L
    private const val MAX_RETRY_AFTER_SEC = 60L

    fun parseMillis(response: Response): Long {
        val seconds = response.header(RETRY_AFTER_HEADER)
            ?.toLongOrNull()
            ?: DEFAULT_RETRY_AFTER_SEC

        return seconds.coerceIn(MIN_RETRY_AFTER_SEC, MAX_RETRY_AFTER_SEC) * 1000
    }
}
