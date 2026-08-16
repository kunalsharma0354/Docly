package com.nexora.docly

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nexora.docly.data.SelectedFile
import com.nexora.docly.security.Protect
import com.nexora.docly.ui.screens.HomeScreen
import com.nexora.docly.ui.screens.PermissionPromptScreen
import com.nexora.docly.ui.screens.SummaryScreen
import com.nexora.docly.ui.theme.DoclyTheme
import com.nexora.docly.util.Permissions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Protect.init(applicationContext)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DoclyTheme {
                DoclyApp()
            }
        }
    }
}

private sealed interface Step {
    data object Permission : Step
    data object Home : Step
    data class Result(val files: List<SelectedFile>) : Step
}

private val StepSaver = listSaver<Step, String>(
    save = { step ->
        when (step) {
            is Step.Permission -> listOf("permission")
            is Step.Home -> listOf("home")
            is Step.Result -> listOf("result") +
                    step.files.flatMap { listOf(it.uri.toString(), it.name) }
        }
    },
    restore = { values ->
        when (values.firstOrNull()) {
            "permission" -> Step.Permission
            "home" -> Step.Home
            else -> Step.Result(
                values.drop(1).chunked(2).map { (uri, name) ->
                    SelectedFile(Uri.parse(uri), name)
                }
            )
        }
    }
)

@Composable
fun DoclyApp() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var step by rememberSaveable(stateSaver = StepSaver) { mutableStateOf<Step>(Step.Permission) }
    var permissionDeniedForever by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            step = Step.Home
        } else {
            permissionDeniedForever = activity != null &&
                    Permissions.requiredPermissions().all { p ->
                        !activity.shouldShowRequestPermissionRationale(p)
                    }
        }
    }

    LaunchedEffect(Unit) {
        if (Permissions.allGranted(context)) {
            step = Step.Home
        } else {
            permissionLauncher.launch(Permissions.requiredPermissions())
        }
    }

    when (val current = step) {
        is Step.Permission -> PermissionPromptScreen(
            permanentlyDenied = permissionDeniedForever,
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                )
            },
            onGrant = { permissionLauncher.launch(Permissions.requiredPermissions()) },
            onSkip = { step = Step.Home }
        )

        is Step.Home -> HomeScreen(
            onSend = { files -> step = Step.Result(files) }
        )

        is Step.Result -> SummaryScreen(
            files = current.files,
            onBack = { step = Step.Home }
        )
    }
}