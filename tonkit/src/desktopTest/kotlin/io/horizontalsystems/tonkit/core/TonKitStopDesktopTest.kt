package io.horizontalsystems.tonkit.core

import co.touchlab.kermit.Logger
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FixtureApi
import io.horizontalsystems.tonkit.PlatformContext
import io.horizontalsystems.tonkit.TonV2Fixture
import io.horizontalsystems.tonkit.api.IApiListener
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.storage.EventDao
import io.horizontalsystems.tonkit.storage.KitDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

/** After [TonKit.stop] returns, nothing the kit started touches the database any more. */
@OptIn(ExperimentalCoroutinesApi::class)
class TonKitStopDesktopTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    private val listener = FakeListener()
    private val completionGate = CompletableDeferred<Unit>()
    private val eventChecks = AtomicInteger()
    private val eventChecksInFlight = AtomicInteger()
    private val database by lazy {
        KitDatabase.getInstance(PlatformContext(tmp.root), "stop-MainNet", ByteArray(32))
    }
    private val api = FixtureApi(TonV2Fixture.jettonBalances)
    private val logger = Logger.withTag("TonKitStopDesktopTest")
    private val address = TonV2Fixture.ownerAddress
    private val accountManager by lazy { AccountManager(address, api, database.accountDao(), null, logger) }
    private val jettonManager by lazy { JettonManager(address, api, database.jettonDao(), emptyList(), logger) }
    private val eventManager by lazy { EventManager(address, api, GatedEventDao(database.eventDao()), logger) }
    private val rawMessageBroadcaster by lazy { RawMessageBroadcaster(api, database.rawMessageBroadcastDao()) }
    private val transactionSigner by lazy { TransactionSigner(TonKit.getTonApi(Network.MainNet)) }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun stop_eventBeingHandled_waitsForItAndStopsCollecting() = runTest(timeout = 30.seconds) {
        val kit = kit(StandardTestDispatcher(testScheduler))
        kit.startListener()
        emitAndHandle("first")
        assertEquals(1, eventChecks.get())

        val stopping = async { kit.stop() }
        runCurrent()
        assertFalse("stop returned while the event was still being handled", stopping.isCompleted)

        completionGate.complete(Unit)
        runCurrent()
        assertTrue(stopping.isCompleted)
        assertEquals(1, listener.stops)

        emitAndHandle("after-stop")
        assertEquals(1, eventChecks.get())
    }

    @Test
    fun stop_previousStopCancelledWhileWaiting_waitsForEventBeingHandled() = runTest(timeout = 30.seconds) {
        val kit = kit(StandardTestDispatcher(testScheduler))
        kit.startListener()
        emitAndHandle("first")

        val interruptedStop = launch { kit.stop() }
        runCurrent()
        interruptedStop.cancel()
        runCurrent()
        assertTrue(interruptedStop.isCancelled)

        val stopping = async {
            kit.stop()
            eventChecksInFlight.get()
        }
        runCurrent()
        assertFalse("stop returned while the event was still being handled", stopping.isCompleted)

        completionGate.complete(Unit)
        assertEquals("event checks in flight after stop returned", 0, stopping.await())
        emitAndHandle("after-stop")
        assertEquals(1, eventChecks.get())
    }

    @Test
    fun startListener_afterStop_handlesEventsAgain() = runTest(timeout = 30.seconds) {
        completionGate.complete(Unit)
        val kit = kit(StandardTestDispatcher(testScheduler))
        kit.startListener()
        kit.stop()

        kit.startListener()
        emitAndHandle("restarted")

        assertEquals(1, eventChecks.get())
        assertEquals(2, listener.starts)
        kit.stop()
    }

    @Test
    fun startListener_duringPendingStop_handlesEventsAfterStopCompletes() = runTest(timeout = 30.seconds) {
        val kit = kit(StandardTestDispatcher(testScheduler))
        kit.startListener()
        emitAndHandle("first")

        val stopping = launch { kit.stop() }
        runCurrent()
        val starting = launch { kit.startListener() }
        runCurrent()
        completionGate.complete(Unit)
        runCurrent()
        assertTrue(stopping.isCompleted && starting.isCompleted)

        emitAndHandle("after-restart")
        assertEquals(2, eventChecks.get())
        assertEquals(1, listener.flow.subscriptionCount.value)
        kit.stop()
        runCurrent()
        assertEquals(0, listener.flow.subscriptionCount.value)
    }

    @Test
    fun startListener_afterCancelledStop_handlesEvents() = runTest(timeout = 30.seconds) {
        val kit = kit(StandardTestDispatcher(testScheduler))
        kit.startListener()
        emitAndHandle("first")

        val interruptedStop = launch { kit.stop() }
        runCurrent()
        interruptedStop.cancel()
        runCurrent()
        val starting = launch { kit.startListener() }
        runCurrent()
        assertEquals("collectors while the cancelled one still runs", 1, listener.flow.subscriptionCount.value)
        completionGate.complete(Unit)
        runCurrent()
        assertTrue(starting.isCompleted)

        emitAndHandle("after-restart")
        assertEquals(2, eventChecks.get())
        assertEquals(1, listener.flow.subscriptionCount.value)
        kit.stop()
        runCurrent()
        assertEquals(0, listener.flow.subscriptionCount.value)
    }

    @Test
    fun startListener_calledTwice_handlesEachEventOnce() = runTest(timeout = 30.seconds) {
        completionGate.complete(Unit)
        val kit = kit(StandardTestDispatcher(testScheduler))
        kit.startListener()
        kit.startListener()

        emitAndHandle("once")

        assertEquals(1, eventChecks.get())
        kit.stop()
    }

    @Test
    fun startListener_concurrentCalls_oneCollectorRunsAndStopEndsIt() {
        completionGate.complete(Unit)
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val startRound = CyclicBarrier(CONCURRENT_STARTERS + 1)
        val roundStarted = CyclicBarrier(CONCURRENT_STARTERS + 1)
        var kit = kit(dispatcher)
        repeat(CONCURRENT_STARTERS) {
            thread(isDaemon = true) {
                repeat(CONCURRENT_START_ROUNDS) {
                    startRound.await()
                    runBlocking { kit.startListener() }
                    roundStarted.await()
                }
            }
        }
        try {
            repeat(CONCURRENT_START_ROUNDS) { round ->
                val roundListener = FakeListener()
                kit = kit(dispatcher, roundListener)
                startRound.await()
                roundStarted.await()
                executor.drain()
                assertEquals("collectors in round $round", 1, roundListener.flow.subscriptionCount.value)

                runBlocking { kit.stop() }
                executor.drain()
                assertEquals("collectors after stop in round $round", 0, roundListener.flow.subscriptionCount.value)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    // Every collector started so far has run up to its subscription once the queued no-op completes.
    private fun ExecutorService.drain() {
        submit {}.get(10, TimeUnit.SECONDS)
    }

    private fun kit(dispatcher: CoroutineDispatcher, apiListener: FakeListener = listener) = TonKit(
        address = address,
        apiListener = apiListener,
        accountManager = accountManager,
        jettonManager = jettonManager,
        eventManager = eventManager,
        transactionSender = null,
        rawMessageBroadcaster = rawMessageBroadcaster,
        network = Network.MainNet,
        transactionSigner = transactionSigner,
        dispatcher = dispatcher,
    )

    // The kit waits before it checks an event, so virtual time is advanced past that delay.
    private fun TestScope.emitAndHandle(eventId: String) {
        runCurrent()
        check(listener.flow.tryEmit(eventId)) { "Event $eventId was not buffered" }
        advanceTimeBy(HANDLE_DELAY_MILLIS)
        runCurrent()
    }

    // A check in flight cannot be cancelled, like a Room query already running on its executor.
    private inner class GatedEventDao(delegate: EventDao) : EventDao by delegate {
        override suspend fun isEventCompleted(id: String): Boolean {
            eventChecks.incrementAndGet()
            eventChecksInFlight.incrementAndGet()
            try {
                withContext(NonCancellable) { completionGate.await() }
            } finally {
                eventChecksInFlight.decrementAndGet()
            }
            return true
        }
    }

    private class FakeListener : IApiListener {
        val flow = MutableSharedFlow<String>(extraBufferCapacity = 16)
        var starts = 0
        var stops = 0

        override val transactionFlow = flow

        override fun start(address: Address) {
            starts++
        }

        override fun stop() {
            stops++
        }
    }

    private companion object {
        const val HANDLE_DELAY_MILLIS = 5_001L
        const val CONCURRENT_START_ROUNDS = 20_000
        const val CONCURRENT_STARTERS = 8
    }
}
