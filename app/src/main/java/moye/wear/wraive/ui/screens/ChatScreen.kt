package moye.wear.wraive.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.material3.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.foundation.hierarchicalFocusGroup
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonDefaults
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import moye.wear.wraive.data.ChatDraft
import moye.wear.wraive.data.DraftStore
import moye.wear.wraive.model.Attachment
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.Conversation
import moye.wear.wraive.model.QuickPhrase
import moye.wear.wraive.model.TranscriptionConfig
import moye.wear.wraive.model.TranscriptionEngine
import moye.wear.wraive.ui.components.MessageBubble
import moye.wear.wraive.ui.components.MessagePart
import moye.wear.wraive.ui.components.chatMessageItems
import moye.wear.wraive.ui.components.rememberChatRotaryScrollableBehavior
import moye.wear.wraive.ui.components.WearActionButton
import moye.wear.wraive.ui.components.WearFisheyeScope
import moye.wear.wraive.ui.components.WearNoticeDialog
import moye.wear.wraive.ui.navigation.SwipeBackGuard
import moye.wear.wraive.ui.components.WearImagePickerOverlay
import moye.wear.wraive.ui.components.deleteCapturedPhoto
import moye.wear.wraive.ui.components.rememberWearCameraCapture
import moye.wear.wraive.ui.components.rememberWearImagePicker
import moye.wear.wraive.ui.components.rememberWearMultiFilePicker
import moye.wear.wraive.ui.tr

/**
 * Composer content kept above the input dialog so closing the dialog does not drop the draft.
 * Only the text is persisted eagerly; attachments are re-read from their source URIs.
 */
@Stable
private class ComposerDraftState(
    text: String = "",
    attachments: List<Attachment> = emptyList(),
    sources: List<String> = emptyList()
) {
    var text by mutableStateOf(text)
    var attachments by mutableStateOf(attachments)
    var sources by mutableStateOf(sources)

    fun clear() {
        text = ""
        attachments = emptyList()
        sources = emptyList()
    }

    fun toChatDraft() = ChatDraft(text, sources)
}

