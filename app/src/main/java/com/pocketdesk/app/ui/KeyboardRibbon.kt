package com.pocketdesk.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdesk.app.runtime.NativeBridge

@Composable
fun KeyboardRibbon(
    onToggleKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scrollState = rememberScrollState()

    var isCtrlLatched by remember { mutableStateOf(false) }
    var isAltLatched by remember { mutableStateOf(false) }
    var showCombosMenu by remember { mutableStateOf(false) }

    fun sendKey(keyCode: Int) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        var modifiers = 0
        if (isCtrlLatched) modifiers = modifiers or 1
        if (isAltLatched) modifiers = modifiers or 2

        NativeBridge.sendKeyEvent(keyCode, true, modifiers)
        NativeBridge.sendKeyEvent(keyCode, false, modifiers)

        if (isCtrlLatched) isCtrlLatched = false
        if (isAltLatched) isAltLatched = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xF0242424))
            .border(width = 0.5.dp, color = Color(0x33FFFFFF))
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(text = "Esc", onClick = { sendKey(KeyEvent.KEYCODE_ESCAPE) })
            KeyButton(text = "Tab", onClick = { sendKey(KeyEvent.KEYCODE_TAB) })
            KeyButton(text = "/", onClick = { sendKey(KeyEvent.KEYCODE_SLASH) })
            KeyButton(text = "-", onClick = { sendKey(KeyEvent.KEYCODE_MINUS) })

            KeyButton(
                text = "Ctrl",
                isLatched = isCtrlLatched,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    isCtrlLatched = !isCtrlLatched
                }
            )

            KeyButton(
                text = "Alt",
                isLatched = isAltLatched,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    isAltLatched = !isAltLatched
                }
            )

            IconButton(
                icon = { Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color.White, modifier = Modifier.size(16.dp)) },
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).text?.toString() ?: ""
                        for (ch in text) {
                            val code = KeyEvent.getDeadChar(0, ch.code)
                            NativeBridge.sendKeyEvent(code, true, 0)
                            NativeBridge.sendKeyEvent(code, false, 0)
                        }
                    }
                }
            )

            Box {
                KeyButton(
                    text = "Combos",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        showCombosMenu = true
                    }
                )

                DropdownMenu(
                    expanded = showCombosMenu,
                    onDismissRequest = { showCombosMenu = false },
                    modifier = Modifier.background(Color(0xFF2E2E2E))
                ) {
                    DropdownMenuItem(
                        text = { Text("Ctrl + C (Copy / Interrupt)", color = Color.White, fontSize = 13.sp) },
                        onClick = {
                            showCombosMenu = false
                            NativeBridge.sendKeyEvent(KeyEvent.KEYCODE_C, true, 1)
                            NativeBridge.sendKeyEvent(KeyEvent.KEYCODE_C, false, 1)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ctrl + V (Paste)", color = Color.White, fontSize = 13.sp) },
                        onClick = {
                            showCombosMenu = false
                            NativeBridge.sendKeyEvent(KeyEvent.KEYCODE_V, true, 1)
                            NativeBridge.sendKeyEvent(KeyEvent.KEYCODE_V, false, 1)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ctrl + Z (Suspend / Undo)", color = Color.White, fontSize = 13.sp) },
                        onClick = {
                            showCombosMenu = false
                            NativeBridge.sendKeyEvent(KeyEvent.KEYCODE_Z, true, 1)
                            NativeBridge.sendKeyEvent(KeyEvent.KEYCODE_Z, false, 1)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Alt + F4 (Close Window)", color = Color.White, fontSize = 13.sp) },
                        onClick = {
                            showCombosMenu = false
                            NativeBridge.sendKeyEvent(KeyEvent.KEYCODE_F4, true, 2)
                            NativeBridge.sendKeyEvent(KeyEvent.KEYCODE_F4, false, 2)
                        }
                    )
                }
            }

            IconButton(
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Left", tint = Color.White, modifier = Modifier.size(16.dp)) },
                onClick = { sendKey(KeyEvent.KEYCODE_DPAD_LEFT) }
            )

            IconButton(
                icon = { Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = Color.White, modifier = Modifier.size(16.dp)) },
                onClick = { sendKey(KeyEvent.KEYCODE_DPAD_UP) }
            )

            IconButton(
                icon = { Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = Color.White, modifier = Modifier.size(16.dp)) },
                onClick = { sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
            )

            IconButton(
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Right", tint = Color.White, modifier = Modifier.size(16.dp)) },
                onClick = { sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
            )

            IconButton(
                icon = { Icon(Icons.Default.Keyboard, contentDescription = "Toggle Keyboard", tint = Color(0xFF3584E4), modifier = Modifier.size(16.dp)) },
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggleKeyboard()
                }
            )
        }
    }
}

@Composable
private fun KeyButton(
    text: String,
    onClick: () -> Unit,
    isLatched: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isLatched) Color(0xFF3584E4) else Color(0xFF383838)

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun IconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .width(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF383838))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}
