package com.fliptofocus.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.fliptofocus.lock.Combo
import com.fliptofocus.ui.components.FocusableRow
import com.fliptofocus.ui.components.NumberPad
import com.fliptofocus.ui.components.PinDots
import com.fliptofocus.ui.components.TvButton
import com.fliptofocus.ui.theme.IosBackground
import com.fliptofocus.ui.theme.IosBlue
import com.fliptofocus.ui.theme.IosGreen
import com.fliptofocus.ui.theme.IosNested
import com.fliptofocus.ui.theme.IosRed
import com.fliptofocus.ui.theme.IosSecondaryLabel
import kotlinx.coroutines.delay

private enum class SettingsMode { MAIN, CHANGE_PIN, SET_SEQUENCE }

private val GRACE_OPTIONS = listOf(
    0 to "Immediately",
    10 to "After 10 seconds",
    30 to "After 30 seconds",
    60 to "After 1 minute",
    300 to "After 5 minutes"
)

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val recoveryCode by viewModel.recoveryCode.collectAsState()
    var mode by remember { mutableStateOf(SettingsMode.MAIN) }

    BackHandler(enabled = mode != SettingsMode.MAIN) { mode = SettingsMode.MAIN }

    // One-time recovery-code display takes over the screen until dismissed.
    if (recoveryCode != null) {
        RecoveryCodePanel(code = recoveryCode!!, onDone = viewModel::consumeRecoveryCode)
        return
    }

    when (mode) {
        SettingsMode.CHANGE_PIN -> PinCreatePanel(
            title = "Set a new PIN",
            onCreated = { pin -> viewModel.changePin(pin) { mode = SettingsMode.MAIN } },
            onCancel = { mode = SettingsMode.MAIN }
        )

        SettingsMode.SET_SEQUENCE -> SequenceSetupPanel(
            onSaved = { seq -> viewModel.setCombo(seq) { mode = SettingsMode.MAIN } },
            onCancel = { mode = SettingsMode.MAIN }
        )

        SettingsMode.MAIN -> SettingsMain(
            uiState = uiState,
            onBack = { navController.popBackStack() },
            onChangePin = { mode = SettingsMode.CHANGE_PIN },
            onSetSequence = { mode = SettingsMode.SET_SEQUENCE },
            onRemoveSequence = viewModel::clearCombo,
            onRegenerateRecovery = viewModel::regenerateRecovery,
            onToggleLocking = { viewModel.setLockingEnabled(!uiState.isLockingEnabled) },
            onSelectGrace = viewModel::setRelockGrace
        )
    }
}

@Composable
private fun SettingsMain(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onSetSequence: () -> Unit,
    onRemoveSequence: () -> Unit,
    onRegenerateRecovery: () -> Unit,
    onToggleLocking: () -> Unit,
    onSelectGrace: (Int) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { firstFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        SectionHeader("SECURITY")
        SettingRow(
            title = "Change PIN",
            subtitle = "Update the PIN used to open locked apps",
            onClick = onChangePin,
            focusRequester = firstFocus
        )
        Spacer(Modifier.height(8.dp))
        SettingRow(
            title = "Secret remote sequence",
            subtitle = if (uiState.isComboSet) "On — an alternative way to unlock" else "Off — add a D-pad unlock sequence",
            onClick = onSetSequence
        )
        if (uiState.isComboSet) {
            Spacer(Modifier.height(8.dp))
            SettingRow(
                title = "Remove secret sequence",
                subtitle = "Unlock with the PIN only",
                onClick = onRemoveSequence,
                titleColor = IosRed
            )
        }
        Spacer(Modifier.height(8.dp))
        SettingRow(
            title = "Recovery code",
            subtitle = if (uiState.isRecoverySet) "Generate a new recovery code (invalidates the old one)" else "Generate a recovery code",
            onClick = onRegenerateRecovery
        )

        Spacer(Modifier.height(20.dp))
        SectionHeader("LOCKING")
        SettingRow(
            title = "Locking",
            subtitle = if (uiState.isLockingEnabled) "On" else "Paused",
            onClick = onToggleLocking,
            trailing = if (uiState.isLockingEnabled) "ON" else "OFF"
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Re-lock a reopened app",
            color = IosSecondaryLabel,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp)
        )
        GRACE_OPTIONS.forEach { (seconds, label) ->
            SettingRow(
                title = label,
                subtitle = null,
                onClick = { onSelectGrace(seconds) },
                selected = uiState.relockGraceSeconds == seconds
            )
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("PRIVACY")
        Text(
            text = "KidLock TV works completely offline. Your PIN, locked-app list, and history stay on " +
                "this device. The app has no internet permission and never sends, collects, or shares " +
                "your data.",
            color = IosSecondaryLabel,
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(Modifier.height(24.dp))
        TvButton(text = "Back", onClick = onBack, containerColor = IosNested)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = IosSecondaryLabel,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    titleColor: Color = Color.White,
    trailing: String? = null,
    selected: Boolean = false
) {
    FocusableRow(onClick = onClick, modifier = modifier, focusRequester = focusRequester) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, color = IosSecondaryLabel, fontSize = 13.sp)
            }
        }
        if (trailing != null) {
            Text(trailing, color = if (trailing == "ON") IosGreen else IosSecondaryLabel, fontWeight = FontWeight.Bold)
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = IosBlue)
        }
    }
}

