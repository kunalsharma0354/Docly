package com.nexora.docly.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexora.docly.R
import com.nexora.docly.ui.components.TransparentCard
import com.nexora.docly.ui.theme.InkBlack
import com.nexora.docly.ui.theme.PureWhite
import com.nexora.docly.ui.theme.SilverGrey

private const val GITHUB_URL = "https://github.com/kunalsharma0354"
private const val DISCORD_URL = "https://discord.gg/Bfay2C89f5"
private const val EMAIL = "Kunalsharma9321@gmail.com"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    fun openLink(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun sendEmail() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL"))
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
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
                text = "About",
                color = PureWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            SectionLabel("The App")
            Spacer(Modifier.height(12.dp))

            TransparentCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = "Docly logo",
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, PureWhite.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Docly",
                        color = PureWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "by NEXORA",
                        color = SilverGrey,
                        fontSize = 12.sp,
                        letterSpacing = 3.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Docly is a sleek, AI-powered document assistant. It reads PDFs, " +
                            "Word, Excel, PowerPoint, e-books, CSVs and even images, then turns them " +
                            "into a clean, short summary and answers any question you have about them " +
                            "in 85+ languages. Built for Android 10 to 16, with a calm, " +
                            "monochrome design that stays out of your way.",
                        color = SilverGrey,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = PureWhite.copy(alpha = 0.12f))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Made with",
                        color = SilverGrey.copy(alpha = 0.8f),
                        fontSize = 10.5.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Kotlin \u00B7 Jetpack Compose \u00B7 Material 3",
                        color = PureWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Mistral AI \u00B7 PDFBox \u00B7 ML Kit OCR",
                        color = PureWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("The Developer")
            Spacer(Modifier.height(12.dp))

            TransparentCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .clip(CircleShape)
                            .border(2.dp, PureWhite.copy(alpha = 0.55f), CircleShape)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.developer),
                            contentDescription = "Kunal Sharma",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Kunal Sharma",
                        color = PureWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Developer \u00B7 Creator of Docly",
                        color = SilverGrey,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Kunal is an Android developer who loves building clean, " +
                            "monochrome apps that feel effortless. Docly is his way of making " +
                            "documents less stressful — one tap, one summary, zero clutter.",
                        color = SilverGrey,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))

                    LinkButton(
                        icon = {
                            Image(
                                painter = painterResource(R.drawable.ic_github),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = "GitHub Profile",
                        onClick = { openLink(GITHUB_URL) }
                    )
                    Spacer(Modifier.height(10.dp))
                    LinkButton(
                        icon = {
                            Image(
                                painter = painterResource(R.drawable.ic_discord),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = "Join the Discord Server",
                        onClick = { openLink(DISCORD_URL) }
                    )
                    Spacer(Modifier.height(10.dp))
                    LinkButton(
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Email,
                                contentDescription = null,
                                tint = PureWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = EMAIL,
                        onClick = { sendEmail() }
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = "Version 1.0",
                color = SilverGrey.copy(alpha = 0.6f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "\u00A9 2026 NEXORA \u00B7 All rights reserved",
                color = SilverGrey.copy(alpha = 0.6f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            color = PureWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = PureWhite.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun LinkButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PureWhite.copy(alpha = 0.09f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = PureWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}