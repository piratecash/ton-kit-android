package io.horizontalsystems.tonkit.api

import com.tonapps.blockchain.ton.extensions.base64
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.Account
import io.horizontalsystems.tonkit.models.AccountStatus
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.JettonBalance
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.models.Network.MainNet
import io.horizontalsystems.tonkit.models.Network.TestNet
import io.tonapi.apis.AccountsApi
import io.tonapi.apis.BlockchainApi
import io.tonapi.apis.EmulationApi
import io.tonapi.apis.JettonsApi
import io.tonapi.apis.LiteServerApi
import io.tonapi.apis.WalletApi
import io.tonapi.models.EmulateMessageToWalletRequest
import io.tonapi.models.MessageConsequences
import io.tonapi.models.SendBlockchainMessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.ton.cell.Cell
import java.math.BigInteger

class TonApi(network: Network, okHttpClient: OkHttpClient) : IApi {
    private val basePath = when (network) {
        MainNet -> "https://tonapi.io"
        TestNet -> "https://testnet.tonapi.io"
    }

    private val accountsApi = AccountsApi(basePath, okHttpClient)
    private val walletApi = WalletApi(basePath, okHttpClient)
    private val jettonsApi = JettonsApi(basePath, okHttpClient)
    private val liteServerApi = LiteServerApi(basePath, okHttpClient)
    val emulationApi = EmulationApi(basePath, okHttpClient)
    private val blockchainApi = BlockchainApi(basePath, okHttpClient)

    override suspend fun getAccount(address: Address): Account {
        val account = accountsApi.getAccount(address.toRaw())

        return Account(
            Address.parse(account.address),
            account.balance,
            AccountStatus.fromApi(account.status),
        )
    }

    override suspend fun getAccountJettonBalances(address: Address): List<JettonBalance> {
        val jettonsBalances = accountsApi.getAccountJettonsBalances(address.toRaw())

        return jettonsBalances.balances.map { balance ->
            JettonBalance(
                Jetton.fromPreview(balance.jetton),
                BigInteger(balance.balance),
                Address.parse(balance.walletAddress.address)
            )
        }
    }

    override suspend fun getEvents(
        address: Address,
        beforeLt: Long?,
        startTimestamp: Long?,
        limit: Int,
    ): List<Event> {
        val events = accountsApi.getAccountEvents(
            accountId = address.toRaw(),
            limit = limit,
            beforeLt = beforeLt,
            startDate = startTimestamp
        )

        return events.events.map(Event.Companion::fromApi)
    }

    override suspend fun getAccountSeqno(address: Address): Int {
        return getAccountSeqno(address.toRaw())
    }

    override suspend fun getAccountSeqno(address: String): Int {
        return walletApi.getAccountSeqno(address).seqno
    }

    override suspend fun getJettonInfo(address: Address): Jetton {
        val jettonInfo = jettonsApi.getJettonInfo(address.toRaw())
        return Jetton.fromJettonInfo(jettonInfo)
    }

    override suspend fun getRawTime(): Int {
        return liteServerApi.getRawTime().time
    }

    override suspend fun estimateFee(boc: String): BigInteger {
        val request = EmulateMessageToWalletRequest(boc)
        val result = emulationApi.emulateMessageToWallet(request)
        return BigInteger.valueOf(result.trace.transaction.totalFees)
    }

    override suspend fun send(boc: String) {
        val request = SendBlockchainMessageRequest(boc)
        blockchainApi.sendBlockchainMessage(request)
    }

    suspend fun emulate(
        boc: String,
        testnet: Boolean,
    ): MessageConsequences = withContext(Dispatchers.IO) {
        val request = EmulateMessageToWalletRequest(boc)
        emulationApi.emulateMessageToWallet(request)
    }

    suspend fun emulate(
        cell: Cell,
        testnet: Boolean,
    ): MessageConsequences {
        return emulate(cell.base64(), testnet)
    }


}