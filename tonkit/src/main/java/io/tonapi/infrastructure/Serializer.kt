package io.tonapi.infrastructure

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.EnumJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.tonapi.models.AccStatusChange
import io.tonapi.models.AccountStatus
import io.tonapi.models.Action
import io.tonapi.models.AuctionBidAction
import io.tonapi.models.BlockchainAccountInspect
import io.tonapi.models.BouncePhaseType
import io.tonapi.models.ComputeSkipReason
import io.tonapi.models.InscriptionBalance
import io.tonapi.models.InscriptionMintAction
import io.tonapi.models.InscriptionTransferAction
import io.tonapi.models.JettonSwapAction
import io.tonapi.models.JettonVerificationType
import io.tonapi.models.Message
import io.tonapi.models.NftCollection
import io.tonapi.models.NftItem
import io.tonapi.models.NftPurchaseAction
import io.tonapi.models.PoolImplementationType
import io.tonapi.models.Refund
import io.tonapi.models.TransactionType
import io.tonapi.models.TrustType
import io.tonapi.models.TvmStackRecord

object Serializer {
    @JvmStatic
    val moshiBuilder: Moshi.Builder by lazy {
        val builder = Moshi.Builder()

        val enumClasses = listOf(
            TransactionType::class.java,
            TrustType::class.java,
            AccountStatus::class.java,
            AuctionBidAction.AuctionType::class.java,
            TvmStackRecord.Type::class.java,
            AccStatusChange::class.java,
            Action.Status::class.java,
            Action.Type::class.java,
            BlockchainAccountInspect.Compiler::class.java,
            BouncePhaseType::class.java,
            ComputeSkipReason::class.java,
            InscriptionBalance.Type::class.java,
            InscriptionMintAction.Type::class.java,
            InscriptionTransferAction.Type::class.java,
            JettonSwapAction.Dex::class.java,
            JettonVerificationType::class.java,
            Message.MsgType::class.java,
            NftCollection.ApprovedBy::class.java,
            NftItem.ApprovedBy::class.java,
            NftPurchaseAction.AuctionType::class.java,
            PoolImplementationType::class.java,
            Refund.Type::class.java,
        )

        enumClasses.forEach {
            builder.add(it, createEnumJsonAdapter(it))
        }

        builder
            .add(OffsetDateTimeAdapter())
            .add(LocalDateTimeAdapter())
            .add(LocalDateAdapter())
            .add(UUIDAdapter())
            .add(ByteArrayAdapter())
            .add(URIAdapter())
            .add(KotlinJsonAdapterFactory())
            .add(BigDecimalAdapter())
            .add(BigIntegerAdapter())
    }

    @JvmStatic
    val moshi: Moshi by lazy {
        moshiBuilder.build()
    }
}

fun <T : Enum<T>> createEnumJsonAdapter(enumType: Class<T>): EnumJsonAdapter<T> {
    val enumConstants = enumType.enumConstants ?: throw IllegalArgumentException("Not Enum")

    return EnumJsonAdapter.create(enumType).withUnknownFallback(enumConstants.last())
}
