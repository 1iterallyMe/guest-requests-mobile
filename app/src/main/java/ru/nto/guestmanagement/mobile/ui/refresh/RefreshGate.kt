package ru.nto.guestmanagement.mobile.ui.refresh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Защита от повторных обновлений: пока запущенное через [launch] обновление выполняется, новый запуск игнорируется
 * (возвращает `null`), второй запрос не создаётся. [cancel] прерывает текущее обновление (например, при смене фильтра).
 *
 * Job создаётся лениво и стартует уже после регистрации обработчика завершения: иначе мгновенно завершившееся
 * обновление (`Dispatchers.Main.immediate`) успело бы «потеряться» и гейт навсегда остался бы занятым.
 */
class RefreshGate {
    private var currentJob: Job? = null

    fun launch(scope: CoroutineScope, block: suspend () -> Unit): Job? = synchronized(this) {
        if (currentJob != null) return@synchronized null
        val job = scope.launch(start = CoroutineStart.LAZY) { block() }
        currentJob = job
        job.invokeOnCompletion { if (currentJob === job) currentJob = null }
        job.start()
        job
    }

    fun cancel() {
        val job = synchronized(this) {
            val running = currentJob
            currentJob = null
            running
        }
        job?.cancel()
    }
}
