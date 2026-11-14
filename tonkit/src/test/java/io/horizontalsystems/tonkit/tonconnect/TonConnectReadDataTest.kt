package io.horizontalsystems.tonkit.tonconnect

import com.tonapps.wallet.data.tonconnect.entities.DAppItemEntity
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TonConnectReadDataTest {

    @Test
    fun readData_tcLink_parsesAllParameters() {
        val request = TonConnectKit.readData("tc://?v=2&id=$CLIENT_ID&r=$ENCODED_R&ret=none")

        assertEquals(2, request.v)
        assertEquals(CLIENT_ID, request.id)
        assertEquals(DECODED_R, request.r)
        assertEquals("none", request.ret)
        assertEquals(MANIFEST_URL, request.payload.manifestUrl)
        assertEquals(
            listOf(DAppItemEntity.TON_ADDR, DAppItemEntity.TON_PROOF),
            request.payload.items.map { it.name }
        )
    }

    @Test
    fun readData_tonkeeperUniversalLink_parsesAllParameters() {
        val request = TonConnectKit.readData(
            "https://app.tonkeeper.com/ton-connect?v=2&id=$CLIENT_ID&r=$ENCODED_R&ret=back"
        )

        assertEquals(CLIENT_ID, request.id)
        assertEquals(MANIFEST_URL, request.payload.manifestUrl)
        assertEquals("back", request.ret)
    }

    @Test
    fun readData_plusInsideR_decodesToSpace() {
        val request = TonConnectKit.readData("tc://?v=2&id=$CLIENT_ID&r=${rWithProofPayload("a+b")}")

        assertEquals("a b", request.payload.items.single().payload)
    }

    @Test
    fun readData_encodedPlusInsideR_decodesToPlus() {
        val request = TonConnectKit.readData("tc://?v=2&id=$CLIENT_ID&r=${rWithProofPayload("a%2Bb")}")

        assertEquals("a+b", request.payload.items.single().payload)
    }

    @Test
    fun readData_missingRet_retIsNull() {
        val request = TonConnectKit.readData("tc://?v=2&id=$CLIENT_ID&r=$ENCODED_R")

        assertNull(request.ret)
    }

    @Test
    fun readData_fragmentAfterQuery_isNotPartOfLastParameter() {
        val request = TonConnectKit.readData("tc://?v=2&id=$CLIENT_ID&r=$ENCODED_R&ret=none#section")

        assertEquals("none", request.ret)
    }

    @Test
    fun readData_repeatedParameter_takesFirst() {
        val request = TonConnectKit.readData("tc://?v=2&id=$CLIENT_ID&id=other&r=$ENCODED_R")

        assertEquals(CLIENT_ID, request.id)
    }

    @Test
    fun readData_missingRequiredParameter_throwsIllegalArgument() {
        listOf(
            "tc://?id=$CLIENT_ID&r=$ENCODED_R" to "v is required",
            "tc://?v=2&r=$ENCODED_R" to "id is required",
            "tc://?v=2&id=$CLIENT_ID" to "r is required",
        ).forEach { (link, message) ->
            val error = assertThrows(IllegalArgumentException::class.java) { TonConnectKit.readData(link) }
            assertEquals(message, error.message)
        }
    }

    @Test
    fun readData_garbage_throwsIllegalArgument() {
        listOf("", "hello", "tc://", "?", "&&==&", "%%%", "tc://?v", "tc://?v=two&id=x&r={}", "tc://?v=2&id=x&r=%zz")
            .forEach { link ->
                assertThrows(link, IllegalArgumentException::class.java) { TonConnectKit.readData(link) }
            }
    }

    @Test
    fun readData_invalidClientKey_throwsIllegalArgument() {
        listOf(
            "0".repeat(64),
            "01" + "0".repeat(62),
            "e0eb7a7c3b41b8ae1656e3faf19fc46ada098deb9c32b1fd866205165f49b800",
            CLIENT_ID.dropLast(2),
            CLIENT_ID + "00",
            "zz" + CLIENT_ID.drop(2),
        ).forEach { id ->
            assertThrows(id, IllegalArgumentException::class.java) {
                TonConnectKit.readData("tc://?v=2&id=$id&r=$ENCODED_R")
            }
        }
    }

    @Test
    fun readData_rIsNotJson_throwsJsonException() {
        assertThrows(JSONException::class.java) { TonConnectKit.readData("tc://?v=2&id=$CLIENT_ID&r=not-json") }
    }

    private fun rWithProofPayload(encodedPayload: String) =
        "%7B%22manifestUrl%22%3A%22https%3A%2F%2Fexample.com%2Fm.json%22%2C%22items%22%3A%5B%7B%22name%22%3A%22ton_proof%22%2C%22payload%22%3A%22$encodedPayload%22%7D%5D%7D"

    private companion object {
        const val CLIENT_ID = "230f1e4df32364888a5dbd92a410266fcb974b73e30ff3e546a654fc8ee2c953"
        const val MANIFEST_URL = "https://app.example.com/tonconnect-manifest.json"
        const val DECODED_R =
            """{"manifestUrl":"$MANIFEST_URL","items":[{"name":"ton_addr"},{"name":"ton_proof","payload":"doc-1"}]}"""
        const val ENCODED_R =
            "%7B%22manifestUrl%22%3A%22https%3A%2F%2Fapp.example.com%2Ftonconnect-manifest.json%22%2C%22items%22%3A%5B%7B%22name%22%3A%22ton_addr%22%7D%2C%7B%22name%22%3A%22ton_proof%22%2C%22payload%22%3A%22doc-1%22%7D%5D%7D"
    }
}
