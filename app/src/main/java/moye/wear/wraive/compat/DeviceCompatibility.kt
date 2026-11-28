package moye.wear.wraive.compat

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object DeviceCompatibility {
    val isXiaomiWatch5: Boolean = Build.MODEL in setOf("M2505W1", "M2501W1")
    val supportsSwipeToDismiss: Boolean = !isXiaomiWatch5

    /** Wear OS does not expose all-files access or the media permissions to users. */
    fun isWatch(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
}
