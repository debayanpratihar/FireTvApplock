package com.fliptofocus.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fliptofocus.ui.theme.IosNested
import com.fliptofocus.ui.theme.IosSeparator

/**
 * D-pad-first building blocks. Every interactive element shows a clear, high-contrast focus
 * indicator (border + subtle scale) so the app is fully usable and navigable with a Fire TV
 * remote - an Amazon Appstore test-criteria requirement - while remaining tappable on touch
 * devices.
 */

/** A prominent, focusable button. */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused && enabled) 1.04f else 1f, label = "tvBtnScale")
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.35f))
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = shape
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.6f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** A focusable list/menu row that highlights when focused. */
@Composable
fun FocusableRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    content: @Composable RowScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .background(
                if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else IosNested
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Dots showing how many PIN digits have been entered (out of [max]). */
@Composable
fun PinDots(length: Int, max: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(max) { i ->
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (i < length) MaterialTheme.colorScheme.primary else IosSeparator
                    )
            )
        }
    }
}

/** A single numeric-pad key. */
@Composable
private fun PadButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.09f else 1f, label = "padScale")
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .width(96.dp)
            .height(66.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) MaterialTheme.colorScheme.primary else IosNested)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = shape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (focused) Color.White else MaterialTheme.colorScheme.onSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * A telephone-style numeric keypad (1-9, delete, 0, OK) built from focusable keys so it is fully
 * operable with a D-pad. [firstKeyFocusRequester], if given, is attached to the "1" key so the
 * caller can move focus onto the pad when the screen appears.
 */
@Composable
fun NumberPad(
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
    firstKeyFocusRequester: FocusRequester? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PadButton("1", { onDigit(1) }, focusRequester = firstKeyFocusRequester)
            PadButton("2", { onDigit(2) })
            PadButton("3", { onDigit(3) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PadButton("4", { onDigit(4) })
            PadButton("5", { onDigit(5) })
            PadButton("6", { onDigit(6) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PadButton("7", { onDigit(7) })
            PadButton("8", { onDigit(8) })
            PadButton("9", { onDigit(9) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PadButton("⌫", onDelete)
            PadButton("0", { onDigit(0) })
            PadButton("OK", onOk)
        }
    }
}
