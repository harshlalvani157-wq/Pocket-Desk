package com.pocketdesk.ui

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
import androidx.compose.material.icons.filled.MoreHoriz
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
import com.pocketdesk.service.LinuxBridgeService
import com.pocketdesk.theme.AdwaitaBlue
import com.pocketdesk.theme.AdwaitaCardBg
import com.pocketdesk.theme.AdwaitaCardElevated
import com.pocketdesk.theme.BorderSubtle
import com.pocketdesk.theme.TextPrimary
import com.pocketdesk.theme.TextSecondary

@Composable
fun KeyboardRibbon(
    bridgeService: LinuxBridgeService?,
    onToggleKeyboardMode: () -> Unit,
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

        bridgeService?.sendKeyEvent(keyCode, true, modifiers)
        bridgeService?.sendKeyEvent(keyCode, false, modifiers)

        // Reset latches after firing key
        if (isCtrlLatched) isCtrlLatched = false
        if (isAltLatched) isAltLatched = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(AdwaitaCardBg.copy(alpha = 0.95f))
            .border(width = 0.5.dp, color = BorderSubtle)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Esc Key
            RibbonKeyButton(text = "Esc", onClick = { sendKey(KeyEvent.KEYCODE_ESCAPE) })

            // Tab Key
            RibbonKeyButton(text = "Tab", onClick = { sendKey(KeyEvent.KEYCODE_TAB) })

            // Slash /
            RibbonKeyButton(text = "/", onClick = { sendKey(KeyEvent.KEYCODE_SLASH) })

            // Minus -
            RibbonKeyButton(text = "-", onClick = { sendKey(KeyEvent.KEYCODE_MINUS) })

            // Ctrl (Latchable)
            RibbonKeyButton(
                text = "Ctrl",
                isLatched = isCtrlLatched,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    isCtrlLatched = !isCtrlLatched
                }
            )

            // Alt (Latchable)
            RibbonKeyButton(
                text = "Alt",
                isLatched = isAltLatched,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    isAltLatched = !isAltLatched
                }
            )

            // Paste Key
            RibbonIconButton(
                icon = { Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = TextPrimary, modifier = Modifier.size(16.dp)) },
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).text?.toString() ?: ""
                        for (ch in text) {
                            val code = KeyEvent.getDeadChar(0, ch.code)
                            bridgeService?.sendKeyEvent(code, true, 0)
                            bridgeService?.sendKeyEvent(code, false, 0)
                        }
                    }
                }
            )

            // Combos Menu Key
            Box {
                RibbonKeyButton(
                    text = "Combos",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        showCombosMenu = true
                    }
                )

                DropdownMenu(
                    expanded = showCombosMenu,
                    onDismissRequest = { showCombosMenu = false },
                    modifier = Modifier.background(AdwaitaCardElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text("Ctrl + C (Copy / Interrupt)", color = TextPrimary, fontSize = 13.sp) },
                        onClick = {
                            showCombosMenu = false
                            bridgeService?.sendKeyEvent(KeyEvent.KEYCODE_C, true, 1)
                            bridgeService?.sendKeyEvent(KeyEvent.KEYCODE_C, false, 1)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ctrl + V (Paste)", color = TextPrimary, fontSize = 13.sp) },
                        onClick = {
                            showCombosMenu = false
                            bridgeService?.sendKeyEvent(KeyEvent.KEYCODE_V, true, 1)
                            bridgeService?.sendKeyEvent(KeyEvent.KEYCODE_V, false, 1)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ctrl + Z (Suspend / Undo)", color = TextPrimary, fontSize = 13.sp) },
                        onClick = {
                            showCombosMenu = false
                            bridgeService?.sendKeyEvent(KeyEvent.KEYCODE_Z, true, 1)
                            bridgeService?.sendKeyEvent(KeyEvent.KEYCODE_Z, false, 1)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Alt + F4 (Close Window)", color = TextPrimary, fontSize = 13.sp) },
                        onClick = {
                            showCombosMenu = false
                            bridgeService?.sendKeyEvent(KeyEvent.KEYCODE_F4, true, 2)
                            bridgeService?.sendKeyEvent(KeyEvent.KEYCODE_F4, false, 2)
                        }
                    )
                }
            }

            // Arrow Left
            RibbonIconButton(
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Left", tint = TextPrimary, modifier = Modifier.size(16.dp)) },
                onClick = { sendKey(KeyEvent.KEYCODE_DPAD_LEFT) }
            )

            // Arrow Up
            RibbonIconButton(
                icon = { Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = TextPrimary, modifier = Modifier.size(16.dp)) },
                onClick = { sendKey(KeyEvent.KEYCODE_DPAD_UP) }
            )

            // Arrow Down
            RibbonIconButton(
                icon = { Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = TextPrimary, modifier = Modifier.size(16.dp)) },
                onClick = { sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
            )

            // Arrow Right
            RibbonIconButton(
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Right", tint = TextPrimary, modifier = Modifier.size(16.dp)) },
                onClick = { sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
            )

            // Keyboard Mode Switcher (Android IME vs Linux squeekboard)
            RibbonIconButton(
                icon = { Icon(Icons.Default.Keyboard, contentDescription = "Switch Keyboard", tint = AdwaitaBlue, modifier = Modifier.size(16.dp)) },
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggleKeyboardMode()
                }
            )
        }
    }
}

@Composable
private fun RibbonKeyButton(
    text: String,
    onClick: () -> Unit,
    isLatched: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isLatched) AdwaitaBlue else AdwaitaCardElevated
    val textColor = if (isLatched) TextPrimary else TextSecondary

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
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun RibbonIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .width(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(AdwaitaCardElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}
