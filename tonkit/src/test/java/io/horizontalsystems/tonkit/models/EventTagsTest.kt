package io.horizontalsystems.tonkit.models

import io.horizontalsystems.tonkit.Address
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigInteger

/**
 * `JettonSwap.jettonMasterIn` is the jetton the user **sends** (input to the swap);
 * `JettonSwap.jettonMasterOut` is the jetton the user **receives** (output of the swap).
 * That semantic matches `TonTransactionConverter`, where `valueIn = jettonMasterIn` is the
 * negative (sent) amount and `valueOut = jettonMasterOut` is the positive (received) amount.
 *
 * Native ⇄ jetton swap: `null` on one side means TON is on that side of the swap.
 */
class EventTagsTest {

    private val userAddress = Address.parse("EQAKtVj024T9MfYaJzU1xnDAkf_GGbHNu-V2mgvyjTuP6rvC")
    private val counterpartyAddress = Address.parse("EQDfvVvoSX_cDJ_L38Z2hkhA3fitZCPW1WV9mw6CcNbIrH-Q")
    private val routerAddress = Address.parse("EQAPPcxn4sMIMU1W0_DKBCo5LN1WD3uFFKjqY0jpyt0WZf7D")
    private val jettonAAddress = Address.parse("EQCD39VS5jcptHL8vMjEXrzGaRcCVYto7HUn4bpAOg8xqB2N")
    private val jettonBAddress = Address.parse("EQDfvVvoSX_cDJ_L38Z2hkhA3fitZCPW1WV9mw6CcNbIrH-Q")

    @Test
    fun tags_jettonSwapJettonToJetton_producesOnlySwapTags() {
        val tags = eventOf(jettonSwapAction(masterIn = jetton(jettonAAddress), masterOut = jetton(jettonBAddress)))
            .tags(userAddress)

        assertContainsTag(tags, Tag.Type.Swap, Tag.Platform.Jetton, jettonAAddress)
        assertContainsTag(tags, Tag.Type.Swap, Tag.Platform.Jetton, jettonBAddress)
        assertNoTagOfType(tags, Tag.Type.Outgoing)
        assertNoTagOfType(tags, Tag.Type.Incoming)
    }

    @Test
    fun tags_jettonSwapJettonToNative_producesOnlySwapTags() {
        // PIRATE -> TON: user sends jetton, receives TON. A completed jetton-to-native swap
        // must produce only Swap tags so it does not surface on the Sent/Received tabs.
        val tags = eventOf(jettonSwapAction(masterIn = jetton(jettonAAddress), masterOut = null))
            .tags(userAddress)

        assertContainsTag(tags, Tag.Type.Swap, Tag.Platform.Jetton, jettonAAddress)
        assertContainsTag(tags, Tag.Type.Swap, Tag.Platform.Native, jettonAddress = null)
        assertNoTagOfType(tags, Tag.Type.Outgoing)
        assertNoTagOfType(tags, Tag.Type.Incoming)
    }

    @Test
    fun tags_jettonSwapNativeToJetton_producesOnlySwapTags() {
        // TON -> PIRATE: user sends TON, receives jetton.
        val tags = eventOf(jettonSwapAction(masterIn = null, masterOut = jetton(jettonAAddress)))
            .tags(userAddress)

        assertContainsTag(tags, Tag.Type.Swap, Tag.Platform.Native, jettonAddress = null)
        assertContainsTag(tags, Tag.Type.Swap, Tag.Platform.Jetton, jettonAAddress)
        assertNoTagOfType(tags, Tag.Type.Outgoing)
        assertNoTagOfType(tags, Tag.Type.Incoming)
    }

    @Test
    fun tags_tonTransferFromUser_producesOutgoingNativeTag() {
        // Regular sends must still be tagged Outgoing.
        val tags = eventOf(
            tonTransferAction(
                sender = accountAddress(userAddress),
                recipient = accountAddress(counterpartyAddress),
            )
        ).tags(userAddress)

        assertContainsTag(tags, Tag.Type.Outgoing, Tag.Platform.Native, jettonAddress = null)
        assertNoTagOfType(tags, Tag.Type.Incoming)
        assertNoTagOfType(tags, Tag.Type.Swap)
    }

