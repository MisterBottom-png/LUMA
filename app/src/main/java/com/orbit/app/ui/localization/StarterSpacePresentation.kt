package com.orbit.app.ui.localization

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.orbit.app.R
import com.orbit.app.data.local.StarterSpaceTemplate

/**
 * Keeps the seeded storage values stable for matching and backup while presenting
 * their names in the selected app language. User-created and renamed Spaces pass
 * through unchanged.
 */
@Composable
fun localizedSpaceName(storedName: String): String = starterSpaceNameRes(storedName)
    ?.let { stringResource(it) }
    ?: storedName

@Composable
fun localizedStarterSpaceName(template: StarterSpaceTemplate): String =
    stringResource(requireNotNull(starterSpaceNameRes(template.storedName)))

@StringRes
internal fun starterSpaceNameRes(storedName: String): Int? = when (storedName) {
    "Work" -> R.string.core_starter_space_work
    "Personal" -> R.string.core_starter_space_personal
    "Car" -> R.string.core_starter_space_car
    "Dog" -> R.string.core_starter_space_dog
    "Money" -> R.string.core_starter_space_money
    "Ideas" -> R.string.core_starter_space_ideas
    "Home" -> R.string.core_starter_space_home
    "Household" -> R.string.core_starter_space_household
    "Health" -> R.string.core_starter_space_health
    "Learning" -> R.string.core_starter_space_learning
    else -> null
}
