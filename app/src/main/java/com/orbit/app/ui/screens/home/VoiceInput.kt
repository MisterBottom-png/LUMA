package com.orbit.app.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.orbit.app.R
import com.orbit.app.capture.OnDeviceVoiceSession
import com.orbit.app.capture.VoiceCapture
import java.util.Locale

/** What this phone offers for dictation. */
internal data class VoiceSupport(val onDevice: Boolean, val systemScreen: Boolean) {
    val available: Boolean get() = onDevice || systemScreen
    val route: VoiceCapture.Route get() = VoiceCapture.route(Build.VERSION.SDK_INT, onDevice)

    companion object {
        fun of(context: Context) = VoiceSupport(
            onDevice = VoiceCapture.isOnDeviceAvailable(context),
            systemScreen = VoiceCapture.hasSystemScreen(context),
        )
    }
}

/** The mic on Home: [start] is null when the phone cannot take dictation at all. */
internal class VoiceInputState(
    val start: (() -> Unit)?,
    val listening: Boolean,
)

@StringRes
internal fun VoiceCapture.Problem.textRes(): Int = when (this) {
    VoiceCapture.Problem.NothingHeard -> R.string.voice_nothing_heard
    VoiceCapture.Problem.LanguageMissing, VoiceCapture.Problem.Unavailable -> R.string.voice_unavailable
}

/**
 * Dictation for Home. On phones with on-device recognition it listens right here (after
 * the microphone permission), so the words stay on the phone. Otherwise, or when the
 * microphone is not allowed or the language is missing on the device, it opens the
 * phone's speech screen, saying once beforehand that this may use Google's servers.
 * A second tap while listening stops and keeps what was heard.
 */
@Composable
internal fun rememberVoiceInput(
    locale: Locale,
    prompt: String,
    cloudNoticeSeen: Boolean,
    onCloudNoticeSeen: () -> Unit,
    onTranscript: (String) -> Unit,
    onProblem: (Int) -> Unit,
    detectSupport: (Context) -> VoiceSupport = VoiceSupport::of,
): VoiceInputState {
    val context = LocalContext.current
    val support = remember(context) { detectSupport(context) }
    val available = support.available
    val route = support.route
    val hasSystemScreen = support.systemScreen
    val latestTranscript by rememberUpdatedState(onTranscript)
    val latestProblem by rememberUpdatedState(onProblem)
    val noticeSeen by rememberUpdatedState(cloudNoticeSeen)
    var listening by remember { mutableStateOf(false) }
    var showCloudNotice by rememberSaveable { mutableStateOf(false) }
    val session = remember { arrayOfNulls<OnDeviceVoiceSession>(1) }
    DisposableEffect(Unit) {
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) session[0]?.destroy()
            session[0] = null
        }
    }

    val systemLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        VoiceCapture.transcript(result.data)?.let { latestTranscript(it) }
    }
    val openSystemScreen: () -> Unit = {
        runCatching { systemLauncher.launch(VoiceCapture.intent(locale, prompt)) }
            .onFailure { latestProblem(R.string.voice_unavailable) }
    }
    val useSystemScreen: () -> Unit = {
        when {
            !hasSystemScreen -> latestProblem(R.string.voice_unavailable)
            VoiceCapture.shouldShowCloudNotice(VoiceCapture.Route.SystemScreen, noticeSeen) -> showCloudNotice = true
            else -> openSystemScreen()
        }
    }
    val listenOnDevice: () -> Unit = listen@{
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@listen useSystemScreen()
        session[0]?.destroy()
        val turn = runCatching {
            OnDeviceVoiceSession(
                context = context,
                locale = locale,
                onTranscript = { latestTranscript(it) },
                onProblem = { problem ->
                    // The device lacks this language: the phone's own speech screen may still have it.
                    if (problem == VoiceCapture.Problem.LanguageMissing && hasSystemScreen) {
                        useSystemScreen()
                    } else {
                        latestProblem(problem.textRes())
                    }
                },
                onFinished = { listening = false },
            )
        }.getOrNull() ?: return@listen useSystemScreen()
        session[0] = turn
        listening = true
        runCatching { turn.start() }.onFailure {
            listening = false
            useSystemScreen()
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listenOnDevice() else useSystemScreen()
    }

    if (showCloudNotice) {
        AlertDialog(
            onDismissRequest = { showCloudNotice = false },
            text = { Text(stringResource(R.string.voice_cloud_notice)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloudNotice = false
                        onCloudNoticeSeen()
                        openSystemScreen()
                    },
                ) { Text(stringResource(R.string.voice_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showCloudNotice = false }) { Text(stringResource(R.string.core_cancel)) }
            },
        )
    }

    val start: (() -> Unit)? = if (!available) {
        null
    } else {
        {
            when {
                listening -> session[0]?.stop()
                route == VoiceCapture.Route.OnDevice -> {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) listenOnDevice() else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }
                else -> useSystemScreen()
            }
        }
    }
    return VoiceInputState(start = start, listening = listening)
}
