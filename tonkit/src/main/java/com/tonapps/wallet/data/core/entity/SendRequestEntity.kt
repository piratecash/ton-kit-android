package com.tonapps.wallet.data.core.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tonapps.blockchain.ton.TonNetwork
import kotlinx.datetime.Clock
import org.json.JSONArray
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds

@Entity
data class SendRequestEntity(
    val data: JSONObject,
    val tonConnectRequestId: String,
    val dAppId: String,
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0
) {
    val fromValue by lazy { parseFrom(data) }
    val validUntil by lazy { data.optLong("_", (Clock.System.now() + 60.seconds).epochSeconds) }
    val messages by lazy { parseMessages(data.getJSONArray("messages")) }
    val network by lazy { parseNetwork(data.opt("network")) }
    val transfers by lazy { messages.map { it.walletTransfer } }

    private companion object {

        private fun parseMessages(array: JSONArray): List<RawMessageEntity> {
            val messages = mutableListOf<RawMessageEntity>()
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                messages.add(RawMessageEntity(json))
            }
            return messages
        }

        private fun parseFrom(json: JSONObject): String? {
            return if (json.has("from")) {
                json.getString("from")
            } else if (json.has("source")) {
                json.getString("source")
            } else {
                null
            }
        }

        private fun parseNetwork(value: Any?): TonNetwork {
            if (value == null) {
                return TonNetwork.MAINNET
            }
            if (value is String) {
                return parseNetwork(value.toIntOrNull())
            }
            if (value !is Int) {
                return parseNetwork(value.toString())
            }
            return if (value == TonNetwork.TESTNET.value) {
                TonNetwork.TESTNET
            } else {
                TonNetwork.MAINNET
            }
        }
    }
}