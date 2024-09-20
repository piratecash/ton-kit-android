package io.horizontalsystems.tonkit.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.core.TonKit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.math.BigDecimal

class SendViewModel : ViewModel() {
    private val tonKit = App.tonKit
    private var account = tonKit.account
    private val balance: BigDecimal?
        get() = account?.balance?.toBigDecimal()?.movePointLeft(9)

    private var feeEstimateInProgress = false
    private var fee: BigDecimal? = null
    private var feeError: Throwable? = null

    private var sendRecipient: String? = null
    private var sendAmount: BigDecimal? = null

    private val sendRecipientForKit: FriendlyAddress?
        get() = sendRecipient?.let { FriendlyAddress.parse(it) }
    private val sendAmountForKit: TonKit.SendAmount?
        get() = sendAmount?.let {
            if (it.compareTo(balance) == 0) {
                TonKit.SendAmount.Max
            } else {
                TonKit.SendAmount.Amount(it.movePointRight(9).toBigInteger())
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
            val sendAmountForKit1 = sendAmountForKit

            if (sendRecipientForKit1 != null && sendAmountForKit1 != null) {
                sendInProgress = true
                emitState()

                try {
                    tonKit.send(sendRecipientForKit1, sendAmountForKit1, null)
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
        val sendAmountForKit1 = sendAmountForKit

        refreshFeeJob?.cancel()
        refreshFeeJob = viewModelScope.launch(Dispatchers.Default) {
            feeError = null
            feeEstimateInProgress = true
            emitState()

            if (sendRecipientForKit1 != null && sendAmountForKit1 != null) {
                try {
                    val estimateFee = tonKit.estimateFee(sendRecipientForKit1, sendAmountForKit1, null)
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


