package com.nexora.docly.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.nexora.docly.R
import com.nexora.docly.data.AiLanguage
import com.nexora.docly.data.SupportedFormats
import com.nexora.docly.ui.theme.PureWhite
import com.nexora.docly.ui.theme.SilverGrey
import com.nexora.docly.util.TtsReader
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp

fun Modifier.dashedBorder(
    cornerRadius: Dp,
    color: Color,
    strokeWidth: Dp = 1.dp,
    dash: Dp = 14.dp,
    gap: Dp = 9.dp
): Modifier = this.drawBehind {
    val path = Path().apply {
        addRoundRect(
            roundRect = RoundRect(
                rect = androidx.compose.ui.geometry.Rect(Offset.Zero, size),
                cornerRadius = CornerRadius(cornerRadius.toPx())
            )
        )
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))
        )
    )
}

/** Frosted, see-through card on the black canvas — the NEXORA signature surface. */
@Composable
fun TransparentCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(16.dp),
        content = content
    )
}

/** AI reply language picker — Home aur Summary dono screens se use hota hai. */
@Composable
fun LanguageSelectorDialog(
    aiLanguage: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151515),
        title = {
            Column {
                Text(
                    text = "AI reply language",
                    color = PureWhite,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Current: $aiLanguage",
                    color = SilverGrey,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
            ) {
                items(AiLanguage.ALL.size) { i ->
                    val lang = AiLanguage.ALL[i]
                    val selected = lang == aiLanguage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) PureWhite.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onSelect(lang) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lang,
                            color = if (selected) PureWhite else SilverGrey,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = PureWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PureWhite)
            }
        }
    )
}

@Composable
fun NexoraBrand(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = "Docly logo",
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "NEXORA",
                color = SilverGrey,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 5.sp
            )
            Text(
                text = "Docly",
                color = PureWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "AI Document Summarizer",
                color = SilverGrey,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun CategoryIcon(
    category: SupportedFormats.Category,
    size: Dp = 24.dp
) {
    val icon: ImageVector = when (category) {
        SupportedFormats.Category.PDF -> Icons.Filled.PictureAsPdf
        SupportedFormats.Category.WORD -> Icons.Filled.Description
        SupportedFormats.Category.TEXT -> Icons.Filled.Article
        SupportedFormats.Category.EXCEL -> Icons.Filled.GridOn
        SupportedFormats.Category.POWERPOINT -> Icons.Filled.Slideshow
        SupportedFormats.Category.EBOOK -> Icons.Filled.MenuBook
        SupportedFormats.Category.IMAGE -> Icons.Filled.Image
    }
    Icon(
        imageVector = icon,
        contentDescription = category.label,
        tint = PureWhite,
        modifier = Modifier.size(size)
    )
}

@Composable
fun ExtBadge(ext: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = ".$ext",
            color = PureWhite.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Toggle button: speaks [text] via TTS in the AI language. Only the button
 *  with matching [id] shows spinning/active state while it is speaking. */
@Composable
fun ReadAloudButton(
    text: String?,
    language: String,
    id: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onLight: Boolean = false
) {
    val context = LocalContext.current
    val myLoading = TtsReader.loading && TtsReader.activeId == id
    val myActive = TtsReader.speaking && TtsReader.activeId == id
    val enabled = text != null
    val fg = if (onLight) Color.Black else PureWhite
    val dim = if (onLight) Color.Black.copy(alpha = 0.35f) else SilverGrey.copy(alpha = 0.5f)
    val idleBg = if (onLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (myLoading) fg.copy(alpha = 0.14f) else if (myActive) fg.copy(alpha = 0.18f) else idleBg)
            .clickable(enabled = enabled) {
                when {
                    myLoading -> TtsReader.stop()
                    myActive -> TtsReader.stop()
                    else -> text?.let { TtsReader.speak(context, it, language, id) }
                }
            }
            .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 8.dp else 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (myLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (compact) 15.dp else 16.dp),
                color = if (onLight) Color.Black else PureWhite,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = if (myActive) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                contentDescription = if (myActive) "Stop reading" else "Read aloud",
                tint = if (enabled) fg else dim,
                modifier = Modifier.size(if (compact) 15.dp else 16.dp)
            )
        }
        if (!compact) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    myLoading -> "Loading..."
                    myActive -> "Stop reading"
                    else -> "Read aloud"
                },
                color = if (enabled) fg else dim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}