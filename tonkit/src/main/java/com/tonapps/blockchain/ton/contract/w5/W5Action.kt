package com.tonapps.blockchain.ton.contract.w5

import com.tonapps.blockchain.ton.TonSendMode
import com.tonapps.blockchain.ton.contract.BaseWalletContract.Companion.createIntMsg
import com.tonapps.blockchain.ton.contract.MessageType
import org.ton.block.AddrStd
import org.ton.block.MessageRelaxed
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.CellBuilder.Companion.beginCell
import org.ton.cell.storeRef
import org.ton.contract.wallet.WalletTransfer
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeTlb

sealed class W5Action(
    open val type: String
) {

    companion object {

        // The contract rejects an external request whose message lacks +2 (ignore errors).
        private fun toSafeV5R1SendMode(sendMode: Int, authType: MessageType): Int = when (authType) {
            MessageType.External -> sendMode or TonSendMode.IGNORE_ERRORS.value
            MessageType.Internal, MessageType.Extension -> sendMode
        }

        private fun isOutActionExtended(out: W5Action): Boolean {
            return out is OutActionSetIsPublicKeyEnabled || out is OutActionAddExtension || out is OutActionRemoveExtension
        }

        private fun isOutActionBasic(out: W5Action): Boolean {
            return !isOutActionExtended(out)
        }

        // out_list$_ prev:^(OutList n) action:OutAction: the action is inline, the first action is the innermost.
        private fun storeOutList(actions: List<Out>): Cell {
            return actions.fold(beginCell().endCell()) { prev, action ->
                beginCell().storeRef(prev).also(action.store()).endCell()
            }
        }

        private fun packExtendedActionsRec(extendedActions: List<Out>): Cell {
            val builder = beginCell().also(extendedActions.first().store())
            val rest = extendedActions.drop(1)
            if (rest.isNotEmpty()) {
                builder.storeRef(packExtendedActionsRec(rest))
            }
            return builder.endCell()
        }

        fun storeOutListExtendedV5R1(gifts: List<WalletTransfer>, authType: MessageType): Cell {
            return storeOutListExtendedV5R1(patchV5R1ActionsSendMode(gifts.map {
                OutActionSendMsg(it)
            }, authType)).endCell()
        }

        fun storeOutListExtendedV5R1(actions: List<Out>): CellBuilder {
            val builder = beginCell()
            val extendedActions = actions.filter { isOutActionExtended(it) }
            val basicActions = actions.filter { isOutActionBasic(it) }

            if (basicActions.isNotEmpty()) {
                builder.storeBit(true)
                builder.storeRef(storeOutList(basicActions))
            } else {
                builder.storeBit(false)
            }

            if (extendedActions.isEmpty()) {
                builder.storeUInt(0, 1)
            } else {
                val rest = extendedActions.drop(1)
                builder.storeUInt(1, 1)
                extendedActions.first().store()(builder)
                if (rest.isNotEmpty()) {
                    builder.storeRef(packExtendedActionsRec(rest))
                }
            }
            return builder
        }

        private fun patchV5R1ActionsSendMode(actions: List<Out>, authType: MessageType): List<Out> {
            return actions.map { action ->
                if (action is OutActionSendMsg) {
                    val safeSendMode = toSafeV5R1SendMode(action.message.sendMode, authType)
                    action.copy(message = action.message.copy(sendMode = safeSendMode))
                } else {
                    action
                }
            }
        }
    }

    sealed class Out(
        override val type: String
    ): W5Action(type) {
        open fun store(): (CellBuilder) -> Unit {
            return { }
        }
    }

    data class OutActionAddExtension(
        val address: AddrStd,
    ): Out("addExtension") {
        override fun store(): (CellBuilder) -> Unit = { builder ->
            builder.storeUInt(0x02, 8)
            builder.storeTlb(AddrStd, address)
        }
    }

    data class OutActionRemoveExtension(
        val address: AddrStd,
    ): Out("removeExtension") {
        override fun store(): (CellBuilder) -> Unit = { builder ->
            builder.storeUInt(0x03, 8)
            builder.storeTlb(AddrStd, address)
        }
    }

    data class OutActionSetIsPublicKeyEnabled(
        val isEnabled: Boolean,
    ): Out("setIsPublicKeyEnabled") {
        override fun store(): (CellBuilder) -> Unit = { builder ->
            builder.storeUInt(0x04, 8)
            builder.storeUInt(if (isEnabled) 1 else 0, 1)
        }

    }

    data class OutActionSendMsg(
        val message: WalletTransfer
    ): Out("sendMsg") {

        override fun store(): (CellBuilder) -> Unit = { builder ->
            builder.storeUInt(0x0ec3c86d, 32)
            builder.storeUInt(message.sendMode, 8)
            builder.storeRef {
                storeTlb(MessageRelaxed.tlbCodec(AnyTlbConstructor), createIntMsg(message))
            }
        }
    }

    data class OutActionSetCode(
        val newCode: Cell
    ): Out("setCode") {

        override fun store(): (CellBuilder) -> Unit = { builder ->
            builder.storeUInt(0xad4de08e, 32)
            builder.storeRef(newCode)
        }
    }

}
