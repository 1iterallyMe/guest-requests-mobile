package ru.nto.guestmanagement.mobile.ui.refresh

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Локальные проверки гейта обновлений: один запрос за раз, разблокировка после завершения и отмены. */
class RefreshGateTest {

    @Test
    fun launchReturnsNullWhilePreviousRunIsActive() = runBlocking {
        val gate = RefreshGate()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val blocker = CompletableDeferred<Unit>()
        var runs = 0

        val first = gate.launch(scope) {
            runs++
            blocker.await()
        }
        assertNotNull(first)

        val second = gate.launch(scope) { runs++ }
        assertNull(second)

        blocker.complete(Unit)
        first!!.join()
        assertEquals(1, runs)
    }

    @Test
    fun newRunIsAllowedAfterCompletion() = runBlocking {
        val gate = RefreshGate()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        var runs = 0

        gate.launch(scope) { runs++ }!!.join()

        val next = gate.launch(scope) { runs++ }
        assertNotNull(next)
        next!!.join()
        assertEquals(2, runs)
    }

    @Test
    fun cancelStopsCurrentRunAndUnblocksGate() = runBlocking {
        val gate = RefreshGate()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val blocker = CompletableDeferred<Unit>()
        var cancelled = false

        val job = gate.launch(scope) {
            try {
                blocker.await()
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        assertNotNull(job)

        gate.cancel()
        job!!.join()

        assertTrue(job.isCancelled)
        assertTrue(cancelled)

        val next = gate.launch(scope) { }
        assertNotNull(next)
        next!!.join()
    }

    @Test
    fun cancelWithoutRunningJobIsNoOp() {
        val gate = RefreshGate()
        gate.cancel()
        gate.cancel()
    }
}