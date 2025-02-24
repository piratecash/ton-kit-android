package com.tonapps.wallet.data.tonconnect.entities.reply

import org.json.JSONArray
import org.json.JSONObject

data class DAppDeviceEntity(
    val appName: String,
    val appVersion: String,
    val maxProtocolVersion: Int = 2,
    val maxMessages: Int,
    val platform: String = "android",
): DAppReply() {

    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("platform", platform)
        json.put("appName", appName)
        json.put("appVersion", appVersion)
        json.put("maxProtocolVersion", maxProtocolVersion)
        json.put("features", features(maxMessages))
        return json
    }

    private fun features(maxMessages: Int): JSONArray {
        val array = JSONArray()
        array.put("SendTransaction")
        array.put(sendTransactionFeature(maxMessages))
        return array
    }

    private fun sendTransactionFeature(maxMessages: Int): JSONObject {
        val json = JSONObject()
        json.put("name", "SendTransaction")
        json.put("maxMessages", maxMessages)
        return json
    }
}