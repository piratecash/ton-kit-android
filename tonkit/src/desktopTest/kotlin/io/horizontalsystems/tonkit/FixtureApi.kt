package io.horizontalsystems.tonkit

import io.horizontalsystems.tonkit.api.IApi
import io.horizontalsystems.tonkit.models.Account
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.JettonBalance
import io.tonapi.models.EmulateMessageToWalletRequestParamsInner
import java.math.BigInteger

internal class FixtureApi(var jettonBalances: List<JettonBalance>) : IApi {
    override suspend fun getAccount(address: Address): Account = TonV2Fixture.account

    override suspend fun getAccountJettonBalances(address: Address): List<JettonBalance> = jettonBalances

    // One page of history, nothing newer.
    override suspend fun getEvents(address: Address, beforeLt: Long?, startTimestamp: Long?, limit: Int) =
        if (beforeLt == null && startTimestamp == null) listOf(TonV2Fixture.event) else emptyList()

    override suspend fun getAccountSeqno(address: Address): Int = error("Not used")
    override suspend fun getJettonInfo(address: Address): Jetton = error("Not used")
    override suspend fun getRawTime(): Int = error("Not used")
    override suspend fun estimateFee(
        boc: String,
        params: List<EmulateMessageToWalletRequestParamsInner>?,
    ): BigInteger = error("Not used")

    override suspend fun send(boc: String): Unit = error("Not used")
    override suspend fun getAccountSeqno(address: String): Int = error("Not used")
    override suspend fun transactionExistsByMessageHash(messageHash: String): Boolean = error("Not used")
}
