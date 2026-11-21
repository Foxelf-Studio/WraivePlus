package moye.wear.wraive.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import android.util.Base64
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.wear.wraive.model.Attachment
import moye.wear.wraive.model.AttachmentKind
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

class AttachmentReader(private val context: Context) {
    suspend fun read(uri: Uri): List<Attachment> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val metadata = resolver.metadata(uri)
        when {
            metadata.mimeType.startsWith("image/") -> listOf(readImage(uri, metadata))
            metadata.mimeType == "application/pdf" -> readPdf(uri, metadata)
            metadata.mimeType.contains("wordprocessingml") ||
                metadata.name.endsWith(".docx", ignoreCase = true) -> listOf(
                readArchiveText(uri, metadata, "word/")
            )
            metadata.mimeType.contains("spreadsheetml") ||
                metadata.name.endsWith(".xlsx", ignoreCase = true) -> listOf(
                readArchiveText(uri, metadata, "xl/")
            )
            metadata.mimeType.contains("presentationml") ||
                metadata.name.endsWith(".pptx", ignoreCase = true) -> listOf(
                readArchiveText(uri, metadata, "ppt/slides/")
            )
            metadata.mimeType.startsWith("text/") || isTextFile(metadata.name) -> listOf(
                Attachment(
                    kind = AttachmentKind.TEXT,
                    name = metadata.name,
                    uri = uri.toString(),
                    mimeType = metadata.mimeType,
                    extractedText = resolver.openInputStream(uri)?.bufferedReader()?.use {
                        it.readText().take(MAX_TEXT_LENGTH)
                    },
                    sizeBytes = metadata.size
                )
            )
            else -> listOf(
                Attachment(
                    kind = AttachmentKind.DOCUMENT,
                    name = metadata.name,
                    uri = uri.toString(),
                    mimeType = metadata.mimeType,
                    sizeBytes = metadata.size
                )
            )
        }
    }

    private fun readImage(uri: Uri, metadata: FileMetadata): Attachment {
        val original = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取图片")
        val bitmap = BitmapFactory.decodeByteArray(original, 0, original.size)
            ?: error("无法解析图片")
        val oriented = applyExifOrientation(bitmap, readExifOrientation(original))
        if (oriented !== bitmap) bitmap.recycle()
        val scale = minOf(1f, MAX_IMAGE_DIMENSION.toFloat() / maxOf(oriented.width, oriented.height))
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * scale).roundToInt(),
                (oriented.height * scale).roundToInt(),
                true
            )
        } else {
            oriented
        }
        val output = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 86, output)
        if (resized !== oriented) resized.recycle()
        oriented.recycle()
        val bytes = output.toByteArray()
        return Attachment(
            kind = AttachmentKind.IMAGE,
            name = metadata.name,
            uri = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP),
            mimeType = "image/jpeg",
            sizeBytes = bytes.size.toLong()
        )
    }

    private fun readExifOrientation(bytes: ByteArray): Int = runCatching {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun readPdf(uri: Uri, metadata: FileMetadata): List<Attachment> {        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("无法读取 PDF")
        return descriptor.use { file ->
            PdfRenderer(file).use { renderer ->
                buildList {
                    val pageCount = minOf(renderer.pageCount, MAX_PDF_PAGES)
                    for (index in 0 until pageCount) {
                        renderer.openPage(index).use { page ->
                            val width = MAX_IMAGE_DIMENSION
                            val height = (width * page.height.toFloat() / page.width).roundToInt()
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val output = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 84, output)
                            bitmap.recycle()
                            val bytes = output.toByteArray()
                            add(
                                Attachment(
                                    kind = AttachmentKind.IMAGE,
                                    name = "${metadata.name} · ${index + 1}",
                                    uri = "data:image/jpeg;base64," +
                                        Base64.encodeToString(bytes, Base64.NO_WRAP),
                                    mimeType = "image/jpeg",
                                    sizeBytes = bytes.size.toLong()
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun readArchiveText(
        uri: Uri,
        metadata: FileMetadata,
        entryPrefix: String
    ): Attachment {
        val text = buildString {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null && length < MAX_TEXT_LENGTH) {
                        if (!entry.isDirectory && entry.name.startsWith(entryPrefix) &&
                            entry.name.endsWith(".xml")
                        ) {
                            val xml = zip.readBytes().toString(Charsets.UTF_8)
                                .replace(Regex("</(w:p|a:p|row|si)>"), "\n")
                            append(
                                Html.fromHtml(xml, Html.FROM_HTML_MODE_LEGACY)
                                    .toString()
                                    .trim()
                            )
                            append('\n')
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        }.take(MAX_TEXT_LENGTH)
        return Attachment(
            kind = AttachmentKind.DOCUMENT,
            name = metadata.name,
            uri = uri.toString(),
            mimeType = metadata.mimeType,
            extractedText = text,
            sizeBytes = metadata.size
        )
    }

    private fun ContentResolver.metadata(uri: Uri): FileMetadata {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = File(requireNotNull(uri.path))
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
                ?: "application/octet-stream"
            return FileMetadata(file.name, mime, file.length())
        }
        var name = uri.lastPathSegment ?: "附件"
        var size = 0L
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        return FileMetadata(name, getType(uri) ?: "application/octet-stream", size)
    }

    private fun isTextFile(name: String): Boolean = listOf(
        ".md", ".json", ".xml", ".csv", ".yaml", ".yml", ".log", ".kt", ".java",
        ".py", ".js", ".ts", ".html", ".css", ".sql"
    ).any { name.endsWith(it, ignoreCase = true) }

    private data class FileMetadata(val name: String, val mimeType: String, val size: Long)

    private companion object {
        const val MAX_IMAGE_DIMENSION = 1_600
        const val MAX_PDF_PAGES = 4
        const val MAX_TEXT_LENGTH = 200_000
    }
}
