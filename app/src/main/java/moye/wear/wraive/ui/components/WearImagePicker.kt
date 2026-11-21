package moye.wear.wraive.ui.components

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.wear.wraive.ui.tr
import java.text.DateFormat
import java.util.Date

internal enum class ImagePickerStage { CLOSED, GALLERY, DENIED }

/**
 * In-window image picker state.
 *
 * The picker renders inside the input dialog instead of a second dialog window, because stacked
 * dialogs are unreliable on Wear OS (the second window can stay hidden behind the input dialog).
 */
@Stable
class WearImagePickerState internal constructor() {
    internal var stage by mutableStateOf(ImagePickerStage.CLOSED)
    internal var error by mutableStateOf<String?>(null)
    internal var launchAction: (() -> Unit)? = null
    internal var retryAction: (() -> Unit)? = null
    internal var systemPickerAction: (() -> Unit)? = null

    val isActive: Boolean get() = stage != ImagePickerStage.CLOSED

    fun launch() {
        launchAction?.invoke()
    }

    fun dismiss() {
        stage = ImagePickerStage.CLOSED
        error = null
    }
}

@Composable
fun rememberWearImagePicker(onImageSelected: (Uri) -> Unit): WearImagePickerState {
    val context = LocalContext.current
    val state = remember { WearImagePickerState() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            state.error = null
            state.stage = ImagePickerStage.GALLERY
        } else {
            state.error = "未获得图片访问权限"
            state.stage = ImagePickerStage.DENIED
        }
    }
    val systemPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            state.dismiss()
            onImageSelected(uri)
        }
    }
    SideEffect {
        state.launchAction = {
            state.error = null
            if (hasImageAccess(context)) {
                state.stage = ImagePickerStage.GALLERY
            } else {
                permissionLauncher.launch(imagePermissions())
            }
        }
        state.retryAction = { permissionLauncher.launch(imagePermissions()) }
        state.systemPickerAction = {
            state.error = null
            runCatching { systemPicker.launch(arrayOf("image/*")) }
                .onFailure { state.error = "此设备无法打开系统图片选择器" }
        }
    }
    return state
}

@Composable
fun WearImagePickerOverlay(
    state: WearImagePickerState,
    onImageSelected: (Uri) -> Unit
) {
    when (state.stage) {
        ImagePickerStage.GALLERY -> WearImageGalleryScreen(
            state = state,
            onSelect = { uri ->
                state.dismiss()
                onImageSelected(uri)
            }
        )
        ImagePickerStage.DENIED -> WearImagePermissionScreen(state)
        ImagePickerStage.CLOSED -> Unit
    }
}

@Composable
private fun WearImageGalleryScreen(
    state: WearImagePickerState,
    onSelect: (Uri) -> Unit
) {
    val context = LocalContext.current
    var images by remember { mutableStateOf<List<DeviceImage>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { queryImages(context) } }
            .onSuccess { images = it }
            .onFailure { loadError = it.message }
    }
    WearListScreen(
        title = "选择图片",
        modifier = Modifier.fillMaxSize(),
        edgeButton = { EdgeButton(onClick = state::dismiss) { Text(tr("取消")) } }
    ) {
        item { WearActionButton("系统图片选择器", { state.systemPickerAction?.invoke() }) }
        when {
            images == null -> item { Text(tr("正在读取相册…")) }
            images?.isEmpty() == true -> item {
                WearInfoCard("相册里没有图片", "手表相册里还没有图片，可以改用系统图片选择器。")
            }
        }
        images.orEmpty().forEach { image ->
            item(key = image.uri.toString()) {
                WearImageRow(image = image, onClick = { onSelect(image.uri) })
            }
        }
        (loadError ?: state.error)?.let { message ->
            item { Text(tr(message), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun WearImagePermissionScreen(state: WearImagePickerState) {
    val context = LocalContext.current
    WearListScreen(
        title = "需要图片访问权限",
        modifier = Modifier.fillMaxSize(),
        edgeButton = { EdgeButton(onClick = state::dismiss) { Text(tr("取消")) } }
    ) {
        item {
            WearInfoCard(
                "无法读取相册",
                "授权后可浏览手表相册。部分手表无法授权时，可以改用系统图片选择器。"
            )
        }
        item { WearActionButton("重新授权", { state.retryAction?.invoke() }, primary = true) }
        item { WearActionButton("打开应用设置", { openAppPermissionSettings(context) }) }
        item { WearActionButton("系统图片选择器", { state.systemPickerAction?.invoke() }) }
        state.error?.let { message ->
            item { Text(tr(message), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun WearImageRow(image: DeviceImage, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AsyncImage(
            model = image.uri,
            contentDescription = image.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
        )
        Column(Modifier.weight(1f)) {
            Text(
                image.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                image.subtitle(context),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class DeviceImage(
    val uri: Uri,
    val name: String,
    val modifiedAt: Long,
    val sizeBytes: Long
)

private fun queryImages(context: Context): List<DeviceImage> {
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.SIZE
    )
    return context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        buildList {
            while (cursor.moveToNext() && size < MAX_IMAGES) {
                val id = cursor.getLong(idColumn)
                add(
                    DeviceImage(
                        uri = ContentUris.withAppendedId(collection, id),
                        name = cursor.getString(nameColumn) ?: "IMG_$id",
                        modifiedAt = cursor.getLong(dateColumn) * 1000L,
                        sizeBytes = cursor.getLong(sizeColumn)
                    )
                )
            }
        }
    }.orEmpty()
}

private fun DeviceImage.subtitle(context: Context): String {
    val size = Formatter.formatShortFileSize(context, sizeBytes)
    val date = if (modifiedAt > 0) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(modifiedAt))
    } else {
        ""
    }
    return listOf(date, size).filter(String::isNotBlank).joinToString(" · ")
}

private fun hasImageAccess(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
        return true
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED
    }
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
}

private fun imagePermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

private fun openAppPermissionSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private const val MAX_IMAGES = 200
