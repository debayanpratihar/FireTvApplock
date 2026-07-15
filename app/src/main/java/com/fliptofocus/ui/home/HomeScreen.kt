package com.fliptofocus.ui.home

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.fliptofocus.domain.model.SessionStatus
import com.fliptofocus.lock.LockActivity
import com.fliptofocus.ui.components.FocusableRow
import com.fliptofocus.ui.components.TvButton
import com.fliptofocus.ui.navigation.Routes
import com.fliptofocus.ui.theme.IosBlue
import com.fliptofocus.ui.theme.IosGreen
import com.fliptofocus.ui.theme.IosGroup
import com.fliptofocus.ui.theme.IosOrange
import com.fliptofocus.ui.theme.IosSecondaryLabel
import com.fliptofocus.util.PermissionUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        runCatching { firstFocus.requestFocus() }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Text("KidLock TV", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    !uiState.isLockingEnabled -> "Locking is paused"
                    uiState.lockedAppCount == 0 -> "No apps locked yet — add some below"
                    else -> "${uiState.lockedAppCount} app(s) locked · ${uiState.unlockedToday} unlocked today"
                },
                color = IosSecondaryLabel,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(18.dp))

            if (!accessibilityOn) {
                AccessibilityHintCard(
                    onEnable = {
                        runCatching { context.startActivity(PermissionUtils.accessibilitySettingsIntent()) }
                    }
                )
                Spacer(Modifier.height(14.dp))
            }

            FocusableRow(
                onClick = { viewModel.setLockingEnabled(!uiState.isLockingEnabled) },
                focusRequester = firstFocus
            ) {
                RowIcon(Icons.Filled.Lock, IosBlue)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Locking", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (uiState.isLockingEnabled) "Locked apps require your PIN" else "Paused — apps open freely",
                        color = IosSecondaryLabel,
                        fontSize = 13.sp
                    )
                }
                StatusPill(
                    text = if (uiState.isLockingEnabled) "ON" else "OFF",
                    color = if (uiState.isLockingEnabled) IosGreen else IosSecondaryLabel
                )
            }
            Spacer(Modifier.height(10.dp))

            MenuRow(
                icon = Icons.Filled.Apps,
                iconColor = IosBlue,
                title = "Locked apps",
                subtitle = "${uiState.lockedAppCount} selected",
                onClick = { navController.navigate(Routes.BLOCKLIST) }
            )
            Spacer(Modifier.height(10.dp))
            MenuRow(
                icon = Icons.Filled.PlayArrow,
                iconColor = IosGreen,
                title = "Preview lock screen",
                subtitle = "See exactly what your child sees",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(context, LockActivity::class.java)
                                .putExtra(LockActivity.EXTRA_PREVIEW, true)
                        )
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            MenuRow(
                icon = Icons.Filled.Settings,
                iconColor = IosSecondaryLabel,
                title = "Settings",
                subtitle = "PIN, secret sequence, recovery",
                onClick = { navController.navigate(Routes.SETTINGS) }
            )

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "RECENT ACTIVITY",
                    color = IosSecondaryLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.recentEvents.isNotEmpty()) {
                    TvButton(
                        text = "Clear",
                        onClick = { viewModel.clearHistory() },
                        containerColor = IosGroup,
                        contentColor = IosBlue
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (uiState.recentEvents.isEmpty()) {
                Text(
                    "When a locked app is opened, it shows here — unlocked or denied.",
                    color = IosSecondaryLabel,
                    fontSize = 14.sp
                )
            } else {
                uiState.recentEvents.forEach { row ->
                    AccessLogItem(row)
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector, bg: Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    FocusableRow(onClick = onClick) {
        RowIcon(icon, iconColor)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp)
            Text(subtitle, color = IosSecondaryLabel, fontSize = 13.sp)
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = IosSecondaryLabel)
    }
}

@Composable
private fun AccessibilityHintCard(onEnable: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(IosGroup)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = IosOrange, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(10.dp))
            Text("Automatic locking is off", fontWeight = FontWeight.SemiBold, color = IosOrange)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Turn on KidLock in Accessibility so locked apps are guarded automatically. You can still " +
                "set your PIN, choose apps, and preview the lock without it.",
            color = IosSecondaryLabel,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        TvButton(text = "Open Accessibility settings", onClick = onEnable)
    }
}

@Composable
private fun AccessLogItem(row: AccessLogRow) {
    val (label, color) = when (row.status) {
        SessionStatus.UNLOCKED -> "Unlocked" to IosGreen
        SessionStatus.DENIED -> "Denied" to IosOrange
        SessionStatus.LOCKED -> "Locked" to IosSecondaryLabel
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(IosGroup)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(formatTime(row.timestamp), color = IosSecondaryLabel, fontSize = 12.sp)
        }
        StatusPill(text = label, color = color)
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))
