import android.view.SoundEffectConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.poltorashka.documents.UserPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

fun Modifier.bounceClick(onClick: () -> Unit) = composed {
    var isPressed by remember { mutableStateOf(false) }

    // Получает доступ к системе устройства для звука и вибрации
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val prefs = remember(context) { UserPreferences.getInstance(context) }
    val animationsEnabled by prefs.animationsEnabledFlow.collectAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = if (animationsEnabled) spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ) else snap(),
        label = "bounce"
    )

    this
        .graphicsLayer {
            scaleX = if (animationsEnabled) scale else 1f
            scaleY = if (animationsEnabled) scale else 1f
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    // Легкий отклик при самом касании (опционально, делает интерфейс очень "живым")
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    waitForUpOrCancellation()
                    isPressed = false
                }
            }
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                // Воспроизводит системный щелчок
                view.playSoundEffect(SoundEffectConstants.CLICK)

                // Выполняется само действие кнопки
                onClick()
            }
        )
}