package io.horizontalsystems.tonkit.models

import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.storage.Converters
import io.tonapi.models.AccountAddress as ApiAccountAddress
import io.tonapi.models.Action as ApiAction
import io.tonapi.models.ActionSimplePreview
import io.tonapi.models.DnsExpiringItemsInner
import io.tonapi.models.ImagePreview
import io.tonapi.models.NftItemCollection
import io.tonapi.models.NftItemTransferAction
import io.tonapi.models.Price
import io.tonapi.models.Sale
import io.tonapi.models.TrustType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.tonapi.models.NftItem as ApiNftItem

class NftItemTest {

    private fun raw(fill: Char) = "0:" + fill.toString().repeat(64)

    private fun account(fill: Char) = ApiAccountAddress(raw(fill), isScam = false, isWallet = true)

    private fun apiItem(
        metadata: Map<String, Any> = emptyMap(),
        owner: ApiAccountAddress? = account('2'),
        sale: Sale? = null,
        collection: NftItemCollection? = null,
        previews: List<ImagePreview>? = null,
        trust: TrustType = TrustType.none,
        address: String = raw('1'),
    ) = ApiNftItem(
        address = address,
        index = 0,
        verified = true,
        metadata = metadata,
        approvedBy = emptyList(),
        trust = trust,
        owner = owner,
        collection = collection,
        sale = sale,
        previews = previews,
    )

    private fun sale(owner: ApiAccountAddress?) =
        Sale(raw('4'), account('5'), Price("1", "TON"), owner)

    @Test
    fun fromApi_nonStringMetadata_yieldsNullsWithoutThrowing() {
        val item = NftItem.fromApi(
            apiItem(
                metadata = mapOf(
                    "name" to 42,
                    "description" to listOf("x"),
                    "render_type" to mapOf("a" to 1),
                    "lottie" to 7,
                    "buttons" to "oops",
                )
            )
        )

        assertNull(item.name)
        assertNull(item.description)
        assertNull(item.renderType)
        assertNull(item.lottie)
        assertTrue(item.buttons.isEmpty())
    }

    @Test
    fun fromApi_stringMetadata_isRead() {
        val item = NftItem.fromApi(
            apiItem(metadata = mapOf("name" to "Duck", "description" to "d", "render_type" to "hidden", "lottie" to "u"))
        )

        assertEquals("Duck", item.name)
        assertEquals("d", item.description)
        assertEquals("hidden", item.renderType)
        assertEquals("u", item.lottie)
    }

    @Test
    fun fromApi_itemOnSale_prefersSaleOwnerAndFlagsSale() {
        val item = NftItem.fromApi(apiItem(owner = account('2'), sale = sale(account('3'))))

        assertEquals(NftItem.fromApi(apiItem(owner = account('3'))).ownerAddress, item.ownerAddress)
        assertTrue(item.ownerAddress != NftItem.fromApi(apiItem(owner = account('2'))).ownerAddress)
        assertTrue(item.isOnSale)
    }

    @Test
    fun fromApi_saleWithoutOwner_fallsBackToOwner() {
        val item = NftItem.fromApi(apiItem(owner = account('2'), sale = sale(null)))

        assertEquals(NftItem.fromApi(apiItem(owner = account('2'))).ownerAddress, item.ownerAddress)
    }

    @Test
    fun fromApi_noOwner_ownerAddressIsNull() {
        val item = NftItem.fromApi(apiItem(owner = null))

        assertNull(item.ownerAddress)
        assertTrue(!item.isOnSale)
    }

    @Test
    fun fromApi_addressForms_nftBounceableOwnerNonBounceable() {
        val item = NftItem.fromApi(
            apiItem(owner = account('2'), collection = NftItemCollection(raw('6'), "C", "cd"))
        )

        assertTrue(item.address.startsWith("EQ"))
        assertTrue(item.collection?.address.orEmpty().startsWith("EQ"))
        assertTrue(item.ownerAddress.orEmpty().startsWith("UQ"))
    }

    @Test
    fun fromApi_missingCollectionAndPreviews_areNullAndEmpty() {
        val item = NftItem.fromApi(apiItem(collection = null, previews = null))

        assertNull(item.collection)
        assertTrue(item.previews.isEmpty())
    }

    @Test
    fun fromApi_collectionAndPreviews_areMapped() {
        val item = NftItem.fromApi(
            apiItem(
                collection = NftItemCollection(raw('6'), "Ducks", "about"),
                previews = listOf(ImagePreview("100x100", "https://a/b.png")),
            )
        )

        assertEquals("Ducks", item.collection?.name)
        assertEquals("about", item.collection?.description)
        assertEquals(listOf(NftPreview("100x100", "https://a/b.png")), item.previews)
    }

    @Test
    fun fromApi_malformedButtons_areIgnored() {
        val buttons = listOf(
            mapOf("label" to "Open", "uri" to "https://a.io"),
            mapOf("label" to 1, "uri" to "https://b.io"),
            mapOf("label" to "NoUri"),
            mapOf("label" to "", "uri" to "https://c.io"),
            "string",
            null,
        )

        val item = NftItem.fromApi(apiItem(metadata = mapOf("buttons" to buttons)))

        assertEquals(listOf(NftButton("Open", "https://a.io")), item.buttons)
    }

