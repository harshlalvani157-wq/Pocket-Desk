package com.pocketdesk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.pocketdesk.theme.AdwaitaBlue
import com.pocketdesk.theme.AdwaitaCardBg
import com.pocketdesk.theme.AdwaitaCardElevated
import com.pocketdesk.theme.AdwaitaDarkBg
import com.pocketdesk.theme.AdwaitaSuccess
import com.pocketdesk.theme.PocketDeskTheme
import com.pocketdesk.theme.TextMuted
import com.pocketdesk.theme.TextPrimary
import com.pocketdesk.theme.TextSecondary
import java.io.File

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        requestRequiredPermissions()

        setContent {
            PocketDeskTheme {
                WelcomeScreen(
                    onProceed = {
                        val intent = Intent(this, DesktopActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

@Composable
private fun WelcomeScreen(
    onProceed: () -> Unit
) {
    val context = LocalContext.current
    var isRootfsPresent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val rootfsDir = File(context.filesDir, "debian-rootfs")
        isRootfsPresent = rootfsDir.exists() && rootfsDir.isDirectory
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AdwaitaDarkBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // App Title & Tagline
            Text(
                text = "PocketDesk",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Native Android Mobile Shell with Embedded Debian 13",
                color = AdwaitaBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Architectural Highlights Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(AdwaitaCardBg)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ArchitecturePill(
                    icon = Icons.Default.PhoneAndroid,
                    title = "Dual-Layer Hybrid Shell",
                    desc = "Host Android UI with seamless touch navigation, search, and recent card switcher"
                )

                ArchitecturePill(
                    icon = Icons.Default.Terminal,
                    title = "Debian 13 (Trixie) ARM64",
                    desc = "Mobian GTK4+Libadwaita touch apps running rootless in isolated window mode"
                )

                ArchitecturePill(
                    icon = Icons.Default.Speed,
                    title = "Hardware OpenGL ES 2.0",
                    desc = "Zero-copy EGL surface presentation streaming at 60 FPS directly into Android SurfaceView"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Rootfs Status Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isRootfsPresent) AdwaitaSuccess else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isRootfsPresent) "Debian 13 Rootfs Ready" else "Ready to run (Fallback Mode / Rootfs Bootstrap)",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Launch Button
            Button(
                onClick = onProceed,
                colors = ButtonDefaults.buttonColors(containerColor = AdwaitaBlue),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = "Enter PocketDesk",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.size(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun ArchitecturePill(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AdwaitaCardElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AdwaitaBlue,
                modifier = Modifier.size(20.dp)
            )
        }

        Column {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
