package io.horizontalsystems.tonkit.tonconnect

import androidx.room.TypeConverter
import org.json.JSONObject

class TonConnectDBConverters {
    @TypeConverter
    fun stringToJson(value: String?): JSONObject? {
        return value?.let { JSONObject(it) }
    }

    @TypeConverter
    fun jsonToString(value: JSONObject?): String? {
        return value?.toString()
    }
}
