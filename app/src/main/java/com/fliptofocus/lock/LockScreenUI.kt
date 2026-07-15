package com.fliptofocus.lock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fliptofocus.ui.components.NumberPad
import com.fliptofocus.ui.components.PinDots
import com.fliptofocus.ui.components.TvButton
import com.fliptofocus.ui.theme.IosBackground
import com.fliptofocus.ui.theme.IosNested
import com.fliptofocus.ui.theme.IosRed
import com.fliptofocus.ui.theme.IosSecondaryLabel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class LockMode { PIN, SEQUENCE, RECOVERY }

private const val PIN_MAX = 6
private const val PIN_MIN = 4
private const val RECOVERY_LEN = 10

/**
 * The lock screen. Renders three D-pad-operable ways in: a numeric PIN, the secret remote sequence,
 * and the numeric recovery code. Verification is delegated to the caller (which holds the hashed
 * credentials) so this composable stays free of any storage concern.
 *
 * @param onUnlocked Invoked once when the correct credential is entered.
 * @param onGoBack Invoked when the user backs out without unlocking.
 */
@Composable
fun LockScreen(
    appLabel: String,
    hasCombo: Boolean,
    hasRecovery: Boolean,
    onVerifyPin: (String) -> Boolean,
    onVerifySequence: (List<Int>) -> Boolean,
    onVerifyRecovery: (String) -> Boolean,
    onUnlocked: () -> Unit,
    onGoBack: () -> Unit,
    onWrongAttempt: () -> Unit = {},
    cooldownRemainingMillis: () -> Long = { 0L }
) {
    var mode by remember { mutableStateOf(LockMode.PIN) }
    var pin by remember { mutableStateOf("") }
    var recovery by remember { mutableStateOf("") }
    var sequence by remember { mutableStateOf(listOf<Int>()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var errorNonce by remember { mutableIntStateOf(0) }

    // Brute-force lockout: poll the shared cooldown every 500 ms and block input while it lasts.
    var cooldownTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(500); cooldownTick++ } }
    val cooldownMs = remember(cooldownTick) { cooldownRemainingMillis() }
    val inCooldown = cooldownMs > 0L

    val padFocus = remember { FocusRequester() }
    val seqFocus = remember { FocusRequester() }

    // Shake the entry area horizontally when a wrong credential is entered.
    val shakeX = remember { Animatable(0f) }
    LaunchedEffect(errorNonce) {
        if (errorNonce == 0) return@LaunchedEffect
        for (dx in listOf(-16f, 14f, -11f, 8f, -4f, 0f)) shakeX.animateTo(dx, tween(45))
    }

    // Move focus onto the right control whenever the mode changes.
    LaunchedEffect(mode) {
        delay(60)
        runCatching {
            when (mode) {
                LockMode.PIN, LockMode.RECOVERY -> padFocus.requestFocus()
                LockMode.SEQUENCE -> seqFocus.requestFocus()
            }
        }
    }

    fun fail(message: String) {
        onWrongAttempt()
        errorText = message
        errorNonce++
    }

    // Back: from a sub-mode return to PIN; from PIN, leave without unlocking.
    BackHandler(enabled = mode != LockMode.PIN) { mode = LockMode.PIN }
    BackHandler(enabled = mode == LockMode.PIN) { onGoBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "App locked",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (mode) {
                    LockMode.PIN -> "Enter your PIN to open $appLabel."
                    LockMode.SEQUENCE -> "Enter your secret remote sequence to open $appLabel."
                    LockMode.RECOVERY -> "Enter your $RECOVERY_LEN-digit recovery code."
                },
                color = IosSecondaryLabel,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp)
            )

            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier.offset { IntOffset(shakeX.value.roundToInt(), 0) },
                contentAlignment = Alignment.Center
            ) {
                when (mode) {
                    LockMode.PIN -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PinDots(length = pin.length, max = PIN_MAX)
                        Spacer(Modifier.height(20.dp))
                        NumberPad(
                            firstKeyFocusRequester = padFocus,
                            onDigit = { d ->
                                if (!inCooldown && pin.length < PIN_MAX) {
                                    val next = pin + d
                                    when {
                                        next.length in PIN_MIN..PIN_MAX && onVerifyPin(next) -> {
                                            pin = ""; onUnlocked()
                                        }
                                        next.length >= PIN_MAX -> { pin = ""; fail("Incorrect PIN. Try again.") }
                                        else -> { errorText = null; pin = next }
                                    }
                                }
                            },
                            onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                            onOk = {
                                if (!inCooldown) {
                                    if (pin.length in PIN_MIN..PIN_MAX && onVerifyPin(pin)) {
                                        pin = ""; onUnlocked()
                                    } else { pin = ""; fail("Incorrect PIN. Try again.") }
                                }
                            }
                        )
                    }

                    LockMode.RECOVERY -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = recovery.padEnd(RECOVERY_LEN, '•'),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(20.dp))
                        NumberPad(
                            firstKeyFocusRequester = padFocus,
                            onDigit = { d ->
                                if (!inCooldown && recovery.length < RECOVERY_LEN) {
                                    val next = recovery + d
                                    if (next.length == RECOVERY_LEN) {
                                        if (onVerifyRecovery(next)) { recovery = ""; onUnlocked() }
                                        else { recovery = ""; fail("Incorrect recovery code.") }
                                    } else { errorText = null; recovery = next }
                                }
                            },
                            onDelete = { if (recovery.isNotEmpty()) recovery = recovery.dropLast(1) },
                            onOk = {
                                if (!inCooldown) {
                                    if (recovery.length == RECOVERY_LEN && onVerifyRecovery(recovery)) {
                                        recovery = ""; onUnlocked()
                                    } else { recovery = ""; fail("Incorrect recovery code.") }
                                }
                            }
                        )
                    }

                    LockMode.SEQUENCE -> SequenceCapture(
                        sequence = sequence,
                        focusRequester = seqFocus,
                        onDirection = { dir ->
                            if (!inCooldown) {
                                errorText = null
                                sequence = if (sequence.size >= Combo.MAX_LENGTH) listOf(dir)
                                else sequence + dir
                            }
                        },
                        onSubmit = {
                            if (!inCooldown) {
                                if (sequence.size >= Combo.MIN_LENGTH && onVerifySequence(sequence)) {
                                    sequence = emptyList(); onUnlocked()
                                } else { sequence = emptyList(); fail("Incorrect sequence. Try again.") }
                            }
                        }
                    )
                }
            }

            when {
                inCooldown -> {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Too many attempts. Try again in ${cooldownMs / 1000 + 1}s.",
                        color = IosRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                errorText != null -> {
                    Spacer(Modifier.height(14.dp))
                    Text(text = errorText!!, color = IosRed, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (mode) {
                    LockMode.PIN -> {
                        if (hasCombo) {
                            TvButton(
                                text = "Secret sequence",
                                onClick = { errorText = null; sequence = emptyList(); mode = LockMode.SEQUENCE },
                                containerColor = IosNested
                            )
                        }
                        if (hasRecovery) {
                            TvButton(
                                text = "Forgot PIN?",
                                onClick = { errorText = null; recovery = ""; mode = LockMode.RECOVERY },
                                containerColor = IosNested
                            )
                        }
                        TvButton(text = "Go back", onClick = onGoBack, containerColor = IosNested)
                    }
                    // RECOVERY offers a focusable "Use PIN" button; SEQUENCE consumes all D-pad
                    // directions, so it relies on Back (handled above) to return to the PIN.
                    LockMode.RECOVERY -> TvButton(
                        text = "Use PIN",
                        onClick = { errorText = null; mode = LockMode.PIN },
                        containerColor = IosNested
                    )
                    LockMode.SEQUENCE -> Unit
                }
            }
        }
    }
}

@Composable
private fun SequenceCapture(
    sequence: List<Int>,
    focusRequester: FocusRequester,
    onDirection: (Int) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { onDirection(Combo.UP); true }
                    Key.DirectionDown -> { onDirection(Combo.DOWN); true }
                    Key.DirectionLeft -> { onDirection(Combo.LEFT); true }
                    Key.DirectionRight -> { onDirection(Combo.RIGHT); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onSubmit(); true }
                    else -> false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 260.dp)
                .height(72.dp)
                .background(IosNested, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (sequence.isEmpty()) "Press remote directions…"
                else sequence.joinToString(" ") { Combo.symbol(it) },
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Use ↑ ↓ ← → on the remote, then press the center (OK) button to unlock. Press Back to use your PIN instead.",
            color = IosSecondaryLabel,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 380.dp)
        )
    }
}

/** Brief success confirmation used by the in-app "Preview lock" demo. */
@Composable
fun UnlockSuccess(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IosBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = com.fliptofocus.ui.theme.IosGreen,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("Unlocked", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "This is what your child will see. The correct PIN or secret sequence opens the app.",
                color = IosSecondaryLabel,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp)
            )
        }
    }
}
