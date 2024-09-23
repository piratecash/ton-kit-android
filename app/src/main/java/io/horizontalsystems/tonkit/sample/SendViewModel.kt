package io.horizontalsystems.tonkit.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.core.TonKit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.math.BigDecimal

class SendViewModel(private val sendType: SendType) : ViewModel() {

    sealed interface SendType {
        data object Ton : SendType
        data class Jetton(val walletAddress: Address, val decimals: Int, val balance: BigDecimal) : SendType
    }

    private val tonKit = App.tonKit
    private val tonBalance: BigDecimal?
        get() = tonKit.account?.balance?.toBigDecimal()?.movePointLeft(9)

    private val balance: BigDecimal?
        get() = when (sendType) {
            is SendType.Jetton -> sendType.balance
            SendType.Ton -> tonBalance
        }

    private var feeEstimateInProgress = false
    private var fee: BigDecimal? = null
    private var feeError: Throwable? = null

    private var sendRecipient: String? = null
    private var sendAmount: BigDecimal? = null

    private val sendRecipientForKit: FriendlyAddress?
        get() = sendRecipient?.let { FriendlyAddress.parse(it) }
    private val sendAmountForKit: TonKit.SendAmount?
        get() = when (sendType) {
            is SendType.Jetton -> {
                sendAmount?.let {
                    TonKit.SendAmount.Amount(it.movePointRight(sendType.decimals).toBigInteger())
                }
            }
            SendType.Ton -> {
                sendAmount?.let {
                    if (it.compareTo(tonBalance) == 0) {
                        TonKit.SendAmount.Max
                    } else {
                        TonKit.SendAmount.Amount(it.movePointRight(9).toBigInteger())
                    }
                }
            }
        }

    private var sendInProgress = false
    private var sendResult = ""

    var uiState by mutableStateOf(
        SendUiState(
            balance = balance,
            feeEstimateInProgress = feeEstimateInProgress,
            fee = fee,
            feeError = feeError,
            sendInProgress = sendInProgress,
            sendResult = sendResult
        )
    )
        private set

    private var refreshFeeJob: Job? = null

    fun setAmount(amount: String) {
        sendAmount = amount.toBigDecimalOrNull()

        refreshFee()
    }

    fun setRecipient(recipient: String) {
        sendRecipient = recipient

        refreshFee()
    }

    fun send() {
        viewModelScope.launch(Dispatchers.Default) {
            val sendRecipientForKit1 = sendRecipientForKit
            val sendAmount1 = sendAmount
            val sendAmountForKit1 = sendAmountForKit

            if (sendRecipientForKit1 != null && sendAmountForKit1 != null && sendAmount1 != null) {
                sendInProgress = true
                emitState()

                try {
                    when (sendType) {
                        is SendType.Jetton -> {
                            val amount = sendAmount1.movePointRight(sendType.decimals).toBigInteger()
                            tonKit.send(sendType.walletAddress, sendRecipientForKit1, amount, null)
                        }
                        SendType.Ton -> {
                            tonKit.send(sendRecipientForKit1, sendAmountForKit1, null)
                        }
                    }

                    sendResult = "Sent Success"
                } catch (e: Throwable) {
                    sendResult = "Sending Failed: $e"
                }

                sendInProgress = false
                emitState()
            }
        }
    }

    private fun emitState() {
        viewModelScope.launch {
            uiState = SendUiState(
                balance = balance,
                feeEstimateInProgress = feeEstimateInProgress,
                fee = fee,
                feeError = feeError,
                sendInProgress = sendInProgress,
                sendResult = sendResult,
            )
        }
    }

    private fun refreshFee() {
        val sendRecipientForKit1 = sendRecipientForKit
        val sendAmount1 = sendAmount
        val sendAmountForKit1 = sendAmountForKit

        refreshFeeJob?.cancel()
        refreshFeeJob = viewModelScope.launch(Dispatchers.Default) {
            feeError = null
            feeEstimateInProgress = true
            emitState()

            if (sendRecipientForKit1 != null && sendAmountForKit1 != null && sendAmount1 != null) {
                try {
                    val estimateFee = when (sendType) {
                        is SendType.Jetton -> {
                            val amount = sendAmount1.movePointRight(sendType.decimals).toBigInteger()
                            tonKit.estimateFee(sendType.walletAddress, sendRecipientForKit1, amount, null)
                        }
                        SendType.Ton -> {
                            tonKit.estimateFee(sendRecipientForKit1, sendAmountForKit1, null)
                        }
                    }

                    ensureActive()
                    fee = estimateFee.toBigDecimal(9)
                } catch (_: CancellationException) {
                } catch (e: Throwable) {
                    feeError = e
                    fee = null
                }
            } else {
                ensureActive()
                fee = null
            }
            feeEstimateInProgress = false
            emitState()
        }
    }
}

data class SendUiState(
    val balance: BigDecimal?,
    val feeEstimateInProgress: Boolean,
    val fee: BigDecimal?,
    val feeError: Throwable?,
    val sendInProgress: Boolean,
    val sendResult: String
) {
    val sendEnabled = fee != null && !sendInProgress
}