@Composable
private fun PinCreatePanel(
    title: String,
    onCreated: (String) -> Unit,
    onCancel: () -> Unit
) {
    var first by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val padFocus = remember { FocusRequester() }
    LaunchedEffect(first) { delay(60); runCatching { padFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (first == null) title else "Confirm your PIN",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text("Choose a 4–6 digit PIN, then press OK.", color = IosSecondaryLabel, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        PinDots(length = entry.length, max = 6)
        Spacer(Modifier.height(20.dp))
        NumberPad(
            firstKeyFocusRequester = padFocus,
            onDigit = { d -> if (entry.length < 6) { error = null; entry += d } },
            onDelete = { if (entry.isNotEmpty()) entry = entry.dropLast(1) },
            onOk = {
                when {
                    entry.length !in 4..6 -> error = "Enter 4 to 6 digits."
                    first == null -> { first = entry; entry = "" }
                    entry == first -> onCreated(entry)
                    else -> { error = "PINs didn't match. Start again."; first = null; entry = "" }
                }
            }
        )
        if (error != null) {
            Spacer(Modifier.height(14.dp))
            Text(error!!, color = IosRed, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(20.dp))
        TvButton(text = "Cancel", onClick = onCancel, containerColor = IosNested)
    }
}

@Composable
private fun SequenceSetupPanel(
    onSaved: (List<Int>) -> Unit,
    onCancel: () -> Unit
) {
    var first by remember { mutableStateOf<List<Int>?>(null) }
    var seq by remember { mutableStateOf(listOf<Int>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(first) { delay(60); runCatching { focus.requestFocus() } }

    fun submit() {
        when {
            seq.size < Combo.MIN_LENGTH -> error = "Use at least ${Combo.MIN_LENGTH} directions."
            first == null -> { first = seq; seq = emptyList(); error = null }
            seq == first -> onSaved(seq)
            else -> { error = "Sequences didn't match. Start again."; first = null; seq = emptyList() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (first == null) "Set secret sequence" else "Confirm sequence",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Press ↑ ↓ ← → on the remote (at least ${Combo.MIN_LENGTH}), then press the center button to save.",
            color = IosSecondaryLabel,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .widthIn(min = 260.dp)
                .height(76.dp)
                .background(IosNested, RoundedCornerShape(14.dp))
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> { error = null; if (seq.size < Combo.MAX_LENGTH) seq = seq + Combo.UP; true }
                        Key.DirectionDown -> { error = null; if (seq.size < Combo.MAX_LENGTH) seq = seq + Combo.DOWN; true }
                        Key.DirectionLeft -> { error = null; if (seq.size < Combo.MAX_LENGTH) seq = seq + Combo.LEFT; true }
                        Key.DirectionRight -> { error = null; if (seq.size < Combo.MAX_LENGTH) seq = seq + Combo.RIGHT; true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { submit(); true }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (seq.isEmpty()) "Press directions…" else seq.joinToString(" ") { Combo.symbol(it) },
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (error != null) {
            Spacer(Modifier.height(14.dp))
            Text(error!!, color = IosRed, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(text = "Clear", onClick = { seq = emptyList(); error = null }, containerColor = IosNested)
            TvButton(text = "Cancel", onClick = onCancel, containerColor = IosNested)
        }
    }
}

@Composable
private fun RecoveryCodePanel(code: String, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { focus.requestFocus() } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Your new recovery code", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Write it down and keep it safe. It unlocks any app if you forget your PIN, and it will not be shown again.",
            color = IosSecondaryLabel,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .background(IosNested, RoundedCornerShape(14.dp))
                .padding(horizontal = 28.dp, vertical = 18.dp)
        ) {
            Text(code, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(24.dp))
        TvButton(text = "I've written it down", onClick = onDone, focusRequester = focus)
    }
}
