package io.horizontalsystems.tonkit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.horizontalsystems.tonkit.Address

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
                    jettonSwap.jettonMasterIn?.let { jetton ->
                        tags.add(
                            Tag(
                                id,
                                Tag.Type.Incoming,
                                Tag.Platform.Jetton,
                                jetton.address,
                                listOf()
                            )
                        )
                        tags.add(
                            Tag(
                                id,
                                Tag.Type.Swap,
                                Tag.Platform.Jetton,
                                jetton.address,
                                listOf()
                            )
                        )
                    }
                    jettonSwap.jettonMasterOut?.let { jetton ->
                        tags.add(
                            Tag(
                                id,
                                Tag.Type.Outgoing,
                                Tag.Platform.Jetton,
                                jetton.address,
                                listOf()
                            )
                        )
                        tags.add(
                            Tag(
                                id,
                                Tag.Type.Swap,
                                Tag.Platform.Jetton,
                                jetton.address,
                                listOf()
                            )
                        )
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
