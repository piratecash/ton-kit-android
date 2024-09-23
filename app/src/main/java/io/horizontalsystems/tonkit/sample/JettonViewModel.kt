package io.horizontalsystems.tonkit.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.JettonBalance
import kotlinx.coroutines.launch
import java.math.BigDecimal

class JettonViewModel(private val jettonAddressStr: String) : ViewModel() {
    private val tonKit = App.tonKit

    private var balance: BigDecimal = BigDecimal.ZERO
    private var jettonBalance: JettonBalance? = null

    var uiState by mutableStateOf(
        JettonUiState(
            balance = balance,
            jettonBalance = jettonBalance,
        )
    )
        private set

    init {
        val jettonAddress = Address.parse(jettonAddressStr)
        jettonBalance = tonKit.jettonBalanceMap[jettonAddress]

        jettonBalance?.let {
            balance = it.balance.toBigDecimal(it.jetton.decimals).stripTrailingZeros()
            emitState()
        }
    }

    private fun emitState() {
        viewModelScope.launch {
            uiState = JettonUiState(
                balance = balance,
                jettonBalance = jettonBalance
            )
        }
    }
}

data class JettonUiState(
    val balance: BigDecimal,
    val jettonBalance: JettonBalance?
)
