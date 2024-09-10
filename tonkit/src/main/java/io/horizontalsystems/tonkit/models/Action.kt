package io.horizontalsystems.tonkit.models

import io.horizontalsystems.tonkit.Address
import java.math.BigInteger

data class Action(val type: Type, val status: Status) {

    sealed class Type {
        data class TonTransfer(val action: Action.TonTransfer) : Type()
        data class JettonTransfer(val action: Action.JettonTransfer) : Type()
        data class JettonBurn(val action: Action.JettonBurn) : Type()
        data class JettonMint(val action: Action.JettonMint) : Type()
        data class ContractDeploy(val action: Action.ContractDeploy) : Type()
        data class JettonSwap(val action: Action.JettonSwap) : Type()
        data class SmartContract(val action: Action.SmartContract) : Type()
        data class Unknown(val rawType: String) : Type()

        companion object {
            fun fromApi(action: io.tonapi.models.Action): Type {
                val type = when (action.type) {
                    io.tonapi.models.Action.Type.TonTransfer -> {
                        action.tonTransfer?.let {
                            TonTransfer(
                                TonTransfer(
                                    sender = AccountAddress.fromApi(it.sender),
                                    recipient = AccountAddress.fromApi(it.recipient),
                                    amount = BigInteger.valueOf(it.amount),
                                    comment = it.comment,
                                )
                            )
                        }
                    }

                    io.tonapi.models.Action.Type.JettonTransfer -> {
                        action.jettonTransfer?.let { jt ->
                            JettonTransfer(
                                JettonTransfer(
                                    sender = jt.sender?.let { AccountAddress.fromApi(it) },
                                    recipient = jt.recipient?.let { AccountAddress.fromApi(it) },
                                    sendersWallet = Address.parse(jt.sendersWallet),
                                    recipientsWallet = Address.parse(jt.recipientsWallet),
                                    amount = BigInteger(jt.amount),
                                    comment = jt.comment,
                                    jetton = Jetton.fromPreview(jt.jetton)
                                )
                            )
                        }
                    }

                    io.tonapi.models.Action.Type.JettonBurn -> {
                        action.jettonBurn?.let { jb ->
                            JettonBurn(
                                JettonBurn(
                                    sender = AccountAddress.fromApi(jb.sender),
                                    sendersWallet = Address.parse(jb.sendersWallet),
                                    amount = BigInteger(jb.amount),
                                    jetton = Jetton.fromPreview(jb.jetton)
                                )
                            )
                        }
                    }

                    io.tonapi.models.Action.Type.JettonMint -> {
                        action.jettonMint?.let { jm ->
                            JettonMint(
                                JettonMint(
                                    recipient = AccountAddress.fromApi(jm.recipient),
                                    recipientsWallet = Address.parse(jm.recipientsWallet),
                                    amount = BigInteger(jm.amount),
                                    jetton = Jetton.fromPreview(jm.jetton)
                                )
                            )
                        }
                    }

                    io.tonapi.models.Action.Type.ContractDeploy -> {
                        action.contractDeploy?.let { cd ->
                            ContractDeploy(
                                ContractDeploy(
                                    address = Address.parse(cd.address),
                                    interfaces = cd.interfaces,
                                )
                            )
                        }
                    }

                    io.tonapi.models.Action.Type.JettonSwap -> {
                        action.jettonSwap?.let { js ->
                            JettonSwap(
                                JettonSwap(
                                    dex = js.dex.value,
                                    amountIn = BigInteger(js.amountIn),
                                    amountOut = BigInteger(js.amountOut),
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
                            )
                        }
                    }

                    io.tonapi.models.Action.Type.SmartContractExec -> {
                        action.smartContractExec?.let { sc ->
                            SmartContract(
                                SmartContract(
                                    contract = AccountAddress.fromApi(sc.contract),
                                    tonAttached = BigInteger.valueOf(sc.tonAttached),
                                    operation = sc.operation,
                                    payload = sc.payload
                                )
                            )
                        }
                    }

                    else -> null
                }

                return type ?: Unknown(action.type.value)
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

    data class SmartContract(
        val contract: AccountAddress,
        val tonAttached: BigInteger,
        val operation: String,
        val payload: String?,
    )

}
