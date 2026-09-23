package com.inkfirst.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.inkfirst.app.data.ApiKeyStore
import com.inkfirst.core.Course
import com.inkfirst.core.CourseStore
import com.inkfirst.core.KtorGeminiClient
import com.inkfirst.core.LabelGenerator
import com.inkfirst.core.UiLabels
import com.inkfirst.core.needsTranslation

/**
 * The book's own language, for the furniture around its pages.
 *
 * Provided by the screens that belong to a BOOK — the reader, its contents,
 * its notes, its search — and deliberately not by the Library, Settings, New
 * course or Backup. Those belong to the person using the app rather than to
 * any one book, which is the same line already drawn for `language`, `units`
 * and `figures`: per course, not per device. A Russian book on an English
 * device should be Russian all the way to its page numbers; the shelf it sits
 * on should not change language because of what is on it.
 *
 * Defaults to English, so a screen that forgets to provide labels shows the
 * strings as written rather than nothing at all.
 */
internal val LocalLabels = staticCompositionLocalOf { UiLabels.ENGLISH }

/** This string in the book's language, or the string itself. */
@Composable
@ReadOnlyComposable
internal fun t(english: String): String = LocalLabels.current.t(english)

/** The same, with `{name}` placeholders filled in after translation. */
@Composable
@ReadOnlyComposable
internal fun t(english: String, vararg values: Pair<String, Any>): String =
    LocalLabels.current.t(english, *values)

/**
 * Reads a book's labels once per language.
 *
 * Keyed on the language rather than on the course, because that is what the
 * file is keyed on: opening a second Russian book re-uses the same words and
 * does not re-read them.
 */
@Composable
internal fun rememberBookLabels(store: CourseStore, course: Course?): UiLabels {
    val language = course?.language
    return remember(language) {
        if (language.isNullOrBlank()) UiLabels.ENGLISH else store.readLabels(language)
    }
}

/**
 * Translates the book chrome for a language the shelf has not seen before.
 *
 * Returns true if it wrote a file, so a caller can re-read the labels it is
 * showing. Once per LANGUAGE for the life of the library — the second Russian
 * book finds the first one's file and makes no call at all.
 *
 * Runs without being asked, which is a deliberate exception to the rule that
 * spending is a decision the reader makes. That rule is about PICTURES, which
 * cost about three cents each; this is one text call against the cheap model,
 * costing a fraction of a cent once per language, and the alternative is a
 * Russian book sitting inside English furniture until someone finds a setting
 * they have no reason to look for.
 *
 * Every failure is swallowed. There is no state to repair: no file means the
 * chrome stays English, and the next time a book in that language is opened
 * it tries again.
 */
internal suspend fun ensureLabels(
    store: CourseStore,
    apiKeyStore: ApiKeyStore,
    language: String?,
): Boolean {
    if (language == null || !needsTranslation(language)) return false
    if (store.hasLabels(language)) return false
    val apiKey = apiKeyStore.getApiKey().orEmpty()
    if (apiKey.isBlank()) return false

    return try {
        KtorGeminiClient().use { client ->
            val translated = LabelGenerator(client, apiKeyStore.getModelName())
                .translate(language = language, apiKey = apiKey)
            if (translated.isEmpty()) return false
            store.writeLabels(language, translated)
            true
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        System.err.println("label translation for $language failed: ${e::class.simpleName}: ${e.message}")
        false
    }
}
