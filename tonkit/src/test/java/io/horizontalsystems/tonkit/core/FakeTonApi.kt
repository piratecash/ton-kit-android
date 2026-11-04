package io.horizontalsystems.tonkit.core

import io.horizontalsystems.tonkit.api.TonApi
import io.horizontalsystems.tonkit.models.Network
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/**
 * A mainnet [TonApi] answering only the seqno and time requests of signing, plus the emulate
 * request with [emulation] when given; each emulate request body is recorded in [emulateRequests].
 */
internal fun fakeTonApi(
    seqno: Int,
    rawTime: Int,
    emulation: String? = null,
    emulateRequests: MutableList<String> = mutableListOf(),
): TonApi {
    val client = OkHttpClient.Builder().addInterceptor { chain ->
        val path = chain.request().url.encodedPath
        val json = when {
            path.endsWith("/seqno") -> """{"seqno":$seqno}"""
            path.endsWith("/get_time") -> """{"time":$rawTime}"""
            path.endsWith("/wallet/emulate") && emulation != null -> {
                emulateRequests += Buffer().also { chain.request().body?.writeTo(it) }.readUtf8()
                emulation
            }
            else -> error("Unexpected request $path")
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(json.toResponseBody("application/json".toMediaType())).build()
    }.build()
    return TonApi(Network.MainNet, client)
}
