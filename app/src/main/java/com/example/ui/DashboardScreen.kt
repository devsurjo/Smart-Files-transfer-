package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ChatMessage
import com.example.model.ClipboardItem
import com.example.model.ConnectedDevice
import com.example.model.TransferItem
import com.example.service.LanServerService
import kotlinx.coroutines.*
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.MutedTeal
import com.example.ui.theme.NeonRose
import com.example.ui.theme.SlateBlack
import com.example.ui.theme.SoftGrey
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Bind Service States
    val isRunning by LanServerService.isRunning.collectAsStateWithLifecycle()
    val serverUrl by LanServerService.serverUrl.collectAsStateWithLifecycle()
    val localIp by LanServerService.localIp.collectAsStateWithLifecycle()
    val wifiSsid by LanServerService.wifiSsid.collectAsStateWithLifecycle()
    val gatewayIp by LanServerService.gatewayIp.collectAsStateWithLifecycle()
    val subnetMask by LanServerService.subnetMask.collectAsStateWithLifecycle()
    
    val connectedDevices by LanServerService.connectedDevices.collectAsStateWithLifecycle()
    val chatMessages by LanServerService.chatMessages.collectAsStateWithLifecycle()
    val clipboardHistory by LanServerService.clipboardHistory.collectAsStateWithLifecycle()
    val transferHistory by LanServerService.transferHistory.collectAsStateWithLifecycle()
    val transferSpeed by LanServerService.currentTransferSpeed.collectAsStateWithLifecycle()
    val isTransferring by LanServerService.isTransferring.collectAsStateWithLifecycle()
    val currentTransferFile by LanServerService.currentTransferFile.collectAsStateWithLifecycle()
    val currentTransferProgress by LanServerService.currentTransferProgress.collectAsStateWithLifecycle()
    
    val passwordProtected by LanServerService.passwordProtected.collectAsStateWithLifecycle()
    val currentPass by LanServerService.accessPassword.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0: Devices, 1: Files, 2: Chat, 3: Clipboard, 4: Settings
    var passwordInput by remember { mutableStateOf(currentPass) }
    
    var showLoadingScreen by remember { mutableStateOf(true) }
    
    LaunchedEffect(showLoadingScreen) {
        if (showLoadingScreen) {
            delay(2500)
            showLoadingScreen = false
        }
    }
    
    // Speed History for Graphing
    val speedHistory = remember { mutableStateListOf<Long>() }
    
    // Sample speed once a second
    LaunchedEffect(transferSpeed) {
        speedHistory.add(transferSpeed)
        if (speedHistory.size > 20) {
            speedHistory.removeAt(0)
        }
    }

    // Modern cyber backdrop layout
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBlack)
    ) {
        val isDesktop = maxWidth >= 768.dp

        // Decorative pulsing background cyber lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            val w = size.width
            // Draw secondary ambient glowing neon lines
            drawLine(
                brush = Brush.horizontalGradient(listOf(Color(0x0066FCF1), Color(0x0D66FCF1), Color(0x0066FCF1))),
                start = Offset(0f, h * 0.2f),
                end = Offset(w, h * 0.2f),
                strokeWidth = 2f
            )
            drawLine(
                brush = Brush.horizontalGradient(listOf(Color(0x00FF2A5F), Color(0x0AFF2A5F), Color(0x00FF2A5F))),
                start = Offset(0f, h * 0.65f),
                end = Offset(w, h * 0.65f),
                strokeWidth = 2f
            )
        }

        if (isDesktop) {
            // ======== DESKTOP SPLIT VIEW (DUAL-PANE PREMIUM ADAPTIVE SYSTEM) ========
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ---- LEFT COLUMN: persistent server config stats & settings ----
                val leftScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                        .verticalScroll(leftScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isRunning) CyberCyan else NeonRose)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "LAN PORTAL",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = "SSID: $wifiSsid",
                                fontSize = 11.sp,
                                color = MutedTeal,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // Action switch
                        IconButton(
                            onClick = {
                                if (isRunning) {
                                    LanServerService.stopService(context)
                                } else {
                                    LanServerService.startService(context)
                                }
                            },
                            modifier = Modifier
                                .background(if (isRunning) Color(0x1F66FCF1) else Color(0x1FFF2A5F), CircleShape)
                                .border(1.dp, if (isRunning) CyberCyan else NeonRose, CircleShape)
                                .testTag("app_running_toggle")
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Toggle Portal",
                                tint = if (isRunning) CyberCyan else NeonRose
                            )
                        }
                    }

                    // QR Link card
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text(
                                    text = "LOCAL SHARE LINK",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MutedTeal
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isRunning) serverUrl else "Portal Offline",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRunning) CyberCyan else SoftGrey,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "IP: $localIp • Port: 8000",
                                    fontSize = 9.sp,
                                    color = SoftGrey
                                )
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            if (isRunning) {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("LAN Share URL", serverUrl))
                                                Toast.makeText(context, "Link Copied!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = isRunning,
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0x3D66FCF1),
                                            disabledContainerColor = Color(0x05ffffff)
                                        ),
                                        border = BorderStroke(1.dp, if (isRunning) CyberCyan else Color.Transparent),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("copy_link_button")
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Copy", modifier = Modifier.size(12.dp), tint = CyberCyan)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy", fontSize = 10.sp, color = if (isRunning) CyberCyan else SoftGrey)
                                    }

                                    Button(
                                        onClick = {
                                            if (isRunning) {
                                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl))
                                                context.startActivity(browserIntent)
                                            }
                                        },
                                        enabled = isRunning,
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0x3D45A29E),
                                            disabledContainerColor = Color(0x05ffffff)
                                        ),
                                        border = BorderStroke(1.dp, if (isRunning) MutedTeal else Color.Transparent),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("open_browser_button")
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Browser", modifier = Modifier.size(12.dp), tint = MutedTeal)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Web", fontSize = 10.sp, color = if (isRunning) MutedTeal else SoftGrey)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .border(1.dp, if (isRunning) CyberCyan else Color(0x1Fffffff), RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0B0C10), RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            ) {
                                if (isRunning) {
                                    QrCodeView(url = serverUrl, modifier = Modifier.fillMaxSize())
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Lock, contentDescription = "Offline", tint = Color(0x3Dffffff), modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Network Metrics Graph
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("LIVE BANDWIDTH SENSOR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MutedTeal)
                                Text(formatBytesPerSec(transferSpeed), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                            }
                            // Speed Canvas
                            SpeedGraph(speedHistory = speedHistory, modifier = Modifier.width(140.dp).height(32.dp))
                        }
                    }

                    // Progress bar for active file transfers
                    AnimatedVisibility(visible = isTransferring) {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("PEER SYNCING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                                    Text(currentTransferFile, fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("${(currentTransferProgress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { currentTransferProgress },
                                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                                color = CyberCyan,
                                trackColor = Color(0x1Fffffff)
                            )
                        }
                    }

                    // Embed Advanced Settings right inside the left persistent panel
                    DesktopSettingsEmbedPanel(onReplayIntro = { showLoadingScreen = true })
                }

                // ---- RIGHT COLUMN: Tab selection details (Devices, Files, Chat, Clipboard) (flexible weight) ----
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TabRow(
                        selectedTabIndex = activeTab.coerceAtMost(3),
                        containerColor = Color(0x0DFFFFFF),
                        contentColor = CyberCyan,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab.coerceAtMost(3)]),
                                color = CyberCyan,
                                height = 2.dp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0x1Fffffff), RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text("Devices", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.Devices, contentDescription = "Devices", modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("Files", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.Folder, contentDescription = "Files", modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 },
                            text = { Text("Chat", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.Chat, contentDescription = "Chat", modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = activeTab == 3,
                            onClick = { activeTab = 3 },
                            text = { Text("Clipboard", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.ContentPaste, contentDescription = "Clipboard", modifier = Modifier.size(18.dp)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Embedded Interactive Tab Detail panel
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (activeTab.coerceAtMost(3)) {
                            0 -> DevicesTab(devices = connectedDevices)
                            1 -> FilesTab(
                                history = transferHistory,
                                serverActive = isRunning,
                                onPickFile = { file ->
                                    val activity = context as? androidx.activity.ComponentActivity
                                    activity?.let {
                                        val intent = Intent(it, LanServerService::class.java)
                                        it.startService(intent)
                                    }
                                    Toast.makeText(context, "File copied to shared portal!", Toast.LENGTH_SHORT).show()
                                }
                            )
                            2 -> ChatTab(messages = chatMessages, onSendMessage = { text ->
                                if (isRunning) {
                                    LanServerService.chatMessages.value = LanServerService.chatMessages.value + ChatMessage(sender = "You", text = text, isMe = true)
                                    LanServerService.startService(context)
                                } else {
                                    Toast.makeText(context, "Server offline. Enable the portal first.", Toast.LENGTH_SHORT).show()
                                }
                            })
                            3 -> ClipboardTab(history = clipboardHistory, onPushText = { text ->
                                if (isRunning) {
                                    Toast.makeText(context, "Copied text shared to portal!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Server offline.", Toast.LENGTH_SHORT).show()
                                }
                            })
                        }
                    }
                }
            }
        } else {
            // ======== MOBILE SINGLE COLUMN (5 TABS NATIVE SYSTEM) ========
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isRunning) CyberCyan else NeonRose)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SMART LAN SHARE",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "SSID: $wifiSsid",
                            fontSize = 11.sp,
                            color = MutedTeal,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            if (isRunning) {
                                LanServerService.stopService(context)
                            } else {
                                LanServerService.startService(context)
                            }
                        },
                        modifier = Modifier
                            .background(if (isRunning) Color(0x1F66FCF1) else Color(0x1FFF2A5F), CircleShape)
                            .border(1.dp, if (isRunning) CyberCyan else NeonRose, CircleShape)
                            .testTag("app_running_toggle")
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Server Toggle",
                            tint = if (isRunning) CyberCyan else NeonRose
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable top banner layout only if Settings is NOT selected, keeping interface clean!
                val mobileScrollState = rememberScrollState()
                if (activeTab != 4) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.3f)
                            .verticalScroll(mobileScrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Portal URL Card
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.3f)) {
                                    Text("LOCAL SHARE LINKPORTAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MutedTeal)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isRunning) serverUrl else "Server Offline",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRunning) CyberCyan else SoftGrey,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Gateway: $gatewayIp • Netmask: $subnetMask",
                                        fontSize = 9.sp,
                                        color = SoftGrey
                                    )
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                if (isRunning) {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("LAN Share URL", serverUrl))
                                                    Toast.makeText(context, "portal URL Copied!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            enabled = isRunning,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x3D66FCF1), disabledContainerColor = Color(0x05ffffff)),
                                            border = BorderStroke(1.dp, if (isRunning) CyberCyan else Color.Transparent),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = "Copy", modifier = Modifier.size(12.dp), tint = CyberCyan)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy Link", fontSize = 10.sp, color = if (isRunning) CyberCyan else SoftGrey)
                                        }

                                        Button(
                                            onClick = {
                                                if (isRunning) {
                                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl))
                                                    context.startActivity(browserIntent)
                                                }
                                            },
                                            enabled = isRunning,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x3D45A29E), disabledContainerColor = Color(0x05ffffff)),
                                            border = BorderStroke(1.dp, if (isRunning) MutedTeal else Color.Transparent),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "Browser", modifier = Modifier.size(12.dp), tint = MutedTeal)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Web", fontSize = 10.sp, color = if (isRunning) MutedTeal else SoftGrey)
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(10.dp))
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .border(1.dp, if (isRunning) CyberCyan else Color(0x1Fffffff), RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0B0C10), RoundedCornerShape(8.dp))
                                        .padding(6.dp)
                                ) {
                                    if (isRunning) {
                                        QrCodeView(url = serverUrl, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Lock, contentDescription = "Offline", tint = Color(0x3Dffffff), modifier = Modifier.size(28.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Password & Speed Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassCard(modifier = Modifier.weight(1.1f)) {
                                Text("ACCESS PROTECTION", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MutedTeal)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (passwordProtected) "🔒 Shield locked" else "🔓 Open network",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (passwordProtected) CyberCyan else SoftGrey
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Modify in settings tab", fontSize = 9.sp, color = SoftGrey)
                            }
                            
                            GlassCard(modifier = Modifier.weight(1f)) {
                                Text("LIVE THROUGHPUT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MutedTeal)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(formatBytesPerSec(transferSpeed), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                                Spacer(modifier = Modifier.height(4.dp))
                                SpeedGraph(speedHistory = speedHistory, modifier = Modifier.fillMaxWidth().height(20.dp))
                            }
                        }

                        // Progress Indicator
                        AnimatedVisibility(visible = isTransferring) {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("TRANSFER IN PROGRESS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                                        Text(currentTransferFile, fontSize = 11.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("${(currentTransferProgress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { currentTransferProgress },
                                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                                    color = CyberCyan,
                                    trackColor = Color(0x1Fffffff)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Interactive Segmented Tab Menu
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color(0x0DFFFFFF),
                    contentColor = CyberCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                            color = CyberCyan,
                            height = 2.dp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x1Fffffff), RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Devices", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Devices, contentDescription = "Devices", modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Files", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Files", modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        text = { Text("Chat", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "Chat", modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = activeTab == 3,
                        onClick = { activeTab = 3 },
                        text = { Text("Clip", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.ContentPaste, contentDescription = "Clipboard", modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = activeTab == 4,
                        onClick = { activeTab = 4 },
                        text = { Text("Settings", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(16.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Render matching active tab content Card
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxWidth()
                ) {
                    when (activeTab) {
                        0 -> DevicesTab(devices = connectedDevices)
                        1 -> FilesTab(
                            history = transferHistory,
                            serverActive = isRunning,
                            onPickFile = { file ->
                                val activity = context as? androidx.activity.ComponentActivity
                                activity?.let {
                                    val intent = Intent(it, LanServerService::class.java)
                                    it.startService(intent)
                                }
                                Toast.makeText(context, "File copied to shared portal!", Toast.LENGTH_SHORT).show()
                            }
                        )
                        2 -> ChatTab(messages = chatMessages, onSendMessage = { text ->
                            if (isRunning) {
                                LanServerService.chatMessages.value = LanServerService.chatMessages.value + ChatMessage(sender = "You", text = text, isMe = true)
                                LanServerService.startService(context)
                            } else {
                                Toast.makeText(context, "Server offline. Chat disabled.", Toast.LENGTH_SHORT).show()
                            }
                        })
                        3 -> ClipboardTab(history = clipboardHistory, onPushText = { text ->
                            if (isRunning) {
                                Toast.makeText(context, "Copied snippet shared!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Server offline.", Toast.LENGTH_SHORT).show()
                            }
                        })
                        4 -> SettingsTab(onReplayIntro = { showLoadingScreen = true })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showLoadingScreen,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(500)),
            modifier = Modifier.fillMaxSize()
        ) {
            DevSurjoLoadingScreen()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(onReplayIntro: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val autoDeleteEnabled by LanServerService.autoDeleteFiles.collectAsStateWithLifecycle()
    val autoDeleteHoursValue by LanServerService.autoDeleteHours.collectAsStateWithLifecycle()
    val autoCopyEnabled by LanServerService.autoCopyPeerClipboard.collectAsStateWithLifecycle()
    val passwordProtected by LanServerService.passwordProtected.collectAsStateWithLifecycle()
    val accessPassword by LanServerService.accessPassword.collectAsStateWithLifecycle()
    
    var localPassword by remember { mutableStateOf(accessPassword) }
    
    val intervals = listOf(
        1 to "1 Hour",
        3 to "3 Hours",
        6 to "6 Hours",
        12 to "12 Hours",
        24 to "24 Hours (1 Day)",
        48 to "48 Hours (2 Days)",
        168 to "168 Hours (7 Days)"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: Advanced Sharing Preferences
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "SHARING PREFERENCES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MutedTeal,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Toggle Auto-Copy peer clipboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Sync System Clipboard", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Instantly copies incoming peer text snippets into your local phone clipboard.", fontSize = 10.sp, color = SoftGrey)
                }
                Switch(
                    checked = autoCopyEnabled,
                    onCheckedChange = { LanServerService.updateAutoCopy(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberCyan,
                        checkedTrackColor = Color(0x3D66FCF1),
                        uncheckedThumbColor = SoftGrey,
                        uncheckedTrackColor = Color(0x1Fffffff)
                    )
                )
            }
        }
        
        // Card 2: Auto Deletion Storage Saver
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "STORAGE SAVER (AUTO CLEANUP)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MutedTeal,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Delete Shared Files", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Silently purges shared resources after a select duration to prevent storage bloat.", fontSize = 10.sp, color = SoftGrey)
                }
                Switch(
                    checked = autoDeleteEnabled,
                    onCheckedChange = { LanServerService.updateAutoDelete(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberCyan,
                        checkedTrackColor = Color(0x3D66FCF1),
                        uncheckedThumbColor = SoftGrey,
                        uncheckedTrackColor = Color(0x1Fffffff)
                    )
                )
            }
            
            if (autoDeleteEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0x0Fffffff))
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    "DELETE CHOSEN TIMEOUT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedTeal
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                // Flow row to pick duration (extremely sleek and premium!)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    intervals.forEach { (hours, label) ->
                        val isSelected = autoDeleteHoursValue == hours
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, if (isSelected) CyberCyan else Color(0x1Fffffff), RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0x1F66FCF1) else Color.Transparent)
                                .clickable {
                                    LanServerService.updateDeleteHours(context, hours)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) CyberCyan else SoftGrey
                            )
                        }
                    }
                }
            }
        }
        
        // Card 3: Shield Protection Settings
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "SECURITY ACCESS PASSWORD",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MutedTeal,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Locks down HTTP access logs, clipboard transfers, chat rooms, and downloads with a required passcode overlay.",
                fontSize = 10.sp,
                color = SoftGrey
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = localPassword,
                onValueChange = { localPassword = it },
                placeholder = { Text("No password (Open Portal)", fontSize = 11.sp, color = SoftGrey) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = Color(0x1Fffffff),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = Color.White),
                trailingIcon = {
                    Button(
                        onClick = {
                            LanServerService.updateAccessPassword(context, localPassword)
                            LanServerService.startService(context) // Restart server to apply
                            Toast.makeText(context, "Access protection password update applied!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x3D66FCF1)),
                        modifier = Modifier.padding(end = 4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Text("Apply", fontSize = 10.sp, color = CyberCyan)
                    }
                }
            )
        }
        
        // Card 4: Quick Destructive Actions
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "SYSTEM MAINTENANCE OPERATIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NeonRose,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        LanServerService.clearAllSharedFiles(context)
                        Toast.makeText(context, "Shared portal cache cleared completely!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x1FFF2A5F)),
                    border = BorderStroke(1.dp, NeonRose),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear Web Files", modifier = Modifier.size(14.dp), tint = NeonRose)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Wipe Storage", fontSize = 10.sp, color = NeonRose, fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = {
                        LanServerService.stopService(context)
                        scope.launch {
                            delay(500)
                            LanServerService.startService(context)
                            Toast.makeText(context, "Portal Server Rebooted Successfully!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x1F66FCF1)),
                    border = BorderStroke(1.dp, CyberCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reboot Server", modifier = Modifier.size(14.dp), tint = CyberCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reboot", fontSize = 10.sp, color = CyberCyan, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Card 5: Developer Support (Prowerd by Dev Surjo)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "DEVELOPER & SUPPORT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Heart Icon/Indicator
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0x1FFF2A5F))
                        .border(1.dp, NeonRose, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Support Heart",
                        tint = NeonRose,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Prowerd by Dev Surjo",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Thank you for using Smart LAN Share! Show some love and support by visiting the GitHub profile. Your support helps build premium, privacy-respecting local network utility tools.",
                        fontSize = 10.sp,
                        color = SoftGrey
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0x0Fffffff))
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // GitHub Button
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/devsurjo"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x1E66FCF1)),
                    border = BorderStroke(1.dp, CyberCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "Github Profile",
                        modifier = Modifier.size(14.dp),
                        tint = CyberCyan
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Support Dev Surjo",
                        fontSize = 10.sp,
                        color = CyberCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Replay Loading Screen button
                Button(
                    onClick = onReplayIntro,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x0Fffffff)),
                    border = BorderStroke(1.dp, Color(0x1Fffffff)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Replay Intro",
                        modifier = Modifier.size(14.dp),
                        tint = SoftGrey
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Replay Intro",
                        fontSize = 10.sp,
                        color = SoftGrey,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun DesktopSettingsEmbedPanel(onReplayIntro: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val autoDeleteEnabled by LanServerService.autoDeleteFiles.collectAsStateWithLifecycle()
    val autoDeleteHoursValue by LanServerService.autoDeleteHours.collectAsStateWithLifecycle()
    val autoCopyEnabled by LanServerService.autoCopyPeerClipboard.collectAsStateWithLifecycle()
    val passwordProtected by LanServerService.passwordProtected.collectAsStateWithLifecycle()
    val accessPassword by LanServerService.accessPassword.collectAsStateWithLifecycle()
    
    var localPassword by remember { mutableStateOf(accessPassword) }
    
    val intervals = listOf(
        1 to "1h",
        6 to "6h",
        24 to "24h",
        168 to "7d"
    )

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "PORTAL SETTINGS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MutedTeal,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Auto copy clipboard
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto Clipboard Sync", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Peer clips sync to phone", fontSize = 9.sp, color = SoftGrey)
            }
            Switch(
                checked = autoCopyEnabled,
                onCheckedChange = { LanServerService.updateAutoCopy(context, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CyberCyan,
                    checkedTrackColor = Color(0x3D66FCF1),
                    uncheckedThumbColor = SoftGrey,
                    uncheckedTrackColor = Color(0x1Fffffff)
                ),
                modifier = Modifier.scale(0.8f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = Color(0x0Fffffff))
        Spacer(modifier = Modifier.height(8.dp))

        // Auto-delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-Delete (24 Hr Safe)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Auto-delete space saver", fontSize = 9.sp, color = SoftGrey)
            }
            Switch(
                checked = autoDeleteEnabled,
                onCheckedChange = { LanServerService.updateAutoDelete(context, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CyberCyan,
                    checkedTrackColor = Color(0x3D66FCF1),
                    uncheckedThumbColor = SoftGrey,
                    uncheckedTrackColor = Color(0x1Fffffff)
                ),
                modifier = Modifier.scale(0.8f)
            )
        }

        if (autoDeleteEnabled) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Timeout: ", fontSize = 9.sp, color = SoftGrey, fontWeight = FontWeight.Bold)
                intervals.forEach { (hours, label) ->
                    val isSelected = autoDeleteHoursValue == hours
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, if (isSelected) CyberCyan else Color(0x1Fffffff), RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0x1F66FCF1) else Color.Transparent)
                            .clickable { LanServerService.updateDeleteHours(context, hours) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isSelected) CyberCyan else SoftGrey)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = Color(0x0Fffffff))
        Spacer(modifier = Modifier.height(10.dp))

        // Password Input
        Text("PORTAL PASSWORD CONFIG", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MutedTeal)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = localPassword,
            onValueChange = { localPassword = it },
            placeholder = { Text("No password", fontSize = 10.sp, color = SoftGrey) },
            singleLine = true,
            shape = RoundedCornerShape(6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = Color(0x1Fffffff),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth().height(42.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 10.sp, color = Color.White),
            trailingIcon = {
                Button(
                    onClick = {
                        LanServerService.updateAccessPassword(context, localPassword)
                        LanServerService.startService(context)
                        Toast.makeText(context, "Password applied!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3D66FCF1)),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Apply", fontSize = 9.sp, color = CyberCyan)
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Color(0x0Fffffff))
        Spacer(modifier = Modifier.height(12.dp))

        // System Maintenance operations
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    LanServerService.clearAllSharedFiles(context)
                    Toast.makeText(context, "Cache deleted!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x1FFF2A5F)),
                border = BorderStroke(1.dp, NeonRose),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier.weight(1f).height(32.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(12.dp), tint = NeonRose)
                Spacer(modifier = Modifier.width(3.dp))
                Text("Wipe Files", fontSize = 9.sp, color = NeonRose)
            }

            Button(
                onClick = {
                     LanServerService.stopService(context)
                     // restart
                     scope.launch {
                         delay(400)
                         LanServerService.startService(context)
                         Toast.makeText(context, "Server Rebooted!", Toast.LENGTH_SHORT).show()
                     }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x1F66FCF1)),
                border = BorderStroke(1.dp, CyberCyan),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier.weight(1f).height(32.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reboot", modifier = Modifier.size(12.dp), tint = CyberCyan)
                Spacer(modifier = Modifier.width(3.dp))
                Text("Reboot", fontSize = 9.sp, color = CyberCyan)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = Color(0x0Fffffff))
        Spacer(modifier = Modifier.height(10.dp))

        // Support segment for Desktop panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0x1FFF2A5F))
                    .border(0.5.dp, NeonRose, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Support Heart",
                    tint = NeonRose,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = "Prowerd by Dev Surjo",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/devsurjo"))
                    context.startActivity(intent)
                },
                modifier = Modifier.weight(1f).height(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x1E66FCF1)),
                border = BorderStroke(0.5.dp, CyberCyan),
                shape = RoundedCornerShape(5.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Donate & Support", fontSize = 9.sp, color = CyberCyan, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onReplayIntro,
                modifier = Modifier.weight(1f).height(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x0Fffffff)),
                border = BorderStroke(0.5.dp, Color(0x1Fffffff)),
                shape = RoundedCornerShape(5.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Replay Intro", fontSize = 9.sp, color = SoftGrey, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DevicesTab(devices: List<ConnectedDevice>) {
    GlassCard(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "CONNECTED DEVICES (${devices.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MutedTeal,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Devices, contentDescription = "Empty", tint = Color(0x1Fffffff), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No connected devices yet", fontSize = 12.sp, color = SoftGrey, textAlign = TextAlign.Center)
                    Text("Open the portal link on another machine", fontSize = 10.sp, color = MutedTeal, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { dev ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x0Dffffff), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0x08ffffff), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0x1F66FCF1), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Computer, contentDescription = "PC", tint = CyberCyan, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(dev.deviceName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("IP Address: ${dev.ip}", fontSize = 11.sp, color = SoftGrey)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilesTab(
    history: List<TransferItem>,
    serverActive: Boolean,
    onPickFile: (File) -> Unit
) {
    val context = LocalContext.current
    
    // File chooser launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // copy to cache
            val file = copyUriToSharedFiles(context, it)
            if (file != null) {
                // Access service to copy
                val serviceIntent = Intent(context, LanServerService::class.java)
                context.startService(serviceIntent)
                
                // Set and share
                LanServerService.startService(context)
                // Call static helper directly
                LanServerService.transferHistory.value = listOf(
                    TransferItem(
                        name = file.name,
                        size = file.length(),
                        path = file.absolutePath,
                        isIncoming = false,
                        progress = 1.0f,
                        status = "Success"
                    )
                ) + LanServerService.transferHistory.value.filter { it.name != file.name }
                
                onPickFile(file)
            }
        }
    }

    GlassCard(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TRANSFER HISTORY AREA",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MutedTeal,
                letterSpacing = 0.5.sp
            )
            
            Button(
                onClick = {
                    if (serverActive) {
                        filePickerLauncher.launch("*/*")
                    } else {
                        Toast.makeText(context, "Start local portal first!", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("pick_file_to_share_button")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = "Add", modifier = Modifier.size(16.dp), tint = SlateBlack)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share File", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateBlack)
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Folder, contentDescription = "No Files", tint = Color(0x1Fffffff), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No file transfers logged", fontSize = 12.sp, color = SoftGrey, textAlign = TextAlign.Center)
                    Text("Incoming web files appear here automatically", fontSize = 10.sp, color = MutedTeal, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x0Dffffff), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0x08ffffff), RoundedCornerShape(10.dp))
                            .clickable {
                                // Action to open file
                                try {
                                    val file = File(item.path)
                                    if (file.exists()) {
                                        val authorities = "${context.packageName}.provider"
                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, authorities, file)
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, context.contentResolver.getType(uri))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Open file with"))
                                    } else {
                                        Toast.makeText(context, "File does not exist on disk.", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Unable to launch file: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (item.isIncoming) Color(0x1F66FCF1) else Color(0x1FFF2A5F), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (item.isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = "Direction",
                                tint = if (item.isIncoming) CyberCyan else NeonRose,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(10.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatBytes(item.size)} • ${if (item.isIncoming) "Inbound Portal" else "Outbound Portal"}",
                                fontSize = 11.sp,
                                color = SoftGrey
                            )
                        }
                        
                        Icon(Icons.Default.Check, contentDescription = "Success", tint = CyberCyan, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChatTab(messages: List<ChatMessage>, onSendMessage: (String) -> Unit) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom of chat
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    GlassCard(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "LIVE PEER CROSS-PORTAL CHAT",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MutedTeal,
            letterSpacing = 0.5.sp
        )
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // Chat List
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Chat, contentDescription = "Empty", tint = Color(0x1Fffffff), modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No messages yet", fontSize = 12.sp, color = SoftGrey)
                        Text("Messages typed on Web Client will appear here", fontSize = 10.sp, color = MutedTeal)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages) { msg ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start
                        ) {
                            Text(
                                text = msg.sender,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedTeal,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (msg.isMe) CyberCyan else Color(0x3D1F2833),
                                        shape = RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (msg.isMe) 12.dp else 2.dp,
                                            bottomEnd = if (msg.isMe) 2.dp else 12.dp
                                        )
                                    )
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    fontSize = 12.sp,
                                    color = if (msg.isMe) SlateBlack else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // Input footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Type message...", fontSize = 12.sp, color = SoftGrey) },
                singleLine = true,
                maxLines = 1,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = Color(0x1Fffffff),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("chat_input_field"),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
            
            IconButton(
                onClick = {
                    if (textInput.trim().isNotEmpty()) {
                        onSendMessage(textInput)
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(CyberCyan, RoundedCornerShape(10.dp))
                    .border(1.dp, CyberCyan, RoundedCornerShape(10.dp))
                    .testTag("send_chat_msg_btn")
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = SlateBlack)
            }
        }
    }
}

@Composable
fun ClipboardTab(history: List<ClipboardItem>, onPushText: (String) -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var textInput by remember { mutableStateOf("") }

    GlassCard(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "SHARED LAN TEXT CLIPBOARD",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MutedTeal,
            letterSpacing = 0.5.sp
        )
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // Quick Push TextArea
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Type text to broadcast to network...", fontSize = 12.sp, color = SoftGrey) },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = Color(0x1Fffffff),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("clipboard_input_field"),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
            
            Button(
                onClick = {
                    if (textInput.trim().isNotEmpty()) {
                        // Native notification & Broadcast
                        clipboardManager.setText(AnnotatedString(textInput))
                        
                        LanServerService.startService(context)
                        LanServerService.clipboardHistory.value = listOf(
                            ClipboardItem(text = textInput, sender = "Host Device")
                        ) + LanServerService.clipboardHistory.value
                        
                        onPushText(textInput)
                        textInput = ""
                        Toast.makeText(context, "Copied on phone & Broad-casted!", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("push_clipboard_btn")
            ) {
                Text("Push", color = SlateBlack, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(14.dp))
        
        Text(
            text = "CLIPBOARD HISTORY",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MutedTeal
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Empty", tint = Color(0x1Fffffff), modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No shared texts", fontSize = 12.sp, color = SoftGrey)
                    Text("Browser copies sync here instantly", fontSize = 10.sp, color = MutedTeal)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x0Dffffff), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0x08ffffff), RoundedCornerShape(10.dp))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(item.text))
                                Toast.makeText(context, "Copied text to local clipboard", Toast.LENGTH_SHORT).show()
                            }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "From: ${item.sender}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Copy",
                                tint = SoftGrey,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.text,
                            fontSize = 12.sp,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// QR Code Custom Canvas Renderer based on ZXing
@Composable
fun QrCodeView(url: String, modifier: Modifier = Modifier) {
    val bitMatrix = remember(url) {
        try {
            QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 200, 200)
        } catch (e: Exception) {
            null
        }
    }
    
    if (bitMatrix != null) {
        Canvas(modifier = modifier) {
            val width = bitMatrix.width
            val height = bitMatrix.height
            val cellWidth = size.width / width
            val cellHeight = size.height / height
            
            // Background grid dark slate
            drawRect(
                color = Color(0xFF0B0C10),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, size.height)
            )
            
            // Draw fluorescent cells
            for (x in 0 until width) {
                for (y in 0 until height) {
                    if (bitMatrix.get(x, y)) {
                        drawRect(
                            color = Color(0xFF66FCF1),
                            topLeft = Offset(x * cellWidth, y * cellHeight),
                            size = Size(cellWidth + 0.5f, cellHeight + 0.5f)
                        )
                    }
                }
            }
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("QR Generation Error", color = NeonRose, fontSize = 11.sp)
        }
    }
}

@Composable
fun SpeedGraph(speedHistory: List<Long>, modifier: Modifier = Modifier) {
    val maxSpeed = (speedHistory.maxOrNull() ?: 1024L).coerceAtLeast(1024L)
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        if (speedHistory.size < 2) {
            // Draw baseline line representing zero
            drawLine(
                color = Color(0x1Fffffff),
                start = Offset(0f, height),
                end = Offset(width, height),
                strokeWidth = 2f
            )
            return@Canvas
        }
        
        val path = Path()
        val stepX = width / (speedHistory.size - 1)
        
        speedHistory.forEachIndexed { index, speed ->
            val fraction = speed.toFloat() / maxSpeed
            val x = index * stepX
            val y = height - (fraction * height * 0.85f) // top margin space
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        // Draw path stroke with cyan glow
        drawPath(
            path = path,
            color = Color(0xFF66FCF1),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Draw gradient fill under the curve
        val fillPath = Path().apply {
            addPath(path)
            lineTo((speedHistory.size - 1) * stepX, height)
            lineTo(0f, height)
            close()
        }
        
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(Color(0x3366FCF1), Color(0x0066FCF1))
            )
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0x14ffffff) // frosted layer
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0x12ffffff))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            content = content
        )
    }
}

