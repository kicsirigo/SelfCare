package com.selfcare.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.selfcare.app.ui.theme.NeonGreen
import com.selfcare.app.ui.theme.NeonWarning
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class CelebrationParticle(
    val startXFraction: Float,
    val velocityX: Float,
    val velocityY: Float,
    val sizeDp: Float,
    val color: Color,
    val isStar: Boolean,
    val spinSpeed: Float,
    val phaseOffset: Float
)

/**
 * Full-overlay lightweight Compose Canvas confetti & twinkling stars celebration burst.
 * Increment [triggerKey] (starting at 0 = idle) to fire a 2.2-second non-blocking burst.
 */
@Composable
fun CelebrationOverlay(
    triggerKey: Int,
    modifier: Modifier = Modifier
) {
    if (triggerKey <= 0) return

    val progress = remember { Animatable(1f) }
    val particles = remember(triggerKey) {
        val rng = Random(triggerKey * 7919 + 42)
        val palette = listOf(
            NeonGreen,
            Color(0xFF38BDF8),
            NeonWarning,
            Color(0xFFA855F7),
            Color(0xFFF43F5E),
            Color(0xFFFDE047),
            Color.White
        )
        List(48) { idx ->
            val angle = rng.nextFloat() * PI.toFloat() * 2f
            val speed = 0.25f + rng.nextFloat() * 0.75f
            CelebrationParticle(
                startXFraction = 0.2f + rng.nextFloat() * 0.6f,
                velocityX = cos(angle) * speed * 0.55f,
                velocityY = -0.35f - rng.nextFloat() * 0.55f,
                sizeDp = if (idx % 3 == 0) 7f + rng.nextFloat() * 5f else 5f + rng.nextFloat() * 4f,
                color = palette[idx % palette.size],
                isStar = idx % 3 == 0,
                spinSpeed = (rng.nextFloat() - 0.5f) * 540f,
                phaseOffset = rng.nextFloat() * 6.28f
            )
        }
    }

    LaunchedEffect(triggerKey) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2100, easing = LinearEasing)
        )
    }

    val t = progress.value
    if (t >= 0.995f) return

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val alpha = ((1f - t) * 1.4f).coerceIn(0f, 1f)
            val gravity = 1.45f

            for (p in particles) {
                val x = w * p.startXFraction + w * p.velocityX * t + sin(t * 7f + p.phaseOffset) * 14f
                val y = h * 0.34f + h * (p.velocityY * t + 0.5f * gravity * t * t)
                if (y < -40f || y > h + 40f) continue
                val pxSize = p.sizeDp.dp.toPx()
                val rot = p.spinSpeed * t
                val drawColor = p.color.copy(alpha = alpha)

                rotate(degrees = rot, pivot = Offset(x, y)) {
                    if (p.isStar) {
                        drawFourPointStar(
                            center = Offset(x, y),
                            outerRadius = pxSize,
                            innerRadius = pxSize * 0.38f,
                            color = drawColor
                        )
                    } else {
                        drawRect(
                            color = drawColor,
                            topLeft = Offset(x - pxSize * 0.6f, y - pxSize * 0.35f),
                            size = Size(pxSize * 1.2f, pxSize * 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated water glass showing live fill level with dual sine-wave liquid motion,
 * rising micro-bubbles, and a cheerful spring bounce + glowing stars when 100% goal is reached.
 */
@Composable
fun AnimatedWaterGlass(
    currentMl: Int,
    targetMl: Int,
    modifier: Modifier = Modifier,
    glassWidth: Dp = 84.dp,
    glassHeight: Dp = 104.dp
) {
    val safeTarget = targetMl.coerceAtLeast(1)
    val rawFraction = (currentMl.toFloat() / safeTarget.toFloat()).coerceIn(0f, 1f)
    val isGoalReached = currentMl >= safeTarget

    val animatedFill = animateFloatAsState(
        targetValue = rawFraction,
        animationSpec = spring(
            dampingRatio = 0.60f,
            stiffness = Spring.StiffnessLow
        ),
        label = "water_fill"
    )

    val celebrationScale = remember { Animatable(1f) }
    val celebrationTilt = remember { Animatable(0f) }

    LaunchedEffect(isGoalReached) {
        if (isGoalReached) {
            celebrationScale.snapTo(0.92f)
            celebrationTilt.snapTo(-6f)
            celebrationScale.animateTo(
                targetValue = 1.08f,
                animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium)
            )
            celebrationTilt.animateTo(
                targetValue = 5f,
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            )
            celebrationTilt.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)
            )
            celebrationScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)
            )
        }
    }

    val infinite = rememberInfiniteTransition(label = "water_wave")
    val wavePhase = infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    val sparklePulse = infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle_pulse"
    )

    Canvas(
        modifier = modifier
            .size(width = glassWidth, height = glassHeight)
            .graphicsLayer {
                scaleX = celebrationScale.value
                scaleY = celebrationScale.value
                rotationZ = celebrationTilt.value
            }
    ) {
        val w = size.width
        val h = size.height
        val padTop = h * 0.14f
        val padBottom = h * 0.08f
        val topLeftX = w * 0.14f
        val topRightX = w * 0.86f
        val bottomLeftX = w * 0.24f
        val bottomRightX = w * 0.76f
        val bottomY = h - padBottom

        val glassPath = Path().apply {
            moveTo(topLeftX, padTop)
            lineTo(bottomLeftX, bottomY)
            lineTo(bottomRightX, bottomY)
            lineTo(topRightX, padTop)
            close()
        }

        // Subtle glass interior backdrop
        drawPath(
            path = glassPath,
            color = Color(0xFF0F1A24).copy(alpha = 0.85f)
        )

        val fill = animatedFill.value
        if (fill > 0.005f) {
            val usableHeight = (bottomY - padTop) * 0.92f
            val waterTopY = bottomY - usableHeight * fill
            val waveAmp = if (fill >= 0.99f) 2.5f else 4.5f

            clipPath(glassPath) {
                // Back wave layer
                val backWave = Path().apply {
                    moveTo(0f, bottomY)
                    lineTo(0f, waterTopY)
                    val steps = 28
                    for (i in 0..steps) {
                        val x = (i.toFloat() / steps) * w
                        val y = waterTopY + sin(wavePhase.value + i * 0.42f + 1.4f) * waveAmp
                        lineTo(x, y)
                    }
                    lineTo(w, bottomY)
                    close()
                }
                drawPath(
                    path = backWave,
                    color = if (isGoalReached) {
                        NeonGreen.copy(alpha = 0.35f)
                    } else {
                        Color(0xFF0EA5E9).copy(alpha = 0.45f)
                    }
                )

                // Front wave layer with rich gradient
                val frontWave = Path().apply {
                    moveTo(0f, bottomY)
                    lineTo(0f, waterTopY)
                    val steps = 28
                    for (i in 0..steps) {
                        val x = (i.toFloat() / steps) * w
                        val y = waterTopY + sin(wavePhase.value + i * 0.38f) * waveAmp
                        lineTo(x, y)
                    }
                    lineTo(w, bottomY)
                    close()
                }
                val liquidBrush = if (isGoalReached) {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF38BDF8), NeonGreen.copy(alpha = 0.85f)),
                        startY = waterTopY,
                        endY = bottomY
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7)),
                        startY = waterTopY,
                        endY = bottomY
                    )
                }
                drawPath(path = frontWave, brush = liquidBrush)

                // Animated rising micro-bubbles inside water
                val bubblePhase = (wavePhase.value / (2f * PI).toFloat())
                val liquidDepth = (bottomY - waterTopY).coerceAtLeast(1f)
                val bubbleOffsets = listOf(0.38f to 0.0f, 0.56f to 0.35f, 0.64f to 0.72f)
                for ((bxFrac, offset) in bubbleOffsets) {
                    val bProgress = (bubblePhase + offset) % 1f
                    val by = bottomY - bProgress * liquidDepth * 0.85f
                    if (by > waterTopY + 6f) {
                        drawCircle(
                            color = Color.White.copy(alpha = (1f - bProgress) * 0.45f),
                            radius = 2.4f + (1f - bProgress) * 1.6f,
                            center = Offset(w * bxFrac, by)
                        )
                    }
                }
            }
        }

        // Glass outline
        drawPath(
            path = glassPath,
            color = if (isGoalReached) NeonGreen else Color(0xFF7DD3FC).copy(alpha = 0.75f),
            style = Stroke(
                width = if (isGoalReached) 2.6.dp.toPx() else 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Subtle glass specular highlight line on the left side
        drawLine(
            color = Color.White.copy(alpha = 0.28f),
            start = Offset(topLeftX + w * 0.09f, padTop + h * 0.08f),
            end = Offset(bottomLeftX + w * 0.06f, bottomY - h * 0.10f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Twinkling celebration stars around the glass when daily water target is reached
        if (isGoalReached) {
            val pulse = sparklePulse.value
            drawFourPointStar(
                center = Offset(w * 0.14f, padTop * 0.55f),
                outerRadius = 6.dp.toPx() * pulse,
                innerRadius = 2.2.dp.toPx() * pulse,
                color = NeonGreen.copy(alpha = pulse)
            )
            drawFourPointStar(
                center = Offset(w * 0.87f, padTop * 0.45f),
                outerRadius = 7.dp.toPx() * (1.25f - pulse * 0.3f),
                innerRadius = 2.5.dp.toPx() * (1.25f - pulse * 0.3f),
                color = Color(0xFFFDE047).copy(alpha = 0.9f)
            )
            drawFourPointStar(
                center = Offset(w * 0.52f, padTop * 0.30f),
                outerRadius = 5.dp.toPx() * pulse,
                innerRadius = 1.8.dp.toPx() * pulse,
                color = Color(0xFF38BDF8).copy(alpha = pulse)
            )
        }
    }
}

private fun DrawScope.drawFourPointStar(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    color: Color
) {
    val path = Path()
    for (i in 0 until 8) {
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val angle = (i * PI / 4.0) - PI / 2.0
        val x = center.x + (cos(angle) * r).toFloat()
        val y = center.y + (sin(angle) * r).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color)
}
