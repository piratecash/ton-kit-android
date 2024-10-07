package io.horizontalsystems.tonkit.api

import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.Account
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.JettonBalance
import java.math.BigInteger

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
    suspend fun estimateFee(boc: String): BigInteger
    suspend fun send(boc: String)
    suspend fun getAccountSeqno(address: String): Int
}
