package com.tonapps.wallet.data.tonconnect.entities

import java.io.ByteArrayOutputStream

// Same result as android.net.Uri.getHost: any scheme, original case, lenient percent-decoding.
internal val String.uriHost: String?
    get() {
        val schemeEnd = indexOf(':')
        if (!startsWith("//", schemeEnd + 1)) return null
        val authorityStart = schemeEnd + 3
        val authorityEnd = indexOfAny(AUTHORITY_TERMINATORS, authorityStart).takeIf { it >= 0 } ?: length
        val authority = substring(authorityStart, authorityEnd)
        val host = authority.substringAfterLast('@')
        val portSeparator = host.lastIndexOf(':')
        val hasPort = portSeparator >= 0 && host.substring(portSeparator + 1).all { it in '0'..'9' }
        return (if (hasPort) host.substring(0, portSeparator) else host).percentDecoded()
    }

private val AUTHORITY_TERMINATORS = charArrayOf('/', '\\', '?', '#')

// Mirrors the lenient mode of android.net.UriCodec.decode, including how it consumes malformed escapes.
private fun String.percentDecoded(): String {
    if ('%' !in this) return this
    val decoded = StringBuilder()
    val bytes = ByteArrayOutputStream()
    var i = 0
    while (i < length) {
        val c = this[i++]
        if (c != '%') {
            decoded.appendDecoded(bytes).append(c)
            continue
        }
        var byte = 0
        for (j in 0..1) {
            if (i == length) return decoded.appendDecoded(bytes).append(INVALID_ESCAPE).toString()
            val digit = this[i++].hexDigit()
            if (digit < 0) {
                decoded.appendDecoded(bytes).append(INVALID_ESCAPE)
                break
            }
            byte = byte * 16 + digit
        }
        bytes.write(byte)
    }
    return decoded.appendDecoded(bytes).toString()
}

// Consecutive escapes form one UTF-8 sequence; malformed bytes become U+FFFD.
private fun StringBuilder.appendDecoded(bytes: ByteArrayOutputStream): StringBuilder {
    if (bytes.size() > 0) {
        append(bytes.toString(Charsets.UTF_8.name()))
        bytes.reset()
    }
    return this
}

private fun Char.hexDigit(): Int = when (this) {
    in '0'..'9' -> this - '0'
    in 'a'..'f' -> this - 'a' + 10
    in 'A'..'F' -> this - 'A' + 10
    else -> -1
}

private const val INVALID_ESCAPE = '\uFFFD'
