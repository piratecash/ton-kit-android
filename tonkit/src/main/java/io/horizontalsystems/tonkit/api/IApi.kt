package io.horizontalsystems.tonkit.api

import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.models.Account
import io.horizontalsystems.tonkit.models.EmulatedFee
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.DnsExpiringItem
import io.horizontalsystems.tonkit.models.JettonBalance
import io.horizontalsystems.tonkit.models.NftItem
import io.tonapi.models.EmulateMessageToWalletRequestParamsInner

interface IApi {
    suspend fun getAccount(address: Address): Account
    suspend fun getAccountJettonBalances(address: Address): List<JettonBalance>
    suspend fun getEvents(
        address: Address,
        beforeLt: Long?,
        startTimestamp: Long?,
        limit: Int,
    ): List<Event>
    suspend fun getAccountSeqno(address: Address): Int
    suspend fun getJettonInfo(address: Address): Jetton
    suspend fun getRawTime(): Int
    suspend fun estimateFee(boc: String, params: List<EmulateMessageToWalletRequestParamsInner>?): EmulatedFee
    suspend fun send(boc: String)
    suspend fun getAccountSeqno(address: String): Int
    suspend fun transactionExistsByMessageHash(messageHash: String): Boolean
    suspend fun getAccountNftItems(address: Address, limit: Int): List<NftItem>
    suspend fun getNftItem(address: Address): NftItem
    suspend fun getDnsExpiring(address: Address, period: Int): List<DnsExpiringItem>
    fun resolveDomain(domain: String): FriendlyAddress?
}
