package com.tonapps.wallet.api

import com.tonapps.network.SSEvent
import com.tonapps.network.post
import com.tonapps.network.sse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class API {

    val defaultHttpClient = baseOkHttpClientBuilder().build()

    private val tonAPIHttpClient: OkHttpClient by lazy {
        createTonAPIHttpClient()
    }

//    private val internalApi = InternalApi(context, defaultHttpClient)
//    private val configRepository = ConfigRepository(context, scope, internalApi)
//
//    val config: ConfigEntity
//        get() {
//            while (configRepository.configEntity == null) {
//                Thread.sleep(32)
//            }
//            return configRepository.configEntity!!
//        }
//
//    val configFlow: Flow<ConfigEntity>
//        get() = configRepository.stream
//
//    private val provider: Provider by lazy {
//        Provider(config.tonapiMainnetHost, config.tonapiTestnetHost, tonAPIHttpClient)
//    }
//
//    fun accounts(testnet: Boolean) = provider.accounts.get(testnet)
//
//    fun wallet(testnet: Boolean) = provider.wallet.get(testnet)
//
//    fun nft(testnet: Boolean) = provider.nft.get(testnet)
//
//    fun blockchain(testnet: Boolean) = provider.blockchain.get(testnet)
//
//    fun emulation(testnet: Boolean) = provider.emulation.get(testnet)
//
//    fun liteServer(testnet: Boolean) = provider.liteServer.get(testnet)
//
//    fun staking(testnet: Boolean) = provider.staking.get(testnet)
//
//    fun rates() = provider.rates.get(false)
//
//    fun getAlertNotifications() = internalApi.getNotifications()
//
//    fun isOkStatus(testnet: Boolean): Boolean {
//        try {
//            val status = provider.blockchain.get(testnet).status()
//            if (!status.restOnline) {
//                return false
//            }
//            if (status.indexingLatency > (5 * 60) - 30) {
//                return false
//            }
//            return true
//        } catch (e: Throwable) {
//            return false
//        }
//    }
//
//    fun getEvents(
//        accountId: String,
//        testnet: Boolean,
//        beforeLt: Long? = null,
//        limit: Int = 20
//    ): AccountEvents {
//        return accounts(testnet).getAccountEvents(
//            accountId = accountId,
//            limit = limit,
//            beforeLt = beforeLt,
//            subjectOnly = true
//        )
//    }
//
//    fun getTokenEvents(
//        tokenAddress: String,
//        accountId: String,
//        testnet: Boolean,
//        beforeLt: Long? = null,
//        limit: Int = 20
//    ): AccountEvents {
//        return accounts(testnet).getAccountJettonHistoryByID(
//            jettonId = tokenAddress,
//            accountId = accountId,
//            limit = limit,
//            beforeLt = beforeLt
//        )
//    }
//
//    suspend fun getTonBalance(
//        accountId: String,
//        testnet: Boolean
//    ): BalanceEntity {
//        val account = getAccount(accountId, testnet) ?: return BalanceEntity(
//            token = TokenEntity.TON,
//            value = Coins.ZERO,
//            walletAddress = accountId
//        )
//        return BalanceEntity(TokenEntity.TON, Coins.of(account.balance), accountId)
//    }
//
//    suspend fun getJettonsBalances(
//        accountId: String,
//        testnet: Boolean,
//        currency: String? = null
//    ): List<BalanceEntity> {
//        try {
//            val jettonsBalances = withRetry {
//                accounts(testnet).getAccountJettonsBalances(
//                    accountId = accountId,
//                    currencies = currency?.let { listOf(it) }
//                ).balances
//            } ?: return emptyList()
//            return jettonsBalances.map { BalanceEntity(it) }.filter { it.value.isPositive }
//        } catch (e: Throwable) {
//            return emptyList()
//        }
//    }
//
//    suspend fun resolveAddressOrName(
//        query: String,
//        testnet: Boolean
//    ): AccountDetailsEntity? {
//        return try {
//            val account = getAccount(query, testnet) ?: return null
//            AccountDetailsEntity(query, account, testnet)
//        } catch (e: Throwable) {
//            null
//        }
//    }
//
//    fun resolvePublicKey(
//        pk: PublicKeyEd25519,
//        testnet: Boolean
//    ): List<AccountDetailsEntity> {
//        return try {
//            val query = pk.key.hex()
//            val wallets = wallet(testnet).getWalletsByPublicKey(query).accounts
//            wallets.map { AccountDetailsEntity(query, it, testnet) }
//        } catch (e: Throwable) {
//            emptyList()
//        }
//    }
//
//    fun getRates(currency: String, tokens: List<String>): Map<String, TokenRates> {
//        return try {
//            rates().getRates(
//                tokens = tokens,
//                currencies = listOf(currency
//                )).rates
//        } catch (e: Throwable) {
//            mapOf()
//        }
//    }
//
//    fun getNft(address: String, testnet: Boolean): NftItem? {
//        return try {
//            nft(testnet).getNftItemByAddress(address)
//        } catch (e: Throwable) {
//            null
//        }
//    }
//
//    fun getNftItems(address: String, testnet: Boolean, limit: Int = 1000): List<NftItem> {
//        return try {
//            accounts(testnet).getAccountNftItems(
//                accountId = address,
//                limit = limit,
//                indirectOwnership = true,
//            ).nftItems
//        } catch (e: Throwable) {
//            emptyList()
//        }
//    }
//
//    fun getPublicKey(
//        accountId: String,
//        testnet: Boolean
//    ): String {
//        return accounts(testnet).getAccountPublicKey(accountId).publicKey
//    }
//
//    fun accountEvents(accountId: String, testnet: Boolean): Flow<SSEvent> {
//        val endpoint = if (testnet) {
//            config.tonapiTestnetHost
//        } else {
//            config.tonapiMainnetHost
//        }
//        // val mempool = okHttpClient.sse("$endpoint/v2/sse/mempool?accounts=${accountId}")
//        val tx = tonAPIHttpClient.sse("$endpoint/v2/sse/accounts/transactions?accounts=${accountId}")
//        // return merge(mempool, tx)
//        return tx
//    }

    fun tonconnectEvents(
        publicKeys: List<String>,
        lastEventId: String?
    ): Flow<SSEvent> {
        if (publicKeys.isEmpty()) {
            return emptyFlow()
        }
        val value = publicKeys.joinToString(",")
        var url = "${BRIDGE_URL}/events?client_id=$value"
        if (lastEventId != null) {
            url += "&last_event_id=$lastEventId"
        }
        return tonAPIHttpClient.sse(url)
    }

//    fun tonconnectPayload(): String? {
//        try {
//            val url = "${config.tonapiMainnetHost}/v2/tonconnect/payload"
//            val json = JSONObject(tonAPIHttpClient.get(url))
//            return json.getString("payload")
//        } catch (e: Throwable) {
//            return null
//        }
//    }

//    fun tonconnectProof(address: String, proof: String): String {
//        val url = "${config.tonapiMainnetHost}/v2/wallet/auth/proof"
//        val data = "{\"address\":\"$address\",\"proof\":$proof}"
//        val response = tonAPIHttpClient.postJSON(url, data)
//        if (!response.isSuccessful) {
//            throw Exception("Failed creating proof: ${response.code}")
//        }
//        val body = response.body?.string() ?: throw Exception("Empty response")
//        return JSONObject(body).getString("token")
//    }

    fun tonconnectSend(
        publicKeyHex: String,
        clientId: String,
        body: String
    ) {
        val mimeType = "text/plain".toMediaType()
        val url = "${BRIDGE_URL}/message?client_id=$publicKeyHex&to=$clientId&ttl=300"
        val response = tonAPIHttpClient.post(url, body.toRequestBody(mimeType))
        if (!response.isSuccessful) {
            throw Exception("Failed sending event: ${response.code}")
        }
    }

//    suspend fun sendToBlockchain(
//        boc: String,
//        testnet: Boolean
//    ): Boolean = withContext(Dispatchers.IO) {
//        if (!isOkStatus(testnet)) {
//            return@withContext false
//        }
//        try {
//            val request = SendBlockchainMessageRequest(boc)
//            blockchain(testnet).sendBlockchainMessage(request)
//            true
//        } catch (e: Throwable) {
//            false
//        }
//    }

//    suspend fun sendToBlockchain(
//        cell: Cell,
//        testnet: Boolean
//    ) = sendToBlockchain(cell.base64(), testnet)
//
//    suspend fun getAccountSeqno(
//        accountId: String,
//        testnet: Boolean,
//    ): Int = withContext(Dispatchers.IO) {
//        wallet(testnet).getAccountSeqno(accountId).seqno
//    }

//    suspend fun resolveAccount(
//        value: String,
//        testnet: Boolean,
//    ): Account? = withContext(Dispatchers.IO) {
//        if (value.isValidTonAddress()) {
//            return@withContext getAccount(value, testnet)
//        }
//        return@withContext resolveDomain(value.lowercase().trim(), testnet)
//    }

//    private suspend fun resolveDomain(domain: String, testnet: Boolean): Account? {
//        return getAccount(domain, testnet) ?: getAccount(domain.unicodeToPunycode(), testnet)
//    }

//    private suspend fun getAccount(accountId: String, testnet: Boolean): Account? {
//        return withRetry {
//            accounts(testnet).getAccount(accountId)
//        }
//    }

//    fun pushSubscribe(
//        locale: Locale,
//        firebaseToken: String,
//        deviceId: String,
//        accounts: List<String>
//    ): Boolean {
//        return try {
//            val url = "${config.tonapiMainnetHost}/v1/internal/pushes/plain/subscribe"
//            val accountsArray = JSONArray()
//            for (account in accounts) {
//                val jsonAccount = JSONObject()
//                jsonAccount.put("address", account)
//                accountsArray.put(jsonAccount)
//            }
//
//            val json = JSONObject()
//            json.put("locale", locale.toString())
//            json.put("device", deviceId)
//            json.put("token", firebaseToken)
//            json.put("accounts", accountsArray)
//
//            return tonAPIHttpClient.postJSON(url, json.toString()).isSuccessful
//        } catch (e: Throwable) {
//            false
//        }
//    }

//    fun pushTonconnectSubscribe(
//        token: String,
//        appUrl: String,
//        accountId: String,
//        firebaseToken: String,
//        sessionId: String?,
//        commercial: Boolean = true,
//        silent: Boolean = true
//    ): Boolean {
//        return try {
//            val url = "${config.tonapiMainnetHost}/v1/internal/pushes/tonconnect"
//
//            val json = JSONObject()
//            json.put("app_url", appUrl)
//            json.put("account", accountId)
//            json.put("firebase_token", firebaseToken)
//            sessionId?.let { json.put("session_id", it) }
//            json.put("commercial", commercial)
//            json.put("silent", silent)
//            val data = json.toString().replace("\\/", "/")
//
//            tonAPIHttpClient.postJSON(url, data, ArrayMap<String, String>().apply {
//                set("X-TonConnect-Auth", token)
//            }).isSuccessful
//        } catch (e: Throwable) {
//            false
//        }
//    }

//    fun getPushFromApps(
//        token: String,
//        accountId: String,
//    ): JSONArray {
//        val url = "${config.tonapiMainnetHost}/v1/messages/history?account=$accountId"
//        val response = tonAPIHttpClient.get(url, ArrayMap<String, String>().apply {
//            set("X-TonConnect-Auth", token)
//        })
//
//        return try {
//            val json = JSONObject(response)
//            json.getJSONArray("items")
//        } catch (e: Throwable) {
//            JSONArray()
//        }
//    }

//    fun getBrowserApps(testnet: Boolean): JSONObject {
//        return internalApi.getBrowserApps(testnet)
//    }

//    fun getFiatMethods(testnet: Boolean): JSONObject {
//        return internalApi.getFiatMethods(testnet)
//    }

//    fun getTransactionEvents(accountId: String, testnet: Boolean, eventId: String): AccountEvent? {
//        return try {
//            accounts(testnet).getAccountEvent(accountId, eventId)
//        } catch (e: Throwable) {
//            null
//        }
//    }

//    fun loadChart(
//        token: String,
//        currency: String,
//        startDate: Long,
//        endDate: Long
//    ): List<ChartEntity> {
//        try {
//            val url = "${config.tonapiMainnetHost}/v2/rates/chart?token=$token&currency=$currency&end_date=$endDate&start_date=$startDate"
//            val array = JSONObject(tonAPIHttpClient.get(url)).getJSONArray("points")
//            return (0 until array.length()).map { index ->
//                ChartEntity(array.getJSONArray(index))
//            }
//        } catch (e: Throwable) {
//            return listOf(ChartEntity(0, 0f))
//        }
//    }

//    suspend fun getServerTime(testnet: Boolean): Int = withContext(Dispatchers.IO) {
//        try {
//            liteServer(testnet).getRawTime().time
//        } catch (e: Throwable) {
//            (System.currentTimeMillis() / 1000).toInt()
//        }
//    }

//    suspend fun resolveCountry(): String? = withContext(Dispatchers.IO) {
//        internalApi.resolveCountry()
//    }

//    suspend fun reportNtfSpam(
//        nftAddress: String,
//        scam: Boolean
//    ) = withContext(Dispatchers.IO) {
//        val url = config.scamEndpoint + "/v1/report/$nftAddress"
//        val data = "{\"is_scam\":$scam}"
//        val response = tonAPIHttpClient.postJSON(url, data)
//        if (!response.isSuccessful) {
//            throw Exception("Failed creating proof: ${response.code}")
//        }
//        response.body?.string() ?: throw Exception("Empty response")
//    }

    companion object {

        const val BRIDGE_URL = "https://bridge.tonapi.io/bridge"

        val JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

//        private val socketFactoryTcpNoDelay = SSLSocketFactoryTcpNoDelay()

        private fun baseOkHttpClientBuilder(): OkHttpClient.Builder {
            return OkHttpClient().newBuilder()
                .retryOnConnectionFailure(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.SECONDS)
                .pingInterval(5, TimeUnit.SECONDS)
                .followSslRedirects(true)
                .followRedirects(true)
                // .sslSocketFactory(socketFactoryTcpNoDelay.sslSocketFactory, socketFactoryTcpNoDelay.trustManager)
                // .socketFactory(SocketFactoryTcpNoDelay())
        }

        private fun createTonAPIHttpClient(
//            context: Context,
//            tonApiV2Key: String
        ): OkHttpClient {
            return baseOkHttpClientBuilder()
//                .addInterceptor(AcceptLanguageInterceptor(context.locale))
//                .addInterceptor(AuthorizationInterceptor.bearer(tonApiV2Key))
                .build()
        }
    }
}