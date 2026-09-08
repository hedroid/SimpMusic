package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
// Deliberate wildcard: androidx material3 1.5.0-alpha26 turned ExposedDropdownMenuBoxScope's
// ExposedDropdownMenu member into a top-level extension, while the desktop JB material3
// (1.12.0-alpha01 ≈ androidx alpha19) still ships the member. A wildcard tolerates both shapes;
// a specific import compiles on exactly one target. See DropdownButton.kt / ModelIdDropdownField.kt.
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.maxrave.simpmusic.ui.theme.typo

/**
 * The shortlist offered by the dropdown — major languages only; anything else can be typed
 * by hand. Native names (as in SUPPORTED_LANGUAGE) so no translation is needed. `zh` stays
 * bare because the SimpMusic lyrics server only accepts 2-letter codes; `zh-Hant` trades
 * that lookup for AI/YouTube fallback to get Traditional.
 */
val COMMON_TRANSLATION_LANGUAGES: List<Pair<String, String>> =
    listOf(
        "zh" to "简体中文",
        "zh-Hant" to "繁體中文",
        "en" to "English",
        "ja" to "日本語",
        "ko" to "한국어",
    )

/** Friendly name for a stored language code; anything outside the shortlist shows as the raw code. */
fun languageDisplayName(code: String): String =
    COMMON_TRANSLATION_LANGUAGES.firstOrNull { it.first == code }?.second ?: code

/**
 * The app language as a dialog prefill: the shortlist entry when one matches ("zh-Hant" for
 * Traditional Chinese, including zh-TW/HK/MO locales), otherwise the language's 2-letter
 * code. The stored app language is empty while following the system locale, so the caller
 * passes the runtime locale tag ([androidx.compose.ui.text.intl.Locale.current]) as the
 * fallback. Mirrors the default the DataStore layer resolves for consumers, so saving the
 * prefilled value changes nothing.
 */
fun appLanguageToTranslationCode(
    appLanguage: String?,
    systemLanguageTag: String,
): String {
    val tag = appLanguage?.takeIf { it.isNotEmpty() } ?: systemLanguageTag
    val isTraditionalChinese =
        tag.startsWith("zh-Hant", ignoreCase = true) ||
            Regex("^zh[-_](tw|hk|mo)", RegexOption.IGNORE_CASE).containsMatchIn(tag)
    return when {
        isTraditionalChinese -> "zh-Hant"
        tag.length >= 2 -> tag.substring(0..1)
        else -> "en"
    }
}

/**
 * Editable text field holding a language code, with a dropdown of common languages.
 * Picking an entry writes its code into the field; anything else can be typed by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    emptyHint: String?,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingError: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            isError = isError,
            singleLine = true,
            // Unify with the app's other dropdown fields (DropdownButton): the theme's
            // default bodyLarge (18sp) dwarfs the dialog around it.
            textStyle = typo().bodyMedium,
            label = label?.takeIf { it.isNotEmpty() }?.let { text -> { Text(text = text) } },
            placeholder = emptyHint?.let { hint -> { Text(text = hint, style = typo().bodyMedium) } },
            supportingText = {
                if (isError && supportingError != null) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = supportingError,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier =
                Modifier
                    .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // The M3 default menu container sits below the AlertDialog's surface, which the
            // AMOLED dark scheme turns into a near-black slab floating on the dialog. Match
            // the dialog surface (surfaceContainerHigh) so the menu reads as part of it.
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            COMMON_TRANSLATION_LANGUAGES.forEach { (code, name) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "$name ($code)",
                            style = typo().bodyMedium,
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        onValueChange(code)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
