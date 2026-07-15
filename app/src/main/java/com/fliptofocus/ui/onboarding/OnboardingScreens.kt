package com.fliptofocus.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fliptofocus.R
import com.fliptofocus.ui.components.NumberPad
import com.fliptofocus.ui.components.PinDots
import com.fliptofocus.ui.components.TvButton
import com.fliptofocus.ui.theme.IosGreen
import com.fliptofocus.ui.theme.IosNested
import com.fliptofocus.ui.theme.IosRed
import com.fliptofocus.ui.theme.IosSecondaryLabel
import com.fliptofocus.util.PermissionUtils
import kotlinx.coroutines.delay

/**
 * KidLock TV setup. The only required step is creating a PIN; enabling the accessibility service is
 * clearly disclosed and skippable, so the flow can always reach the end and the app is never stuck
 * asking for a permission the device cannot grant.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val step by viewModel.step.collectAsState()
    val recoveryCode by viewModel.recoveryCode.collectAsState()

    var accessibilityOn by remember {
        mutableStateOf(PermissionUtils.isAccessibilityServiceEnabled(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityOn = PermissionUtils.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val accessibilityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { accessibilityOn = PermissionUtils.isAccessibilityServiceEnabled(context) }

    BackHandler(enabled = step != OnbStep.WELCOME) { viewModel.back() }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (step) {
                OnbStep.WELCOME -> WelcomeStep(onNext = viewModel::next)
                OnbStep.PIN -> CreatePinStep(onPinCreated = viewModel::createPinAndContinue)
                OnbStep.RECOVERY -> RecoveryStep(code = recoveryCode, onNext = viewModel::next)
                OnbStep.ACCESSIBILITY -> AccessibilityStep(
                    enabled = accessibilityOn,
                    onEnable = {
                        runCatching {
                            accessibilityLauncher.launch(PermissionUtils.accessibilitySettingsIntent())
                        }
                    },
                    onContinue = viewModel::next
                )
                OnbStep.DONE -> DoneStep(onFinish = onFinished)
            }
        }
    }
}

@Composable
private fun StepHeader(icon: ImageVector, title: String, subtitle: String) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(60.dp)
    )
    Spacer(Modifier.height(16.dp))
    Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        text = subtitle,
        color = IosSecondaryLabel,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 460.dp)
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { focus.requestFocus() } }
    StepHeader(
        icon = Icons.Filled.Shield,
        title = "KidLock TV",
        subtitle = "Lock any app on your Fire TV behind a PIN so children can only open what you allow. " +
            "Everything runs on this device — no account, no internet, no tracking."
    )
    TvButton(text = "Get started", onClick = onNext, focusRequester = focus)
}

@Composable
private fun CreatePinStep(onPinCreated: (String) -> Unit) {
    // first == null while entering the PIN; non-null while confirming it.
    var first by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val padFocus = remember { FocusRequester() }
    LaunchedEffect(first) { delay(60); runCatching { padFocus.requestFocus() } }

    StepHeader(
        icon = Icons.Filled.Lock,
        title = if (first == null) "Create a PIN" else "Confirm your PIN",
        subtitle = "Choose a 4–6 digit PIN. You'll enter it to open locked apps. Press OK when done."
    )
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
                entry == first -> onPinCreated(entry)
                else -> { error = "PINs didn't match. Start again."; first = null; entry = ""; }
            }
        }
    )
    if (error != null) {
        Spacer(Modifier.height(14.dp))
        Text(text = error!!, color = IosRed, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RecoveryStep(code: String?, onNext: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { focus.requestFocus() } }
    StepHeader(
        icon = Icons.Filled.Shield,
        title = "Save your recovery code",
        subtitle = "If you ever forget your PIN, this code unlocks any app and lets you set a new PIN. " +
            "Write it down and keep it safe — it will not be shown again."
    )
    Box(
        modifier = Modifier
            .widthIn(min = 240.dp)
            .background(IosNested, RoundedCornerShape(14.dp))
            .padding(horizontal = 28.dp, vertical = 18.dp)
    ) {
        Text(
            text = code ?: "————",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
    Spacer(Modifier.height(24.dp))
    TvButton(text = "I've written it down", onClick = onNext, focusRequester = focus)
}

@Composable
private fun AccessibilityStep(
    enabled: Boolean,
    onEnable: () -> Unit,
    onContinue: () -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(enabled) { delay(60); runCatching { focus.requestFocus() } }
    StepHeader(
        icon = Icons.Filled.Lock,
        title = "Turn on automatic locking",
        subtitle = stringResource(R.string.accessibility_disclosure)
    )
    if (enabled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = IosGreen, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(8.dp))
            Text("Enabled", color = IosGreen, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(20.dp))
        TvButton(text = "Continue", onClick = onContinue, focusRequester = focus)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(text = "Enable in Settings", onClick = onEnable, focusRequester = focus)
            TvButton(text = "Skip for now", onClick = onContinue, containerColor = IosNested)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "You can turn this on later. Until then, use “Preview lock screen” on the home screen to see how the lock works.",
            color = IosSecondaryLabel,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 440.dp)
        )
    }
}

@Composable
private fun DoneStep(onFinish: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { focus.requestFocus() } }
    Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = null,
        tint = IosGreen,
        modifier = Modifier.size(64.dp)
    )
    Spacer(Modifier.height(16.dp))
    Text("You're all set", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Add apps to your lock list and KidLock guards them with your PIN. Change your PIN, add a " +
            "secret remote sequence, or adjust locking anytime in Settings.",
        color = IosSecondaryLabel,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 460.dp)
    )
    Spacer(Modifier.height(24.dp))
    TvButton(text = "Choose apps to lock", onClick = onFinish, focusRequester = focus)
}
