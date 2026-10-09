package com.orbit.app.ui.screens.home

import androidx.annotation.StringRes
import com.orbit.app.R
import com.orbit.app.domain.analyzer.CaptureAnalyzerSource
import com.orbit.app.domain.analyzer.CaptureConfidence
import com.orbit.app.domain.analyzer.CaptureLifeSignal

/** Analyzer enums keep English names for logs and prompts; the UI shows these. */
@StringRes
internal fun CaptureAnalyzerSource.labelRes(): Int = when (this) {
    CaptureAnalyzerSource.Gemini -> R.string.analysis_source_gemini
    CaptureAnalyzerSource.Local, CaptureAnalyzerSource.GeminiFallback -> R.string.analysis_source_local
}

@StringRes
internal fun CaptureConfidence.labelRes(): Int = when (this) {
    CaptureConfidence.High -> R.string.analysis_confidence_high
    CaptureConfidence.Medium -> R.string.analysis_confidence_medium
    CaptureConfidence.Low -> R.string.analysis_confidence_low
}

/** Null for "open loop", which is the default and not worth a chip. */
@StringRes
internal fun CaptureLifeSignal.labelResOrNull(): Int? = when (this) {
    CaptureLifeSignal.None -> null
    CaptureLifeSignal.WaitingFor -> R.string.analysis_signal_waiting_for
    CaptureLifeSignal.Someday -> R.string.analysis_signal_someday
    CaptureLifeSignal.Reflection -> R.string.analysis_signal_reflection
}
