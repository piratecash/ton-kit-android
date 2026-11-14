package io.horizontalsystems.tonkit.models

import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.storage.Converters
import io.tonapi.infrastructure.Serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger

class LiquidityDepositActionTest {

    @Test
    fun fromApi_nativeAndJettonTokens_mapsBoth() {
        val deposit = kitAction(
            depositJson(
                tokens = """
                [
                  {"price": {"value": "1500000000", "token_name": "TON", "currency_type": "native"}, "vault": "$VAULT_A"},
                  {"price": {"value": "2500000", "token_name": "USD₮", "currency_type": "jetton",
                             "decimals": 6, "image": "https://example.com/usdt.png", "jetton": "$JETTON"}, "vault": "$VAULT_B"}
                ]
                """
            )
        ).liquidityDeposit

        assertNotNull(deposit)
        assertEquals("DeDust", deposit?.protocol)
        assertEquals(Address.parse(USER), deposit?.from?.address)
        val (native, jetton) = deposit?.tokens.orEmpty()
        assertEquals(BigInteger.valueOf(1_500_000_000), native.amount)
        assertNull(native.jetton)
        assertEquals(Address.parse(VAULT_A), native.vault)
        assertEquals(BigInteger.valueOf(2_500_000), jetton.amount)
        assertEquals(Address.parse(JETTON), jetton.jetton?.address)
        assertEquals("USD₮", jetton.jetton?.symbol)
        assertEquals(6, jetton.jetton?.decimals)
        assertEquals(JettonVerificationType.UNKNOWN, jetton.jetton?.verification)
    }

    @Test
    fun fromApi_malformedTokens_areSkippedWithoutThrowing() {
        val deposit = kitAction(
            depositJson(
                tokens = """
                [
                  {"price": {"value": "1", "token_name": "USD", "currency_type": "fiat"}, "vault": "$VAULT_A"},
                  {"price": {"value": "not-a-number", "token_name": "TON", "currency_type": "native"}, "vault": "$VAULT_A"},
                  {"price": {"value": "1", "token_name": "TON", "currency_type": "native"}, "vault": "garbage"},
                  {"price": {"value": "1", "token_name": "X", "currency_type": "jetton", "decimals": 9}, "vault": "$VAULT_A"},
                  {"price": {"value": "7", "token_name": "TON", "currency_type": "native"}, "vault": "$VAULT_B"}
                ]
                """
            )
        ).liquidityDeposit

        assertEquals(listOf(BigInteger.valueOf(7)), deposit?.tokens?.map { it.amount })
    }

    @Test
    fun fromApi_noSurvivingToken_hasNoDeposit() {
        val action = kitAction(
            depositJson(
                tokens = """[{"price": {"value": "1", "token_name": "USD", "currency_type": "fiat"}, "vault": "$VAULT_A"}]"""
            )
        )

        assertNull(action.liquidityDeposit)
    }

    @Test
    fun fromApi_absentTokensOrFrom_hasNoDeposit() {
        assertNull(kitAction(depositJson(tokens = null)).liquidityDeposit)
        assertNull(
            kitAction(
                depositJson(
                    from = null,
                    tokens = """[{"price": {"value": "1", "token_name": "TON", "currency_type": "native"}, "vault": "$VAULT_A"}]"""
                )
            ).liquidityDeposit
        )
    }

    @Test
    fun fromApi_malformedSender_hasNoDepositAndDoesNotThrow() {
        val action = kitAction(
            depositJson(
                from = """{"address": "0:bad", "is_scam": false, "is_wallet": true}""",
                tokens = """[{"price": {"value": "1", "token_name": "TON", "currency_type": "native"}, "vault": "$VAULT_A"}]"""
            )
        )

        assertNull(action.liquidityDeposit)
    }

