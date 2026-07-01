package io.horizontalsystems.tonkit.core

import io.horizontalsystems.tonkit.api.IApi
import io.horizontalsystems.tonkit.models.RawMessageBroadcastMetadata
import io.horizontalsystems.tonkit.models.RawMessageBroadcastResult
import io.horizontalsystems.tonkit.models.RawMessageBroadcastStatus
import io.horizontalsystems.tonkit.storage.RawMessageBroadcastDao
import io.horizontalsystems.tonkit.storage.RawMessageBroadcastRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal class RawMessageBroadcaster(
    private val api: IApi,
    private val dao: RawMessageBroadcastDao,
    private val nowProvider: () -> Long = { System.currentTimeMillis() / MILLIS_IN_SECOND },
    private val retryIntervalSeconds: Long = DEFAULT_RETRY_INTERVAL_SECONDS,
    private val networkTimeoutMillis: Long = DEFAULT_NETWORK_TIMEOUT_MILLIS,
) {
    private val lock = Any()
    private val inFlight = mutableSetOf<String>()
    private var retryRunning = false

    suspend fun broadcast(
        rawMessage: ByteArray,
        metadata: RawMessageBroadcastMetadata?,
    ): RawMessageBroadcastResult {
        val decoded = RawMessageUtils.decode(rawMessage)
        val effectiveMetadata = metadata?.withDecoded(decoded)

        check(markInFlight(decoded.messageHash)) { "Raw message broadcast already in progress" }

        return try {
            broadcastDecoded(decoded, effectiveMetadata)
        } finally {
            clearInFlight(decoded.messageHash)
        }
    }

    suspend fun retryQueued() {
        if (!beginRetry()) return

        try {
            dao.records().forEach { retry(it) }
        } finally {
            endRetry()
        }
    }

    suspend fun transactionExists(messageHash: String): Boolean {
        return withNetworkTimeout { api.transactionExistsByMessageHash(messageHash) }
    }

    private suspend fun broadcastDecoded(
        decoded: DecodedRawMessage,
        metadata: EffectiveMetadata?,
    ): RawMessageBroadcastResult {
        return try {
            if (transactionExists(decoded.messageHash) || metadata.isSeqnoConsumed()) {
                dao.delete(decoded.messageHash)
                return alreadyKnown(decoded.messageHash)
            }

            if (metadata?.isExpired() == true) {
                throw RawMessageExpiredException()
            }

            withNetworkTimeout { api.send(decoded.bocBase64) }
            dao.delete(decoded.messageHash)
            submitted(decoded.messageHash)
        } catch (error: TimeoutCancellationException) {
            handleBroadcastError(error, decoded, metadata)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            handleBroadcastError(error, decoded, metadata)
        }
    }

    private suspend fun retry(record: RawMessageBroadcastRecord) {
        val now = nowProvider()
        if (record.validUntil <= now) {
            dao.delete(record.messageHash)
            return
        }

        if (record.lastSendTime + retryIntervalSeconds > now) return
        if (!markInFlight(record.messageHash)) return

        try {
            if (transactionExists(record.messageHash) || record.isSeqnoConsumed()) {
                dao.delete(record.messageHash)
                return
            }

            withNetworkTimeout { api.send(record.bocBase64) }
            dao.delete(record.messageHash)
        } catch (error: TimeoutCancellationException) {
            handleRetryError(error, record)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            handleRetryError(error, record)
        } finally {
            clearInFlight(record.messageHash)
        }
    }

    private fun handleBroadcastError(
        error: Throwable,
        decoded: DecodedRawMessage,
        metadata: EffectiveMetadata?,
    ): RawMessageBroadcastResult {
        if (error.isKnownSubmitted()) {
            dao.delete(decoded.messageHash)
            return alreadyKnown(decoded.messageHash)
        }

        if (error.isPermanent()) {
            dao.delete(decoded.messageHash)
            throw error
        }

        if (metadata == null || metadata.isExpired()) throw error

        enqueue(decoded, metadata)
        return RawMessageBroadcastResult(decoded.messageHash, RawMessageBroadcastStatus.Queued)
    }

    private fun handleRetryError(error: Throwable, record: RawMessageBroadcastRecord) {
        if (error.isKnownSubmitted() || error.isPermanent() || record.validUntil <= nowProvider()) {
            dao.delete(record.messageHash)
            return
        }

        dao.updateRetry(
            messageHash = record.messageHash,
            retriesCount = record.retriesCount + 1,
            lastSendTime = nowProvider(),
        )
    }

    private fun enqueue(decoded: DecodedRawMessage, metadata: EffectiveMetadata) {
        val now = nowProvider()
        dao.insert(
            RawMessageBroadcastRecord(
                messageHash = decoded.messageHash,
                bocBase64 = decoded.bocBase64,
                validUntil = metadata.validUntil,
                senderAddress = metadata.senderAddress,
                seqno = metadata.seqno,
                firstSendTime = now,
                lastSendTime = now,
                retriesCount = 0,
            )
        )
    }

    private suspend fun EffectiveMetadata?.isSeqnoConsumed(): Boolean {
        val metadata = this ?: return false
        val senderAddress = metadata.senderAddress ?: return false
        val seqno = metadata.seqno ?: return false

        return withNetworkTimeout { api.getAccountSeqno(senderAddress) } > seqno
    }

    private suspend fun RawMessageBroadcastRecord.isSeqnoConsumed(): Boolean {
        val senderAddress = senderAddress ?: return false
        val seqno = seqno ?: return false

        return withNetworkTimeout { api.getAccountSeqno(senderAddress) } > seqno
    }

    private fun RawMessageBroadcastMetadata.withDecoded(decoded: DecodedRawMessage): EffectiveMetadata {
        return EffectiveMetadata(
            validUntil = validUntil,
            senderAddress = senderAddress ?: decoded.senderAddress,
            seqno = seqno ?: decoded.seqno,
        )
    }

    private fun EffectiveMetadata.isExpired(): Boolean {
        return validUntil <= nowProvider()
    }

    private suspend fun <T> withNetworkTimeout(block: suspend () -> T): T {
        return withTimeout(networkTimeoutMillis) { block() }
    }

    private fun submitted(messageHash: String): RawMessageBroadcastResult {
        return RawMessageBroadcastResult(messageHash, RawMessageBroadcastStatus.Submitted)
    }

    // The message hash was already found on-chain (or its seqno already consumed) before we
    // even attempted to send it. TON has no stable tx hash for an external message prior to
    // inclusion, so this check — and the resulting status — is keyed on the message hash only.
    private fun alreadyKnown(messageHash: String): RawMessageBroadcastResult {
        return RawMessageBroadcastResult(messageHash, RawMessageBroadcastStatus.AlreadyKnown)
    }

    private fun Throwable.isKnownSubmitted(): Boolean {
        return messageText().containsAny(knownSubmittedMessages)
    }

    private fun Throwable.isPermanent(): Boolean {
        return this is RawMessageExpiredException || messageText().containsAny(permanentMessages)
    }

    private fun Throwable.messageText(): String {
        return listOfNotNull(message, cause?.message).joinToString(" ").lowercase()
    }

    private fun String.containsAny(patterns: List<String>): Boolean {
        return patterns.any(::contains)
    }

    private fun markInFlight(messageHash: String): Boolean = synchronized(lock) {
        inFlight.add(messageHash)
    }

    private fun clearInFlight(messageHash: String) = synchronized(lock) {
        inFlight.remove(messageHash)
    }

    private fun beginRetry(): Boolean = synchronized(lock) {
        if (retryRunning) {
            false
        } else {
            retryRunning = true
            true
        }
    }

    private fun endRetry() = synchronized(lock) {
        retryRunning = false
    }

    private data class EffectiveMetadata(
        val validUntil: Long,
        val senderAddress: String?,
        val seqno: Int?,
    )

    companion object {
        private const val MILLIS_IN_SECOND = 1000L
        private const val DEFAULT_RETRY_INTERVAL_SECONDS = 60L
        private const val DEFAULT_NETWORK_TIMEOUT_MILLIS = 30_000L

        private val knownSubmittedMessages = listOf(
            "already",
            "duplicate",
            "external message was already imported",
        )

        private val permanentMessages = listOf(
            "expired",
            "valid_until",
            "seqno",
            "signature",
            "invalid boc",
            "invalid message",
        )
    }
}

private class RawMessageExpiredException : IllegalStateException("Raw message is expired")
