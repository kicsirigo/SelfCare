package com.selfcare.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Material 3 Expressive motion physics tokens & reusable tactile micro-interactions.
 */
object ExpressiveMotion {
    /** Expressive spatial spring: natural, lively movement & scaling. */
    fun <T> spatialSpring() = spring<T>(
        dampingRatio = 0.62f,
        stiffness = 420f
    )

    /** Playful bouncy spring for checkmarks, completions, and goal hits. */
    fun <T> bouncySpring() = spring<T>(
        dampingRatio = 0.48f,
        stiffness = 380f
    )

    /** Crisp, zero-overshoot spring for color & opacity shifts. */
    fun <T> effectsSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 600f
    )
}

/**
 * Smooth odometer-like rolling integer text for calories, water ml, and remaining budgets.
 */
@Composable
fun AnimatedRollingInt(
    targetValue: Int,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    style: TextStyle = MaterialTheme.typography.titleLarge,
    fontWeight: FontWeight? = FontWeight.Bold,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val animated by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "rolling_int"
    )
    Text(
        text = "$prefix$animated$suffix",
        style = style,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier
    )
}

/**
 * Floating dopamine reward popup (e.g. "+250 ml") that rises, pops in with spring physics,
 * and smoothly fades out above a card or button whenever [triggerKey] increments.
 */
@Composable
fun FloatingDeltaPopup(
    triggerKey: Int,
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (triggerKey <= 0 || text.isBlank()) return

    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.6f) }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val risePx = remember(density) { with(density) { -42.dp.toPx() } }

    LaunchedEffect(triggerKey) {
        offsetY.snapTo(8f)
        alpha.snapTo(1f)
        scale.snapTo(0.65f)
        launch {
            scale.animateTo(1.12f, ExpressiveMotion.bouncySpring())
            scale.animateTo(1.0f, ExpressiveMotion.spatialSpring())
        }
        launch {
            offsetY.animateTo(risePx, tween(durationMillis = 820, easing = FastOutSlowInEasing))
        }
        alpha.animateTo(0f, tween(durationMillis = 820, delayMillis = 220))
    }

    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        modifier = modifier.graphicsLayer {
            translationY = offsetY.value
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        }
    )
}

/**
 * Soft breathing neon aura behind the floating AI Coach FAB.
 * Uses deferred state reads inside graphicsLayer (0 recompositions/sec at 120Hz).
 */
@Composable
fun BreathingNeonHalo(
    color: Color,
    size: Dp = 74.dp,
    content: @Composable () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "ai_fab_halo")
    val pulseState = infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_scale"
    )
    val glowAlphaState = infinite.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )
    val radialBrush = remember(color) {
        Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f))
        )
    }

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val p = pulseState.value
                    scaleX = p
                    scaleY = p
                    alpha = glowAlphaState.value
                }
        ) {
            drawCircle(brush = radialBrush)
        }
        content()
    }
}