    @Test
    fun tags_depositFromUser_givesOutgoingNativeAndJettonTags() {
        val tags = eventOf(kitAction(depositJson(tokens = TWO_TOKENS))).tags(Address.parse(USER))

        assertEquals(
            listOf(
                Triple(Tag.Type.Outgoing, Tag.Platform.Native, null),
                Triple(Tag.Type.Outgoing, Tag.Platform.Jetton, Address.parse(JETTON)),
            ),
            tags.map { Triple(it.type, it.platform, it.jettonAddress) }
        )
    }

    @Test
    fun tags_depositFromSomeoneElse_givesNoTags() {
        val tags = eventOf(kitAction(depositJson(tokens = TWO_TOKENS))).tags(Address.parse(VAULT_A))

        assertEquals(emptyList<Tag>(), tags)
    }

    @Test
    fun converters_actionStoredByPreviousModel_readsWithoutDeposit() {
        val stored = """[{"type":"Unknown","status":"OK"}]"""

        val actions = Converters().stringToActionList(stored)

        assertEquals(1, actions?.size)
        assertNull(actions?.single()?.liquidityDeposit)
    }

    @Test
    fun converters_actionWithDeposit_roundTrips() {
        val action = kitAction(depositJson(tokens = TWO_TOKENS))
        val converters = Converters()

        val restored = converters.stringToActionList(converters.actionListToString(listOf(action)))

        assertEquals(listOf(action), restored)
    }

    @Test
    fun fromApi_realStonfiDeposit_mapsNativeAndJetton() {
        val action = kitAction(STONFI_DEPOSIT)

        val deposit = requireNotNull(action.liquidityDeposit)
        assertEquals("stonfi", deposit.protocol)
        assertEquals(Address.parse("0:7f700dc3225ef811f0936eaf5ceb72c06359312a68470c69322d8e52f54dbcc8"), deposit.from.address)
        val (native, jetton) = deposit.tokens
        assertEquals(BigInteger.valueOf(1_000_000_000), native.amount)
        assertNull(native.jetton)
        assertEquals(Address.parse("0:e1e681cfbdf4703eed9b3910460a82c43b859064a2f87e4e430f583275e346f6"), native.vault)
        assertEquals(BigInteger.valueOf(2_634_284_289_094_671), jetton.amount)
        assertEquals(15, jetton.jetton?.decimals)
        assertEquals("DYOR", jetton.jetton?.symbol)
        assertEquals(Address.parse("0:906697a57af1cdb1bc39214b7049ae02b3c92a5b0c342ac61e0eb8bd9304b5f1"), jetton.jetton?.address)
        assertEquals(Address.parse("0:5072c7e45ba04229c29d0b492b606eb624b4e651c794657fda2016fbd8b09fc3"), jetton.vault)
    }

    @Test
    fun tags_realStonfiDepositSender_givesOutgoingNativeAndJettonTags() {
        val sender = Address.parse("0:7f700dc3225ef811f0936eaf5ceb72c06359312a68470c69322d8e52f54dbcc8")

        val tags = eventOf(kitAction(STONFI_DEPOSIT)).tags(sender)

        assertEquals(
            listOf(
                Triple(Tag.Type.Outgoing, Tag.Platform.Native, null),
                Triple(
                    Tag.Type.Outgoing,
                    Tag.Platform.Jetton,
                    Address.parse("0:906697a57af1cdb1bc39214b7049ae02b3c92a5b0c342ac61e0eb8bd9304b5f1")
                ),
            ),
            tags.map { Triple(it.type, it.platform, it.jettonAddress) }
        )
    }

    private fun kitAction(json: String): Action {
        val api = requireNotNull(Serializer.moshi.adapter(io.tonapi.models.Action::class.java).fromJson(json))
        return Action.fromApi(api)
    }

    private fun eventOf(action: Action) = Event("event-id", 0, 0, false, false, 0, listOf(action))

