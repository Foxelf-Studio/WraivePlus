package moye.wear.wraive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import moye.wear.wraive.model.AttachmentKind
import moye.wear.wraive.model.Attachment
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.MessageRole
import moye.wear.wraive.model.MessageStatus
import moye.wear.wraive.ui.tr
import java.text.DateFormat
import java.util.Date

@Composable
internal fun MessageBubble(
    message: ChatMessage,
    showReasoning: Boolean,
    markdownEnabled: Boolean,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onReasoningToggle: () -> Unit,
    reasoningExpanded: Boolean,
    part: MessagePart = MessagePart.FULL,
    reasoningText: String = "",
    assistantName: String = ""
) {
    if (part == MessagePart.REASONING) {
        SelectionContainer(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            Text(
                text = reasoningText.removeSuffix("\n"),
                modifier = Modifier.padding(horizontal = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    if (message.role == MessageRole.TOOL) {
        ToolMessage(message, modifier)
        return
    }
    val mine = message.role == MessageRole.USER
    val assistant = message.role == MessageRole.ASSISTANT
    val container = when {
        mine -> MaterialTheme.colorScheme.primaryContainer
        assistant -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (mine) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val shape = when (part) {
        MessagePart.HEADER -> MaterialTheme.shapes.large.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
        MessagePart.FOOTER -> MaterialTheme.shapes.large.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp))
        else -> if (assistant) CardDefaults.shape else RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = if (mine) 24.dp else 8.dp,
            bottomEnd = if (mine) 8.dp else 24.dp
        )
    }
    val timestamp = remember(message.createdAt) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAt))
    }
    var previewImage by remember(message.id) { mutableStateOf<Attachment?>(null) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 260.dp)
                .clip(shape)
                .background(container)
                .combinedClickable(onClick = {
                    if (!assistant && showReasoning && message.reasoning.isNotBlank()) {
                        onReasoningToggle()
                    }
                }, onLongClick = onLongClick)
                .padding(start = 14.dp, end = 14.dp,
                    top = if (part == MessagePart.FOOTER) 10.dp else 12.dp,
                    bottom = if (part == MessagePart.HEADER) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (assistant) 10.dp else 6.dp)
        ) {
            if (assistant && part != MessagePart.FOOTER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = assistantName.ifBlank { tr("助手") },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (!assistant) {
                Text(
                    text = tr(if (mine) "你" else "助手"),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (mine) contentColor else MaterialTheme.colorScheme.primary
                )
            }
            if (assistant && part != MessagePart.FOOTER && showReasoning && message.reasoning.isNotBlank()) {
                ReasoningButton(
                    thinking = message.status == MessageStatus.STREAMING && message.content.isBlank(),
                    expanded = reasoningExpanded,
                    onToggle = onReasoningToggle
                )
            } else if (!assistant && showReasoning && message.reasoning.isNotBlank()) {
                Text(
                    text = tr(if (reasoningExpanded) "思考过程" else "思考过程 · 点击展开"),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.68f)
                )
                if (reasoningExpanded) {
                    SelectionContainer {
                        Text(
                            text = message.reasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.78f)
                        )
                    }
                }
            }
            if (part != MessagePart.HEADER && message.content.isNotBlank()) {
                if (markdownEnabled) {
                    // The native TextView is already selectable; wrapping it in SelectionContainer
                    // adds a second selection layer and slows scrolling.
                    MarkdownMessageText(message.content, contentColor)
                } else {
                    SelectionContainer {
                        Text(message.content, color = contentColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            val citations = message.providerContent?.citations.orEmpty()
            if (part != MessagePart.HEADER && citations.isNotEmpty()) {
                Text(
                    text = buildAnnotatedString {
                        citations.forEachIndexed { index, citation ->
                            if (index > 0) append("\n")
                            withLink(LinkAnnotation.Url(
                                citation.url,
                                TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
                            )) { append("${index + 1}. ${citation.title}") }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            message.attachments.takeIf { part != MessagePart.HEADER }.orEmpty().forEach { attachment ->
                if (attachment.kind == AttachmentKind.IMAGE) {
                    AttachmentImage(
                        attachment = attachment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { previewImage = attachment },
                        contentScale = ContentScale.Crop
                    )
                    attachment.extractedText?.takeIf(String::isNotBlank)?.let { revisedPrompt ->
                        Text(
                            text = revisedPrompt,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.72f),
                            maxLines = 3
                        )
                    }
                } else {
                    Text(
                        text = tr("附件 · ${attachment.name}"),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.72f)
                    )
                }
            }
            if (assistant && part != MessagePart.HEADER) {
                AssistantMessageStatus(message, showReasoning)
            } else if (!assistant) {
                val status = when (message.status) {
                    MessageStatus.STREAMING -> " · 生成中"
                    MessageStatus.FAILED -> " · 失败"
                    MessageStatus.CANCELLED -> " · 已停止"
                    MessageStatus.COMPLETE -> ""
                }
                Text(
                    text = timestamp + tr(status),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.58f)
                )
            }
            message.error?.takeIf { part != MessagePart.HEADER }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    previewImage?.let { attachment ->
        var scale by remember(attachment.uri) { mutableFloatStateOf(1f) }
        var offset by remember(attachment.uri) { mutableStateOf(Offset.Zero) }
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            val next = (scale * zoomChange).coerceIn(1f, 5f)
            scale = next
            offset = if (next <= 1f) Offset.Zero else offset + panChange
        }
        Dialog(
            onDismissRequest = { previewImage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(attachment.uri) {
                        detectTapGestures(
                            onTap = { previewImage = null },
                            onDoubleTap = {
                                if (scale > 1.01f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    },
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                AttachmentImage(
                    attachment = attachment,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(
                            state = transformState,
                            lockRotationOnZoomPan = true
                        )
                )
            }
        }
    }
}

@Composable
private fun ReasoningButton(
    thinking: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val expansionState = tr(if (expanded) "已展开" else "已收起")
    Button(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics { stateDescription = expansionState },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (thinking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = tr(if (thinking) "思考中" else "思考过程"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AssistantMessageStatus(message: ChatMessage, showReasoning: Boolean) {
    val streaming = message.status == MessageStatus.STREAMING
    // The reasoning button already communicates progress while thinking.
    if (message.status == MessageStatus.COMPLETE ||
        (streaming && showReasoning && message.reasoning.isNotBlank() && message.content.isBlank())
    ) return
    val label = when (message.status) {
        MessageStatus.STREAMING -> when {
            message.content.isNotBlank() -> "回复中"
            message.reasoning.isNotBlank() -> "思考中"
            else -> "等待回复"
        }
        MessageStatus.FAILED -> "回复失败"
        MessageStatus.CANCELLED -> "已停止"
        MessageStatus.COMPLETE -> return
    }
    val color = if (message.status == MessageStatus.FAILED) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (streaming) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                if (message.status == MessageStatus.FAILED) Icons.Default.ErrorOutline
                    else Icons.Default.StopCircle,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
        }
        Text(text = tr(label), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ToolMessage(message: ChatMessage, modifier: Modifier) {
    val call = message.toolCalls.firstOrNull()
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 220.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.68f))
                .padding(horizontal = 9.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = call?.name?.ifBlank { tr("工具") } ?: tr("工具"),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                textAlign = TextAlign.Center
            )
            Text(
                text = message.content.take(800),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 8
            )
        }
    }
}
