package io.horizontalsystems.tonkit.models

import io.horizontalsystems.tonkit.Address
import java.math.BigInteger

data class Action(
    val type: Type,
    val status: Status,
    val tonTransfer: TonTransfer?,
    val jettonTransfer: JettonTransfer?,
    val jettonBurn: JettonBurn?,
    val jettonMint: JettonMint?,
    val contractDeploy: ContractDeploy?,
    val jettonSwap: JettonSwap?,
    val smartContractExec: SmartContractExec?
) {
    enum class Type {
        TonTransfer,
        JettonTransfer,
        JettonBurn,
        JettonMint,
        ContractDeploy,
        JettonSwap,
        SmartContract,
        Unknown;

        companion object {
            fun fromApi(type: io.tonapi.models.Action.Type) = when (type) {
                io.tonapi.models.Action.Type.TonTransfer -> TonTransfer
                io.tonapi.models.Action.Type.JettonTransfer -> JettonTransfer
                io.tonapi.models.Action.Type.JettonBurn -> JettonBurn
                io.tonapi.models.Action.Type.JettonMint -> JettonMint
                io.tonapi.models.Action.Type.ContractDeploy -> ContractDeploy
                io.tonapi.models.Action.Type.JettonSwap -> JettonSwap
                io.tonapi.models.Action.Type.SmartContractExec -> SmartContract
                else -> Unknown
            }
        }
    }

    enum class Status(val value: String) {
        OK("ok"),
        FAILED("failed");

        companion object {
            fun fromApi(status: io.tonapi.models.Action.Status) = when (status) {
                io.tonapi.models.Action.Status.ok -> OK
                io.tonapi.models.Action.Status.failed -> FAILED
                else -> throw IllegalArgumentException()
            }
        }
    }

    companion object {
        fun fromApi(action: io.tonapi.models.Action): Action {
            val tonTransfer = action.tonTransfer?.let {
                TonTransfer(
                    sender = AccountAddress.fromApi(it.sender),
                    recipient = AccountAddress.fromApi(it.recipient),
                    amount = BigInteger.valueOf(it.amount),
                    comment = it.comment,
                )
            }
            val jettonTransfer = action.jettonTransfer?.let { jt ->
                JettonTransfer(
                    sender = jt.sender?.let { AccountAddress.fromApi(it) },
                    recipient = jt.recipient?.let { AccountAddress.fromApi(it) },
                    sendersWallet = Address.parse(jt.sendersWallet),
                    recipientsWallet = Address.parse(jt.recipientsWallet),
                    amount = BigInteger(jt.amount),
                    comment = jt.comment,
                    jetton = Jetton.fromPreview(jt.jetton)
                )
            }
            val jettonBurn = action.jettonBurn?.let { jb ->
                JettonBurn(
                    sender = AccountAddress.fromApi(jb.sender),
                    sendersWallet = Address.parse(jb.sendersWallet),
                    amount = BigInteger(jb.amount),
                    jetton = Jetton.fromPreview(jb.jetton)
                )
            }
            val jettonMint = action.jettonMint?.let { jm ->
                JettonMint(
                    recipient = AccountAddress.fromApi(jm.recipient),
                    recipientsWallet = Address.parse(jm.recipientsWallet),
                    amount = BigInteger(jm.amount),
                    jetton = Jetton.fromPreview(jm.jetton)
                )
            }
            val contractDeploy = action.contractDeploy?.let { cd ->
                ContractDeploy(
                    address = Address.parse(cd.address),
                    interfaces = cd.interfaces,
                )
            }
            val jettonSwap = action.jettonSwap?.let { js ->
                JettonSwap(
                    dex = js.dex.value,
                    amountIn = js.amountIn.toBigIntegerOrNull() ?: BigInteger.ZERO,
                    amountOut = js.amountOut.toBigIntegerOrNull()
                        ?: BigInteger.ZERO,
                    tonIn = js.tonIn?.let { BigInteger.valueOf(it) },
                    tonOut = js.tonOut?.let { BigInteger.valueOf(it) },
                    userWallet = AccountAddress.fromApi(js.userWallet),
                    router = AccountAddress.fromApi(js.router),
                    jettonMasterIn = js.jettonMasterIn?.let { Jetton.fromPreview(it) },
                    jettonMasterOut = js.jettonMasterOut?.let {
                        Jetton.fromPreview(
                            it
                        )
                    },
                )
            }
            val smartContractExec = action.smartContractExec?.let { sc ->
                SmartContractExec(
                    contract = AccountAddress.fromApi(sc.contract),
                    tonAttached = BigInteger.valueOf(sc.tonAttached),
                    operation = sc.operation,
                    payload = sc.payload
                )
            }

            return Action(
                Type.fromApi(action.type),
                Status.fromApi(action.status),
                tonTransfer,
                jettonTransfer,
                jettonBurn,
                jettonMint,
                contractDeploy,
                jettonSwap,
                smartContractExec
            )
        }
    }
}

data class TonTransfer(
    val sender: AccountAddress,
    val recipient: AccountAddress,
    val amount: BigInteger,
    val comment: String?,
)

data class JettonTransfer(
    val sender: AccountAddress?,
    val recipient: AccountAddress?,
    val sendersWallet: Address,
    val recipientsWallet: Address,
    val amount: BigInteger,
    val comment: String?,
    val jetton: Jetton,
)

data class JettonBurn(
    val sender: AccountAddress,
    val sendersWallet: Address,
    val amount: BigInteger,
    val jetton: Jetton,
)

data class JettonMint(
    val recipient: AccountAddress,
    val recipientsWallet: Address,
    val amount: BigInteger,
    val jetton: Jetton,
)

data class ContractDeploy(
    val address: Address,
    val interfaces: List<String>,
)

data class JettonSwap(
    val dex: String,
    val amountIn: BigInteger,
    val amountOut: BigInteger,
    val tonIn: BigInteger?,
    val tonOut: BigInteger?,
    val userWallet: AccountAddress,
    val router: AccountAddress,
    val jettonMasterIn: Jetton?,
    val jettonMasterOut: Jetton?,
)

data class SmartContractExec(
    val contract: AccountAddress,
    val tonAttached: BigInteger,
    val operation: String,
    val payload: String?,
)
