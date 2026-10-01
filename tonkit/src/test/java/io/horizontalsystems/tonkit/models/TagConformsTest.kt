package io.horizontalsystems.tonkit.models

import io.horizontalsystems.tonkit.Address
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Tag.conforms` must mirror the SQL filtering performed by `EventDao.events`:
 * null fields in [TagQuery] are wildcards (the DAO skips the corresponding WHERE clause).
 *
 * This is critical for `EventManager.eventFlow`, which uses `conforms` to decide whether
 * live-emitted events should pass through to a subscriber. A subscriber for the global
 * "Swaps" tab uses `TagQuery(type = Swap, platform = null, jettonAddress = null, address = null)` —
 * if `conforms` used strict equality, swap events tagged with concrete platform/jetton would
 * fail the filter and live updates would be dropped.
 */
class TagConformsTest {

    private val jettonAddress = Address.parse("EQCD39VS5jcptHL8vMjEXrzGaRcCVYto7HUn4bpAOg8xqB2N")
    private val counterpartyAddress = Address.parse("EQDfvVvoSX_cDJ_L38Z2hkhA3fitZCPW1WV9mw6CcNbIrH-Q")

    // --- Wildcard semantics (null query fields skip the check) ---

    @Test
    fun conforms_emptyQuery_alwaysMatches() {
        val tag = swapJettonTag()
        val query = TagQuery(type = null, platform = null, jettonAddress = null, address = null)

        assertTrue(tag.conforms(query))
    }

    @Test
    fun conforms_swapTypeOnlyQuery_matchesAnyPlatform() {
        // The global "Swaps" tab subscribes with type=Swap and null platform/jetton.
        // A jetton swap tag (with concrete platform and jettonAddress) must still pass
        // through eventFlow's conforms-based filter.
        val jettonSwapTag = swapJettonTag()
        val nativeSwapTag = Tag(eventId = "e", type = Tag.Type.Swap, platform = Tag.Platform.Native)
        val swapsTabQuery = TagQuery(
            type = Tag.Type.Swap,
            platform = null,
            jettonAddress = null,
            address = null,
        )

        assertTrue(jettonSwapTag.conforms(swapsTabQuery))
        assertTrue(nativeSwapTag.conforms(swapsTabQuery))
    }

    @Test
    fun conforms_typeOnlyQuery_rejectsDifferentType() {
        val outgoingTag = Tag(eventId = "e", type = Tag.Type.Outgoing, platform = Tag.Platform.Native)
        val swapQuery = TagQuery(type = Tag.Type.Swap, platform = null, jettonAddress = null, address = null)

        assertFalse(outgoingTag.conforms(swapQuery))
    }

    @Test
    fun conforms_platformOnlyQuery_matchesAnyType() {
        val incomingJettonTag = Tag(
            eventId = "e",
            type = Tag.Type.Incoming,
            platform = Tag.Platform.Jetton,
            jettonAddress = jettonAddress,
        )
        val jettonPlatformQuery = TagQuery(
            type = null,
            platform = Tag.Platform.Jetton,
            jettonAddress = null,
            address = null,
        )

        assertTrue(incomingJettonTag.conforms(jettonPlatformQuery))
    }

    @Test
    fun conforms_jettonAddressOnlyQuery_matchesByJetton() {
        val tag = Tag(
            eventId = "e",
            type = Tag.Type.Incoming,
            platform = Tag.Platform.Jetton,
            jettonAddress = jettonAddress,
        )
        val query = TagQuery(type = null, platform = null, jettonAddress = jettonAddress, address = null)

        assertTrue(tag.conforms(query))
    }

    @Test
    fun conforms_addressOnlyQuery_matchesWhenAddressesContain() {
        val tag = Tag(
            eventId = "e",
            type = Tag.Type.Outgoing,
            platform = Tag.Platform.Native,
            addresses = listOf(counterpartyAddress),
        )
        val query = TagQuery(type = null, platform = null, jettonAddress = null, address = counterpartyAddress)

        assertTrue(tag.conforms(query))
    }

    @Test
    fun conforms_addressOnlyQuery_rejectsWhenAddressesMissing() {
        val tag = Tag(
            eventId = "e",
            type = Tag.Type.Outgoing,
            platform = Tag.Platform.Native,
            addresses = listOf(),
        )
        val query = TagQuery(type = null, platform = null, jettonAddress = null, address = counterpartyAddress)

        assertFalse(tag.conforms(query))
    }

    @Test
    fun conforms_fullQuery_requiresAllFieldsToMatch() {
        val tag = Tag(
            eventId = "e",
            type = Tag.Type.Incoming,
            platform = Tag.Platform.Jetton,
            jettonAddress = jettonAddress,
            addresses = listOf(counterpartyAddress),
        )
        val matchingQuery = TagQuery(
            type = Tag.Type.Incoming,
            platform = Tag.Platform.Jetton,
            jettonAddress = jettonAddress,
            address = counterpartyAddress,
        )
        val wrongTypeQuery = matchingQuery.copy(type = Tag.Type.Outgoing)
        val wrongPlatformQuery = matchingQuery.copy(platform = Tag.Platform.Native)

        assertTrue(tag.conforms(matchingQuery))
        assertFalse(tag.conforms(wrongTypeQuery))
        assertFalse(tag.conforms(wrongPlatformQuery))
    }

    private fun swapJettonTag() = Tag(
        eventId = "e",
        type = Tag.Type.Swap,
        platform = Tag.Platform.Jetton,
        jettonAddress = jettonAddress,
    )
}
