package io.horizontalsystems.tonkit.api

import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.Action
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.JettonVerificationType
import io.tonapi.infrastructure.Serializer
import io.tonapi.models.Account
import io.tonapi.models.AccountEvents
import io.tonapi.models.AccountStatus
import io.tonapi.models.JettonsBalances
import io.tonapi.models.MessageConsequences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

// Response bodies shaped after the tonapi.io v2 OpenAPI examples; addresses are synthetic.
class TonApiSerializerTest {

    @Test
    fun getAccount_realResponse_parsesFields() {
        val account = parse<Account>(
            """
            {
              "address": "$WALLET",
              "balance": 1234567890,
              "last_activity": 1727600000,
              "status": "active",
              "interfaces": ["wallet_v4r2"],
              "get_methods": ["get_public_key", "seqno", "get_subwallet_id"],
              "is_wallet": true,
              "memo_required": false,
              "is_scam": false,
              "extra_field_added_later": {"nested": 1}
            }
            """
        )

        assertEquals(WALLET, account.address)
        assertEquals(1234567890L, account.balance)
        assertEquals(AccountStatus.active, account.status)
        assertEquals(listOf("wallet_v4r2"), account.interfaces)
        assertTrue(account.isWallet)
        assertNull(account.name)
    }

    @Test
    fun getAccount_unknownStatus_fallsBackToUnknown() {
        val account = parse<Account>(
            """{"address": "$WALLET", "balance": 0, "last_activity": 0, "status": "suspended",
               "get_methods": [], "is_wallet": false}"""
        )

        assertEquals(AccountStatus.unknown_default_open_api, account.status)
    }

    @Test
    fun getAccountJettonsBalances_realResponse_mapsToKitJetton() {
        val balances = parse<JettonsBalances>(
            """
            {
              "balances": [
                {
                  "balance": "25000000",
                  "wallet_address": {"address": "$JETTON_WALLET", "is_scam": false, "is_wallet": false},
                  "jetton": {
                    "address": "$JETTON_MASTER",
                    "name": "Tether USD",
                    "symbol": "USD₮",
                    "decimals": 6,
                    "image": "https://cache.tonapi.io/imgproxy/usdt.png",
                    "verification": "whitelist",
                    "score": 100
                  },
                  "extensions": ["custom_payload"]
                }
              ]
            }
            """
        )

        val balance = balances.balances.single()
        val jetton = Jetton.fromPreview(balance.jetton)
        assertEquals(BigInteger("25000000"), BigInteger(balance.balance))
        assertEquals(Address.parse(JETTON_WALLET), Address.parse(balance.walletAddress.address))
        assertEquals(Address.parse(JETTON_MASTER), jetton.address)
        assertEquals("USD₮", jetton.symbol)
        assertEquals(6, jetton.decimals)
        assertEquals(JettonVerificationType.WHITELIST, jetton.verification)
    }

