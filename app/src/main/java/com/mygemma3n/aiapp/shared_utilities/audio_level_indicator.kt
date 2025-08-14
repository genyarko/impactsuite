import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Audio-level UI with 5 bars and an optional AGC status dot.
 *
 * @param audioLevel   Normalised level 0.0 – 1.0 where 0 → -90 dBFS and 1 → -20 dBFS
 * @param isActive     True while the mic stream is running
 * @param agcEnabled   Pass the result of AutomaticGainControl.isAvailable() && agc.enabled
 */
@Composable
fun AudioLevelIndicator(
    audioLevel: Float,
    isActive: Boolean,
    agcEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val primary  = MaterialTheme.colorScheme.primary
    val surface  = MaterialTheme.colorScheme.surfaceVariant
    val errorCol = MaterialTheme.colorScheme.error

    // Smooth animation (10 Hz – matches ViewModel update)
    val animLevel by animateFloatAsState(
        targetValue = if (isActive) audioLevel else 0f,
        animationSpec = tween(durationMillis = 100),
        label = "levelAnim"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        /* ───── 5 vertical bars ───── */
        repeat(5) { idx ->
            val barGate = (idx + 1) * 0.2f                      // 0.2, 0.4 … 1.0
            val isLit   = animLevel >= barGate
            val barFill = when {
                !isLit               -> surface.copy(alpha = 0.3f)
                animLevel > 0.9f     -> errorCol                 // possible clip
                else                 -> primary
            }
            Canvas(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
            ) {
                val h = size.height * (idx + 1) / 5f
                drawRoundRect(
                    color         = barFill,
                    topLeft       = Offset(0f, size.height - h),
                    size          = Size(size.width, h),
                    cornerRadius  = CornerRadius(1.dp.toPx())
                )
            }
        }

        /* ───── AGC status dot ───── */
        Canvas(
            modifier = Modifier
                .padding(start = 4.dp)
                .size(6.dp)
        ) {
            val color = if (agcEnabled) Color(0xFF1EB980) else Color(0xFFFFA000)
            drawCircle(color = color, radius = size.minDimension / 2)
        }
    }
}

/* ───── Convenience wrapper for caption UI ───── */
@Composable
fun AudioFeedbackRow(
    isListening: Boolean,
    audioLevel: Float,
    agcEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isListening) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AudioLevelIndicator(
            audioLevel  = audioLevel,
            isActive    = isListening,
            agcEnabled  = agcEnabled
        )

        /* Pulsing “LIVE” badge */
        val pulse = rememberInfiniteTransition(label = "livePulse")
            .animateFloat(
                initialValue = 0.3f,
                targetValue  = 1f,
                animationSpec = infiniteRepeatable(
                    tween(1000, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ), label = "alphaAnim"
            )
        Canvas(Modifier.size(8.dp)) {
            drawCircle(
                color  = Color.Red.copy(alpha = pulse.value),
                radius = size.width / 2
            )
        }
        Text(
            text  = "LIVE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}