    private fun depositJson(
        from: String? = """{"address": "$USER", "is_scam": false, "is_wallet": true}""",
        tokens: String?,
    ): String {
        val fields = listOfNotNull(
            """"protocol": {"name": "DeDust"}""",
            from?.let { """"from": $it""" },
            tokens?.let { """"tokens": $it""" },
        ).joinToString(",")
        return """
            {
              "type": "LiquidityDeposit",
              "status": "ok",
              "LiquidityDeposit": {$fields},
              "simple_preview": {"name": "Liquidity Deposit", "description": "Deposit", "accounts": []},
              "base_transactions": ["aa11"]
            }
        """.trimIndent()
    }

    private companion object {
        const val USER = "0:1111111111111111111111111111111111111111111111111111111111111111"
        const val VAULT_A = "0:2222222222222222222222222222222222222222222222222222222222222222"
        const val VAULT_B = "0:3333333333333333333333333333333333333333333333333333333333333333"
        const val JETTON = "0:4444444444444444444444444444444444444444444444444444444444444444"
        const val STONFI_DEPOSIT = """
{
 "type": "LiquidityDeposit",
 "status": "ok",
 "LiquidityDeposit": {
  "protocol": {
   "name": "stonfi",
   "image": "https://cache.tonapi.io/imgproxy/iV5uvIiMmu2G-Cl1CL3CdTNcTGxzQb053tBu-FYZl_Y/rs:fill:200:200:1/g:no/aHR0cHM6Ly9zdGF0aWMuc3Rvbi5maS9mYXZpY29uL2FuZHJvaWQtY2hyb21lLTE5MngxOTIucG5n.webp"
  },
  "from": {
   "address": "0:7f700dc3225ef811f0936eaf5ceb72c06359312a68470c69322d8e52f54dbcc8",
   "is_scam": false,
   "is_wallet": true
  },
  "tokens": [
   {
    "price": {
     "currency_type": "native",
     "value": "1000000000",
     "decimals": 9,
     "token_name": "Gram",
     "verification": "whitelist",
     "image": "https://raw.githubusercontent.com/tonkeeper/opentonapi/master/pkg/references/media/gram_diamond_mark_200.png"
    },
    "vault": "0:e1e681cfbdf4703eed9b3910460a82c43b859064a2f87e4e430f583275e346f6"
   },
   {
    "price": {
     "currency_type": "jetton",
     "value": "2634284289094671",
     "decimals": 15,
     "token_name": "DYOR",
     "verification": "whitelist",
     "image": "https://cache.tonapi.io/imgproxy/OTzNEZPKmu88irmXVLfWPizwJk6DBXYObEu7HvoqaF8/rs:fill:200:200:1/g:no/aHR0cHM6Ly9zdG9yYWdlLmR5b3IuaW8vamV0dG9ucy9FUUNRWnBlbGV2SE5zYnc1SVV0d1NhNENzOGtxV3d3MEtzWWVEcmk5a3dTMThlQ3ovZHlvcmNvaW4ucG5n.webp",
     "jetton": "0:906697a57af1cdb1bc39214b7049ae02b3c92a5b0c342ac61e0eb8bd9304b5f1"
    },
    "vault": "0:5072c7e45ba04229c29d0b492b606eb624b4e651c794657fda2016fbd8b09fc3"
   }
  ]
 },
 "simple_preview": {
  "name": "Liquidity Deposit",
  "description": "Deposit liquidity into the stonfi pool",
  "value": "1 Gram + 2.63 DYOR",
  "accounts": []
 },
 "base_transactions": [
  "741a6e182f09d50bd01a83aa0a411a3a0a6f47e1ff4aee4ef870f01fd03a1248"
 ]
}
"""

        const val TWO_TOKENS = """
            [
              {"price": {"value": "1500000000", "token_name": "TON", "currency_type": "native"}, "vault": "$VAULT_A"},
              {"price": {"value": "2500000", "token_name": "USD₮", "currency_type": "jetton",
                         "decimals": 6, "image": "https://example.com/usdt.png", "jetton": "$JETTON"}, "vault": "$VAULT_B"}
            ]
        """
    }
}
