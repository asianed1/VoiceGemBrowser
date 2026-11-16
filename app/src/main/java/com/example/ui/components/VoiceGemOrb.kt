package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.AssistantState
import com.example.ui.theme.GemCyan
import com.example.ui.theme.GemCyanVariant
import com.example.ui.theme.GemDeepNavy
import com.example.ui.theme.GemElectricBlue
import com.example.ui.theme.GemEmerald
import com.example.ui.theme.GemPurple
import com.example.ui.theme.GemRose
import com.example.ui.theme.GemViolet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VoiceGemOrb(
    state: AssistantState,
    rmsLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerSize: Dp = 204.dp,
    bodySize: Dp = 152.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gem_orb_fx")

    // Rotation angle for thinking / processing state
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Gentle breathing pulse for idle / resting state
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )

    // Speaking wave oscillation
    val speakingWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "speaking_wave"
    )

    // Smooth RMS audio scale
    val smoothRms by animateFloatAsState(
        targetValue = if (state == AssistantState.LISTENING) rmsLevel else 0f,
        animationSpec = tween(durationMillis = 100),
        label = "smooth_rms"
    )

    val currentScale = when (state) {
        AssistantState.LISTENING -> 1f + (smoothRms * 0.28f)
        AssistantState.PROCESSING -> 1.02f
        AssistantState.SPEAKING -> 1.04f + (sin(speakingWave) * 0.04f)
        AssistantState.IDLE -> idlePulse
        AssistantState.ERROR -> 1.0f
    }

    val primaryGlow = when (state) {
        AssistantState.LISTENING -> GemEmerald
        AssistantState.PROCESSING -> GemCyanVariant
        AssistantState.SPEAKING -> GemViolet
        AssistantState.IDLE -> GemCyan
        AssistantState.ERROR -> GemRose
    }

    val secondaryGlow = when (state) {
        AssistantState.LISTENING -> GemCyan
        AssistantState.PROCESSING -> GemPurple
        AssistantState.SPEAKING -> GemElectricBlue
        AssistantState.IDLE -> GemViolet
        AssistantState.ERROR -> Color(0xFFFF8A80)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(containerSize)
            .testTag("voice_gem_orb_container")
    ) {
        // Outer concentric audio ripples (when listening)
        if (state == AssistantState.LISTENING) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val baseRadius = size.minDimension / 2.3f

                // Dynamic outer rings
                for (i in 1..3) {
                    val ringRadius = baseRadius + (i * 18.dp.toPx() * (0.4f + smoothRms))
                    val alpha = ((0.5f - (i * 0.12f)) * (0.3f + smoothRms * 0.7f)).coerceIn(0f, 1f)
                    drawCircle(
                        color = primaryGlow.copy(alpha = alpha),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = (2.5f - i * 0.5f).dp.toPx())
                    )
                }
            }
        }

        // Speaking audio waveform rings (when speaking)
        if (state == AssistantState.SPEAKING) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val baseRadius = size.minDimension / 2.4f
                val points = 60
                val path = Path()

                for (idx in 0..points) {
                    val angle = (idx.toFloat() / points) * (2 * PI).toFloat()
                    val waveMod = sin((angle * 5) + speakingWave) * 12.dp.toPx()
                    val r = baseRadius + waveMod
                    val x = center.x + r * cos(angle)
                    val y = center.y + r * sin(angle)
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()

                drawPath(
                    path = path,
                    brush = Brush.sweepGradient(
                        listOf(GemViolet, GemCyan, GemElectricBlue, GemPurple, GemViolet),
                        center
                    ),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Center Gem Orb Body
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(bodySize)
                .scale(currentScale)
                .shadow(
                    elevation = 28.dp,
                    shape = CircleShape,
                    spotColor = primaryGlow,
                    ambientColor = secondaryGlow
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = 0.55f),
                            secondaryGlow.copy(alpha = 0.40f),
                            GemDeepNavy.copy(alpha = 0.95f)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = 90.dp),
                    onClick = onClick
                )
                .testTag("voice_gem_orb_button")
        ) {
            // Rotating faceted crystal overlay
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(if (state == AssistantState.PROCESSING) rotationAngle else 0f)
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2.1f

                // Draw faceted crystal geometric lines
                val numFacets = 8
                val facetStep = (2 * PI / numFacets).toFloat()

                for (i in 0 until numFacets) {
                    val angle1 = i * facetStep
                    val angle2 = (i + 1) * facetStep
                    val p1 = Offset(center.x + radius * cos(angle1), center.y + radius * sin(angle1))
                    val p2 = Offset(center.x + radius * cos(angle2), center.y + radius * sin(angle2))
                    val innerP = Offset(center.x + (radius * 0.45f) * cos(angle1 + facetStep / 2), center.y + (radius * 0.45f) * sin(angle1 + facetStep / 2))

                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = center,
                        end = p1,
                        strokeWidth = 1.2.dp.toPx()
                    )
                    drawLine(
                        color = primaryGlow.copy(alpha = 0.5f),
                        start = p1,
                        end = p2,
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = secondaryGlow.copy(alpha = 0.4f),
                        start = innerP,
                        end = p1,
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Inner core glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.85f),
                            primaryGlow.copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * 0.5f
                    ),
                    radius = radius * 0.5f,
                    center = center
                )
            }

            // Center icon reflecting current state
            val icon = when (state) {
                AssistantState.LISTENING -> Icons.Default.Mic
                AssistantState.PROCESSING -> Icons.Default.GraphicEq
                AssistantState.SPEAKING -> Icons.AutoMirrored.Filled.VolumeUp
                AssistantState.IDLE -> Icons.Default.Mic
                AssistantState.ERROR -> Icons.Default.Stop
            }

            Icon(
                imageVector = icon,
                contentDescription = state.name,
                tint = Color.White,
                modifier = Modifier
                    .size(44.dp)
                    .shadow(12.dp, CircleShape, spotColor = Color.White)
            )
        }
    }
}
