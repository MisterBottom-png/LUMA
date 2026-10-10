package com.orbit.app.domain.analyzer

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ReviewLoopType { Task, Capture }

data class ReviewLoop(
    val id: Long,
    val type: ReviewLoopType,
    val title: String,
    val updatedAt: Long,
    val hasPendingBrainDump: Boolean = false,
) {
    val key: String = "${type.name}_$id"
}

data class TinyActionSuggestion(
    val sourceKey: String,
    val sourceTitle: String,
    val action: String,
    val sourceLabel: String = "Local suggestion",
)

class LocalReviewAnalyzer {
    fun findStaleLoops(
        tasks: List<TaskEntity>,
        captures: List<CaptureEntity>,
        staleLoopDays: Int = DefaultStaleLoopDays,
        now: Long = System.currentTimeMillis(),
    ): List<ReviewLoop> {
        val safeDays = staleLoopDays.coerceAtLeast(1)
        val staleBefore = now - TimeUnit.DAYS.toMillis(safeDays.toLong())
        val taskLoops = tasks
            .asSequence()
            .filter { it.status == TaskStatus.Open || it.status == TaskStatus.WaitingFor }
            .filter { it.updatedAt <= staleBefore }
            .map { ReviewLoop(it.id, ReviewLoopType.Task, it.title, it.updatedAt) }
        val captureLoops = captures
            .asSequence()
            .filter { it.status == CaptureStatus.Inbox }
            .filter { it.updatedAt <= staleBefore }
            .map { ReviewLoop(it.id, ReviewLoopType.Capture, it.rawText, it.updatedAt) }

        return (taskLoops + captureLoops).sortedBy { it.updatedAt }.toList()
    }

    fun makeSmaller(loop: ReviewLoop, locale: Locale = Locale.ENGLISH): TinyActionSuggestion {
        val title = loop.title.trim().ifEmpty { "this open loop" }
        return TinyActionSuggestion(loop.key, title, makeSmallerText(title, locale))
    }

    companion object {
        const val DefaultStaleLoopDays = 7

        fun makeSmallerText(text: String, locale: Locale = Locale.ENGLISH): String {
            val title = text.trim().ifEmpty { "this open loop" }
            val lower = title.lowercase()
            val guidanceLocale = localGuidanceLocale(title, locale)
            return when (guidanceLocale.language) {
                "et" -> makeSmallerInEstonian(title, lower)
                "ru" -> makeSmallerInRussian(title, lower)
                else -> makeSmallerInEnglish(title, lower)
            }
        }

        private fun makeSmallerInEnglish(title: String, lower: String): String = when {
                lower.contains("money") || lower.contains("budget") ||
                    lower.contains("salary") || lower.contains("pay") ->
                    "Check one small expense category."

                lower.contains("clean") || lower.contains("home") ||
                    lower.contains("apartment") || lower.contains("tidy") ->
                    "Put one thing back where it belongs."

                lower.contains("car") || lower.contains("audi") ||
                    lower.contains("lexus") || lower.contains("mazda") ||
                    lower.contains("sound") ->
                    "Write down when the sound happens."

                lower.contains("project") || lower.contains("work") ||
                    lower.contains("plan") || lower.contains("change management") ->
                    "List the first three questions."

                lower.contains("too much") || lower.contains("overwhelming") ||
                    lower.contains("everything") ->
                    "Write one sentence about what makes this hard."

                lower.contains(" and ") -> {
                    val firstPart = title.substringBefore(" and ", missingDelimiterValue = title)
                    "Start with just this: $firstPart."
                }

                StartsWithCommunicationWord.any { lower.startsWith(it) } ->
                    "Write one sentence you want to say, then decide whether to send it."

                StartsWithMakingWord.any { lower.startsWith(it) } ->
                    "Open a blank note and make a rough first line."

                StartsWithTidyingWord.any { lower.startsWith(it) } ->
                    "Spend five minutes on the smallest visible part."

                else -> "Take two minutes to name the very first physical step."
            }

        private fun makeSmallerInEstonian(title: String, lower: String): String = when {
            lower.contains("raha") || lower.contains("eelarve") || lower.contains("palk") ->
                "Vaata üle üks väike kulukategooria."
            lower.contains("korista") || lower.contains("kodu") || lower.contains("korter") ->
                "Pane üks asi tagasi oma kohale."
            lower.contains("auto") || lower.contains("heli") ->
                "Pane kirja, millal heli tekib."
            lower.contains("töö") || lower.contains("projekt") || lower.contains("plaan") ->
                "Kirjuta üles kolm esimest küsimust."
            lower.contains("liiga palju") || lower.contains("üle jõu") ->
                "Kirjuta üks lause sellest, mis selle raskeks teeb."
            " ja " in lower -> "Alusta ainult sellest: ${title.substringBefore(" ja ")}."
            else -> "Võta kaks minutit, et nimetada kõige esimene konkreetne samm."
        }

        private fun makeSmallerInRussian(title: String, lower: String): String = when {
            lower.contains("деньг") || lower.contains("бюджет") || lower.contains("зарплат") ->
                "Проверьте одну небольшую категорию расходов."
            lower.contains("уборк") || lower.contains("дом") || lower.contains("квартир") ->
                "Положите одну вещь на её место."
            lower.contains("машин") || lower.contains("авто") || lower.contains("звук") ->
                "Запишите, когда появляется этот звук."
            lower.contains("работ") || lower.contains("проект") || lower.contains("план") ->
                "Запишите первые три вопроса."
            lower.contains("слишком много") || lower.contains("тяжело") ->
                "Напишите одно предложение о том, что делает это трудным."
            " и " in lower -> "Начните только с этого: ${title.substringBefore(" и ")}."
            else -> "Потратьте две минуты, чтобы назвать самый первый конкретный шаг."
        }

        private val StartsWithCommunicationWord = listOf(
            "ask ", "call ", "email ", "message ", "send ", "reply ",
        )
        private val StartsWithMakingWord = listOf(
            "create ", "draft ", "make ", "prepare ", "write ",
        )
        private val StartsWithTidyingWord = listOf(
            "clean ", "clear ", "organize ", "sort ", "tidy ",
        )
    }
}
