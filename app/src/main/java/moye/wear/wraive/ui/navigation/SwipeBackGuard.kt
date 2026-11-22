package moye.wear.wraive.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Temporary block for the swipe-to-dismiss gesture while a full-screen dialog is showing.
 *
 * The input sheet is a platform Dialog window. If the nav host behind it also handled the
 * right-swipe, it popped the chat route while the dialog window stayed alive, leaving an
 * invisible window that swallowed every touch until the user pressed system back.
 *
 * Dialogs lock this guard while they are composed; the nav host reads [isBlocked] and
 * disables swipe-to-dismiss for that period. System back keeps working because the dialog
 * consumes it first.
 */
object SwipeBackGuard {
    var locks by mutableIntStateOf(0)
        private set

    val isBlocked: Boolean get() = locks > 0

    fun lock() {
        locks++
    }

    fun unlock() {
        if (locks > 0) locks--
    }
}
