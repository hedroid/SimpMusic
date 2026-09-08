package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
// Deliberate wildcard: androidx material3 1.5.0-alpha26 turned ExposedDropdownMenuBoxScope's
// ExposedDropdownMenu member into a top-level extension, while the desktop JB material3
// (1.12.0-alpha01 ≈ androidx alpha19) still ships the member. A wildcard tolerates both shapes;
// a specific import compiles on exactly one target. See DropdownButton.kt.
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.AiModelsState
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.fetch_models_failed
import simpmusic.composeapp.generated.resources.no_models_found
import simpmusic.composeapp.generated.resources.processing

/**
 * Editable model-ID text field whose dropdown lists the models fetched from the current
 * AI provider. Opening the dropdown triggers [onOpen] (fetch/retry); picking an entry
 * writes it into the field. No label — the dialog title already names the field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelIdDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    state: AiModelsState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingError: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { open ->
            expanded = open
            if (open) onOpen()
        },
        modifier = modifier,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            isError = isError,
            singleLine = true,
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
                if (state is AiModelsState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier =
                Modifier
                    .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            when (state) {
                is AiModelsState.Success ->
                    if (state.models.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(Res.string.no_models_found),
                                    style = typo().bodyMedium,
                                )
                            },
                            onClick = {},
                            enabled = false,
                        )
                    } else {
                        Box(
                            Modifier
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Column {
                                state.models.forEach { modelId ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = modelId,
                                                style = typo().bodyMedium,
                                                maxLines = 1,
                                            )
                                        },
                                        onClick = {
                                            onValueChange(modelId)
                                            expanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                    )
                                }
                            }
                        }
                    }

                is AiModelsState.Error ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text =
                                    stringResource(
                                        Res.string.fetch_models_failed,
                                        state.reason ?: "",
                                    ),
                                style = typo().bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        // Tap the error to retry the fetch.
                        onClick = onOpen,
                    )

                else ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(Res.string.processing),
                                    style = typo().bodyMedium,
                                )
                            }
                        },
                        onClick = {},
                        enabled = false,
                    )
            }
        }
    }
}
