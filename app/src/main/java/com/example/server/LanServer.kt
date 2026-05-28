package com.example.server

import android.content.Context
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.http.content.*
import java.io.File
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LanServer(
    private val context: Context,
    private val port: Int = 8000,
    password: String = "",
    private val onDeviceConnected: (ip: String, userAgent: String) -> Unit,
    private val onDeviceDisconnected: (ip: String) -> Unit,
    private val onChatMessage: (sender: String, message: String) -> Unit,
    private val onClipboardReceived: (sender: String, text: String) -> Unit,
    private val onTransferUpdate: (fileName: String, progressBytes: Long, totalBytes: Long, isIncoming: Boolean, status: String) -> Unit
) {
    private val password: String
        get() = com.example.service.LanServerService.accessPassword.value

    private var server: EmbeddedServer<*, *>? = null
    val sharedDir = File(context.cacheDir, "shared_files").apply { mkdirs() }
    
    // Thread-safe session tracking
    private val activeSessions = Collections.synchronizedSet(LinkedHashSet<DefaultWebSocketServerSession>())
    
    private fun isPortAvailable(port: Int): Boolean {
        return try {
            java.net.ServerSocket(port).use { socket ->
                socket.reuseAddress = true
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun start() {
        if (server != null) return

        // Ensure port is available before starting Ktor, to prevent async BindException in CIO engine
        var portFree = false
        for (i in 1..5) {
            if (isPortAvailable(port)) {
                portFree = true
                break
            }
            android.util.Log.d("LanServer", "Port $port is busy. Waiting for release (attempt $i/5)...")
            try { Thread.sleep(300) } catch (e: Exception) {}
        }

        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets) {
                pingPeriodMillis = 15000
                timeoutMillis = 20000
            }
            
            routing {
                // Serve portal web client
                get("/") {
                    call.respondText(getHtmlPortal(password.isNotEmpty()), ContentType.Text.Html)
                }
                
                // Get JSON array of available files
                get("/files") {
                    val filesArray = org.json.JSONArray()
                    sharedDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val fileObj = org.json.JSONObject().apply {
                                put("name", file.name)
                                put("size", file.length())
                                put("url", "/download/${file.name}")
                            }
                            filesArray.put(fileObj)
                        }
                    }
                    call.respondText(filesArray.toString(), ContentType.Application.Json)
                }
                
                // Upload a file locally from browser
                post("/upload") {
                    if (password.isNotEmpty()) {
                        val authHeader = call.request.headers["Authorization"] ?: call.parameters["auth"]
                        if (authHeader != password) {
                            call.respond(HttpStatusCode.Unauthorized, "Invalid Password")
                            return@post
                        }
                    }
                    
                    try {
                        val multipart = call.receiveMultipart()
                        multipart.forEachPart { part ->
                            if (part is PartData.FileItem) {
                                val originalName = part.originalFileName ?: "upload_${System.currentTimeMillis()}"
                                val destFile = File(sharedDir, originalName)
                                
                                val totalLength = call.request.contentLength() ?: 1L
                                var uploadedBytes = 0L
                                
                                onTransferUpdate(originalName, 0L, totalLength, true, "Transferring")
                                
                                part.streamProvider().use { input ->
                                    destFile.outputStream().use { output ->
                                        val buffer = ByteArray(65536)
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            output.write(buffer, 0, bytesRead)
                                            uploadedBytes += bytesRead
                                            onTransferUpdate(originalName, uploadedBytes, totalLength, true, "Transferring")
                                        }
                                    }
                                }
                                onTransferUpdate(originalName, uploadedBytes, uploadedBytes, true, "Success")
                                broadcast(org.json.JSONObject().apply {
                                    put("type", "file_uploaded")
                                    put("name", originalName)
                                    put("size", uploadedBytes)
                                }.toString())
                            }
                            part.dispose()
                        }
                        call.respond(HttpStatusCode.OK, "File uploaded successfully")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
                    }
                }
                
                // Download file to browser
                get("/download/{fileName}") {
                    if (password.isNotEmpty()) {
                        val authHeader = call.request.headers["Authorization"] ?: call.parameters["auth"]
                        if (authHeader != password) {
                            call.respond(HttpStatusCode.Unauthorized, "Invalid Password")
                            return@get
                        }
                    }
                    
                    val fileName = call.parameters["fileName"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val file = File(sharedDir, fileName)
                    if (file.exists()) {
                        onTransferUpdate(fileName, file.length(), file.length(), false, "Success")
                        call.respondFile(file)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
                
                // WebSocket for chat, clipboard and notification sync
                webSocket("/ws") {
                    val clientIp = call.request.local.remoteHost
                    val userAgent = call.request.headers["User-Agent"] ?: "Browser Device"
                    
                    activeSessions.add(this)
                    onDeviceConnected(clientIp, userAgent)
                    
                    val joinMsg = org.json.JSONObject().apply {
                        put("type", "chat")
                        put("sender", "System")
                        put("text", "New connection from $clientIp")
                        put("timestamp", System.currentTimeMillis())
                    }
                    broadcast(joinMsg.toString())
                    
                    // Send auth request if needed
                    send(Frame.Text(org.json.JSONObject().apply {
                        put("type", "auth_status")
                        put("required", password.isNotEmpty())
                    }.toString()))
                    
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                try {
                                    val obj = org.json.JSONObject(text)
                                    val type = obj.optString("type")
                                    
                                    if (password.isNotEmpty() && type != "auth") {
                                        val providedAuth = obj.optString("auth")
                                        if (providedAuth != password) {
                                            send(Frame.Text("{\"type\":\"unauthorized\"}"))
                                            continue
                                        }
                                    }
                                    
                                    when (type) {
                                        "auth" -> {
                                            val provided = obj.optString("password")
                                            val isSuccess = provided == password
                                            send(Frame.Text(org.json.JSONObject().apply {
                                                put("type", "auth_response")
                                                put("success", isSuccess)
                                            }.toString()))
                                        }
                                        "chat" -> {
                                            val textContent = obj.optString("text")
                                            val sender = obj.optString("sender", "Web Client")
                                            onChatMessage(sender, textContent)
                                            broadcast(text, this)
                                        }
                                        "clipboard" -> {
                                            val textContent = obj.optString("text")
                                            val sender = obj.optString("sender", "Web Client")
                                            onClipboardReceived(sender, textContent)
                                            broadcast(text, this)
                                        }
                                    }
                                } catch (je: Exception) {
                                    je.printStackTrace()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        activeSessions.remove(this)
                        onDeviceDisconnected(clientIp)
                        
                        val leaveMsg = org.json.JSONObject().apply {
                            put("type", "chat")
                            put("sender", "System")
                            put("text", "Client disconnected: $clientIp")
                            put("timestamp", System.currentTimeMillis())
                        }
                        broadcast(leaveMsg.toString())
                    }
                }
            }
        }.apply { start(wait = false) }
    }
    
    fun stop() {
        server?.stop(500, 1000)
        server = null
    }
    
    fun broadcast(message: String, excludeSession: DefaultWebSocketServerSession? = null) {
        val deadSessions = mutableListOf<DefaultWebSocketServerSession>()
        synchronized(activeSessions) {
            activeSessions.forEach { session ->
                if (session != excludeSession) {
                    try {
                        CoroutineScope(Dispatchers.IO).launch {
                            session.send(Frame.Text(message))
                        }
                    } catch (e: Exception) {
                        deadSessions.add(session)
                    }
                }
            }
            activeSessions.removeAll(deadSessions)
        }
    }
    
    private fun getHtmlPortal(isPasswordProtected: Boolean): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Smart LAN Share - Portal</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
            font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, Helvetica, Arial, sans-serif;
        }
        body {
            background-color: #0b0c10;
            color: #c5c6c7;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            overflow-x: hidden;
            font-size: 15px;
        }
        .header {
            background: linear-gradient(135deg, #1f2833, #0b0c10);
            padding: 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            border-bottom: 2px solid #66fcf1;
            box-shadow: 0 4px 20px rgba(102, 252, 241, 0.15);
        }
        .logo {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .logo-icon {
            width: 32px;
            height: 32px;
            background: #66fcf1;
            border-radius: 8px;
            display: flex;
            align-items: center;
            justify-content: center;
            color: #0b0c10;
            font-weight: bold;
            box-shadow: 0 0 10px #66fcf1;
        }
        .logo-text h1 {
            font-size: 1.25rem;
            color: #ffffff;
            letter-spacing: 0.5px;
        }
        .logo-text p {
            font-size: 0.75rem;
            color: #66fcf1;
        }
        .status-badge {
            background-color: rgba(102, 252, 241, 0.1);
            color: #66fcf1;
            padding: 6px 12px;
            border-radius: 20px;
            border: 1px solid #66fcf1;
            font-size: 0.8rem;
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .status-dot {
            width: 8px;
            height: 8px;
            background-color: #66fcf1;
            border-radius: 50%;
            display: inline-block;
            box-shadow: 0 0 8px #66fcf1;
        }
        .status-disconnected .status-dot {
            background-color: #ff4d4d;
            box-shadow: 0 0 8px #ff4d4d;
        }
        .status-disconnected {
            color: #ff4d4d;
            border-color: #ff4d4d;
            background-color: rgba(255, 77, 77, 0.1);
        }
        .container {
            max-width: 1200px;
            width: 100%;
            margin: 0 auto;
            padding: 20px;
            flex: 1;
            display: grid;
            grid-template-columns: 1fr;
            gap: 20px;
        }
        @media(min-width: 850px) {
            .container {
                grid-template-columns: 1fr 1fr;
            }
            .full-row {
                grid-column: span 2;
            }
        }
        .card {
            background: rgba(31, 40, 51, 0.45);
            backdrop-filter: blur(8px);
            -webkit-backdrop-filter: blur(8px);
            border-radius: 16px;
            border: 1px solid rgba(255, 255, 255, 0.05);
            padding: 20px;
            display: flex;
            flex-direction: column;
            box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.3);
            transition: transform 0.2sease, border-color 0.2s ease;
        }
        .card:hover {
            border-color: rgba(102, 252, 241, 0.2);
        }
        .card-header {
            margin-bottom: 15px;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .card-title {
            font-size: 1.1rem;
            color: #ffffff;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .card-title svg {
            color: #66fcf1;
        }
        .dropzone {
            border: 2px dashed rgba(102, 252, 241, 0.4);
            border-radius: 12px;
            padding: 30px;
            text-align: center;
            cursor: pointer;
            transition: all 0.2s ease;
            background: rgba(11, 12, 16, 0.4);
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 10px;
        }
        .dropzone:hover, .dropzone.dragover {
            border-color: #66fcf1;
            background: rgba(102, 252, 241, 0.05);
        }
        .upload-icon {
            font-size: 2.5rem;
            color: #66fcf1;
        }
        .file-input {
            display: none;
        }
        .progress-container {
            margin-top: 15px;
            display: none;
        }
        .progress-header {
            display: flex;
            justify-content: space-between;
            font-size: 0.85rem;
            margin-bottom: 6px;
        }
        .progress-bar-bg {
            height: 8px;
            background: #1f2833;
            border-radius: 4px;
            overflow: hidden;
        }
        .progress-bar-fg {
            height: 100%;
            background: linear-gradient(90deg, #45a29e, #66fcf1);
            width: 0%;
            transition: width 0.1s ease;
            box-shadow: 0 0 10px #66fcf1;
        }
        .file-list {
            list-style: none;
            overflow-y: auto;
            max-height: 250px;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .file-item {
            background: rgba(11, 12, 16, 0.6);
            border-radius: 10px;
            padding: 12px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
            border-left: 3px solid #45a29e;
        }
        .file-info {
            flex: 1;
            overflow: hidden;
        }
        .file-name {
            font-size: 0.9rem;
            color: #ffffff;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .file-size {
            font-size: 0.75rem;
            color: #858585;
            margin-top: 2px;
        }
        .btn-download {
            background-color: #45a29e;
            color: #ffffff;
            border: none;
            padding: 6px 14px;
            border-radius: 6px;
            cursor: pointer;
            font-size: 0.85rem;
            transition: background 0.2s;
            text-decoration: none;
            display: inline-flex;
            align-items: center;
            gap: 6px;
        }
        .btn-download:hover {
            background-color: #66fcf1;
            color: #0c001c;
        }
        .chat-box {
            display: flex;
            flex-direction: column;
            height: 350px;
        }
        .chat-messages {
            flex: 1;
            overflow-y: auto;
            padding: 10px;
            background: rgba(11, 12, 16, 0.6);
            border-radius: 12px;
            margin-bottom: 12px;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .message {
            max-width: 80%;
            padding: 10px 14px;
            border-radius: 12px;
            font-size: 0.9rem;
            line-height: 1.4;
            word-break: break-word;
        }
        .message-incoming {
            background-color: #1f2833;
            color: #ffffff;
            align-self: flex-start;
            border-bottom-left-radius: 2px;
        }
        .message-outgoing {
            background-color: #45a29e;
            color: #0b0c10;
            align-self: flex-end;
            border-bottom-right-radius: 2px;
        }
        .message-system {
            background-color: rgba(102, 252, 241, 0.05);
            color: #66fcf1;
            align-self: center;
            font-size: 0.8rem;
            text-align: center;
            border-radius: 6px;
            padding: 4px 10px;
            border: 1px solid rgba(102, 252, 241, 0.1);
        }
        .message-sender {
            font-size: 0.75rem;
            font-weight: bold;
            margin-bottom: 4px;
            opacity: 0.8;
        }
        .message-time {
            font-size: 0.7rem;
            text-align: right;
            margin-top: 4px;
            opacity: 0.6;
        }
        .chat-input-area {
            display: flex;
            gap: 8px;
        }
        .input-text {
            flex: 1;
            background: rgba(11, 12, 16, 0.8);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 8px;
            padding: 10px 14px;
            color: #ffffff;
            outline: none;
            transition: border-color 0.2s;
        }
        .input-text:focus {
            border-color: #66fcf1;
        }
        .btn-send {
            background: #66fcf1;
            color: #0b0c10;
            border: none;
            border-radius: 8px;
            padding: 0 20px;
            cursor: pointer;
            font-weight: bold;
            transition: all 0.2s;
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .btn-send:hover {
            box-shadow: 0 0 10px #66fcf1;
            transform: translateY(-1px);
        }
        .clipboard-area {
            display: flex;
            flex-direction: column;
            gap: 12px;
        }
        .clipboard-viewer {
            background: rgba(11, 12, 16, 0.6);
            border-radius: 12px;
            padding: 15px;
            min-height: 100px;
            position: relative;
            font-family: monospace;
            white-space: pre-wrap;
            word-break: break-all;
            border: 1px solid rgba(255, 255, 255, 0.05);
        }
        .btn-copy {
            position: absolute;
            top: 10px;
            right: 10px;
            background: rgba(102, 252, 241, 0.1);
            color: #66fcf1;
            border: 1px solid #66fcf1;
            border-radius: 6px;
            padding: 4px 8px;
            font-size: 0.75rem;
            cursor: pointer;
            transition: all 0.2s;
        }
        .btn-copy:hover {
            background: #66fcf1;
            color: #0b0c10;
        }
        /* Password lock screen styling */
        .password-overlay {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: #0b0c10;
            z-index: 1000;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }
        .password-box {
            max-width: 400px;
            width: 100%;
            background: rgba(31, 40, 51, 0.85);
            border: 1px solid #66fcf1;
            box-shadow: 0 0 25px rgba(102, 252, 241, 0.2);
            border-radius: 16px;
            padding: 30px;
            text-align: center;
        }
        .password-logo {
            font-size: 3rem;
            color: #66fcf1;
            margin-bottom: 15px;
        }
        .password-prompt {
            margin-bottom: 20px;
            color: #ffffff;
            font-size: 1.1rem;
        }
        .password-error {
            color: #ff4d4d;
            font-size: 0.85rem;
            margin-top: 10px;
            display: none;
        }
        .footer {
            text-align: center;
            padding: 20px;
            font-size: 0.8rem;
            color: #45a29e;
            margin-top: auto;
        }
    </style>
</head>
<body>
    <!-- Password Overlay Lock -->
    <div id="passwordOverlay" class="password-overlay" style="display: ${if (isPasswordProtected) "flex" else "none"};">
        <div class="password-box">
            <div class="password-logo">🔒</div>
            <div class="password-prompt">Access Password Required</div>
            <div style="display:flex; gap: 8px;">
                <input type="password" id="accessPassword" class="input-text" placeholder="Enter LAN Password">
                <button onclick="submitPassword()" class="btn-send">Unlock</button>
            </div>
            <div id="passwordError" class="password-error">Incorrect password. Please try again.</div>
        </div>
    </div>

    <!-- Header -->
    <div class="header">
        <div class="logo">
            <div class="logo-icon">⇅</div>
            <div class="logo-text">
                <h1>Smart LAN Share</h1>
                <p>High-speed wireless local portal</p>
            </div>
        </div>
        <div id="statusBadge" class="status-badge">
            <span class="status-dot"></span>
            <span id="statusText">Connecting...</span>
        </div>
    </div>

    <div class="container">
        <!-- Dropzone / File Sender -->
        <div class="card">
            <div class="card-header">
                <div class="card-title">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12"/></svg>
                    Send File to Phone
                </div>
            </div>
            <div class="dropzone" id="dropzone" onclick="document.getElementById('fileInput').click()">
                <div class="upload-icon">📤</div>
                <div><b>Drag & Drop files</b> or click to select</div>
                <div style="font-size:0.75rem; color:#858585;">Image, Audio, Video, PDF, ZIP, APK (Max 2GB)</div>
                <input type="file" id="fileInput" class="file-input">
            </div>
            <div class="progress-container" id="uploadProgress">
                <div class="progress-header">
                    <span id="progressFileName">file.zip</span>
                    <span id="progressPercent">0%</span>
                </div>
                <div class="progress-bar-bg">
                    <div class="progress-bar-fg" id="progressBar"></div>
                </div>
            </div>
        </div>

        <!-- Download Shared Area -->
        <div class="card">
            <div class="card-header">
                <div class="card-title">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/></svg>
                    Download Shared Files
                </div>
                <button onclick="refreshFileList()" class="btn-download" style="padding: 4px 10px;">↻ Refresh</button>
            </div>
            <ul class="file-list" id="sharedFilesList">
                <li style="text-align:center; color:#858585; padding-top:20px;">No files shared yet.</li>
            </ul>
        </div>

        <!-- Chat Area -->
        <div class="card">
            <div class="card-header">
                <div class="card-title">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                    Live LAN Chat
                </div>
            </div>
            <div class="chat-box">
                <div class="chat-messages" id="chatMessages">
                    <div class="message message-system">Welcome to LAN Share quick chat portal.</div>
                </div>
                <div class="chat-input-area">
                    <input type="text" id="chatSender" class="input-text" style="max-width: 120px;" placeholder="Name" value="Guest">
                    <input type="text" id="chatText" class="input-text" placeholder="Type a message..." onkeypress="if(event.key==='Enter') sendChatMessage()">
                    <button onclick="sendChatMessage()" class="btn-send">Send</button>
                </div>
            </div>
        </div>

        <!-- Clipboard Copier -->
        <div class="card">
            <div class="card-header">
                <div class="card-title">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><rect x="8" y="2" width="8" height="4" rx="1" ry="1"/></svg>
                    Shared LAN Clipboard
                </div>
            </div>
            <div class="clipboard-area">
                <div class="clipboard-viewer" id="clipboardViewer">
                    <button class="btn-copy" onclick="copyClipboardText()">Copy Text</button>
                    <span id="clipboardContent" style="color: #66fcf1; font-style:italic;">No text copied yet from same Wi-Fi...</span>
                </div>
                <div class="chat-input-area">
                    <input type="text" id="clipboardInput" class="input-text" placeholder="Type text to send to host clipboard...">
                    <button onclick="sendClipboardToPhone()" class="btn-send">Push Text</button>
                </div>
            </div>
        </div>
    </div>

    <div class="footer">
        Powered by Smart LAN Share • Secured Local Wi-Fi Connection
    </div>

    <script>
        let ws;
        let authPassword = "";
        const host = window.location.host;
        
        function getAuthToken() {
            return authPassword;
        }

        function initWebSocket() {
            const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
            ws = new WebSocket(protocol + "//" + host + "/ws");

            ws.onopen = () => {
                document.getElementById("statusBadge").className = "status-badge";
                document.getElementById("statusText").innerText = "Connected";
                if (authPassword) {
                    ws.send(JSON.stringify({
                        type: "auth",
                        password: authPassword
                    }));
                }
            };

            ws.onclose = () => {
                document.getElementById("statusBadge").className = "status-badge status-disconnected";
                document.getElementById("statusText").innerText = "Disconnected";
                setTimeout(initWebSocket, 3000);
            };

            ws.onerror = (err) => {
                console.error("WS Error: ", err);
            };

            ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    handleSocketMessage(data);
                } catch(e) {
                    console.error("Parse WS error", e);
                }
            };
        }

        function handleSocketMessage(data) {
            if (data.type === "auth_status") {
                if (data.required && !authPassword) {
                    document.getElementById("passwordOverlay").style.display = "flex";
                } else {
                    document.getElementById("passwordOverlay").style.display = "none";
                }
            } else if (data.type === "auth_response") {
                if (data.success) {
                    document.getElementById("passwordOverlay").style.display = "none";
                    document.getElementById("passwordError").style.display = "none";
                    refreshFileList();
                } else {
                    document.getElementById("passwordError").style.display = "block";
                    authPassword = "";
                }
            } else if (data.type === "chat") {
                appendChatMessage(data.sender, data.text, false, data.timestamp);
            } else if (data.type === "clipboard") {
                document.getElementById("clipboardContent").innerText = data.text;
                document.getElementById("clipboardContent").style.color = "#ffffff";
                document.getElementById("clipboardContent").style.fontStyle = "normal";
            } else if (data.type === "file_uploaded") {
                appendSystemMessage("File shared directly: " + data.name);
                refreshFileList();
            } else if (data.type === "unauthorized") {
                document.getElementById("passwordOverlay").style.display = "flex";
            }
        }

        function submitPassword() {
            const pwd = document.getElementById("accessPassword").value;
            if (pwd) {
                authPassword = pwd;
                if (ws && ws.readyState === WebSocket.OPEN) {
                    ws.send(JSON.stringify({
                        type: "auth",
                        password: pwd
                    }));
                }
            }
        }

        function appendChatMessage(sender, text, isMe, timestamp) {
            const msgs = document.getElementById("chatMessages");
            const div = document.createElement("div");
            div.className = "message " + (isMe ? "message-outgoing" : (sender === "System" ? "message-system" : "message-incoming"));
            
            if (sender !== "System") {
                const senderDiv = document.createElement("div");
                senderDiv.className = "message-sender";
                senderDiv.innerText = sender;
                div.appendChild(senderDiv);
            }
            
            const textSpan = document.createElement("span");
            textSpan.innerText = text;
            div.appendChild(textSpan);
            
            if (timestamp) {
                const timeDiv = document.createElement("div");
                timeDiv.className = "message-time";
                const d = new Date(timestamp);
                timeDiv.innerText = d.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
                div.appendChild(timeDiv);
            }

            msgs.appendChild(div);
            msgs.scrollTop = msgs.scrollHeight;
        }

        function appendSystemMessage(text) {
            appendChatMessage("System", text, false, Date.now());
        }

        function sendChatMessage() {
            const sender = document.getElementById("chatSender").value || "Web Client";
            const input = document.getElementById("chatText");
            const text = input.value.trim();
            if(!text) return;

            const msgObj = {
                type: "chat",
                sender: sender,
                text: text,
                auth: authPassword,
                timestamp: Date.now()
            };

            ws.send(JSON.stringify(msgObj));
            appendChatMessage("You", text, true, Date.now());
            input.value = "";
        }

        function sendClipboardToPhone() {
            const input = document.getElementById("clipboardInput");
            const text = input.value.trim();
            if(!text) return;

            const msgObj = {
                type: "clipboard",
                sender: document.getElementById("chatSender").value || "Web Client",
                text: text,
                auth: authPassword
            };

            ws.send(JSON.stringify(msgObj));
            document.getElementById("clipboardContent").innerText = text;
            document.getElementById("clipboardContent").style.color = "#ffffff";
            document.getElementById("clipboardContent").style.fontStyle = "normal";
            input.value = "";
            appendSystemMessage("Pushed text content to host clipboard.");
        }

        function copyClipboardText() {
            const content = document.getElementById("clipboardContent").innerText;
            navigator.clipboard.writeText(content).then(() => {
                alert("Text copied to clipboard!");
            });
        }

        function formatBytes(bytes) {
            if (bytes === 0) return '0 Bytes';
            const k = 1024;
            const sizes = ['Bytes', 'KB', 'MB', 'GB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
        }

        function refreshFileList() {
            fetch("/files")
                .then(r => r.json())
                .then(files => {
                    const list = document.getElementById("sharedFilesList");
                    list.innerHTML = "";
                    if (files.length === 0) {
                        list.innerHTML = '<li style="text-align:center; color:#858585; padding-top:20px;">No files shared yet.</li>';
                        return;
                    }
                    files.forEach(f => {
                        const li = document.createElement("li");
                        li.className = "file-item";
                        
                        const fileInfo = document.createElement("div");
                        fileInfo.className = "file-info";
                        
                        const name = document.createElement("div");
                        name.className = "file-name";
                        name.innerText = f.name;
                        
                        const size = document.createElement("div");
                        size.className = "file-size";
                        size.innerText = formatBytes(f.size);
                        
                        fileInfo.appendChild(name);
                        fileInfo.appendChild(size);
                        
                        const btn = document.createElement("a");
                        btn.className = "btn-download";
                        btn.href = f.url + (authPassword ? "?auth=" + encodeURIComponent(authPassword) : "");
                        btn.target = "_blank";
                        btn.innerHTML = '⬇ Download';
                        
                        li.appendChild(fileInfo);
                        li.appendChild(btn);
                        list.appendChild(li);
                    });
                }).catch(err => console.error("Error fetching file list: ", err));
        }

        // Drag and drop setup
        const dropzone = document.getElementById("dropzone");
        dropzone.addEventListener("dragover", (e) => {
            e.preventDefault();
            dropzone.className = "dropzone dragover";
        });
        dropzone.addEventListener("dragleave", () => {
            dropzone.className = "dropzone";
        });
        dropzone.addEventListener("drop", (e) => {
            e.preventDefault();
            dropzone.className = "dropzone";
            if (e.dataTransfer.files.length > 0) {
                handleFileUpload(e.dataTransfer.files[0]);
            }
        });

        document.getElementById("fileInput").addEventListener("change", (e) => {
            if (e.target.files.length > 0) {
                handleFileUpload(e.target.files[0]);
            }
        });

        function handleFileUpload(file) {
            const progContainer = document.getElementById("uploadProgress");
            const progBar = document.getElementById("progressBar");
            const progPercent = document.getElementById("progressPercent");
            const progName = document.getElementById("progressFileName");

            progName.innerText = file.name;
            progPercent.innerText = "0%";
            progBar.style.width = "0%";
            progContainer.style.display = "block";

            const formData = new FormData();
            formData.append("file", file);

            const xhr = new XMLHttpRequest();
            const actionUrl = "/upload" + (authPassword ? "?auth=" + encodeURIComponent(authPassword) : "");
            
            xhr.open("POST", actionUrl, true);
            
            xhr.upload.onprogress = (e) => {
                if (e.lengthComputable) {
                    const percent = Math.round((e.loaded / e.total) * 100);
                    progBar.style.width = percent + "%";
                    progPercent.innerText = percent + "%";
                }
            };

            xhr.onload = () => {
                if (xhr.status === 200) {
                    appendSystemMessage("Inbound speed transfer complete: " + file.name);
                    progPercent.innerText = "Success ✓";
                    progBar.style.backgroundColor = "#4caf50";
                    setTimeout(() => {
                        progContainer.style.display = "none";
                        progBar.style.backgroundColor = "";
                    }, 3000);
                    refreshFileList();
                } else if (xhr.status === 401) {
                    alert("Unauthorized. Enter connection password first.");
                    document.getElementById("passwordOverlay").style.display = "flex";
                } else {
                    progPercent.innerText = "Error ❌";
                    progBar.style.backgroundColor = "#ff4d4d";
                    alert("Error uploading file: " + xhr.responseText);
                }
            };

            xhr.onerror = () => {
                progPercent.innerText = "Error ❌";
                progBar.style.backgroundColor = "#ff4d4d";
                alert("Upload network failure.");
            };

            xhr.send(formData);
        }

        // Run on start
        initWebSocket();
        refreshFileList();
    </script>
</body>
</html>
        """.trimIndent()
    }
}
