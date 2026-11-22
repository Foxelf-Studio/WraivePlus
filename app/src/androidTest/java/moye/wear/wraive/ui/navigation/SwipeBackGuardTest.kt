package moye.wear.wraive.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeBackGuardTest {
    @Test
    fun locksAreReferenceCounted() {
        assertFalse(SwipeBackGuard.isBlocked)
        SwipeBackGuard.lock()
        SwipeBackGuard.lock()
        assertTrue(SwipeBackGuard.isBlocked)
        SwipeBackGuard.unlock()
        assertTrue(SwipeBackGuard.isBlocked)
        SwipeBackGuard.unlock()
        assertFalse(SwipeBackGuard.isBlocked)
    }
}
