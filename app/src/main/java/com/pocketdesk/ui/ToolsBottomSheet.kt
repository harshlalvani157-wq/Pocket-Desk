package com.pocketdesk.ui

import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdesk.theme.AdwaitaBlue
import com.pocketdesk.theme.AdwaitaCardBg
import com.pocketdesk.theme.AdwaitaDestructive
import com.pocketdesk.theme.TextMuted
import com.pocketdesk.theme.TextPrimary
import com.pocketdesk.theme.TextSecondary

enum class KeyboardMode {
    ANDROID_HOST_IME,
    LINUX_SQUEEKBOARD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsBottomSheet(
    currentScale: Float,
    currentOrientation: Int,
    currentKeyboardMode: KeyboardMode,
    onScaleChange: (Float) -> Unit,
    onOrientationChange: (Int) -> Unit,
    onKeyboardModeChange: (KeyboardMode) -> Unit,
    onTerminateAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sliderValue by remember { mutableFloatStateOf(currentScale) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AdwaitaCardBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Text(
                text = "PocketDesk Quick Controls",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            // 1. DPI & Scaling Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = null, tint = AdwaitaBlue)
                        Text("Display Scaling (Auto-DPI)", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    val xftDpi = (96 * sliderValue).toInt()
                    Text(
                        text = "%.2fx (%d DPI)".format(sliderValue, xftDpi),
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        onScaleChange(it)
                    },
                    valueRange = 1.0f..3.0f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = AdwaitaBlue,
                        activeTrackColor = AdwaitaBlue,
                        inactiveTrackColor = AdwaitaCardBg
                    )
                )
            }

            // 2. Orientation Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = AdwaitaBlue)
                    Text("Screen Orientation", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
                        onClick = { onOrientationChange(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) },
                        label = { Text("Auto") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AdwaitaBlue,
                            selectedLabelColor = TextPrimary
                        )
                    )
                    FilterChip(
                        selected = (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
                        onClick = { onOrientationChange(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) },
                        label = { Text("Portrait") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AdwaitaBlue,
                            selectedLabelColor = TextPrimary
                        )
                    )
                    FilterChip(
                        selected = (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
                        onClick = { onOrientationChange(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) },
                        label = { Text("Landscape") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AdwaitaBlue,
                            selectedLabelColor = TextPrimary
                        )
                    )
                }
            }

            // 3. Keyboard Mode Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = null, tint = AdwaitaBlue)
                    Text("Input Keyboard Provider", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = (currentKeyboardMode == KeyboardMode.ANDROID_HOST_IME),
                        onClick = { onKeyboardModeChange(KeyboardMode.ANDROID_HOST_IME) },
                        label = { Text("Android Gboard / IME") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AdwaitaBlue,
                            selectedLabelColor = TextPrimary
                        )
                    )
                    FilterChip(
                        selected = (currentKeyboardMode == KeyboardMode.LINUX_SQUEEKBOARD),
                        onClick = { onKeyboardModeChange(KeyboardMode.LINUX_SQUEEKBOARD) },
                        label = { Text("Linux Squeekboard") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AdwaitaBlue,
                            selectedLabelColor = TextPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Terminate All Tasks Button
            Button(
                onClick = {
                    onTerminateAll()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AdwaitaDestructive),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Terminate All Linux Applications", fontWeight = FontWeight.Bold)
            }
        }
    }
}