// Utility formatting methods
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 Bytes"
    val units = arrayOf("Bytes", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun formatBytesPerSec(bytes: Long): String {
    return "${formatBytes(bytes)}/s"
}

// File copying utility for URIs
fun copyUriToSharedFiles(context: Context, uri: Uri): File? {
    return try {
        val resolver = context.contentResolver
        var name = "share_${System.currentTimeMillis()}"
        val cursor = resolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
        
        val tempDir = File(context.cacheDir, "shared_files").apply { mkdirs() }
        val destFile = File(tempDir, name)
        
        resolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        destFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun DevSurjoLoadingScreen() {
    var stepMessage by remember { mutableStateOf("Initializing Core Systems...") }
    val stepMessages = listOf(
        "Initializing Core Systems...",
        "Scanning local Wi-Fi interfaces...",
        "Resolving local gateway IP...",
        "Spinning up Ktor HTTP Web Portal Service...",
        "Creating Zero-Config WebSockets engine...",
        "Setting up custom Peer-to-Peer clipboard bridge...",
        "Smart LAN Share is fully initialized!"
    )

    LaunchedEffect(Unit) {
        stepMessages.forEachIndexed { idx, msg ->
            stepMessage = msg
            delay(350)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "loading_rotation")
    
    // Rotate 360 degrees
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulse alpha for loading text
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Pulse size for logo core
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // High-Tech Visual Radar / Scanner Core
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(pulseScale),
                contentAlignment = Alignment.Center
            ) {
                // outer scanning circle rotating
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = CyberCyan,
                        startAngle = angle,
                        sweepAngle = 280f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    
                    drawArc(
                        color = NeonRose,
                        startAngle = angle + 180f,
                        sweepAngle = 60f,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Inner core glowing icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0x1F66FCF1))
                        .border(1.dp, Color(0x3D66FCF1), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Logo",
                        tint = CyberCyan,
                        modifier = Modifier
                            .size(36.dp)
                            .scale(pulseScale)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Main Branding Header with Cyberpunk typography
            Text(
                text = "SMART LAN SHARE",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Developer attribution
            Text(
                text = "Prowerd by Dev Surjo",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(pulseAlpha)
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Tech diagnostic log console
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x0Fffffff)),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0x12ffffff)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = NeonRose,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stepMessage,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = SoftGrey
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Footer info
            Text(
                text = "Secure local sharing without Internet connection",
                fontSize = 11.sp,
                color = MutedTeal,
                textAlign = TextAlign.Center
            )
        }
    }
}
