package io.horizontalsystems.tonkit.api

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber

class RateLimitInterceptor(
    private val apiKeyProvider: ApiKeyProvider,
    private val sleeper: Sleeper = Sleeper { Thread.sleep(it) }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        var apiKey = waitForHealthyKey()
        var response = chain.proceed(originalRequest.withApiKey(apiKey))

        var attempt = 0
        while (response.code == RetryAfterParser.HTTP_TOO_MANY_REQUESTS && attempt < MAX_RETRIES) {
            attempt++
            val retryAfterMs = RetryAfterParser.parseMillis(response)
            response.close()

            apiKeyProvider.banKey(apiKey, retryAfterMs)

            val nextKey = apiKeyProvider.nextHealthyKey()
            if (nextKey != null) {
                Timber.tag(TAG).i("Rate limited, rotating to healthy key (attempt %d/%d)", attempt, MAX_RETRIES)
                apiKey = nextKey
            } else {
                Timber.tag(TAG).i("All keys banned (attempt %d/%d)", attempt, MAX_RETRIES)
                apiKey = waitForHealthyKey()
            }

            response = chain.proceed(originalRequest.withApiKey(apiKey))
        }

        if (response.code == RetryAfterParser.HTTP_TOO_MANY_REQUESTS) {
            apiKeyProvider.banKey(apiKey, RetryAfterParser.parseMillis(response))
        }

        return response
    }

    private fun waitForHealthyKey(): String {
        while (true) {
            apiKeyProvider.nextHealthyKey()?.let { return it }

            val waitMs = apiKeyProvider.msUntilAnyHealthyKey()
            if (waitMs > 0) {
                Timber.tag(TAG).i("No healthy keys, waiting %dms", waitMs)
                sleeper.sleep(waitMs)
            }
        }
    }

    private fun Request.withApiKey(key: String): Request =
        newBuilder().header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$key").build()

    fun interface Sleeper {
        fun sleep(millis: Long)
    }

    companion object {
        private const val TAG = "RateLimitInterceptor"
        private const val MAX_RETRIES = 3
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
}
