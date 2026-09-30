package com.tonapps.wallet.data.tonconnect.entities


import com.tonapps.security.CryptoBox
import com.tonapps.security.hex
import org.json.JSONObject
import java.net.URLDecoder

data class DAppRequestEntity(
    val v: Int = 2,
    val id: String,
    val r: String,
    val ret: String? = null,
) {

    val payload = DAppPayloadEntity(JSONObject(r))

    companion object {
        fun parse(uri: String): DAppRequestEntity {
            val query = uri.substringBefore('#').substringAfter('?', "")
            return DAppRequestEntity(
                v = query.parameter("v")?.toInt() ?: throw IllegalArgumentException("v is required"),
                id = query.parameter("id")
                    ?.also { require(isValidClientId(it)) { "id is not a valid client public key" } }
                    ?: throw IllegalArgumentException("id is required"),
                r = query.parameter("r") ?: throw IllegalArgumentException("r is required"),
                ret = query.parameter("ret")
            )
        }

        /** A client key the bridge can encrypt to; checked before a connection is stored. */
        internal fun isValidClientId(id: String): Boolean =
            id.length == CLIENT_ID_LENGTH && id.all { Character.digit(it, 16) >= 0 } && CryptoBox.isValidPublicKey(id.hex())

        private const val CLIENT_ID_LENGTH = 64

        // Same lookup as android.net.Uri.getQueryParameter: first match by raw name, `+` decodes to a space.
        private fun String.parameter(name: String): String? =
            split('&')
                .firstOrNull { it.substringBefore('=') == name }
                ?.let { URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }
    }
}
