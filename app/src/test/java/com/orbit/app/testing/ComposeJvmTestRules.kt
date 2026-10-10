package com.orbit.app.testing

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf

/**
 * Compose's host activity is declared by ui-test-manifest, which only the debug
 * variant merges. Registering it with Robolectric lets the same tests run in
 * testReleaseUnitTest without adding a test activity to the release manifest.
 */
class RegisterComposeHostActivityRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            shadowOf(context.packageManager).addActivityIfNotPresent(
                ComponentName(context, ComponentActivity::class.java),
            )
            base.evaluate()
        }
    }
}

/** A compose rule that works in both debug and release unit-test variants. */
class JvmComposeRule private constructor(
    val compose: ComposeContentTestRule,
) : ComposeContentTestRule by compose {
    constructor() : this(createComposeRule())

    override fun apply(base: Statement, description: Description): Statement =
        RuleChain.outerRule(RegisterComposeHostActivityRule()).around(compose).apply(base, description)
}
