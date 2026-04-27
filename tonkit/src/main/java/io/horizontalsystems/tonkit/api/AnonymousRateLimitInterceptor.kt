package io.horizontalsystems.tonkit.api

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber

class AnonymousRateLimitInterceptor(
    private val sleeper: RateLimitInterceptor.Sleeper = RateLimitInterceptor.Sleeper { Thread.sleep(it) },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var attempt = 0

        while (response.code == RetryAfterParser.HTTP_TOO_MANY_REQUESTS && attempt < MAX_RETRIES) {
            attempt++
            val retryAfterMs = RetryAfterParser.parseMillis(response)
            logRateLimit(request, response, attempt, retryAfterMs, willRetry = true)
            response.close()

            sleeper.sleep(retryAfterMs)
            response = chain.proceed(request)
        }

        if (response.code == RetryAfterParser.HTTP_TOO_MANY_REQUESTS) {
            logRateLimit(request, response, attempt, null, willRetry = false)
        }

        return response
    }

    private fun logRateLimit(
        request: Request,
        response: Response,
        attempt: Int,
        retryAfterMs: Long?,
        willRetry: Boolean,
    ) {
        val headers = response.headers
            .filter { (name, _) -> name in LOGGABLE_HEADERS || name.startsWith(RATE_LIMIT_PREFIX, ignoreCase = true) }
            .joinToString { (name, value) -> "$name=$value" }
        val action = if (willRetry) "retry in ${retryAfterMs}ms" else "return 429"

        Timber.tag(TAG).w(
            "Anonymous TonAPI 429: %s %s, attempt=%d/%d, action=%s, headers=[%s]",
            request.method, request.url.host, attempt, MAX_RETRIES, action, headers
        )
    }

    companion object {
        private const val TAG = "TonApiRateLimit"
        private const val MAX_RETRIES = 6
        private const val RATE_LIMIT_PREFIX = "X-RateLimit-"

        // Allowlist prevents accidental leakage of Set-Cookie, Authorization,
        // proxy-auth and other sensitive headers if a consumer plants a release Tree.
        private val LOGGABLE_HEADERS: Set<String> = sortedSetOf(
            String.CASE_INSENSITIVE_ORDER,
            "Retry-After", "CF-RAY", "Date", "Content-Type"
        )
    }
}
