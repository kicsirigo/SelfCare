package com.selfcare.app.ui.coach

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfcare.app.R
import com.selfcare.app.ui.icons.AppIcons
import com.selfcare.app.ui.theme.NeonGreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiCoachChatSheet(
    viewModel: AiCoachViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputText by remember { mutableStateOf("") }

    state.contextPreview?.let { previewText ->
        AlertDialog(
            onDismissRequest = viewModel::dismissContextPreview,
            title = { Text(stringResource(R.string.ai_coach_preview_title)) },
            text = {
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissContextPreview) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("ai_coach_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = AppIcons.Sparkle,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.ai_coach_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.lastModelUsed.isNotBlank()) {
                            Text(
                                text = state.lastModelUsed,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
                TextButton(onClick = viewModel::openContextPreview) {
                    Text(
                        text = stringResource(R.string.ai_coach_preview_btn),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (state.messages.isNotEmpty()) {
                    TextButton(onClick = viewModel::clearConversation) {
                        Text(
                            text = stringResource(R.string.ai_coach_clear_btn),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Granular Privacy Scope Chips
            Text(
                text = stringResource(R.string.ai_coach_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = state.scope.includeNutrition,
                    onClick = viewModel::toggleNutritionScope,
                    label = { Text(stringResource(R.string.ai_coach_scope_nutrition)) }
                )
                FilterChip(
                    selected = state.scope.includeFitnessWeight,
                    onClick = viewModel::toggleFitnessScope,
                    label = { Text(stringResource(R.string.ai_coach_scope_fitness)) }
                )
                FilterChip(
                    selected = state.scope.includeRoutineShelf,
                    onClick = viewModel::toggleShelfScope,
                    label = { Text(stringResource(R.string.ai_coach_scope_shelf)) }
                )
                FilterChip(
                    selected = state.scope.includeFaceTrends,
                    onClick = viewModel::toggleFaceScope,
                    label = { Text(stringResource(R.string.ai_coach_scope_face)) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // Messages List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.ai_coach_empty_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.privacy_no_ai_training_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF38BDF8)
                            )
                            val p1 = stringResource(R.string.ai_coach_prompt_timing)
                            val p2 = stringResource(R.string.ai_coach_prompt_missing)
                            val p3 = stringResource(R.string.ai_coach_prompt_skin)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(p1, p2, p3).forEach { prompt ->
                                    AssistChip(
                                        onClick = { viewModel.sendMessage(prompt) },
                                        label = { Text(prompt) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(state.messages) { msg ->
                        val isUser = msg.role == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isUser) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = if (!isUser) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                } else null,
                                modifier = Modifier.fillMaxWidth(0.88f)
                            ) {
                                Text(
                                    text = if (isUser) {
                                        AnnotatedString(msg.content)
                                    } else {
                                        formatCoachMarkdown(msg.content)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isUser) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.errorMessage?.let { err ->
                val displayErr = if (err == "NO_GROQ_KEY") {
                    stringResource(R.string.nutrition_err_no_groq_key)
                } else err
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayErr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = viewModel::clearError, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = stringResource(R.string.action_close),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text(stringResource(R.string.ai_coach_input_hint)) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_coach_input")
                )
                Button(
                    onClick = {
                        val toSend = inputText.trim()
                        if (toSend.isNotEmpty()) {
                            inputText = ""
                            viewModel.sendMessage(toSend)
                        }
                    },
                    enabled = inputText.isNotBlank() && !state.isBusy,
                    modifier = Modifier.testTag("ai_coach_send_btn")
                ) {
                    Text(stringResource(R.string.ai_coach_send))
                }
            }
        }
    }
}

internal fun formatCoachMarkdown(raw: String): AnnotatedString {
    val normalized = raw
        .replace(Regex("(?m)^\\s*[*-]\\s+"), "• ")
        .replace(Regex("(?m)^#{1,3}\\s+"), "")
    return buildAnnotatedString {
        var i = 0
        val n = normalized.length
        while (i < n) {
            if (i + 1 < n && normalized[i] == '*' && normalized[i + 1] == '*') {
                val close = normalized.indexOf("**", i + 2)
                if (close != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(normalized.substring(i + 2, close))
                    }
                    i = close + 2
                    continue
                }
            }
            if (normalized[i] == '*') {
                val close = normalized.indexOf('*', i + 1)
                if (close != -1 && close > i + 1 && !normalized.substring(i + 1, close).contains('\n')) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(normalized.substring(i + 1, close))
                    }
                    i = close + 1
                    continue
                }
            }
            append(normalized[i])
            i++
        }
    }
}