    @Test
    fun getAccountEvents_realResponse_mapsToKitEvents() {
        val events = parse<AccountEvents>(
            """
            {
              "events": [
                {
                  "event_id": "e1",
                  "account": {"address": "$WALLET", "is_scam": false, "is_wallet": true},
                  "timestamp": 1727600100,
                  "actions": [
                    {
                      "type": "TonTransfer",
                      "status": "ok",
                      "TonTransfer": {
                        "sender": {"address": "$OTHER", "is_scam": false, "is_wallet": true, "name": "sender.ton"},
                        "recipient": {"address": "$WALLET", "is_scam": false, "is_wallet": true},
                        "amount": 1500000000,
                        "comment": "Thanks!"
                      },
                      "simple_preview": {
                        "name": "Ton Transfer",
                        "description": "Transferring 1.5 TON",
                        "value": "1.5 TON",
                        "accounts": [{"address": "$OTHER", "is_scam": false, "is_wallet": true}]
                      },
                      "base_transactions": ["aa11"]
                    },
                    {
                      "type": "JettonTransfer",
                      "status": "ok",
                      "JettonTransfer": {
                        "sender": {"address": "$WALLET", "is_scam": false, "is_wallet": true},
                        "recipient": {"address": "$OTHER", "is_scam": false, "is_wallet": true},
                        "senders_wallet": "$JETTON_WALLET",
                        "recipients_wallet": "$OTHER_JETTON_WALLET",
                        "amount": "1000000",
                        "jetton": {
                          "address": "$JETTON_MASTER", "name": "Tether USD", "symbol": "USD₮",
                          "decimals": 6, "image": "https://cache.tonapi.io/imgproxy/usdt.png", "verification": "whitelist"
                        }
                      },
                      "simple_preview": {"name": "Jetton Transfer", "description": "Transferring 1 USD₮", "accounts": []},
                      "base_transactions": ["bb22"]
                    },
                    {
                      "type": "SomeActionAddedLater",
                      "status": "ok",
                      "simple_preview": {"name": "New", "description": "New action", "accounts": []},
                      "base_transactions": []
                    }
                  ],
                  "is_scam": false,
                  "lt": 49739000003,
                  "in_progress": false,
                  "extra": -2380000
                }
              ],
              "next_from": 49738000001
            }
            """
        )

        val event = Event.fromApi(events.events.single())
        assertEquals(49738000001L, events.nextFrom)
        assertEquals("e1", event.id)
        assertEquals(49739000003L, event.lt)
        assertEquals(-2380000L, event.extra)
        assertEquals(
            listOf(Action.Type.TonTransfer, Action.Type.JettonTransfer, Action.Type.Unknown),
            event.actions.map { it.type }
        )
        val tonTransfer = event.actions[0].tonTransfer
        assertEquals(BigInteger.valueOf(1500000000L), tonTransfer?.amount)
        assertEquals("Thanks!", tonTransfer?.comment)
        assertEquals("sender.ton", tonTransfer?.sender?.name)
        val jettonTransfer = event.actions[1].jettonTransfer
        assertEquals(BigInteger("1000000"), jettonTransfer?.amount)
        assertEquals(Address.parse(OTHER_JETTON_WALLET), jettonTransfer?.recipientsWallet)
    }

    @Test
    fun emulateMessageToWallet_realResponse_parsesTotalFees() {
        val consequences = parse<MessageConsequences>(
            """
            {
              "trace": {
                "transaction": {
                  "hash": "cc33",
                  "lt": 49740000001,
                  "account": {"address": "$WALLET", "is_scam": false, "is_wallet": true},
                  "success": true,
                  "utime": 1727600200,
                  "orig_status": "active",
                  "end_status": "active",
                  "total_fees": 2380000,
                  "end_balance": 1230000000,
                  "transaction_type": "TransOrd",
                  "state_update_old": "aa",
                  "state_update_new": "bb",
                  "out_msgs": [],
                  "block": "(0,8000000000000000,46000000)",
                  "aborted": false,
                  "destroyed": false,
                  "raw": "b5ee9c72"
                },
                "interfaces": ["wallet_v4r2"],
                "emulated": true
              },
              "risk": {"transfer_all_remaining_balance": false, "ton": 1500000000, "jettons": [], "nfts": []},
              "event": {
                "event_id": "e2",
                "account": {"address": "$WALLET", "is_scam": false, "is_wallet": true},
                "timestamp": 1727600200,
                "actions": [],
                "is_scam": false,
                "lt": 49740000001,
                "in_progress": true,
                "extra": -2380000
              }
            }
            """
        )

        assertEquals(2380000L, consequences.trace.transaction.totalFees)
        assertEquals(1500000000L, consequences.risk.ton)
        assertTrue(consequences.event.inProgress)
    }

    private inline fun <reified T> parse(json: String): T =
        requireNotNull(Serializer.moshi.adapter(T::class.java).fromJson(json.trimIndent()))

    private companion object {
        const val WALLET = "0:1111111111111111111111111111111111111111111111111111111111111111"
        const val OTHER = "0:2222222222222222222222222222222222222222222222222222222222222222"
        const val JETTON_MASTER = "0:3333333333333333333333333333333333333333333333333333333333333333"
        const val JETTON_WALLET = "0:4444444444444444444444444444444444444444444444444444444444444444"
        const val OTHER_JETTON_WALLET = "0:5555555555555555555555555555555555555555555555555555555555555555"
    }
}
