package com.nexora.docly.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexora.docly.R
import com.nexora.docly.data.AiLanguage
import com.nexora.docly.data.FileExtractor
import com.nexora.docly.data.SelectedFile
import com.nexora.docly.data.ai.ChatMessage
import com.nexora.docly.data.ai.MistralApi
import com.nexora.docly.data.ai.Summarizer
import com.nexora.docly.ui.components.CategoryIcon
import com.nexora.docly.ui.components.LanguageSelectorDialog
import com.nexora.docly.ui.components.ReadAloudButton
import com.nexora.docly.ui.components.TransparentCard
import com.nexora.docly.ui.theme.InkBlack
import com.nexora.docly.ui.theme.PureWhite
import com.nexora.docly.ui.theme.SilverGrey
import com.nexora.docly.util.TtsReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface FilePhase {
    data object Extracting : FilePhase
    data object Summarizing : FilePhase
    data class Done(val summary: String) : FilePhase
    data class Failed(val message: String) : FilePhase
}

@Composable
fun SummaryScreen(
    files: List<SelectedFile>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val phases = remember { mutableStateMapOf<String, FilePhase>() }
    val summaries = remember { mutableStateMapOf<String, String>() }
    val rawTexts = remember { mutableStateMapOf<String, String>() }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    var query by remember { mutableStateOf("") }
    var chatThinking by remember { mutableStateOf(false) }
    var aiLanguage by remember { mutableStateOf(AiLanguage.current(context)) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val gearRotation by animateFloatAsState(
        targetValue = if (showLanguageDialog) 90f else 0f,
        animationSpec = tween(350),
        label = "gear"
    )

    fun process(file: SelectedFile, force: Boolean) {
        val key = file.uri.toString()
        if (!force && phases.containsKey(key)) return
        scope.launch {
            phases[key] = FilePhase.Extracting
            if (!MistralApi.hasKey()) {
                phases[key] = FilePhase.Failed(
                    "AI service is not configured. Add MISTRAL_API_KEY in app/local.properties and rebuild."
                )
                return@launch
            }
            val result = FileExtractor.extract(context, file.uri, file.name)
            val text = when (result) {
                is FileExtractor.Result.Success -> result.text
                is FileExtractor.Result.Pending -> {
                    phases[key] = FilePhase.Failed(result.message)
                    return@launch
                }
                is FileExtractor.Result.Failed -> {
                    phases[key] = FilePhase.Failed(result.message)
                    return@launch
                }
            }
            if (text.isBlank()) {
                phases[key] = FilePhase.Failed("No text found in this file.")
                return@launch
            }
            rawTexts[key] = text.take(60_000)
            phases[key] = FilePhase.Summarizing
            val summary = try {
                Summarizer.summarize(file.name, text, aiLanguage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (summary == null) {
                phases[key] = FilePhase.Failed("Summary failed. Check your internet connection and retry.")
            } else {
                summaries[key] = summary
                phases[key] = FilePhase.Done(summary)
            }
        }
    }

    LaunchedEffect(files) { files.forEach { process(it, force = false) } }

    LaunchedEffect(aiLanguage) {
        if (phases.isNotEmpty()) {
            phases.clear()
            summaries.clear()
            rawTexts.clear()
            files.forEach { process(it, force = true) }
        }
    }

    fun sendQuestion() {
        val q = query.trim()
        if (q.isEmpty() || chatThinking || summaries.isEmpty()) return
        query = ""
        chatMessages += ChatMessage("user", q)
        chatThinking = true
        scope.launch {
            val budget = 85_000
            val docContext = StringBuilder()
            for (file in files) {
                val key = file.uri.toString()
                val s = summaries[key] ?: continue
                docContext.append("File: ").append(file.name).append("\nSummary:\n").append(s).append("\n\n")
                val raw = rawTexts[key]
                if (raw != null) {
                    val remaining = budget - docContext.length
                    if (remaining > 5_000) {
                        docContext.append("Extracted text (used for detail questions):\n")
                            .append(raw.take(remaining))
                            .append("\n\n")
                    } else {
                        docContext.append("[Extracted text omitted - use only the summary above]\n\n")
                    }
                }
            }
            val reply = try {
                Summarizer.ask(docContext.toString(), chatMessages.toList(), aiLanguage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Error: ${e.message ?: "Something went wrong"}"
            }
            chatMessages += ChatMessage("assistant", reply)
            chatThinking = false
        }
    }

    val doneCount = phases.values.count { it is FilePhase.Done }
    val listSize = (files.size + 1 + chatMessages.size + if (chatThinking) 1 else 0)
    LaunchedEffect(listSize) {
        if (listSize > 0) listState.animateScrollToItem(listSize - 1)
    }

    BackHandler { onBack() }

    DisposableEffect(Unit) {
        onDispose { TtsReader.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PureWhite
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = "AI Summary",
                color = PureWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$doneCount/${files.size}",
                color = if (doneCount == files.size) PureWhite else SilverGrey,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { showLanguageDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "AI language settings",
                    tint = PureWhite,
                    modifier = Modifier.rotate(gearRotation)
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 6.dp, bottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(files, key = { it.uri.toString() }) { file ->
                val key = file.uri.toString()
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(400)) +
                        slideInVertically(animationSpec = tween(400)) { it / 5 },
                    modifier = Modifier.animateItem()
                ) {
                    FileSummaryCard(
                        file = file,
                        phase = phases[key] ?: FilePhase.Extracting,
                        language = aiLanguage,
                        onRetry = { process(file, force = true) },
                        onCopy = { summary ->
                            clipboard.setText(AnnotatedString(summary))
                        }
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(450, delayMillis = 150))
                ) {
                    HorizontalDivider(color = PureWhite.copy(alpha = 0.15f))
                }
                Spacer(Modifier.height(2.dp))
            }

            items(chatMessages.size) { i ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300)) +
                        scaleIn(initialScale = 0.9f, animationSpec = tween(300))
                ) {
                    Bubble(message = chatMessages[i], language = aiLanguage, speakId = "msg-$i")
                }
            }

            if (chatThinking) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300)) +
                            scaleIn(initialScale = 0.9f, animationSpec = tween(300))
                    ) {
                        Bubble(thinking = true)
                    }
                }
            }
        }

        HorizontalDivider(color = PureWhite.copy(alpha = 0.2f))

        if (showLanguageDialog) {
            BackHandler(enabled = true) { showLanguageDialog = false }
            LanguageSelectorDialog(
                aiLanguage = aiLanguage,
                onSelect = { lang ->
                    aiLanguage = lang
                    AiLanguage.set(context, lang)
                    showLanguageDialog = false
                },
                onDismiss = { showLanguageDialog = false }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Ask a question", color = SilverGrey) },
                singleLine = true,
                enabled = summaries.isNotEmpty(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PureWhite.copy(alpha = 0.8f),
                    unfocusedBorderColor = PureWhite.copy(alpha = 0.25f),
                    disabledBorderColor = PureWhite.copy(alpha = 0.1f),
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite,
                    disabledTextColor = SilverGrey,
                    cursorColor = PureWhite,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedPlaceholderColor = SilverGrey,
                    unfocusedPlaceholderColor = SilverGrey,
                    disabledPlaceholderColor = SilverGrey.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (chatThinking || query.isBlank() || summaries.isEmpty())
                            PureWhite.copy(alpha = 0.14f)
                        else PureWhite
                    )
                    .clickable(enabled = !chatThinking && query.isNotBlank() && summaries.isNotEmpty()) {
                        sendQuestion()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (chatThinking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = SilverGrey,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (query.isBlank() || summaries.isEmpty()) SilverGrey.copy(alpha = 0.6f) else Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FileSummaryCard(
    file: SelectedFile,
    phase: FilePhase,
    language: String,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit
) {
    TransparentCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    file.category?.let { CategoryIcon(it, size = 20.dp) }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        color = PureWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    when (phase) {
                        is FilePhase.Extracting -> StatusLine(
                            icon = { ProgressDot(stroke = 2.dp) },
                            text = "Extracting text..."
                        )
                        is FilePhase.Summarizing -> StatusLine(
                            icon = { ProgressDot(stroke = 2.dp) },
                            text = "Generating summary..."
                        )
                        is FilePhase.Done -> StatusLine(
                            icon = { Text("\u2713", color = PureWhite, fontSize = 13.sp) },
                            text = "Summary ready"
                        )
                        is FilePhase.Failed -> StatusLine(
                            icon = { Text("!", color = PureWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                            text = phase.message
                        )
                    }
                }
            }

            when (phase) {
                is FilePhase.Done -> {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(450)) +
                            expandVertically(expandFrom = Alignment.Top, animationSpec = tween(450))
                    ) {
                        Column {
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = PureWhite.copy(alpha = 0.12f))
                            Spacer(Modifier.height(12.dp))
                            SelectionContainer {
                                Text(
                                    text = phase.summary,
                                    color = PureWhite,
                                    fontSize = 13.5.sp,
                                    lineHeight = 20.sp
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable { onCopy(phase.summary) }
                                        .padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy summary",
                                        tint = PureWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Copy summary",
                                        color = PureWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                ReadAloudButton(
                                    text = phase.summary,
                                    language = language,
                                    id = "summary-${file.uri}",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                is FilePhase.Failed -> {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = PureWhite
                        )
                    ) {
                        Text("Retry", fontSize = 13.sp)
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun StatusLine(icon: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = SilverGrey,
            fontSize = 12.5.sp,
            maxLines = 2
        )
    }
}

@Composable
private fun ProgressDot(stroke: Dp) {
    CircularProgressIndicator(
        modifier = Modifier.size(13.dp),
        color = PureWhite,
        strokeWidth = stroke
    )
}

@Composable
private fun Bubble(
    message: ChatMessage? = null,
    language: String = "English",
    speakId: String = "",
    thinking: Boolean = false
) {
    val isUser = message?.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            Row(
                modifier = Modifier
                    .background(PureWhite, RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .heightIn(min = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message?.content.orEmpty(),
                    color = Color.Black,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (thinking) {
                    Image(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = "Docly",
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .border(1.dp, PureWhite.copy(alpha = 0.25f), CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = SilverGrey,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Thinking...",
                        color = SilverGrey,
                        fontSize = 13.sp
                    )
                } else {
                    SelectionContainer(
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = message?.content.orEmpty(),
                            color = PureWhite,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    ReadAloudButton(
                        text = message?.content,
                        language = language,
                        id = speakId,
                        compact = true
                    )
                }
            }
        }
    }
}