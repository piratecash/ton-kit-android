package io.horizontalsystems.tonkit.api

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.logging.Logger

class RateLimitInterceptor(
    private val apiKeyProvider: ApiKeyProvider,
    private val sleeper: Sleeper = Sleeper { Thread.sleep(it) }
) : Interceptor {

    private val logger = Logger.getLogger("RateLimitInterceptor")

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        var apiKey = waitForHealthyKey()
        var response = chain.proceed(originalRequest.withApiKey(apiKey))

        var attempt = 0
        while (response.code == HTTP_TOO_MANY_REQUESTS && attempt < MAX_RETRIES) {
            attempt++
            val retryAfterSec = parseRetryAfter(response)
            response.close()

            apiKeyProvider.banKey(apiKey, retryAfterSec * 1000)

            val nextKey = apiKeyProvider.nextHealthyKey()
            if (nextKey != null) {
                logger.info("Rate limited, rotating to healthy key (attempt $attempt/$MAX_RETRIES)")
                apiKey = nextKey
            } else {
                logger.info("All keys banned (attempt $attempt/$MAX_RETRIES)")
                apiKey = waitForHealthyKey()
            }

            response = chain.proceed(originalRequest.withApiKey(apiKey))
        }

        if (response.code == HTTP_TOO_MANY_REQUESTS) {
            apiKeyProvider.banKey(apiKey, parseRetryAfter(response) * 1000)
        }

        return response
    }

    private fun waitForHealthyKey(): String {
        while (true) {
            apiKeyProvider.nextHealthyKey()?.let { return it }

            val waitMs = apiKeyProvider.msUntilAnyHealthyKey()
            if (waitMs > 0) {
                logger.info("No healthy keys, waiting ${waitMs}ms")
                sleeper.sleep(waitMs)
            }
        }
    }

    private fun Request.withApiKey(key: String): Request =
        newBuilder().header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$key").build()

    private fun parseRetryAfter(response: Response): Long {
        val header = response.header("Retry-After") ?: return DEFAULT_RETRY_AFTER_SEC
        val seconds = header.toLongOrNull() ?: return DEFAULT_RETRY_AFTER_SEC
        return seconds.coerceIn(1, MAX_RETRY_AFTER_SEC)
    }

    fun interface Sleeper {
        fun sleep(millis: Long)
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val DEFAULT_RETRY_AFTER_SEC = 30L
        private const val MAX_RETRY_AFTER_SEC = 60L
    }
}
