package com.selfcare.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.selfcare.app.R
import com.selfcare.app.ui.icons.AppIcons

private const val GROQ_KEYS_CONSOLE_URL = "https://console.groq.com/keys"

/**
 * Beginner-friendly 4-step guide explaining what Groq is, why it is optional/free,
 * and how to create & paste a free API key in under a minute.
 */
@Composable
fun GroqSetupGuideDialog(
    onDismiss: () -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("groq_setup_guide_dialog"),
        icon = {
            Icon(
                imageVector = AppIcons.Sparkle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.groq_guide_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.groq_guide_what_is_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.groq_guide_what_is_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                GroqGuideStepRow(
                    stepNumber = "1",
                    title = stringResource(R.string.groq_guide_step1_title),
                    body = stringResource(R.string.groq_guide_step1_body)
                )
                GroqGuideStepRow(
                    stepNumber = "2",
                    title = stringResource(R.string.groq_guide_step2_title),
                    body = stringResource(R.string.groq_guide_step2_body)
                )
                GroqGuideStepRow(
                    stepNumber = "3",
                    title = stringResource(R.string.groq_guide_step3_title),
                    body = stringResource(R.string.groq_guide_step3_body)
                )
                GroqGuideStepRow(
                    stepNumber = "4",
                    title = stringResource(R.string.groq_guide_step4_title),
                    body = stringResource(R.string.groq_guide_step4_body)
                )

                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(GROQ_KEYS_CONSOLE_URL))
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("groq_guide_open_console_btn")
                ) {
                    Icon(
                        imageVector = AppIcons.Globe,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "  ${stringResource(R.string.groq_guide_open_console)}",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (onOpenSettings != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenSettings()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("groq_guide_open_settings_btn")
                    ) {
                        Icon(
                            imageVector = AppIcons.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(text = "  ${stringResource(R.string.groq_guide_go_settings)}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("groq_guide_close_btn")
            ) {
                Text(text = stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun GroqGuideStepRow(
    stepNumber: String,
    title: String,
    body: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
