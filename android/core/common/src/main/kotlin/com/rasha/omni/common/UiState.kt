package com.rasha.omni.common

/**
 * The four honest states every OMNI screen can be in. Stale content is still content: it carries the age of the data so the screen can say so
 * ("Updated 3 h ago") instead of pretending it is fresh, and a failed refresh never replaces content that is still usable.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Empty(val message: String) : UiState<Nothing>
    data class Error(val message: String, val canRetry: Boolean = true) : UiState<Nothing>
    data class Content<T>(val data: T, val fetchedAtMs: Long = 0L, val refreshing: Boolean = false, val lastError: String? = null) : UiState<T>
}

/** Plain age text for stale-data labels. */
fun ageText(ms: Long): String {
    if (ms < 0) return ""
    val m = ms / 60_000
    return when {
        m < 2 -> "just now"
        m < 60 -> "$m min ago"
        m < 24 * 60 -> "${m / 60} h ago"
        else -> "a day or more ago"
    }
}
