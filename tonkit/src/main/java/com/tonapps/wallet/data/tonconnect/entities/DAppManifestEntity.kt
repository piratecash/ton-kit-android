package com.tonapps.wallet.data.tonconnect.entities

import org.json.JSONObject

data class DAppManifestEntity(
    val url: String,
    val name: String,
    val iconUrl: String,
    val termsOfUseUrl: String?,
    val privacyPolicyUrl: String?
) {

    val host: String
        get() = url.uriHost ?: name

    constructor(json: JSONObject) : this(
        url = json.getString("url"),
        name = json.getString("name"),
        iconUrl = json.getString("iconUrl"),
        termsOfUseUrl = json.optString("termsOfUseUrl"),
        privacyPolicyUrl = json.optString("privacyPolicyUrl")
    )
}