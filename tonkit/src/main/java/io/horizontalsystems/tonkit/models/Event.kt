package io.horizontalsystems.tonkit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.horizontalsystems.tonkit.Address
import io.tonapi.models.AccountEvent

@Entity
data class Event(
    @PrimaryKey
    val id: String,
    val lt: Long,
    val timestamp: Long,
    val scam: Boolean,
    val inProgress: Boolean,
    val extra: Long,
    val actions: List<Action>
) {
    fun tags(address: Address): List<Tag> {
        val tags = mutableListOf<Tag>()
        for (action in actions) {
            when (action.type) {
                Action.Type.TonTransfer -> {
                    val tonTransfer = action.tonTransfer ?: continue

                    if (tonTransfer.sender.address == address) {
                        tags.add(
                            Tag(
                                id,
                                Tag.Type.Outgoing,
                                Tag.Platform.Native,
                                addresses = listOf(tonTransfer.recipient.address)
                            )
                        )
                    }

                    if (tonTransfer.recipient.address == address) {
                        tags.add(
                            Tag(
                                id,
                                Tag.Type.Incoming,
                                Tag.Platform.Native,
                                addresses = listOf(tonTransfer.sender.address)
                            )
                        )
                    }
                }

                Action.Type.JettonTransfer -> {
                    val jettonTransfer = action.jettonTransfer ?: continue
                    val sender = jettonTransfer.sender
                    val recipient = jettonTransfer.recipient

                    if (sender?.address == address) {
                        tags.add(
                            Tag(
                                id,
                                Tag.Type.Outgoing,
                                Tag.Platform.Jetton,
                                jettonTransfer.jetton.address,
                                listOfNotNull(recipient?.address)
                            )
                        )
                    }

                    if (recipient?.address == address) {
                        tags.add(
                            Tag(
                                id,
                                Tag.Type.Incoming,
                                Tag.Platform.Jetton,
                                jettonTransfer.jetton.address,
                                listOfNotNull(sender?.address)
                            )
                        )
                    }
                }

                Action.Type.JettonBurn -> {
                    val jettonBurn = action.jettonBurn ?: continue
                    tags.add(
                        Tag(
                            id,
                            Tag.Type.Outgoing,
                            Tag.Platform.Jetton,
                            jettonBurn.jetton.address,
                            listOf()
                        )
                    )
                }

                Action.Type.JettonMint -> {
                    val jettonMint = action.jettonMint ?: continue
                    tags.add(
                        Tag(
                            id,
                            Tag.Type.Incoming,
                            Tag.Platform.Jetton,
                            jettonMint.jetton.address,
                            listOf()
                        )
                    )
                }

                Action.Type.JettonSwap -> {
                    val jettonSwap = action.jettonSwap ?: continue
                    val jettonMasterIn = jettonSwap.jettonMasterIn
                    if (jettonMasterIn != null) {
                        tags.add(Tag(id, Tag.Type.Incoming, Tag.Platform.Jetton, jettonMasterIn.address))
                        tags.add(Tag(id, Tag.Type.Swap, Tag.Platform.Jetton, jettonMasterIn.address))
                    } else {
                        tags.add(Tag(id, Tag.Type.Incoming, Tag.Platform.Native))
                        tags.add(Tag(id, Tag.Type.Swap, Tag.Platform.Native))
                    }

                    val jettonMasterOut = jettonSwap.jettonMasterOut
                    if (jettonMasterOut != null) {
                        tags.add(Tag(id, Tag.Type.Outgoing, Tag.Platform.Jetton, jettonMasterOut.address))
                        tags.add(Tag(id, Tag.Type.Swap, Tag.Platform.Jetton, jettonMasterOut.address))
                    } else {
                        tags.add(Tag(id, Tag.Type.Outgoing, Tag.Platform.Native))
                        tags.add(Tag(id, Tag.Type.Swap, Tag.Platform.Native))
                    }
                }

                Action.Type.SmartContract -> {
                    val smartContractExec = action.smartContractExec ?: continue
                    tags.add(
                        Tag(
                            id,
                            Tag.Type.Outgoing,
                            Tag.Platform.Native,
                            addresses = listOf(smartContractExec.contract.address)
                        )
                    )
                }
                else -> Unit
            }
        }

        return tags
    }

    companion object {
        fun fromApi(event: AccountEvent) = Event(
            event.eventId,
            event.lt,
            event.timestamp,
            event.isScam,
            event.inProgress,
            event.extra,
            event.actions.map { action ->
                Action.fromApi(action)
            }
        )
    }
}

data class EventInfo(
    val events: List<Event>,
    val initial: Boolean
)

@Entity
data class EventSyncState(
    @PrimaryKey
    val id: String,
    val allSynced: Boolean
) {
    constructor(allSynced: Boolean) : this("unique_id", allSynced)
}