@Composable
fun ChatScreen(
    conversation: Conversation,
    messages: List<ChatMessage>,
    quickPhrases: List<QuickPhrase>,
    generating: Boolean,
    showReasoning: Boolean,
    markdownEnabled: Boolean,
    autoFocusInput: Boolean = true,
    readAttachment: suspend (Uri) -> List<Attachment>,
    transcriptionConfig: TranscriptionConfig,
    startRecording: (TranscriptionConfig) -> Unit,
    stopAndTranscribe: suspend (TranscriptionConfig) -> String,
    cancelRecording: () -> Unit,
    onSend: (String, List<Attachment>) -> Unit,
    onStop: () -> Unit,
    onMessageActions: (ChatMessage) -> Unit,
    draftStore: DraftStore,
    assistantName: String = ""
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val rotaryBehavior = rememberChatRotaryScrollableBehavior(state)
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val floatingButtonBottom = (configuration.screenHeightDp.dp * 0.25f).coerceIn(36.dp, 52.dp)
    var followLatest by remember(conversation.id) { mutableStateOf(true) }
    // Mirrors WearQQ Next: the jump button turns into an unread counter while scrolled up.
    var pendingBelow by remember(conversation.id) { mutableIntStateOf(0) }
    var knownMessageCount by remember(conversation.id) { mutableIntStateOf(messages.size) }
    LaunchedEffect(messages.size, followLatest) {
        if (messages.size > knownMessageCount && !followLatest) {
            pendingBelow += messages.size - knownMessageCount
        }
        if (followLatest) pendingBelow = 0
        knownMessageCount = messages.size
    }
    var lastMessageLayoutRevision by remember(conversation.id) { mutableIntStateOf(0) }
    var expandedReasoningIds by rememberSaveable(conversation.id) { mutableStateOf(emptyList<String>()) }
    val chatItems = remember(messages, expandedReasoningIds, showReasoning) {
        chatMessageItems(messages, expandedReasoningIds, showReasoning)
    }
    val bottomIndex = if (messages.isEmpty()) 2 else chatItems.size + 1
    var showInput by remember { mutableStateOf(false) }
    val draft = remember(conversation.id) {
        val stored = if (conversation.temporary) ChatDraft() else draftStore.load(conversation.id)
        ComposerDraftState(stored.text, sources = stored.attachmentUris)
    }
    var restoringDraft by remember(conversation.id) { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    fun persistDraft() {
        if (!conversation.temporary) draftStore.save(conversation.id, draft.toChatDraft())
    }
    DisposableEffect(conversation.id, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) persistDraft()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            persistDraft()
        }
    }
    // Debounced autosave so leaving the app mid-sentence keeps the draft.
    LaunchedEffect(conversation.id) {
        snapshotFlow { draft.text to draft.sources }
            .collectLatest { (text, sources) ->
                delay(350)
                if (!conversation.temporary) {
                    draftStore.save(conversation.id, ChatDraft(text, sources))
                }
            }
    }
    // Attachments are kept as source URIs in the draft and re-read on demand.
    LaunchedEffect(showInput, conversation.id) {
        if (!showInput || restoringDraft || draft.attachments.isNotEmpty()) return@LaunchedEffect
        val uris = draft.sources
        if (uris.isEmpty()) return@LaunchedEffect
        restoringDraft = true
        val restored = uris.flatMap { uri ->
            runCatching { readAttachment(Uri.parse(uri)) }.getOrDefault(emptyList())
        }
        if (draft.attachments.isEmpty()) draft.attachments = restored
        restoringDraft = false
    }
    val scrollConnection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) followLatest = false
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to state.canScrollForward }
            .collect { (scrolling, canScrollForward) ->
                if (!scrolling && !canScrollForward) followLatest = true
            }
    }
    // Follow measured growth instead of restarting an animation for every token.
    LaunchedEffect(bottomIndex, lastMessageLayoutRevision, followLatest, state.isScrollInProgress) {
        if (followLatest && messages.isNotEmpty() && !state.isScrollInProgress) {
            state.requestScrollToItem(bottomIndex)
        }
    }
    Box(Modifier.fillMaxSize().hierarchicalFocusGroup(active = !showInput)) {
        ScreenScaffold(
            modifier = Modifier.fillMaxSize(),
            scrollState = state,
            timeText = { TimeText() },
            edgeButton = {
                val size = EdgeButtonSize.ExtraSmall
                EdgeButton(
                    onClick = if (generating) onStop else ({ showInput = true }),
                    modifier = Modifier.fillMaxWidth(),
                    buttonSize = size,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (generating) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (generating) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (generating) Icons.Default.Stop else Icons.Default.Edit,
                        contentDescription = tr(if (generating) "停止" else "输入"),
                        modifier = Modifier.size(EdgeButtonDefaults.iconSizeFor(size))
                    )
                }
            }
        ) { contentPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize()
                    .testTag("chat-list")
                    .nestedScroll(scrollConnection)
                    .onPreRotaryScrollEvent {
                        if (it.verticalScrollPixels < 0) followLatest = false
                        false
                    }
                    .requestFocusOnHierarchyActive()
                    .rotaryScrollable(rotaryBehavior, focusRequester),
                state = state,
                contentPadding = contentPadding
            ) {
                item(key = "chat-title") {
                    ListHeader(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text(
                            conversation.title,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1
                        )
                    }
                }
                if (messages.isEmpty()) {
                    item(key = "chat-empty") {
                        Box(
                            Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                tr("想聊点什么？"),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(chatItems, key = { it.key }, contentType = {
                    if (it.part == MessagePart.FULL) it.message.role else it.part
                }) { item ->
                    val message = item.message
                    MessageBubble(
                        message = message,
                        part = item.part,
                        reasoningText = item.reasoningText,
                        reasoningExpanded = message.id in expandedReasoningIds,
                        assistantName = assistantName,
                        showReasoning = showReasoning,
                        markdownEnabled = markdownEnabled,
                        onLongClick = { onMessageActions(message) },
                        onReasoningToggle = {
                            val expanding = message.id !in expandedReasoningIds
                            if (expanding) followLatest = false
                            expandedReasoningIds = if (expanding) expandedReasoningIds + message.id
                                else expandedReasoningIds - message.id
                        },
                        modifier = Modifier.testTag("message-${item.key}")
                            .onSizeChanged {
                                if (message.id == messages.lastOrNull()?.id) lastMessageLayoutRevision++
                            }
                            .padding(bottom = if (item.part == MessagePart.FULL || item.part == MessagePart.FOOTER) 8.dp else 0.dp)
                    )
                }
                item(key = "chat-bottom") { Spacer(Modifier.fillMaxWidth().height(8.dp).testTag("chat-bottom")) }
            }
        }
        // Floating jump control copied from WearQQ Next: 52dp circle, 16dp arrow,
        // unread count instead of the arrow, fade + scale in/out.
        AnimatedVisibility(
            visible = !followLatest && !state.isScrollInProgress,
            enter = fadeIn(tween(140)) +
                scaleIn(initialScale = 0.5f, animationSpec = tween(140)),
            exit = fadeOut(tween(120)) +
                scaleOut(targetScale = 0.72f, animationSpec = tween(120)),
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(
                    end = if (configuration.isScreenRound) 16.dp else 4.dp,
                    bottom = floatingButtonBottom + 4.dp
                )
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        scope.launch {
                            state.animateScrollToItem(bottomIndex)
                            followLatest = true
                            pendingBelow = 0
                        }
                    }
                    .testTag("chat-jump-to-bottom"),
                contentAlignment = Alignment.Center
            ) {
                if (pendingBelow > 0) {
                    Text(
                        text = if (pendingBelow > 99) "99+" else pendingBelow.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = tr("回到最新消息"),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
    ChatInputDialog(
        visible = showInput,
        autoFocusInput = autoFocusInput,
        draft = draft,
        restoringDraft = restoringDraft,
        quickPhrases = quickPhrases.filter {
            it.assistantId == null || it.assistantId == conversation.assistantId
        },
        readAttachment = readAttachment,
        transcriptionConfig = transcriptionConfig,
        startRecording = startRecording,
        stopAndTranscribe = stopAndTranscribe,
        cancelRecording = cancelRecording,
        onDismiss = {
            cancelRecording()
            showInput = false
            persistDraft()
        },
        onSend = { text, attachments ->
            cancelRecording()
            followLatest = true
            showInput = false
            draft.sources.forEach { deleteCapturedPhoto(context, it) }
            draft.clear()
            if (!conversation.temporary) draftStore.clear(conversation.id)
            onSend(text, attachments)
        }
    )
}

@Composable
private fun ChatInputDialog(
    visible: Boolean,
    autoFocusInput: Boolean,
    draft: ComposerDraftState,
    restoringDraft: Boolean,
    quickPhrases: List<QuickPhrase>,
    readAttachment: suspend (Uri) -> List<Attachment>,
    transcriptionConfig: TranscriptionConfig,
    startRecording: (TranscriptionConfig) -> Unit,
    stopAndTranscribe: suspend (TranscriptionConfig) -> String,
    cancelRecording: () -> Unit,
    onDismiss: () -> Unit,
    onSend: (String, List<Attachment>) -> Unit
) {
    if (!visible) return
    var attachmentError by remember(visible) { mutableStateOf<String?>(null) }
    // Voice problems are surfaced in a dialog: the inline label lives below the
    // fold of the input sheet, so users never noticed it.
    var voiceError by remember(visible) { mutableStateOf<String?>(null) }
    var recording by remember(visible) { mutableStateOf(false) }
    var recordedSeconds by remember(visible) { mutableIntStateOf(0) }
    var transcribing by remember(visible) { mutableStateOf(false) }
    var readingAttachments by remember(visible) { mutableStateOf(false) }
    var inputFocusedOnce by remember(visible) { mutableStateOf(false) }
    LaunchedEffect(recording) {
        if (!recording) {
            recordedSeconds = 0
            return@LaunchedEffect
        }
        recordedSeconds = 0
        while (true) {
            delay(1000)
            recordedSeconds++
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound
    val microphonePermissionError = tr("需要麦克风权限才能录音")
    val voicePrompt = tr("说点什么")
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognized = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!recognized.isNullOrBlank()) {
                draft.text = recognized
            } else {
                voiceError = "没有识别到语音内容，请再说一次"
            }
        }
        // CANCELED covers both a deliberate cancel and an internal recognizer
        // failure, and the intent carries no error code, so it is left silent.
    }
    val recordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runCatching { startRecording(transcriptionConfig) }
                .onSuccess { recording = true }
                .onFailure { voiceError = it.message ?: microphonePermissionError }
        } else {
            voiceError = microphonePermissionError
        }
    }
    val attachmentLauncher = rememberWearMultiFilePicker { uris ->
        scope.launch {
            attachmentError = null
            readingAttachments = true
            runCatching { uris.flatMap { readAttachment(it) } }
                .onSuccess {
                    draft.attachments = draft.attachments + it
                    draft.sources = draft.sources + uris.map(Uri::toString)
                }
                .onFailure { attachmentError = it.message }
            readingAttachments = false
        }
    }
    val addImage: (Uri) -> Unit = { uri ->
        scope.launch {
            attachmentError = null
            readingAttachments = true
            runCatching { readAttachment(uri) }
                .onSuccess {
                    draft.attachments = draft.attachments + it
                    draft.sources = draft.sources + uri.toString()
                }
                .onFailure {
                    attachmentError = it.message
                    deleteCapturedPhoto(context, uri.toString())
                }
            readingAttachments = false
        }
    }
    val camera = rememberWearCameraCapture(
        onPhotoCaptured = addImage,
        onError = { attachmentError = it }
    )
    val imagePicker = rememberWearImagePicker(onImageSelected = addImage)
    val hasCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    // Keep the nav host from consuming a right-swipe while this dialog window is up.
    DisposableEffect(Unit) {
        SwipeBackGuard.lock()
        onDispose { SwipeBackGuard.unlock() }
    }
    val inputBusy = recording || transcribing || readingAttachments || restoringDraft
    val canSend = !inputBusy && (draft.text.isNotBlank() || draft.attachments.isNotEmpty())
    val submit = {
        if (canSend) {
            onSend(draft.text.trim(), draft.attachments)
        }
    }
    Dialog(
        visible = visible,
        onDismissRequest = {
            if (imagePicker.isActive) {
                imagePicker.dismiss()
            } else {
                if (recording) cancelRecording()
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(imagePicker.isActive, autoFocusInput) {
            if (autoFocusInput && !imagePicker.isActive && !inputFocusedOnce) {
                delay(200)
                focusRequester.requestFocus()
                inputFocusedOnce = true
            }
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                ScreenScaffold(
                    scrollState = listState,
                    edgeButton = {
                        val size = EdgeButtonSize.ExtraSmall
                        EdgeButton(
                            onClick = submit,
                            enabled = canSend,
                            modifier = Modifier.fillMaxWidth(),
                            buttonSize = size
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = tr("发送"),
                                modifier = Modifier.size(EdgeButtonDefaults.iconSizeFor(size))
                            )
                        }
                    }
                ) { contentPadding ->
                    TransformingLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = contentPadding,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        rotaryScrollableBehavior = rememberChatRotaryScrollableBehavior(listState)
                    ) {
                        with(WearFisheyeScope(this, transformationSpec, isRound)) {
                            item {
                                ListHeader(Modifier.fillMaxWidth()) { Text(tr("输入消息")) }
                            }
                            item {
                                OutlinedTextField(
                                    value = draft.text,
                                    onValueChange = { draft.text = it },
                                    placeholder = { Text(tr("输入内容")) },
                                    minLines = 3,
                                    maxLines = 8,
                                    shape = RoundedCornerShape(24.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { submit() }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                                )
                            }
                            item {
                                WearActionButton(
                                    label = when {
                                        transcribing -> "正在转写…"
                                        recording -> tr("停止并转写") + " · " +
                                            tr("已录音%d秒").format(recordedSeconds)
                                        else -> "语音输入"
                                    },
                                    secondary = when (transcriptionConfig.engine) {
                                        TranscriptionEngine.SYSTEM -> "系统识别"
                                        TranscriptionEngine.OPENAI -> "OpenAI 兼容"
                                        TranscriptionEngine.GOOGLE -> "Google Cloud"
                                        TranscriptionEngine.DEEPSEEK -> "DeepSeek ASR"
                                    },
                                    icon = Icons.Default.Mic,
                                    onClick = {
                                        if (transcriptionConfig.engine == TranscriptionEngine.SYSTEM) {
                                            val available = runCatching {
                                                SpeechRecognizer.isRecognitionAvailable(context)
                                            }.getOrDefault(false)
                                            if (!available) {
                                                voiceError =
                                                    "设备没有可用的系统语音识别服务，请在设置中改用其他转写引擎"
                                            } else {
                                                val intent =
                                                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                        putExtra(
                                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                                        )
                                                        putExtra(
                                                            RecognizerIntent.EXTRA_LANGUAGE,
                                                            transcriptionConfig.languageCode
                                                        )
                                                        putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
                                                    }
                                                runCatching { voiceLauncher.launch(intent) }
                                                    .onFailure {
                                                        voiceError =
                                                            "无法启动系统语音识别，请在设置中改用其他转写引擎"
                                                    }
                                            }
                                        } else if (recording) {
                                            recording = false
                                            transcribing = true
                                            scope.launch {
                                                runCatching { stopAndTranscribe(transcriptionConfig) }
                                                    .onSuccess { recognized ->
                                                        draft.text = listOf(draft.text, recognized)
                                                            .filter(String::isNotBlank)
                                                            .joinToString(" ")
                                                    }
                                                    .onFailure {
                                                        voiceError = it.message ?: "转写失败，请重试"
                                                    }
                                                transcribing = false
                                            }
                                        } else {
                                            recordPermission.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    enabled = !transcribing && !readingAttachments
                                )
                            }
                            if (hasCamera) {
                                item {
                                    WearActionButton(
                                        label = "拍照",
                                        secondary = "调用系统相机",
                                        icon = Icons.Default.PhotoCamera,
                                        onClick = { camera.launch() },
                                        enabled = !inputBusy
                                    )
                                }
                            }
                            item {
                                WearActionButton(
                                    label = if (restoringDraft) "正在恢复草稿…" else "上传图片",
                                    secondary = "从相册选择",
                                    icon = Icons.Default.AddPhotoAlternate,
                                    onClick = { imagePicker.launch() },
                                    enabled = !inputBusy
                                )
                            }
                            item {
                                WearActionButton(
                                    label = if (readingAttachments) "正在读取附件…" else "添加附件",
                                    secondary = if (draft.attachments.isEmpty()) null else {
                                        draft.attachments.joinToString { it.name }
                                    },
                                    icon = Icons.Default.AttachFile,
                                    onClick = { attachmentLauncher.launch(arrayOf("*/*")) },
                                    enabled = !inputBusy
                                )
                            }
                            if (draft.attachments.isNotEmpty()) {
                                item {
                                    WearActionButton(
                                        label = "清空附件",
                                        secondary = "已选 ${draft.attachments.size} 个",
                                        onClick = {
                                            draft.sources.forEach {
                                                deleteCapturedPhoto(context, it)
                                            }
                                            draft.attachments = emptyList()
                                            draft.sources = emptyList()
                                        },
                                        enabled = !inputBusy,
                                        danger = true
                                    )
                                }
                            }
                            attachmentError?.let { error ->
                                item { Text(error, color = MaterialTheme.colorScheme.error) }
                            }
                            if (quickPhrases.isNotEmpty()) {
                                item {
                                    Text(
                                        tr("快捷短语"),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                quickPhrases.forEach { phrase ->
                                    item(key = phrase.id) {
                                        WearActionButton(
                                            label = phrase.title,
                                            secondary = phrase.content,
                                            onClick = { draft.text = phrase.content }
                                        )
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(36.dp)) }
                        }
                    }
                }
        }
    }
    if (imagePicker.isActive) {
        Dialog(
            visible = true,
            onDismissRequest = { imagePicker.dismiss() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                WearImagePickerOverlay(state = imagePicker, onImageSelected = addImage)
            }
        }
    }
    voiceError?.let { message ->
        WearNoticeDialog(
            visible = true,
            title = "语音输入",
            message = message,
            onDismiss = { voiceError = null }
        )
    }
}
