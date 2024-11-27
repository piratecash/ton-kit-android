package com.tonapps.wallet.data.tonconnect.entities.reply

import org.json.JSONArray
import org.json.JSONObject

data class DAppDeviceEntity(
    val appName: String,
    val appVersion: String,
    val maxProtocolVersion: Int = 2,
    val features: List<String> = listOf("SendTransaction"),
    val platform: String = "android",
): DAppReply() {

    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("platform", platform)
        json.put("appName", appName)
        json.put("appVersion", appVersion)
        json.put("maxProtocolVersion", maxProtocolVersion)
        json.put("features", JSONArray(features))
        return json
    }
}