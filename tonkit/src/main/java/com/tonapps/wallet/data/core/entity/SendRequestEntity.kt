package com.tonapps.wallet.data.core.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.extensions.currentTimeSeconds
import org.json.JSONArray
import org.json.JSONObject
import org.ton.block.AddrStd

@Entity
data class SendRequestEntity(
    val data: JSONObject,
    val tonConnectRequestId: String,
    val dAppId: String,
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0
) {
    val fromValue by lazy { parseFrom(data) }
    val validUntil by lazy { parseValidUnit(data) }
    val messages by lazy { parseMessages(data.getJSONArray("messages")) }
    val network by lazy { parseNetwork(data.opt("network")) }
    val transfers by lazy { messages.map { it.walletTransfer } }

    val from: AddrStd?
        get() {
            val value = fromValue ?: return null
            return AddrStd.parse(value)
        }

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

        private fun parseValidUnit(json: JSONObject): Long {
            val value = json.opt("valid_until") ?: json.opt("validUntil")
            if (value == null) {
                return 0
            }
            val validUnit = when (value) {
                is Long -> value
                is Int -> value.toLong()
                is String -> value.toLongOrNull() ?: throw IllegalArgumentException("Invalid validUntil parameter. Expected: int64 (Like ${currentTimeSeconds()}), Received: $value")
                else -> throw IllegalArgumentException("Invalid validUntil parameter. Expected: int64 (Like ${currentTimeSeconds()}), Received: $value")
            }
            if (validUnit > 1000000000000) {
                return validUnit / 1000
            }
            if (validUnit > 1000000000) {
                return validUnit
            }
            return 0
        }
    }
}