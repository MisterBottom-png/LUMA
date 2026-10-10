package com.orbit.app.ui.localization

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.orbit.app.R
import com.orbit.app.domain.ai.LocalAnswerKind
import com.orbit.app.domain.ai.LocalAnswerText
import java.util.Locale

@StringRes
internal fun LocalAnswerKind.textRes(): Int = when (this) {
    LocalAnswerKind.NoMatch -> R.string.ask_found_nothing
    LocalAnswerKind.NoData -> R.string.ask_found_no_data
    LocalAnswerKind.Matches -> R.string.ask_found_matches
    LocalAnswerKind.Overdue -> R.string.ask_found_overdue
    LocalAnswerKind.Waiting -> R.string.ask_found_waiting
    LocalAnswerKind.Completed -> R.string.ask_found_completed
    LocalAnswerKind.Upcoming -> R.string.ask_found_upcoming
    LocalAnswerKind.Recent -> R.string.ask_found_recent
}

/** Local Ask answers in the language the question was asked in, from string resources. */
class ResourceLocalAnswerText(private val context: Context) : LocalAnswerText {
    override fun text(locale: Locale, kind: LocalAnswerKind, titles: String): String {
        val localized = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(locale) },
        )
        return localized.getString(kind.textRes(), titles)
    }
}
