# Smart LAN Share 🚀

Smart LAN Share is a lightning-fast, secure, offline-first cross-platform peer file-sharing and text clipboard portal built natively for Android using Jetpack Compose and Ktor Server.

It automatically starts a local web server (Ktor) and shares a portal URL with any machine connected to the same Wi-Fi network. Browser clients can uploads/downloads files, share clipboard snippets, and chat in real-time.

---
<img width="720" height="1612" alt="1416" src="https://github.com/user-attachments/assets/28def50e-2e05-456f-b3cf-e08a8231403c" />
<img width="720" height="1612" alt="1415" src="https://github.com/user-attachments/assets/a48de285-5059-49d8-8e56-84eb5b2ae46d" />
<img width="720" height="1612" alt="1414" src="https://github.com/user-attachments/assets/f94e8b24-a042-43c3-9376-d010cc13a418" />
<img width="720" height="1612" alt="1413" src="https://github.com/user-attachments/assets/6aa327f9-b609-4e83-9602-d8996af58255" />
<img width="720" height="1612" alt="1412" src="https://github.com/user-attachments/assets/83230397-2191-4a87-b3b7-339dd26aa963" />

## 🎨 Design Theme: Cosmic Cyber Neon
- **Frosted Glassmorphic Cards**: Features rich translucent blocks bordered by subtle neon outlines.
- **Fluorescent Cyan Accents**: Highlighting connection badges, metrics, and transfer flows against dark slate canvas.
- **Dynamic Speed Graph**: Real-time canvas chart reflecting active live throughput.
- **Unified Controls**: Slider segments to toggle between Connected Peers, Transferred Files, Chat, and Clipboards.

---

## ⚡ Core Features
1. **SSID & IP Auto-Detect**: Instantly displays the currently active local IPv4 interface.
2. **Instant QR Code Generator**: Renders a glowing QR code for easy browser connections (no manual IP typing required).
3. **Double-Sided Transfer Portal**: Fast upload and download mechanisms supporting movies, APKs, documents, and lists.
4. **Interactive Chat & Clipboard Sync**: Full WebSocket peer communication for real-time messages and texts.
5. **Secure Shield Lock**: Option to set password authentication for the web portal.
6. **Foreground Broadcast Service**: Keeps the server running stable in the background even if the app UI is closed.

---

## 🛠️ How to Compile / Build APK
To assemble the debug signed APK locally, run the standard build task:
```bash
gradle :app:assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📦 Requirements
- **Android Target**: Android 10 to Android 15 (SDK 29 to 35).
- **Core Dependencies**:
  - Jetpack Compose (Material 3)
  - Ktor Server (CIO engine & WebSockets)
  - ZXing Barcode Scanning
  - Kotlin Symbol Processing (KSP)

---

## 🔒 Security
No cloud databases or external telemetry are used. All transfers are entirely local to your private local area network (LAN).
