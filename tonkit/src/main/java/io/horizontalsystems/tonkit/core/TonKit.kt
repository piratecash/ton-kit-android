package io.horizontalsystems.tonkit.core

class TonKit {


    enum class WalletType {
        full(KeyPair),
        watch(Address)
    }

    enum WalletVersion {
        case v3
        case v4
        case v5
    }

    enum SyncError: Error {
        case notStarted
    }

    enum WalletError: Error {
        case watchOnly
    }

    enum SendAmount {
        case amount(value: BigUInt)
        case max
    }
}