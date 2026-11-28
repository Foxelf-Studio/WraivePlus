package moye.wear.wraive.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.wear.wraive.compat.DeviceCompatibility
import moye.wear.wraive.ui.tr
import java.io.File

class WearFilePickerLauncher internal constructor(private val open: (Array<String>) -> Unit) {
    fun launch(mimeTypes: Array<String>) = open(mimeTypes)
}

@Composable
fun rememberWearFilePicker(onFileSelected: (Uri) -> Unit): WearFilePickerLauncher =
    rememberWearFilePickerLauncher(multiple = false) { onFileSelected(it.first()) }

@Composable
fun rememberWearMultiFilePicker(onFilesSelected: (List<Uri>) -> Unit): WearFilePickerLauncher =
    rememberWearFilePickerLauncher(multiple = true, onFilesSelected = onFilesSelected)

@Composable
private fun rememberWearFilePickerLauncher(
    multiple: Boolean,
    onFilesSelected: (List<Uri>) -> Unit
): WearFilePickerLauncher {
    var requestedTypes by remember { mutableStateOf<Array<String>?>(null) }
    val context = LocalContext.current
    val watch = remember(context) { DeviceCompatibility.isWatch(context) }
    val directSingle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onFilesSelected(listOf(it)) }
    }
    val directMulti =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) onFilesSelected(uris)
        }
    requestedTypes?.let { mimeTypes ->
        WearFilePickerDialog(
            mimeTypes = mimeTypes,
            multiple = multiple,
            onDismiss = { requestedTypes = null },
            onFilesSelected = {
                requestedTypes = null
                onFilesSelected(it)
            }
        )
    }
    return WearFilePickerLauncher { types ->
        // Wear OS cannot grant all-files access, so go straight to the system picker.
        if (watch) {
            runCatching {
                if (multiple) directMulti.launch(types) else directSingle.launch(types)
            }
        } else {
            requestedTypes = types
        }
    }
}

@Composable
private fun WearFilePickerDialog(
    mimeTypes: Array<String>,
    multiple: Boolean,
    onDismiss: () -> Unit,
    onFilesSelected: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    var storageGranted by remember { mutableStateOf(hasStorageAccess(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        storageGranted = it
        if (!it) error = "需要文件读取权限才能浏览本地文件"
    }
    val storageSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        storageGranted = hasStorageAccess(context)
    }
    val systemSinglePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onFilesSelected(listOf(uri))
    }
    val systemMultiPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onFilesSelected(uris)
    }
    val openSystemPicker = {
        runCatching {
            if (multiple) systemMultiPicker.launch(mimeTypes) else systemSinglePicker.launch(mimeTypes)
        }.onFailure { error = "此设备无法打开系统文件选择器" }
        Unit
    }
    Dialog(visible = true, onDismissRequest = onDismiss) {
        if (storageGranted) {
            LocalFilePicker(
                root = remember { Environment.getExternalStorageDirectory() },
                mimeTypes = mimeTypes,
                multiple = multiple,
                onDismiss = onDismiss,
                onFilesSelected = onFilesSelected,
                onOpenSystemPicker = openSystemPicker,
                pickerError = error
            )
        } else {
            WearListScreen("选择文件", edgeButton = {
                EdgeButton(onClick = onDismiss) { Text(tr("取消")) }
            }) {
                item { WearActionButton("系统文件选择器", openSystemPicker) }
                item {
                    WearInfoCard("访问本地文件", "授权文件访问后，可直接浏览手表存储并选择文件。")
                }
                item {
                    WearActionButton("授权文件访问", {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = listOf(
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}")),
                                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            ).firstOrNull { it.resolveActivity(context.packageManager) != null }
                            if (intent == null) {
                                error = "此设备没有文件授权设置，请使用系统文件选择器或通过 ADB 授权"
                            } else {
                                storageSettings.launch(intent)
                            }
                        } else {
                            permission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }, primary = true)
                }
                error?.let { message -> item { Text(tr(message), color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}

@Composable
internal fun LocalFilePicker(
    root: File,
    mimeTypes: Array<String>,
    multiple: Boolean,
    onDismiss: () -> Unit,
    onFilesSelected: (List<Uri>) -> Unit,
    onOpenSystemPicker: (() -> Unit)? = null,
    pickerError: String? = null
) {
    var currentPath by rememberSaveable { mutableStateOf(root.absolutePath) }
    val currentDir = remember(currentPath) { File(currentPath) }
    var selectedPaths by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberTransformingLazyColumnState()
    LaunchedEffect(currentPath) {
        listState.scrollToItem(0)
        loading = true
        error = null
        files = emptyList()
        val listing = withContext(Dispatchers.IO) {
            runCatching {
                val children = currentDir.listFiles() ?: error("无法读取此目录")
                children.filter { it.isDirectory || it.matchesMimeTypes(mimeTypes) }
                    .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            }
        }
        listing.onSuccess { files = it }.onFailure { error = it.message }
        loading = false
    }
    val goBack = {
        if (currentDir != root) currentPath = currentDir.parentFile!!.absolutePath else onDismiss()
    }
    BackHandler(enabled = currentDir != root, onBack = goBack)
    WearListScreen("选择文件", scrollState = listState, edgeButton = {
        EdgeButton(
            enabled = selectedPaths.isNotEmpty(),
            onClick = { onFilesSelected(selectedPaths.map { Uri.fromFile(File(it)) }) }
        ) { Text(tr("选择 (${selectedPaths.size})")) }
    }) {
        onOpenSystemPicker?.let { open -> item { WearActionButton("系统文件选择器", open) } }
        item { WearInfoCard(currentDir.name, currentPath) }
        if (currentDir != root) {
            item { WearActionButton("上一级", goBack, icon = Icons.AutoMirrored.Filled.ArrowBack) }
        }
        when {
            loading -> item { Text(tr("正在读取文件…")) }
            files.isEmpty() && error == null -> item { Text(tr("此目录没有可选择的文件")) }
        }
        files.forEach { file ->
            item(key = file.absolutePath) {
                if (file.isDirectory) {
                    WearActionButton(file.name, { currentPath = file.absolutePath }, icon = Icons.Default.Folder)
                } else {
                    WearCheckboxButton(file.name, file.absolutePath in selectedPaths) { checked ->
                        selectedPaths = ArrayList(if (multiple) {
                            if (checked) selectedPaths + file.absolutePath else selectedPaths - file.absolutePath
                        } else {
                            if (checked) listOf(file.absolutePath) else emptyList()
                        })
                    }
                }
            }
        }
        (error ?: pickerError)?.let { message -> item { Text(tr(message), color = MaterialTheme.colorScheme.error) } }
        item { WearActionButton("取消", onDismiss) }
    }
}

private fun hasStorageAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

private fun File.matchesMimeTypes(mimeTypes: Array<String>): Boolean {
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        ?: "application/octet-stream"
    return mimeTypes.any { type ->
        type == "*/*" || type == mime || (type.endsWith("/*") && mime.startsWith(type.dropLast(1)))
    }
}
