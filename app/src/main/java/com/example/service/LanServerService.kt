package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.model.ChatMessage
import com.example.model.ClipboardItem
import com.example.model.ConnectedDevice
import com.example.model.TransferItem
import com.example.server.LanServer
import com.example.util.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.withLock
import java.io.File

class LanServerService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    
    private var lanServer: LanServer? = null
    private var connectivityManager: ConnectivityManager? = null
    private val serverMutex = kotlinx.coroutines.sync.Mutex()
    
    // Track transfer metrics
    private var lastSpeedUpdate = 0L
    private var lastBytes = 0L

    companion object {
        const val CHANNEL_ID = "SmartLanShareChannel"
        const val NOTIFICATION_ID = 1001
        const val DEVICE_CONNECT_NOTIFICATION_ID = 1002
        
        // Broadcast / Live States for UI bindings
        val isRunning = MutableStateFlow(false)
        val serverUrl = MutableStateFlow("http://0.0.0.0:8000")
        val localIp = MutableStateFlow("0.0.0.0")
        val wifiSsid = MutableStateFlow("Disconnected")
        val gatewayIp = MutableStateFlow("0.0.0.0")
        val subnetMask = MutableStateFlow("255.255.255.0")
        
        val connectedDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
        val chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
        val clipboardHistory = MutableStateFlow<List<ClipboardItem>>(emptyList())
        val transferHistory = MutableStateFlow<List<TransferItem>>(emptyList())
        val currentTransferSpeed = MutableStateFlow(0L) // bytes per sec
        val isTransferring = MutableStateFlow(false)
        val currentTransferFile = MutableStateFlow("")
        val currentTransferProgress = MutableStateFlow(0.0f)
        
        val passwordProtected = MutableStateFlow(false)
        val accessPassword = MutableStateFlow("")

        // Persistent Advanced Settings Toggles
        val autoDeleteFiles = MutableStateFlow(true)
        val autoDeleteHours = MutableStateFlow(24)
        val autoCopyPeerClipboard = MutableStateFlow(true)

        private const val PREFS_NAME = "SmartLanSharePrefs"
        private const val KEY_AUTO_DELETE = "auto_delete_files"
        private const val KEY_DELETE_HOURS = "auto_delete_hours"
        private const val KEY_AUTO_COPY = "auto_copy_peer_clipboard"
        private const val KEY_PASSWORD_PROTECTED = "password_protected"
        private const val KEY_ACCESS_PASSWORD = "access_password"

        fun loadSettings(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            autoDeleteFiles.value = prefs.getBoolean(KEY_AUTO_DELETE, true)
            autoDeleteHours.value = prefs.getInt(KEY_DELETE_HOURS, 24)
            autoCopyPeerClipboard.value = prefs.getBoolean(KEY_AUTO_COPY, true)
            
            val isPassProtected = prefs.getBoolean(KEY_PASSWORD_PROTECTED, false)
            val passStr = prefs.getString(KEY_ACCESS_PASSWORD, "") ?: ""
            passwordProtected.value = isPassProtected
            accessPassword.value = passStr
        }

        fun updateAutoDelete(context: Context, enabled: Boolean) {
            autoDeleteFiles.value = enabled
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_DELETE, enabled)
                .apply()
        }

        fun updateDeleteHours(context: Context, hours: Int) {
            autoDeleteHours.value = hours
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_DELETE_HOURS, hours)
                .apply()
        }

        fun updateAutoCopy(context: Context, enabled: Boolean) {
            autoCopyPeerClipboard.value = enabled
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_COPY, enabled)
                .apply()
        }

        fun updateAccessPassword(context: Context, pass: String) {
            accessPassword.value = pass
            passwordProtected.value = pass.isNotEmpty()
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PASSWORD_PROTECTED, pass.isNotEmpty())
                .putString(KEY_ACCESS_PASSWORD, pass)
                .apply()
        }

        fun clearAllSharedFiles(context: Context) {
            val cacheFolder = File(context.cacheDir, "shared_files")
            if (cacheFolder.exists()) {
                cacheFolder.listFiles()?.forEach { it.delete() }
            }
            transferHistory.value = emptyList()
        }
        
        fun startService(context: Context) {
            val intent = Intent(context, LanServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, LanServerService::class.java)
            context.stopService(intent)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            scope.launch {
                delay(1000) // wait for IP assignment
                updateNetworkInfo()
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            scope.launch {
                updateNetworkInfo()
            }
        }
    }

    private var cleanupJob: Job? = null

    private fun startFileCleanupJob() {
        cleanupJob?.cancel()
        cleanupJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    if (autoDeleteFiles.value) {
                        performFileCleanup()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // Check periodically: every 15 minutes to prevent bloat actively
                delay(15 * 60 * 1000L)
            }
        }
    }

    private fun performFileCleanup() {
        val cacheFolder = File(cacheDir, "shared_files")
        if (!cacheFolder.exists()) return
        
        val now = System.currentTimeMillis()
        val limitMillis = autoDeleteHours.value * 3600 * 1000L
        val thresholdTime = now - limitMillis
        
        val files = cacheFolder.listFiles() ?: return
        var deletedCount = 0
        
        for (file in files) {
            if (file.isFile) {
                if (file.lastModified() < thresholdTime) {
                    val name = file.name
                    if (file.delete()) {
                        deletedCount++
                        scope.launch(Dispatchers.Main) {
                            transferHistory.value = transferHistory.value.filter { it.name != name }
                        }
                    }
                }
            }
        }
        
        if (deletedCount > 0) {
            android.util.Log.d("LanServerService", "Auto-cleanup deleted $deletedCount old file(s).")
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback)
        
        loadSettings(this)
        updateNetworkInfo()
        startFileCleanupJob()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceWithNotification()
        startLanServer()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundServiceWithNotification() {
        val notification = createNotification("Initializing server...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning.value = true
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart LAN Share Running")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Smart LAN Share Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun updateNetworkInfo() {
        val ip = NetworkUtils.getLocalIpAddress() ?: "0.0.0.0"
        localIp.value = ip
        serverUrl.value = "http://$ip:8000"
        wifiSsid.value = NetworkUtils.getWifiSsid(this)
        gatewayIp.value = NetworkUtils.getGatewayAddress(this)
        subnetMask.value = NetworkUtils.getSubnetMask(this)
    }

    private fun startLanServer() {
        scope.launch(Dispatchers.IO) {
            serverMutex.withLock {
                if (lanServer != null) {
                    android.util.Log.d("LanServerService", "Server already running; skipping initialization.")
                    return@withLock
                }
                try {
                    val ip = NetworkUtils.getLocalIpAddress() ?: "0.0.0.0"
                    lanServer = LanServer(
                        context = this@LanServerService,
                        port = 8000,
                        password = accessPassword.value,
                        onDeviceConnected = { deviceIp, userAgent ->
                            scope.launch {
                                val newDevice = ConnectedDevice(id = deviceIp, ip = deviceIp, deviceName = getDeviceShortName(userAgent))
                                val currentList = connectedDevices.value.toMutableList()
                                if (!currentList.any { it.id == deviceIp }) {
                                    currentList.add(newDevice)
                                    connectedDevices.value = currentList
                                    showDeviceConnectionNotification(newDevice.deviceName)
                                }
                            }
                        },
                        onDeviceDisconnected = { deviceIp ->
                            scope.launch {
                                connectedDevices.value = connectedDevices.value.filter { it.id != deviceIp }
                            }
                        },
                        onChatMessage = { sender, text ->
                            scope.launch {
                                val msg = ChatMessage(sender = sender, text = text, isMe = false)
                                chatMessages.value = chatMessages.value + msg
                            }
                        },
                        onClipboardReceived = { sender, text ->
                            scope.launch {
                                val item = ClipboardItem(text = text, sender = sender)
                                clipboardHistory.value = listOf(item) + clipboardHistory.value
                                
                                // Native copy to clipboard on Android if enabled
                                if (autoCopyPeerClipboard.value) {
                                    try {
                                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("LAN Share Text", text)
                                        clipboard.setPrimaryClip(clip)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        },
                        onTransferUpdate = { fileName, progressBytes, totalBytes, isIncoming, status ->
                            scope.launch {
                                val fraction = if (totalBytes > 0) progressBytes.toFloat() / totalBytes else 0.0f
                                
                                isTransferring.value = (status == "Transferring")
                                currentTransferFile.value = fileName
                                currentTransferProgress.value = fraction
                                
                                // Speed Calculation
                                val now = System.currentTimeMillis()
                                val duration = now - lastSpeedUpdate
                                if (duration >= 900) {
                                    val sizeDiff = progressBytes - lastBytes
                                    val speedSec = if (sizeDiff > 0 && duration > 0) (sizeDiff * 1000) / duration else 0L
                                    currentTransferSpeed.value = speedSec
                                    lastSpeedUpdate = now
                                    lastBytes = progressBytes
                                }
                                
                                if (status == "Success" || progressBytes == totalBytes) {
                                    currentTransferSpeed.value = 0L
                                    isTransferring.value = false
                                    lastBytes = 0L
                                    
                                    val fullPath = File(lanServer?.sharedDir, fileName).absolutePath
                                    val newItem = TransferItem(
                                        name = fileName,
                                        size = totalBytes,
                                        path = fullPath,
                                        isIncoming = isIncoming,
                                        progress = 1.0f,
                                        status = "Success"
                                    )
                                    // Prevent duplication
                                    transferHistory.value = listOf(newItem) + transferHistory.value.filter { it.name != fileName }
                                }
                            }
                        }
                    )
                    lanServer?.start()
                    
                    scope.launch(Dispatchers.Main) {
                        updateNotification("Server running at ${serverUrl.value}")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setPassword(pwd: String) {
        accessPassword.value = pwd
        passwordProtected.value = pwd.isNotEmpty()
    }

    private fun showDeviceConnectionNotification(deviceName: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("New Device Linked")
            .setContentText("$deviceName connected to Smart LAN Share.")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(DEVICE_CONNECT_NOTIFICATION_ID, builder.build())
    }

    private fun getDeviceShortName(userAgent: String): String {
        return when {
            userAgent.contains("Windows") -> "Windows PC"
            userAgent.contains("Macintosh") -> "Mac OS device"
            userAgent.contains("iPhone") -> "iPhone"
            userAgent.contains("Android") -> {
                val match = "Android\\s+([^;]+)".toRegex().find(userAgent)
                match?.groupValues?.get(0) ?: "Android Device"
            }
            userAgent.contains("Linux") -> "Linux PC"
            else -> "Web Browser"
        }
    }

    // Shared method for pushing host files
    fun shareHostFile(file: File) {
        scope.launch(Dispatchers.IO) {
            lanServer?.let { server ->
                val dest = File(server.sharedDir, file.name)
                if (dest.absolutePath != file.absolutePath) {
                    file.copyTo(dest, overwrite = true)
                }
                
                scope.launch(Dispatchers.Main) {
                    val item = TransferItem(
                        name = file.name,
                        size = file.length(),
                        path = dest.absolutePath,
                        isIncoming = false,
                        progress = 1.0f,
                        status = "Success"
                    )
                    transferHistory.value = listOf(item) + transferHistory.value.filter { it.name != file.name }
                    
                    // Broadcast file upload to clients
                    server.broadcast(org.json.JSONObject().apply {
                        put("type", "file_uploaded")
                        put("name", file.name)
                        put("size", file.length())
                    }.toString())
                }
            }
        }
    }

    // Shared method for copying and broadcasting clipboard from host phone
    fun pushHostClipboard(text: String) {
        scope.launch(Dispatchers.IO) {
            val item = ClipboardItem(text = text, sender = "Host Device")
            clipboardHistory.value = listOf(item) + clipboardHistory.value
            
            lanServer?.broadcast(org.json.JSONObject().apply {
                put("type", "clipboard")
                put("sender", "Host Device")
                put("text", text)
            }.toString())
        }
    }

    // Send chat from host phone
    fun pushHostChatMessage(text: String) {
        scope.launch(Dispatchers.IO) {
            val msg = ChatMessage(sender = "Host Phone", text = text, isMe = true)
            chatMessages.value = chatMessages.value + msg
            
            lanServer?.broadcast(org.json.JSONObject().apply {
                put("type", "chat")
                put("sender", "Host Phone")
                put("text", text)
                put("timestamp", System.currentTimeMillis())
            }.toString())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        lanServer?.stop()
        connectivityManager?.unregisterNetworkCallback(networkCallback)
        isRunning.value = false
    }
}
