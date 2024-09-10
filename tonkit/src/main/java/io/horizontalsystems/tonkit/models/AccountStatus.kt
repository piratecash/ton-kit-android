package io.horizontalsystems.tonkit.models

enum class AccountStatus(val value: kotlin.String) {
    NONEXIST("nonexist"),// :/
    UNINIT("uninit"),// :/
    ACTIVE("active"),// :/
    FROZEN("frozen");// :/

    companion object {
        fun fromApi(accountStatus: io.tonapi.models.AccountStatus) = when (accountStatus) {
            io.tonapi.models.AccountStatus.nonexist -> NONEXIST
            io.tonapi.models.AccountStatus.uninit -> UNINIT
            io.tonapi.models.AccountStatus.active -> ACTIVE
            io.tonapi.models.AccountStatus.frozen -> FROZEN
        }
    }
}