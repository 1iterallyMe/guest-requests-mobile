package ru.nto.guestmanagement.mobile.ui.refresh

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Вызывает [onResume] при каждом возвращении на экран: первое открытие, возврат из деталей/формы и из фона.
 * `LifecycleResumeEffect` срабатывает и при первой композиции, если владелец жизненного цикла уже в RESUMED.
 */
@Composable
fun ResumeEffect(onResume: () -> Unit) {
    // Колбэк может меняться между рекомпозициями, а эффект регистрируется один раз.
    val currentOnResume by rememberUpdatedState(onResume)
    LifecycleResumeEffect(Unit) {
        currentOnResume()
        onPauseOrDispose { }
    }
}
