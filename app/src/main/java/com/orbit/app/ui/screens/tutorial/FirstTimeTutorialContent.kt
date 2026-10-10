package com.orbit.app.ui.screens.tutorial

import androidx.annotation.StringRes
import com.orbit.app.R

/** What each guide page shows above its words: a small piece of the real app. */
internal enum class TutorialIllustration {
    Write,
    Suggest,
    Spaces,
    Private,
}

internal data class FirstTimeTutorialPage(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
    val illustration: TutorialIllustration,
)

/** Four short steps: show, don't tell, and get the user into the app in under a minute. */
internal val firstTimeTutorialPages = listOf(
    FirstTimeTutorialPage(
        titleRes = R.string.tutorial_write_title,
        bodyRes = R.string.tutorial_write_body,
        illustration = TutorialIllustration.Write,
    ),
    FirstTimeTutorialPage(
        titleRes = R.string.tutorial_suggest_title,
        bodyRes = R.string.tutorial_suggest_body,
        illustration = TutorialIllustration.Suggest,
    ),
    FirstTimeTutorialPage(
        titleRes = R.string.tutorial_spaces_pick_title,
        bodyRes = R.string.tutorial_spaces_pick_body,
        illustration = TutorialIllustration.Spaces,
    ),
    FirstTimeTutorialPage(
        titleRes = R.string.tutorial_private_title,
        bodyRes = R.string.tutorial_private_body,
        illustration = TutorialIllustration.Private,
    ),
)
