package moye.wear.wraive.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.wear.wraive.model.Attachment
import moye.wear.wraive.ui.tr
import kotlin.math.max

/**
 * Renders an image attachment.
 *
 * Attachments read from the picker are stored as `data:image/...;base64,` URIs, which Coil cannot
 * fetch. Those are decoded directly; other URIs keep using Coil.
 */
@Composable
internal fun AttachmentImage(
    attachment: Attachment,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val uri = attachment.uri
    if (!uri.startsWith(DATA_URI_PREFIX)) {
        AsyncImage(
            model = uri,
            contentDescription = attachment.name,
            modifier = modifier,
            contentScale = contentScale
        )
        return
    }
    val cacheKey = attachment.id.ifBlank { uri.hashCode().toString() }
    var state by remember(cacheKey) {
        mutableStateOf<ImageState>(
            AttachmentImageCache.get(cacheKey)?.let(ImageState::Ready) ?: ImageState.Loading
        )
    }
    LaunchedEffect(cacheKey) {
        if (state is ImageState.Loading) {
            val decoded = withContext(DecodeDispatcher) { decodeDataUriImage(uri) }
            if (decoded != null) AttachmentImageCache.put(cacheKey, decoded)
            state = if (decoded != null) ImageState.Ready(decoded) else ImageState.Failed
        }
    }
    when (val current = state) {
        is ImageState.Ready -> Image(
            bitmap = current.bitmap,
            contentDescription = attachment.name,
            modifier = modifier,
            contentScale = contentScale
        )
        ImageState.Loading -> Box(
            modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        ImageState.Failed -> Box(
            modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                tr("图片无法显示"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private sealed interface ImageState {
    data object Loading : ImageState
    data object Failed : ImageState
    data class Ready(val bitmap: ImageBitmap) : ImageState
}

private fun decodeDataUriImage(uri: String): ImageBitmap? = runCatching {
    val comma = uri.indexOf(',')
    require(comma > 0) { "无效的图片数据" }
    val bytes = Base64.decode(uri.substring(comma + 1), Base64.DEFAULT)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODE_DIMENSION) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}.getOrNull()

private object AttachmentImageCache {
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 2
    }

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, value: ImageBitmap) {
        cache.put(key, value)
    }
}

private val DecodeDispatcher = Dispatchers.IO.limitedParallelism(2)

private const val DATA_URI_PREFIX = "data:image/"
private const val MAX_DECODE_DIMENSION = 1024
private const val CACHE_BYTES = 16 * 1024 * 1024
