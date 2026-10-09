package com.orbit.app.ui.screens.tutorial

import androidx.annotation.StringRes
import com.orbit.app.R

internal enum class TutorialIllustration {
    Idea,
    Home,
    Confirm,
    BrainDump,
    Spaces,
    Review,
    Control,
}

internal data class FirstTimeTutorialPage(
    @param:StringRes val stepRes: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
    @param:StringRes val principleTitleRes: Int,
    @param:StringRes val principleBodyRes: Int,
    val illustration: TutorialIllustration,
)

internal val firstTimeTutorialPages = listOf(
    FirstTimeTutorialPage(
        stepRes = R.string.tutorial_idea_step,
        titleRes = R.string.tutorial_idea_title,
        bodyRes = R.string.tutorial_idea_body,
        principleTitleRes = R.string.tutorial_idea_principle_title,
        principleBodyRes = R.string.tutorial_idea_principle_body,
        illustration = TutorialIllustration.Idea,
    ),
    FirstTimeTutorialPage(
        stepRes = R.string.tutorial_home_step,
        titleRes = R.string.tutorial_home_title,
        bodyRes = R.string.tutorial_home_body,
        principleTitleRes = R.string.tutorial_home_principle_title,
        principleBodyRes = R.string.tutorial_home_principle_body,
        illustration = TutorialIllustration.Home,
    ),
    FirstTimeTutorialPage(
        stepRes = R.string.tutorial_confirm_step,
        titleRes = R.string.tutorial_confirm_title,
        bodyRes = R.string.tutorial_confirm_body,
        principleTitleRes = R.string.tutorial_confirm_principle_title,
        principleBodyRes = R.string.tutorial_confirm_principle_body,
        illustration = TutorialIllustration.Confirm,
    ),
    FirstTimeTutorialPage(
        stepRes = R.string.tutorial_brain_dump_step,
        titleRes = R.string.tutorial_brain_dump_title,
        bodyRes = R.string.tutorial_brain_dump_body,
        principleTitleRes = R.string.tutorial_brain_dump_principle_title,
        principleBodyRes = R.string.tutorial_brain_dump_principle_body,
        illustration = TutorialIllustration.BrainDump,
    ),
    FirstTimeTutorialPage(
        stepRes = R.string.tutorial_spaces_step,
        titleRes = R.string.tutorial_spaces_title,
        bodyRes = R.string.tutorial_spaces_body,
        principleTitleRes = R.string.tutorial_spaces_principle_title,
        principleBodyRes = R.string.tutorial_spaces_principle_body,
        illustration = TutorialIllustration.Spaces,
    ),
    FirstTimeTutorialPage(
        stepRes = R.string.tutorial_review_step,
        titleRes = R.string.tutorial_review_title,
        bodyRes = R.string.tutorial_review_body,
        principleTitleRes = R.string.tutorial_review_principle_title,
        principleBodyRes = R.string.tutorial_review_principle_body,
        illustration = TutorialIllustration.Review,
    ),
    FirstTimeTutorialPage(
        stepRes = R.string.tutorial_control_step,
        titleRes = R.string.tutorial_control_title,
        bodyRes = R.string.tutorial_control_body,
        principleTitleRes = R.string.tutorial_control_principle_title,
        principleBodyRes = R.string.tutorial_control_principle_body,
        illustration = TutorialIllustration.Control,
    ),
)
