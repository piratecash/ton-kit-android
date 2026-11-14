package io.horizontalsystems.tonkit.core

import io.horizontalsystems.tonkit.api.TonApi
import io.horizontalsystems.tonkit.models.Network
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/** Answers every request with the status code and JSON body [respond] gives. */
internal fun fakeHttpClient(respond: (Request) -> Pair<Int, String>): OkHttpClient =
    OkHttpClient.Builder().addInterceptor { chain ->
        val (code, json) = respond(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("OK")
            .body(json.toResponseBody("application/json".toMediaType())).build()
    }.build()

/**
 * A mainnet [TonApi] answering only the seqno (unless [seqno] is null) and time requests of signing, plus the
 * emulate request with [emulation] when given; each emulate request body is recorded in [emulateRequests].
 */
internal fun fakeTonApi(
    seqno: Int?,
    rawTime: Int,
    emulation: String? = null,
    emulateRequests: MutableList<String> = mutableListOf(),
): TonApi {
    val client = fakeHttpClient { request ->
        val path = request.url.encodedPath
        200 to when {
            path.endsWith("/seqno") && seqno != null -> """{"seqno":$seqno}"""
            path.endsWith("/get_time") -> """{"time":$rawTime}"""
            path.endsWith("/wallet/emulate") && emulation != null -> {
                emulateRequests += Buffer().also { request.body?.writeTo(it) }.readUtf8()
                emulation
            }
            else -> error("Unexpected request $path")
        }
    }
    return TonApi(Network.MainNet, client)
}
