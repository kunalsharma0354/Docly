package com.nexora.docly.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexora.docly.R
import com.nexora.docly.data.AiLanguage
import com.nexora.docly.data.SelectedFile
import com.nexora.docly.data.SelectedFilesSaver
import com.nexora.docly.data.SupportedFormats
import com.nexora.docly.ui.components.CategoryIcon
import com.nexora.docly.ui.components.ExtBadge
import com.nexora.docly.ui.components.LanguageSelectorDialog
import com.nexora.docly.ui.components.NexoraBrand
import com.nexora.docly.ui.components.TransparentCard
import com.nexora.docly.ui.components.dashedBorder
import com.nexora.docly.ui.theme.InkBlack
import com.nexora.docly.ui.theme.PureWhite
import com.nexora.docly.ui.theme.SilverGrey
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSend: (List<SelectedFile>) -> Unit
) {
    val context = LocalContext.current
    var files by rememberSaveable(stateSaver = SelectedFilesSaver) { mutableStateOf(listOf<SelectedFile>()) }
    var aiLanguage by remember { mutableStateOf(AiLanguage.current(context)) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var booted by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { booted = true }
    val gearRotation by animateFloatAsState(
        targetValue = if (showLanguageDialog) 90f else 0f,
        animationSpec = tween(350),
        label = "gear"
    )
    val brandAlpha by animateFloatAsState(
        targetValue = if (booted) 1f else 0f,
        animationSpec = tween(600),
        label = "brand"
    )
    val sendAlpha by animateFloatAsState(
        targetValue = if (booted) 1f else 0f,
        animationSpec = tween(500, delayMillis = 250),
        label = "send"
    )

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val picked = uris.mapNotNull { uri ->
            val name = context.contentResolver
                .query(uri, null, null, null, null)
                ?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                }
            SelectedFile(uri, name ?: "document")
        }
        runCatching {
            picked.forEach { file ->
                context.contentResolver.takePersistableUriPermission(
                    file.uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        files = (files + picked).distinctBy { it.uri }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF101010),
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                modifier = Modifier.width(300.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = "Docly logo",
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, PureWhite.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "NEXORA",
                                color = SilverGrey,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp
                            )
                            Text(
                                text = "Docly",
                                color = PureWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "AI Document Summarizer",
                        color = SilverGrey.copy(alpha = 0.7f),
                        fontSize = 11.5.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = PureWhite.copy(alpha = 0.15f))
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .clickable {
                                scope.launch { drawerState.close() }
                                showAbout = true
                            }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "About Docly",
                            color = PureWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Made by Kunal Sharma \u00B7 Developer",
                        color = SilverGrey.copy(alpha = 0.55f),
                        fontSize = 10.5.sp
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        NexoraBrand(modifier = Modifier.graphicsLayer { alpha = brandAlpha })
        Spacer(Modifier.height(26.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (files.isEmpty()) "Your documents" else "Selected documents",
                    color = PureWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (files.isEmpty()) "Pick a file to start" else "${files.size} file${if (files.size > 1) "s" else ""} ready",
                    color = SilverGrey,
                    fontSize = 13.sp
                )
            }
            IconButton(onClick = { showLanguageDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "AI language settings",
                    tint = PureWhite,
                    modifier = Modifier.rotate(gearRotation)
                )
            }
            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Menu",
                    tint = PureWhite
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (files.isEmpty()) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(500)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(500)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                AddFileCard(onClick = { picker.launch(arrayOf("*/*")) })
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "PDF \u00B7 Word \u00B7 Excel \u00B7 PPT \u00B7 E-books \u00B7 CSV \u00B7 Images + more",
                color = SilverGrey.copy(alpha = 0.8f),
                fontSize = 12.5.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(files, key = { it.uri.toString() }) { file ->
                    FileCard(
                        file = file,
                        onRemove = { files = files - file },
                        modifier = Modifier.animateItem()
                    )
                }
                item {
                    Spacer(Modifier.height(2.dp))
                    AddFileRow(onClick = { picker.launch(arrayOf("*/*")) })
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = { onSend(files) },
            enabled = files.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = PureWhite,
                contentColor = Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.14f),
                disabledContentColor = SilverGrey.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .graphicsLayer {
                    alpha = sendAlpha
                    scaleX = 0.92f + 0.08f * sendAlpha
                    scaleY = 0.92f + 0.08f * sendAlpha
                }
        ) {
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (files.isEmpty()) "Send" else "Summarize \u00B7 ${files.size} file${if (files.size > 1) "s" else ""}",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "\u00A9 2026 NEXORA \u00B7 All rights reserved",
            color = SilverGrey.copy(alpha = 0.7f),
            fontSize = 11.5.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    BackHandler(enabled = showLanguageDialog) { showLanguageDialog = false }

            if (showLanguageDialog) {
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

            BackHandler(enabled = showAbout) { showAbout = false }

            if (showAbout) {
                AboutScreen(onBack = { showAbout = false })
            }
        }
    }
}

@Composable
private fun AddFileCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .dashedBorder(cornerRadius = 24.dp, color = PureWhite.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, PureWhite.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add files",
                    tint = PureWhite,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Tap to add documents",
                color = PureWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Anything from PDFs to images works",
                color = SilverGrey,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun AddFileRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Add more files",
            tint = SilverGrey,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Add more files",
            color = SilverGrey,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FileCard(file: SelectedFile, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    TransparentCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                file.category?.let { CategoryIcon(it, size = 22.dp) }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = PureWhite,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                file.category?.let {
                    Text(
                        text = it.label,
                        color = SilverGrey,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            file.extension?.let { ExtBadge(it) }
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove ${file.name}",
                    tint = SilverGrey,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}