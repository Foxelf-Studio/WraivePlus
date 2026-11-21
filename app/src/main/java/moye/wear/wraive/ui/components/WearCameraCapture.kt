package moye.wear.wraive.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class WearCameraCaptureLauncher internal constructor(private val open: () -> Unit) {
    fun launch() = open()
}

/**
 * Captures a photo with the system camera and hands back the [Uri] of the saved file.
 *
 * Photos live in `filesDir/camera` so a persisted draft can still read them after a process
 * restart; [deleteCapturedPhoto] removes them once the draft is sent or cleared.
 */
@Composable
fun rememberWearCameraCapture(
    onPhotoCaptured: (Uri) -> Unit,
    onError: (String) -> Unit
): WearCameraCaptureLauncher {
    val context = LocalContext.current
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = pendingUri
        pendingUri = null
        if (success && captured != null) {
            onPhotoCaptured(Uri.parse(captured))
        } else if (captured != null) {
            deleteCapturedPhoto(context, captured)
        }
    }

    fun startCamera() {
        if (pendingUri != null) return
        val uri = runCatching { createPhotoUri(context) }.getOrElse {
            onError("无法创建照片文件")
            return
        }
        pendingUri = uri.toString()
        runCatching { takePicture.launch(uri) }.onFailure {
            pendingUri = null
            deleteCapturedPhoto(context, uri.toString())
            onError("此设备没有可用的相机应用")
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else onError("需要相机权限才能拍照")
    }
    return WearCameraCaptureLauncher {
        val hasCamera = context.packageManager
            .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        when {
            !hasCamera -> onError("此设备没有相机")
            granted -> startCamera()
            else -> cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }
}

fun deleteCapturedPhoto(context: Context, uri: String) {
    val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return
    if (parsed.authority != "${context.packageName}.files") return
    val segments = parsed.pathSegments
    if (segments.size != 2 || segments[0] != CAMERA_DIR) return
    runCatching { File(File(context.filesDir, CAMERA_DIR), segments[1]).delete() }
}

private fun createPhotoUri(context: Context): Uri {
    val directory = File(context.filesDir, CAMERA_DIR).apply { mkdirs() }
    val file = File(directory, "photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

private const val CAMERA_DIR = "camera"
