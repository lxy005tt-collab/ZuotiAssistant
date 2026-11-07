package `fun`.kirari.hanako.feature.overlay.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** REQUESTED means the platform accepted the call, not that a recording was verified. */
internal enum class StealthStatus { INACTIVE, CHECKING, REQUESTED, UNSUPPORTED }

internal fun aggregateStealthStatus(statuses: Collection<StealthStatus>): StealthStatus = when {
    StealthStatus.UNSUPPORTED in statuses -> StealthStatus.UNSUPPORTED
    StealthStatus.CHECKING in statuses -> StealthStatus.CHECKING
    StealthStatus.REQUESTED in statuses -> StealthStatus.REQUESTED
    else -> StealthStatus.INACTIVE
}

/** Main-thread window owners register while active and unregister when detached. */
internal object OverlayStealthStatus {
    private val windows = mutableMapOf<Any, StealthStatus>()
    private val mutableStatus = MutableStateFlow(StealthStatus.INACTIVE)
    val status = mutableStatus.asStateFlow()

    fun report(owner: Any, status: StealthStatus): Boolean {
        val previous = mutableStatus.value
        windows[owner] = status
        mutableStatus.value = aggregateStealthStatus(windows.values)
        return previous != StealthStatus.UNSUPPORTED && mutableStatus.value == StealthStatus.UNSUPPORTED
    }

    fun remove(owner: Any) {
        windows.remove(owner)
        mutableStatus.value = aggregateStealthStatus(windows.values)
    }
}
