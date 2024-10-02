package io.horizontalsystems.tonkit.tonconnect

import android.net.Uri
import android.util.Log
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.network.get
import com.tonapps.security.CryptoBox
import com.tonapps.tonkeeper.core.tonconnect.models.TCData
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.WalletProof
import com.tonapps.wallet.data.account.entities.ProofDomainEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppItemEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppManifestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppAddressItemEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppEventSuccessEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppProofItemReplySuccess
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppReply
import io.horizontalsystems.tonkit.core.TonKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.StateInit
import org.ton.crypto.base64

class TonConnectKit {
    private val api = API()

    fun readData(uriString: String): DAppRequestEntity {
        val uri = Uri.parse(uriString)

        if (uri.scheme != "tc") {
            throw UriError("Unknown scheme: ${uri.scheme}")
        }

        return DAppRequestEntity(uri)
    }

    suspend fun connect(dAppRequestEntity: DAppRequestEntity, walletType: TonKit.WalletType, testnet: Boolean): DAppEventSuccessEntity {
        val request = dAppRequestEntity
        val manifest = getManifest(request.payload.manifestUrl)

        val data = TCData(
            manifest = manifest,
            accountId = walletType.address.toRaw(),
            clientId = request.id,
            items = request.payload.items,
            testnet = testnet,
        )


        val privateKey = walletType.privateKey ?: throw Exception("No private key")

        val walletEntity = WalletEntity(
            id = "id",
            publicKey = privateKey.publicKey(),
            type = Wallet.Type.Default,
            version = WalletVersion.V4R2,
            label = Wallet.Label("", "", 0)
        )
        return connect(walletEntity, privateKey, data.manifest, data.clientId, data.items)
    }

    suspend fun connect(
        wallet: WalletEntity,
        privateKey: PrivateKeyEd25519,
        manifest: DAppManifestEntity,
        clientId: String,
        requestItems: List<DAppItemEntity>,
//        firebaseToken: String?,
    ): DAppEventSuccessEntity = withContext(Dispatchers.IO) {
//        val enablePush = firebaseToken != null
        val app = newApp(manifest, wallet.accountId, wallet.testnet, clientId, wallet.id, false)

        xxxApp(app)

        val items = createItems(app, wallet, privateKey, requestItems)
        val res = DAppEventSuccessEntity(items)
        send(app, res.toJSON())
//        firebaseToken?.let {
//            subscribePush(wallet, app, it)
//        }
        res.copy()
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private fun xxxApp(app: DAppEntity) {
        coroutineScope.launch {
            api.tonconnectEvents(listOf(app.publicKeyHex), null).collect {
                Log.e("AAA", "Event: $it")
            }
        }
    }

    suspend fun send(
        app: DAppEntity,
        body: JSONObject,
    ) = send(app, body.toString())

    suspend fun send(
        app: DAppEntity,
        body: String,
    ) = withContext(Dispatchers.IO) {
        Log.e("AAA", "send body: $body")
        val encrypted = app.encrypt(body)
        api.tonconnectSend(app.publicKeyHex, app.clientId, base64(encrypted))
    }

    private fun createItems(
        app: DAppEntity,
        wallet: WalletEntity,
        privateKey: PrivateKeyEd25519,
        items: List<DAppItemEntity>
    ): List<DAppReply> {
        val result = mutableListOf<DAppReply>()
        for (requestItem in items) {
            if (requestItem.name == DAppItemEntity.TON_ADDR) {
                result.add(createAddressItem(
                    accountId = wallet.accountId,
                    testnet = wallet.testnet,
                    publicKey = wallet.publicKey,
                    stateInit = wallet.contract.stateInit
                ))
            } else if (requestItem.name == DAppItemEntity.TON_PROOF) {
                result.add(createProofItem(
                    payload = requestItem.payload ?: "",
                    domain = app.domain,
                    address = wallet.contract.address,
                    privateWalletKey = privateKey,
                    stateInit = wallet.contract.getStateCell().base64()
                ))
            }
        }
        return result
    }

    private fun createProofItem(
        payload: String,
        domain: ProofDomainEntity,
        address: AddrStd,
        privateWalletKey: PrivateKeyEd25519,
        stateInit: String,
    ): DAppProofItemReplySuccess {
        val proof = WalletProof.sign(
            address,
            privateWalletKey,
            payload,
            domain,
            stateInit
        )
        return DAppProofItemReplySuccess(proof = proof)
    }

    private fun createAddressItem(
        accountId: String,
        testnet: Boolean,
        publicKey: PublicKeyEd25519,
        stateInit: StateInit
    ): DAppAddressItemEntity {
        return DAppAddressItemEntity(
            address = accountId,
            network = if (testnet) TonNetwork.TESTNET else TonNetwork.MAINNET,
            walletStateInit = stateInit,
            publicKey = publicKey
        )
    }

    suspend fun newApp(
        manifest: DAppManifestEntity,
        accountId: String,
        testnet: Boolean,
        clientId: String,
        walletId: String,
        enablePush: Boolean,
    ): DAppEntity = withContext(Dispatchers.IO) {
        val keyPair = CryptoBox.keyPair()
        val app = DAppEntity(
            url = manifest.url,
            accountId = accountId,
            testnet = testnet,
            clientId = clientId,
            keyPair = keyPair,
            walletId = walletId,
            enablePush = enablePush,
            manifest = manifest,
        )
//        localDataSource.addApp(app)
//        val oldValue = _appsFlow.value ?: emptyList()
//        _appsFlow.value = oldValue.plus(app)
        app
    }



    private fun getManifest(manifestUrl: String): DAppManifestEntity {
        //            val local = localDataSource.getManifest(sourceUrl)
        //            if (local == null) {
        val remote = loadManifest(manifestUrl)
        //                localDataSource.setManifest(sourceUrl, remote)
        return remote
        //            } else {
        //                local
        //            }
    }

    fun loadManifest(url: String): DAppManifestEntity {
        val response = api.defaultHttpClient.get(url)
        Log.d("APINewLog", "loadManifest: $response")
        return DAppManifestEntity(JSONObject(response))
    }

}

class UriError(message: String) : Error(message)