    @Test
    fun fromApi_trustTypes_areDistinguishedAndUnknownIsNone() {
        fun trustOf(type: TrustType) = NftItem.fromApi(apiItem(trust = type)).trust

        assertEquals(NftTrust.WHITELIST, trustOf(TrustType.whitelist))
        assertEquals(NftTrust.GRAYLIST, trustOf(TrustType.graylist))
        assertEquals(NftTrust.BLACKLIST, trustOf(TrustType.blacklist))
        assertEquals(NftTrust.NONE, trustOf(TrustType.none))
        assertEquals(NftTrust.NONE, trustOf(TrustType.unknown_default_open_api))
    }

    private val malformedAddresses = run {
        val friendly = Address.parse(raw('1')).toUserFriendly(bounceable = true)
        listOf(
            "0:" + "zz".repeat(32),
            "0:abcd",
            "!".repeat(48),
            friendly.dropLast(1) + if (friendly.last() == 'A') 'B' else 'A',
            "",
        )
    }

    @Test
    fun fromApiOrNull_malformedAddress_skipsOnlyThatItem() {
        malformedAddresses.forEach { malformed ->
            val items = listOf(apiItem(address = malformed), apiItem(address = raw('7')))
                .mapNotNull(NftItem.Companion::fromApiOrNull)

            assertEquals("address '$malformed'", 1, items.size)
        }
    }

    @Test
    fun nftTransferFromApi_malformedAddress_isNull() {
        malformedAddresses.forEach { malformed ->
            assertNull("address '$malformed'", NftTransfer.fromApi(NftItemTransferAction(malformed)))
        }
    }

    @Test
    fun dnsExpiringItemFromApi_wellFormedDnsItem_isMapped() {
        val item = DnsExpiringItem.fromApi(DnsExpiringItemsInner(100, "foo.ton", apiItem().copy(dns = "foo.ton")))

        assertEquals(100L, item.expiringAt)
        assertEquals("foo.ton", item.name)
        assertEquals("foo.ton", item.nftItem?.dns)
    }

    @Test
    fun dnsExpiringItemFromApi_malformedDnsItem_keepsNameAndExpiry() {
        val item = DnsExpiringItem.fromApi(DnsExpiringItemsInner(100, "foo.ton", apiItem(address = "garbage")))

        assertEquals(100L, item.expiringAt)
        assertEquals("foo.ton", item.name)
        assertNull(item.nftItem)
    }

    @Test
    fun fromApi_dns_isKept() {
        val item = NftItem.fromApi(apiItem().copy(dns = "foo.ton"))

        assertEquals("foo.ton", item.dns)
    }

    @Test
    fun actionFromApi_nftItemTransfer_setsTypeAndNftTransfer() {
        val action = Action.fromApi(
            apiAction(
                ApiAction.Type.NftItemTransfer,
                NftItemTransferAction(raw('1'), sender = account('2'), recipient = account('3'), comment = "hi"),
            )
        )

        assertEquals(Action.Type.NftTransfer, action.type)
        val transfer = action.nftTransfer
        assertNotNull(transfer)
        assertEquals(raw('1'), transfer?.nft?.toRaw())
        assertEquals(raw('2'), transfer?.sender?.address?.toRaw())
        assertEquals(raw('3'), transfer?.recipient?.address?.toRaw())
        assertEquals("hi", transfer?.comment)
    }

    @Test
    fun actionFromApi_nftTransferWithMalformedAddress_keepsActionWithoutTransfer() {
        val action = Action.fromApi(
            apiAction(ApiAction.Type.NftItemTransfer, NftItemTransferAction("garbage"))
        )

        assertEquals(Action.Type.NftTransfer, action.type)
        assertNull(action.nftTransfer)
    }

    @Test
    fun converters_actionPersistedBeforeNftTransfer_stillParses() {
        val legacyJson = """[{"type":"TonTransfer","status":"OK","tonTransfer":{"sender":{"address":"${raw('2')}","name":null,""" +
            """"isScam":false,"isWallet":true},"recipient":{"address":"${raw('3')}","name":null,"isScam":false,""" +
            """"isWallet":true},"amount":"5","comment":null},"jettonTransfer":null,"jettonBurn":null,"jettonMint":null,""" +
            """"contractDeploy":null,"jettonSwap":null,"smartContractExec":null,"liquidityDeposit":null}]"""

        val actions = Converters().stringToActionList(legacyJson)

        assertEquals(Action.Type.TonTransfer, actions?.single()?.type)
        assertNull(actions?.single()?.nftTransfer)
    }

    @Test
    fun converters_nftTransferAction_roundTrips() {
        val converters = Converters()
        val action = Action.fromApi(
            apiAction(ApiAction.Type.NftItemTransfer, NftItemTransferAction(raw('1'), recipient = account('3')))
        )

        val restored = converters.stringToActionList(converters.actionListToString(listOf(action)))

        assertEquals(listOf(action), restored)
    }

    private fun apiAction(type: ApiAction.Type, transfer: NftItemTransferAction) = ApiAction(
        type = type,
        status = ApiAction.Status.ok,
        simplePreview = ActionSimplePreview("n", "d", emptyList()),
        baseTransactions = emptyList(),
        nftItemTransfer = transfer,
    )
}
