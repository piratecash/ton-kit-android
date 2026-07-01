package io.horizontalsystems.tonkit.core

import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.api.IApi
import io.horizontalsystems.tonkit.models.Account
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.JettonBalance
import io.horizontalsystems.tonkit.models.RawMessageBroadcastMetadata
import io.horizontalsystems.tonkit.models.RawMessageBroadcastStatus
import io.horizontalsystems.tonkit.storage.RawMessageBroadcastDao
import io.horizontalsystems.tonkit.storage.RawMessageBroadcastRecord
import io.tonapi.models.EmulateMessageToWalletRequestParamsInner
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

class RawMessageBroadcasterTest {
    @Test
    fun broadcast_transientWithMetadata_queues() = runBlocking {
        val api = FakeApi(sendError = IllegalStateException("network"))
        val dao = InMemoryRawMessageBroadcastDao()
        val broadcaster = broadcaster(api, dao)
        val rawMessage = RawMessageTestHelper.rawMessage()

        val result = broadcaster.broadcast(
            rawMessage,
            RawMessageBroadcastMetadata(
                validUntil = 200,
                senderAddress = RawMessageTestHelper.senderAddress,
                seqno = 1,
            )
        )

        assertEquals(RawMessageBroadcastStatus.Queued, result.status)
        assertEquals(1, dao.records().size)
        assertEquals(RawMessageTestHelper.senderAddress, dao.records().first().senderAddress)
        assertEquals(1, dao.records().first().seqno)
    }