    @Test
    fun tags_jettonTransferToUser_producesIncomingJettonTag() {
        // Regular receives must still be tagged Incoming.
        val tags = eventOf(
            jettonTransferAction(
                sender = accountAddress(counterpartyAddress),
                recipient = accountAddress(userAddress),
                jettonAddress = jettonAAddress,
            )
        ).tags(userAddress)

        assertContainsTag(tags, Tag.Type.Incoming, Tag.Platform.Jetton, jettonAAddress)
        assertNoTagOfType(tags, Tag.Type.Outgoing)
        assertNoTagOfType(tags, Tag.Type.Swap)
    }

    private fun assertContainsTag(
        tags: List<Tag>,
        type: Tag.Type,
        platform: Tag.Platform,
        jettonAddress: Address?,
    ) {
        val match = tags.firstOrNull { tag ->
            tag.type == type && tag.platform == platform && tag.jettonAddress == jettonAddress
        }
        assertNotNull("Expected tag type=$type platform=$platform jetton=$jettonAddress in $tags", match)
    }

    private fun assertNoTagOfType(tags: List<Tag>, type: Tag.Type) {
        assertFalse("Expected no tag of type $type in $tags", tags.any { it.type == type })
    }

    private fun eventOf(vararg actions: Action) = Event(
        id = "event-id",
        lt = 0,
        timestamp = 0,
        scam = false,
        inProgress = false,
        extra = 0,
        actions = actions.toList()
    )

    private fun accountAddress(address: Address) = AccountAddress(
        address = address,
        name = null,
        isScam = false,
        isWallet = true,
    )

    private fun jetton(address: Address) = Jetton(
        address = address,
        name = "Jetton",
        symbol = "JET",
        decimals = 9,
        image = null,
        verification = JettonVerificationType.WHITELIST,
    )

    private fun jettonSwapAction(masterIn: Jetton?, masterOut: Jetton?) = Action(
        type = Action.Type.JettonSwap,
        status = Action.Status.OK,
        tonTransfer = null,
        jettonTransfer = null,
        jettonBurn = null,
        jettonMint = null,
        contractDeploy = null,
        jettonSwap = JettonSwap(
            dex = "stonfi",
            amountIn = BigInteger.ONE,
            amountOut = BigInteger.ONE,
            tonIn = if (masterIn == null) BigInteger.ONE else null,
            tonOut = if (masterOut == null) BigInteger.ONE else null,
            userWallet = accountAddress(userAddress),
            router = accountAddress(routerAddress),
            jettonMasterIn = masterIn,
            jettonMasterOut = masterOut,
        ),
        smartContractExec = null,
    )

    private fun tonTransferAction(sender: AccountAddress, recipient: AccountAddress) = Action(
        type = Action.Type.TonTransfer,
        status = Action.Status.OK,
        tonTransfer = TonTransfer(
            sender = sender,
            recipient = recipient,
            amount = BigInteger.ONE,
            comment = null,
        ),
        jettonTransfer = null,
        jettonBurn = null,
        jettonMint = null,
        contractDeploy = null,
        jettonSwap = null,
        smartContractExec = null,
    )

    private fun jettonTransferAction(
        sender: AccountAddress,
        recipient: AccountAddress,
        jettonAddress: Address,
    ) = Action(
        type = Action.Type.JettonTransfer,
        status = Action.Status.OK,
        tonTransfer = null,
        jettonTransfer = JettonTransfer(
            sender = sender,
            recipient = recipient,
            sendersWallet = sender.address,
            recipientsWallet = recipient.address,
            amount = BigInteger.ONE,
            comment = null,
            jetton = jetton(jettonAddress),
        ),
        jettonBurn = null,
        jettonMint = null,
        contractDeploy = null,
        jettonSwap = null,
        smartContractExec = null,
    )
}
