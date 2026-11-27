package com.selfcare.app.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.selfcare.app.R
import com.selfcare.app.ui.components.ExpressiveMotion
import com.selfcare.app.ui.icons.AppIcons
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val badgeRes: Int,
    val titleRes: Int,
    val bodyRes: Int,
    val tipRes: Int,
    val showGroqGuideAction: Boolean = false
)

/**
 * First-launch swipeable onboarding cards (also replayable anytime from Settings).
 * Uses 120Hz expressive spring indicators and zero Unicode emojis.
 */
@Composable
fun OnboardingDialog(
    onFinish: () -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    val pages = remember {
        listOf(
            OnboardingPage(
                icon = AppIcons.Lock,
                badgeRes = R.string.onboarding_p1_badge,
                titleRes = R.string.onboarding_p1_title,
                bodyRes = R.string.onboarding_p1_body,
                tipRes = R.string.onboarding_p1_tip
            ),
            OnboardingPage(
                icon = AppIcons.Sun,
                badgeRes = R.string.onboarding_p2_badge,
                titleRes = R.string.onboarding_p2_title,
                bodyRes = R.string.onboarding_p2_body,
                tipRes = R.string.onboarding_p2_tip
            ),
            OnboardingPage(
                icon = AppIcons.WaterDrop,
                badgeRes = R.string.onboarding_p3_badge,
                titleRes = R.string.onboarding_p3_title,
                bodyRes = R.string.onboarding_p3_body,
                tipRes = R.string.onboarding_p3_tip
            ),
            OnboardingPage(
                icon = AppIcons.Face,
                badgeRes = R.string.onboarding_p4_badge,
                titleRes = R.string.onboarding_p4_title,
                bodyRes = R.string.onboarding_p4_body,
                tipRes = R.string.onboarding_p4_tip
            ),
            OnboardingPage(
                icon = AppIcons.Sparkle,
                badgeRes = R.string.onboarding_p5_badge,
                titleRes = R.string.onboarding_p5_title,
                bodyRes = R.string.onboarding_p5_body,
                tipRes = R.string.onboarding_p5_tip,
                showGroqGuideAction = true
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    var showGroqGuide by remember { mutableStateOf(false) }

    if (showGroqGuide) {
        GroqSetupGuideDialog(
            onDismiss = { showGroqGuide = false },
            onOpenSettings = onOpenSettings?.let { openSettings ->
                {
                    showGroqGuide = false
                    onFinish()
                    openSettings()
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onFinish,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("onboarding_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            ),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top bar: page counter + Skip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${pages.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = onFinish,
                        modifier = Modifier.testTag("onboarding_skip_btn")
                    ) {
                        Text(text = stringResource(R.string.onboarding_skip))
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { index ->
                    val page = pages[index]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            )
                        ) {
                            Text(
                                text = stringResource(page.badgeRes),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = stringResource(page.titleRes),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = stringResource(page.bodyRes),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(page.tipRes),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }

                        if (page.showGroqGuideAction) {
                            OutlinedButton(
                                onClick = { showGroqGuide = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("onboarding_groq_guide_btn")
                            ) {
                                Icon(
                                    imageVector = AppIcons.Sparkle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "  ${stringResource(R.string.groq_guide_open_btn)}",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Expressive pill page indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pages.size) { index ->
                        val active = pagerState.currentPage == index
                        val pillWidth by animateDpAsState(
                            targetValue = if (active) 24.dp else 8.dp,
                            animationSpec = ExpressiveMotion.spatialSpring(),
                            label = "onboarding_dot_$index"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(pillWidth)
                                .clip(CircleShape)
                                .background(
                                    if (active) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom navigation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pagerState.currentPage > 0) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.onboarding_back))
                        }
                    }
                    val isLast = pagerState.currentPage == pages.lastIndex
                    Button(
                        onClick = {
                            if (isLast) {
                                onFinish()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag(if (isLast) "onboarding_finish_btn" else "onboarding_next_btn")
                    ) {
                        Text(
                            text = stringResource(
                                if (isLast) R.string.onboarding_get_started else R.string.onboarding_next
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
