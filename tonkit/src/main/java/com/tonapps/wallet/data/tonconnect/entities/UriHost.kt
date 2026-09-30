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

private fun String.percentDecoded(): String {
    if ('%' !in this) return this
    val bytes = ByteArrayOutputStream()
    var i = 0
    while (i < length) {
        if (this[i] != '%') {
            val next = indexOf('%', i).takeIf { it >= 0 } ?: length
            bytes.write(substring(i, next).toByteArray())
            i = next
            continue
        }
        val escaped = substring(i + 1, minOf(i + 3, length)).hexByte()
        if (escaped != null) {
            bytes.write(escaped)
            i += 3
        } else {
            bytes.write(INVALID_ESCAPE)
            i++
        }
    }
    return bytes.toString(Charsets.UTF_8.name())
}

private fun String.hexByte(): Int? =
    takeIf { length == 2 && all { Character.digit(it, 16) >= 0 } }?.toInt(16)

private val INVALID_ESCAPE = "\uFFFD".toByteArray()
