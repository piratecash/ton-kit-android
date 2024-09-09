package io.horizontalsystems.tonkit.models

enum class AccountStatus(val value: kotlin.String) {
    NONEXIST("nonexist"),// :/
    UNINIT("uninit"),// :/
    ACTIVE("active"),// :/
    FROZEN("frozen");// :/

    companion object {
        fun fromApi(accountStatus: io.swagger.client.models.AccountStatus): AccountStatus =
            when (accountStatus) {
                io.swagger.client.models.AccountStatus.NONEXIST -> NONEXIST
                io.swagger.client.models.AccountStatus.UNINIT -> UNINIT
                io.swagger.client.models.AccountStatus.ACTIVE -> ACTIVE
                io.swagger.client.models.AccountStatus.FROZEN -> FROZEN
            }
    }
}