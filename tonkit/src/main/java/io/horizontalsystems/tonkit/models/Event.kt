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

        actions.forEach { action ->
            val actionType = action.type
            when (actionType) {
                is Action.Type.TonTransfer -> {
                    val typedAction = actionType.action

                    if (typedAction.sender.address == address) {
                        tags.add(
                            Tag(
                                id,
                                Tag.Type.Outgoing,
                                Tag.Platform.Native,
                                addresses = listOf(typedAction.recipient.address)
                            )
                        )
                    }

                    if (typedAction.recipient.address == address) {
                        tags.add(
                            Tag(
                                id,
                                Tag.Type.Incoming,
                                Tag.Platform.Native,
                                addresses = listOf(typedAction.sender.address)
                            )
                        )
                    }
                }
                is Action.Type.JettonTransfer -> {
                    val typedAction = actionType.action
                    val sender = typedAction.sender
                    val recipient = typedAction.recipient

                    if (sender?.address == address) {
                        tags.add(
                            Tag(
                                id,
                                Tag.Type.Outgoing,
                                Tag.Platform.Jetton,
                                typedAction.jetton.address,
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
                                typedAction.jetton.address,
                                listOfNotNull(sender?.address)
                            )
                        )
                    }
                }
                is Action.Type.JettonBurn -> {
                    val typedAction = actionType.action
                    tags.add(
                        Tag(
                            id,
                            Tag.Type.Outgoing,
                            Tag.Platform.Jetton,
                            typedAction.jetton.address,
                            listOf()
                        )
                    )
                }
                is Action.Type.JettonMint -> {
                    val typedAction = actionType.action
                    tags.add(Tag(id, Tag.Type.Incoming, Tag.Platform.Jetton, typedAction.jetton.address, listOf()))
                }
                is Action.Type.JettonSwap -> {
                    val typedAction = actionType.action
                    typedAction.jettonMasterIn?.let { jetton ->
                        tags.add(Tag(id, Tag.Type.Incoming, Tag.Platform.Jetton, jetton.address, listOf()))
                        tags.add(Tag(id, Tag.Type.Swap, Tag.Platform.Jetton, jetton.address, listOf()))
                    }
                    typedAction.jettonMasterIn?.let { jetton ->
                        tags.add(Tag(id, Tag.Type.Outgoing, Tag.Platform.Jetton, jetton.address, listOf()))
                        tags.add(Tag(id, Tag.Type.Swap, Tag.Platform.Jetton, jetton.address, listOf()))
                    }
                }
                is Action.Type.SmartContract -> {
                    val typedAction = actionType.action
                    tags.add(Tag(id, Tag.Type.Outgoing, Tag.Platform.Native, addresses = listOf(typedAction.contract.address)))
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

data class EventSyncState(
    val id: String,
    val allSynced: Boolean
) {
    constructor(allSynced: Boolean) : this("unique_id", allSynced)
}
