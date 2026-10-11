package com.orbit.app.ui.screens.home

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.speech.RecognizerIntent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.testing.JvmComposeRule
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * On a phone without on-device recognition, the first mic tap says once that the
 * phone's speech screen may use Google's servers; after that the mic just opens it.
 */
@RunWith(AndroidJUnit4::class)
class VoiceCloudNoticeJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    private val notice = "Voice may use Google’s servers on this phone."
    private var voice: VoiceInputState? = null
    private var noticeSeenCalls = 0

    private fun addSpeechScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "test.speech"
                name = "test.speech.RecognizeActivity"
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), resolveInfo)
    }

    private fun show(noticeSeen: Boolean, support: VoiceSupport = VoiceSupport(onDevice = false, systemScreen = true)) {
        composeRule.setContent {
            MaterialTheme {
                voice = rememberVoiceInput(
                    locale = Locale.ENGLISH,
                    prompt = "prompt",
                    cloudNoticeSeen = noticeSeen,
                    onCloudNoticeSeen = { noticeSeenCalls += 1 },
                    onTranscript = {},
                    onProblem = {},
                    detectSupport = { support },
                )
            }
        }
    }

    @Test
    fun noSpeechRecognitionOnThePhone_meansNoMic() {
        show(noticeSeen = false, support = VoiceSupport(onDevice = false, systemScreen = false))

        composeRule.runOnIdle { assertNull(voice?.start) }
    }

    @Test
    fun theFirstTapExplainsTheServers_andContinueRemembersIt() {
        addSpeechScreen()
        show(noticeSeen = false)

        composeRule.runOnIdle { voice?.start?.invoke() }
        composeRule.onNodeWithText(notice).assertExists()
        composeRule.onNodeWithText("Continue").performClick()

        composeRule.onNodeWithText(notice).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, noticeSeenCalls) }
    }

    @Test
    fun onceSeen_theMicOpensTheSpeechScreenWithoutAsking() {
        addSpeechScreen()
        show(noticeSeen = true)

        composeRule.runOnIdle { voice?.start?.invoke() }
        composeRule.onNodeWithText(notice).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, noticeSeenCalls) }
    }

    @Test
    fun onDeviceRecognition_neverShowsTheServersNotice() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(context).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        show(noticeSeen = false, support = VoiceSupport(onDevice = true, systemScreen = true))

        composeRule.runOnIdle { voice?.start?.invoke() }
        composeRule.onNodeWithText(notice).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, noticeSeenCalls) }
    }
}