    @Test
    fun broadcast_transientWithoutMetadata_throws() {
        val broadcaster = broadcaster(FakeApi(sendError = IllegalStateException("network")))

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                broadcaster.broadcast(RawMessageTestHelper.rawMessage(), null)
            }
        }
    }

    @Test
    fun broadcast_existingMessage_returnsAlreadyKnownWithoutSend() = runBlocking {
        val api = FakeApi(transactionExists = true)
        val broadcaster = broadcaster(api)

        val result = broadcaster.broadcast(RawMessageTestHelper.rawMessage(), null)

        assertEquals(RawMessageBroadcastStatus.AlreadyKnown, result.status)
        assertEquals(0, api.sendCalls)
    }

    @Test
    fun broadcast_knownSubmittedError_returnsAlreadyKnownWithoutQueue() = runBlocking {
        val api = FakeApi(sendError = IllegalStateException("external message was already imported"))
        val dao = InMemoryRawMessageBroadcastDao()
        val broadcaster = broadcaster(api, dao)

        val result = broadcaster.broadcast(RawMessageTestHelper.rawMessage(), null)

        assertEquals(RawMessageBroadcastStatus.AlreadyKnown, result.status)
        assertEquals(0, dao.records().size)
    }

    @Test
    fun broadcast_expiredMetadata_throwsWithoutQueue() {
        val dao = InMemoryRawMessageBroadcastDao()
        val broadcaster = broadcaster(FakeApi(), dao, now = 100)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                broadcaster.broadcast(
                    RawMessageTestHelper.rawMessage(),
                    RawMessageBroadcastMetadata(validUntil = 100)
                )
            }
        }
        assertEquals(0, dao.records().size)
    }

    @Test
    fun broadcast_permanentError_throwsWithoutQueue() {
        val dao = InMemoryRawMessageBroadcastDao()
        val broadcaster = broadcaster(FakeApi(sendError = IllegalStateException("invalid signature")), dao)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                broadcaster.broadcast(
                    RawMessageTestHelper.rawMessage(),
                    RawMessageBroadcastMetadata(validUntil = 200)
                )
            }
        }
        assertEquals(0, dao.records().size)
    }

    @Test
    fun broadcast_timeoutWithMetadata_queuesAndReleasesGuard() = runBlocking {
        val api = FakeApi(sendDelayMillis = 50)
        val dao = InMemoryRawMessageBroadcastDao()
        val broadcaster = broadcaster(api, dao, networkTimeoutMillis = 1)
        val metadata = RawMessageBroadcastMetadata(validUntil = 200)

        val firstResult = broadcaster.broadcast(RawMessageTestHelper.rawMessage(), metadata)
        val secondResult = broadcaster.broadcast(RawMessageTestHelper.rawMessage(), metadata)

        assertEquals(RawMessageBroadcastStatus.Queued, firstResult.status)
        assertEquals(RawMessageBroadcastStatus.Queued, secondResult.status)
        assertEquals(1, dao.records().size)
    }

    @Test
    fun retry_seqnoConsumed_deletesWithoutSend() = runBlocking {
        val api = FakeApi(currentSeqno = 2)
        val dao = InMemoryRawMessageBroadcastDao()
        val broadcaster = broadcaster(api, dao, now = 100)
        dao.insert(record(validUntil = 200))

        broadcaster.retryQueued()

        assertEquals(0, dao.records().size)
        assertEquals(0, api.sendCalls)
    }

    @Test
    fun retry_expiredRecord_deletesWithoutSend() = runBlocking {
        val api = FakeApi()
        val dao = InMemoryRawMessageBroadcastDao()
        val broadcaster = broadcaster(api, dao, now = 100)
        dao.insert(record(validUntil = 100))

        broadcaster.retryQueued()

        assertEquals(0, dao.records().size)
        assertEquals(0, api.sendCalls)
    }

    @Test
    fun retry_notDue_skipsWithoutSend() = runBlocking {
        val api = FakeApi()
        val dao = InMemoryRawMessageBroadcastDao()
        val broadcaster = broadcaster(api, dao, now = 100)
        dao.insert(record(validUntil = 200, lastSendTime = 100))

        broadcaster.retryQueued()

        assertEquals(1, dao.records().size)
        assertEquals(0, api.sendCalls)
    }

    @Test
    fun retry_transientError_updatesRetryMetadata() = runBlocking {
        val dao = InMemoryRawMessageBroadcastDao()
        val broadcaster = broadcaster(FakeApi(sendError = IllegalStateException("network")), dao, now = 100)
        dao.insert(record(validUntil = 200, retriesCount = 3, lastSendTime = 1))

        broadcaster.retryQueued()

        val record = dao.records().single()
        assertEquals(4, record.retriesCount)
        assertEquals(100, record.lastSendTime)
    }

    @Test
    fun retry_knownSubmittedError_deletesRecord() = runBlocking {
        val dao = InMemoryRawMessageBroadcastDao()
        val broadcaster = broadcaster(
            FakeApi(sendError = IllegalStateException("duplicate external message")),
            dao,
            now = 100,
        )
        dao.insert(record(validUntil = 200, lastSendTime = 1))

        broadcaster.retryQueued()

        assertEquals(0, dao.records().size)
    }

    private fun broadcaster(
        api: FakeApi,
        dao: InMemoryRawMessageBroadcastDao = InMemoryRawMessageBroadcastDao(),
        now: Long = 100,
        networkTimeoutMillis: Long = 1_000,
    ): RawMessageBroadcaster {
        return RawMessageBroadcaster(
            api = api,
            dao = dao,
            nowProvider = { now },
            retryIntervalSeconds = 1,
            networkTimeoutMillis = networkTimeoutMillis,
        )
    }

    private fun record(
        validUntil: Long,
        lastSendTime: Long = 1,
        retriesCount: Int = 0,
    ): RawMessageBroadcastRecord {
        val decoded = RawMessageUtils.decode(RawMessageTestHelper.rawMessage())
        return RawMessageBroadcastRecord(
            messageHash = decoded.messageHash,
            bocBase64 = decoded.bocBase64,
            validUntil = validUntil,
            senderAddress = RawMessageTestHelper.senderAddress,
            seqno = 1,
            firstSendTime = 1,
            lastSendTime = lastSendTime,
            retriesCount = retriesCount,
        )
    }

    private class InMemoryRawMessageBroadcastDao : RawMessageBroadcastDao {
        private val records = linkedMapOf<String, RawMessageBroadcastRecord>()

        override fun insert(record: RawMessageBroadcastRecord): Long {
            if (records.containsKey(record.messageHash)) return -1
            records[record.messageHash] = record
            return 1
        }

        override fun records(): List<RawMessageBroadcastRecord> = records.values.toList()

        override fun delete(messageHash: String) {
            records.remove(messageHash)
        }

        override fun updateRetry(messageHash: String, retriesCount: Int, lastSendTime: Long) {
            val record = records[messageHash] ?: return
            records[messageHash] = record.copy(
                retriesCount = retriesCount,
                lastSendTime = lastSendTime,
            )
        }
    }

    private class FakeApi(
        private val transactionExists: Boolean = false,
        private val sendError: Throwable? = null,
        private val currentSeqno: Int = 0,
        private val sendDelayMillis: Long = 0,
    ) : IApi {
        var sendCalls = 0
            private set

        override suspend fun send(boc: String) {
            sendCalls += 1
            if (sendDelayMillis > 0) {
                delay(sendDelayMillis)
            }
            sendError?.let { throw it }
        }

        override suspend fun transactionExistsByMessageHash(messageHash: String): Boolean = transactionExists

        override suspend fun getAccountSeqno(address: String): Int = currentSeqno

        override suspend fun getAccountSeqno(address: Address): Int = currentSeqno

        override suspend fun estimateFee(
            boc: String,
            params: List<EmulateMessageToWalletRequestParamsInner>?,
        ): BigInteger = BigInteger.ZERO

        override suspend fun getAccount(address: Address): Account = error("Not used")

        override suspend fun getAccountJettonBalances(address: Address): List<JettonBalance> = error("Not used")

        override suspend fun getEvents(
            address: Address,
            beforeLt: Long?,
            startTimestamp: Long?,
            limit: Int,
        ): List<Event> = error("Not used")

        override suspend fun getJettonInfo(address: Address): Jetton = error("Not used")

        override suspend fun getRawTime(): Int = 0
    }
}
