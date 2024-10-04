package com.tonapps.wallet.data.tonconnect.entities.reply

import org.json.JSONObject

data class DAppErrorEntity(
    val id: String,
    val errorCode: Int,
    val errorMessage: String
): DAppReply() {

    override fun toJSON(): JSONObject {
        val error = JSONObject()
        error.put("code", errorCode)
        error.put("message", errorMessage)

        val json = JSONObject()
        json.put("id", id)
        json.put("error", error)
        return json
    }

    companion object {
        fun methodNotSupported(id: String) = DAppErrorEntity(
            id = id,
            errorCode = 400,
            errorMessage = "Method not supported"
        )
    }
}