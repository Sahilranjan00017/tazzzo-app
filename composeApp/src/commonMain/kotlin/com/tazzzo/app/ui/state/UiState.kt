package com.tazzzo.app.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/**
 * Every asynchronous screen load is one of exactly four things.
 *
 * Before this existed the app had zero try/catch blocks: the mock repositories
 * could not fail, so no screen had ever handled a timeout, a 5xx or a malformed
 * response. The moment real APIs land, that is a hang or a crash on every screen.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Failure(val error: LoadError) : UiState<Nothing>
}

/** A failure classified into something the UI can respond to sensibly. */
data class LoadError(
    val kind: Kind,
    val technical: String? = null
) {
    enum class Kind { Network, Timeout, Server, Unauthorized, Unknown }

    /** Customer-facing copy. Never leaks a stack trace or an HTTP code. */
    val message: String
        get() = when (kind) {
            Kind.Network -> "No internet connection"
            Kind.Timeout -> "That took too long"
            Kind.Server -> "Something went wrong at our end"
            Kind.Unauthorized -> "Please log in again"
            Kind.Unknown -> "Something went wrong"
        }

    val hint: String
        get() = when (kind) {
            Kind.Network -> "Check your connection and try again."
            Kind.Timeout -> "Your connection looks slow. Try once more."
            Kind.Server -> "We're on it. Please try again in a moment."
            Kind.Unauthorized -> "Your session expired."
            Kind.Unknown -> "Please try again."
        }

    val isRetryable: Boolean get() = kind != Kind.Unauthorized
}

/**
 * Maps a thrown exception to a classified error.
 *
 * Kept deliberately simple until the HTTP client lands; the Ktor-specific
 * branches (timeouts, status codes) plug in here and nowhere else.
 */
fun Throwable.toLoadError(): LoadError {
    val name = this::class.simpleName.orEmpty()
    val text = message.orEmpty()
    val kind = when {
        name.contains("Timeout", true) || text.contains("timeout", true) -> LoadError.Kind.Timeout
        name.contains("UnknownHost", true) || name.contains("IO", true) ||
            text.contains("network", true) || text.contains("connect", true) -> LoadError.Kind.Network
        text.contains("401") || text.contains("403") -> LoadError.Kind.Unauthorized
        text.contains("50") && text.contains("server", true) -> LoadError.Kind.Server
        else -> LoadError.Kind.Unknown
    }
    return LoadError(kind, technical = "$name: $text".take(200))
}

/**
 * Runs a suspending load and exposes it as [UiState], with retry.
 *
 * Usage — this single line replaces the old hand-rolled `var loading by remember`
 * pattern and gives every screen loading, empty, error and retry for free:
 *
 * ```
 * val products = rememberLoad(categoryId) { ServiceLocator.catalog.getProducts(categoryId) }
 * StateHost(products) { list -> ProductGrid(list) }
 * ```
 */
@Composable
fun <T> rememberLoad(
    vararg keys: Any?,
    isEmpty: (T) -> Boolean = { it is Collection<*> && it.isEmpty() },
    load: suspend () -> T
): LoadHandle<T> {
    val handle = remember(*keys) { LoadHandleImpl<T>() }

    LaunchedEffect(*keys, handle.attempt) {
        handle.current = UiState.Loading
        handle.current = try {
            val result = load()
            if (isEmpty(result)) UiState.Empty else UiState.Success(result)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            UiState.Failure(t.toLoadError())
        }
    }
    return handle
}

interface LoadHandle<T> {
    val state: UiState<T>
    fun retry()
}

private class LoadHandleImpl<T> : LoadHandle<T> {
    var current by mutableStateOf<UiState<T>>(UiState.Loading)
    var attempt by mutableStateOf(0)
        private set

    override val state: UiState<T> get() = current
    override fun retry() { attempt++ }
}
