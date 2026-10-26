package io.horizontalsystems.tonkit.core

import okhttp3.OkHttpClient

/** Created by [TonKit.createHttpClient]; kits sharing it share the API key rotation state and the connection pool. */
class TonHttpClient internal constructor(internal val okHttpClient: OkHttpClient)
