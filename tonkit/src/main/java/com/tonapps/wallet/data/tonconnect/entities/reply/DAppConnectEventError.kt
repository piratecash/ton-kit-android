package com.tonapps.wallet.data.tonconnect.entities.reply

import org.json.JSONObject

data class DAppConnectEventError(
    val id: String,
    val errorCode: Int,
    val errorMessage: String
) : DAppReply() {

    override fun toJSON(): JSONObject {
        val payloadObject = JSONObject()
        payloadObject.put("code", errorCode)
        payloadObject.put("message", errorMessage)

        val json = JSONObject()
        json.put("event", "connect_error")
        json.put("id", id)
        json.put("payload", payloadObject)
        return json
    }
}