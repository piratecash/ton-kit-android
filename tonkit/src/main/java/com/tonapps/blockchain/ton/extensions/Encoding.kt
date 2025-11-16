package com.tonapps.blockchain.ton.extensions

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
fun base64(string: String): ByteArray = Base64.Default.decode(string)

@OptIn(ExperimentalEncodingApi::class)
fun base64(byteArray: ByteArray): String = Base64.Default.encode(byteArray)

@OptIn(ExperimentalEncodingApi::class)
fun base64url(string: String): ByteArray = Base64.UrlSafe.decode(string)

@OptIn(ExperimentalEncodingApi::class)
fun base64url(byteArray: ByteArray): String = Base64.UrlSafe.encode(byteArray)

fun hex(s: String): ByteArray {
    val result = ByteArray(s.length / 2)
    for (idx in result.indices) {
        val srcIdx = idx * 2
        val high = s[srcIdx].toString().toInt(16) shl 4
        val low = s[srcIdx + 1].toString().toInt(16)
        result[idx] = (high or low).toByte()
    }
    return result
}

@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.encodeBase64(): String = Base64.encode(this)
