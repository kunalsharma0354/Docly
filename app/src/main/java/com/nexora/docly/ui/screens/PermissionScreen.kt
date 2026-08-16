package com.nexora.docly.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexora.docly.ui.components.NexoraBrand
import com.nexora.docly.ui.components.TransparentCard
import com.nexora.docly.ui.theme.InkBlack
import com.nexora.docly.ui.theme.PureWhite
import com.nexora.docly.ui.theme.SilverGrey

@Composable
fun PermissionPromptScreen(
    permanentlyDenied: Boolean,
    onOpenSettings: () -> Unit,
    onGrant: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(72.dp))
        NexoraBrand()
        Spacer(Modifier.height(52.dp))

        Text(
            text = "File access",
            color = PureWhite,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Allow Docly to read your documents, spreadsheets, " +
                    "images and e-books so text extraction works smoothly.",
            color = SilverGrey,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp)
        )
        Spacer(Modifier.height(32.dp))

        FeatureRow("Android 10 \u2192 16 fully supported")
        Spacer(Modifier.height(12.dp))
        FeatureRow("PDF \u00B7 Word \u00B7 Excel \u00B7 PPT \u00B7 E-books \u00B7 Images")
        Spacer(Modifier.height(12.dp))
        FeatureRow("Private & offline \u2014 files never leave your phone")

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = { if (permanentlyDenied) onOpenSettings() else onGrant() },
            colors = ButtonDefaults.buttonColors(
                containerColor = PureWhite,
                contentColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp)
        ) {
            Text(
                text = if (permanentlyDenied) "Open Settings" else "Allow Access",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onSkip) {
            Text(
                text = "Continue anyway",
                color = SilverGrey
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "You can still pick any file \u2014 the system file " +
                    "picker works without permissions too.",
            color = SilverGrey.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureRow(text: String) {
    TransparentCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = PureWhite,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                color = PureWhite.copy(alpha = 0.90f),
                fontSize = 13.5.sp,
                lineHeight = 19.sp
            )
        }
    }
}