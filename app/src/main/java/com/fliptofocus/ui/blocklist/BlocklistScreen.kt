package com.fliptofocus.ui.blocklist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.fliptofocus.ui.components.FocusableRow
import com.fliptofocus.ui.components.TvButton
import com.fliptofocus.ui.theme.IosBackground
import com.fliptofocus.ui.theme.IosGreen
import com.fliptofocus.ui.theme.IosNested
import com.fliptofocus.ui.theme.IosSecondaryLabel
import com.fliptofocus.ui.theme.IosSeparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocklistScreen(
    navController: NavController,
    viewModel: BlocklistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(uiState.isLoading, uiState.apps.size) {
        if (!uiState.isLoading && uiState.apps.isNotEmpty()) {
            kotlinx.coroutines.delay(80)
            runCatching { firstFocus.requestFocus() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Choose apps to lock", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TvButton(text = "Back", onClick = { navController.popBackStack() }, containerColor = IosNested)
        }
        Spacer(Modifier.size(12.dp))
        Text(
            "Select the apps a child must not open without your PIN.",
            color = IosSecondaryLabel,
            fontSize = 14.sp
        )
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            label = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
        )
        Spacer(Modifier.size(12.dp))

        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            uiState.apps.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No apps found. Install apps to lock them, or clear your search.",
                    color = IosSecondaryLabel,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(uiState.apps, key = { _, app -> app.packageName }) { index, app ->
                    AppToggleRow(
                        item = app,
                        focusRequester = if (index == 0) firstFocus else null,
                        onToggle = { viewModel.setBlocked(app, !app.isBlocked) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppToggleRow(
    item: BlocklistItem,
    focusRequester: FocusRequester?,
    onToggle: () -> Unit
) {
    FocusableRow(onClick = onToggle, focusRequester = focusRequester) {
        AppIcon(packageName = item.packageName)
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.appLabel, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (item.isBlocked) "Locked" else "Not locked",
                color = if (item.isBlocked) IosGreen else IosSecondaryLabel,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.size(12.dp))
        Icon(
            imageVector = if (item.isBlocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            contentDescription = if (item.isBlocked) "Locked" else "Not locked",
            tint = if (item.isBlocked) IosGreen else IosSeparator
        )
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val icon = rememberAppIcon(packageName)
    val shape = RoundedCornerShape(10.dp)
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(44.dp).clip(shape))
    } else {
        Box(
            modifier = Modifier.size(44.dp).clip(shape).background(IosNested),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Android, contentDescription = null, tint = IosSecondaryLabel)
        }
    }
}

/** Loads an app's launcher icon off the main thread; returns null until ready or on failure. */
@Composable
private fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        icon = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }
    return icon
}
