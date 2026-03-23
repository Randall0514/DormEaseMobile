# DormEase Tunnel Setup Guide

Complete guide for setting up and running DormEase with Cloudflare Tunnel for mobile connectivity.

---

## Prerequisites

- **Backend**: Node.js backend running on `localhost:3000`
- **Mobile App**: Android app that needs to connect from different network (e.g., mobile hotspot)
- **Cloudflared**: Cloudflare tunnel client installed

---

## 1. Install Cloudflared

### Option 1: Using WinGet (Windows)
```powershell
winget install --id Cloudflare.cloudflared
```

### Option 2: Manual Download
Download from: https://github.com/cloudflare/cloudflared/releases

### Verify Installation
```powershell
# Close and reopen PowerShell terminal, then run:
cloudflared --version
```

### If Command Not Found
```powershell
# Add to PATH manually
$links = "$env:LOCALAPPDATA\Microsoft\WinGet\Links"
[Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path","User") + ";$links", "User")
$env:Path += ";$links"
```

---

## 2. Start Backend Server

Open **Terminal 1** in `Backend` folder:

```powershell
cd C:\Users\randall\AndroidStudioProjects\DormEaseMobile\Backend
npm run dev
```

**Expected output:**
```
✅ Connected to PostgreSQL
✅ Schema up to date
🚀 Server running at http://10.37.20.131:3000
🔌 WebSocket server is ready
```

**Keep this terminal running.**

---

## 3. Start Cloudflare Tunnel

Open **Terminal 2** in `Backend` folder:

```powershell
cd C:\Users\randall\AndroidStudioProjects\DormEaseMobile
cloudflared --config NUL tunnel --url http://localhost:3000
```

**Expected output:**
```
Your quick tunnel has been created! Visit it at (it may take a few seconds to be reachable):
https://recommend-remind-databases-indianapolis.trycloudflare.com
```

**Copy the URL shown** (it will be different each time you start the tunnel).

> Note: You may see a log line like "Configuration file NUL was empty". This is expected when forcing quick-tunnel mode and can be ignored.

**Keep this terminal running.**

---

## 4. Update Android Constants

### File Location
```
app/src/main/java/com/firstapp/dormease/network/Constants.kt
```

### Extract Tunnel Host
From the tunnel URL:
```
https://recommend-remind-databases-indianapolis.trycloudflare.com
```

Take **ONLY the domain part** (remove `https://`):
```
recommend-remind-databases-indianapolis.trycloudflare.com
```

### Update Constants.kt

```kotlin
object Constants {
    private const val USE_TUNNEL = true  // Toggle: true for tunnel, false for local
    
    // ✅ CORRECT - Only domain, no https://
    private const val TUNNEL_HOST = "recommend-remind-databases-indianapolis.trycloudflare.com"
    
    // ❌ WRONG - Do not include https://
    // private const val TUNNEL_HOST = "https://recommend-remind-databases-indianapolis.trycloudflare.com"
    
    private const val LOCAL_HOST = "192.168.x.x:3000"  // Your PC's local IP
    
    // URLs are constructed automatically with https:// for tunnel
    val BASE_URL = if (USE_TUNNEL) "https://$TUNNEL_HOST" else "http://$LOCAL_HOST"
    val SOCKET_URL = if (USE_TUNNEL) "https://$TUNNEL_HOST" else "http://$LOCAL_HOST"
}
```

### Important Rules
- ✅ **DO**: Use only the domain name in `TUNNEL_HOST`
- ❌ **DON'T**: Include `https://` in `TUNNEL_HOST`
- ✅ **DO**: Update `TUNNEL_HOST` every time you restart the tunnel (URL changes)
- ✅ **DO**: Set `USE_TUNNEL = true` when using tunnel
- ✅ **DO**: Set `USE_TUNNEL = false` when testing on same WiFi network

---

## 5. Rebuild Android App

After updating `Constants.kt`:

1. **Build** → **Make Project** (or press `Ctrl+F9`)
2. **Run** → Select your device/emulator
3. Wait for app to install

---

## 6. Testing Checklist

### ✅ Backend Running
- Terminal 1 shows: `🚀 Server running at http://10.37.20.131:3000`
- Terminal 1 shows: `🔌 WebSocket server is ready`

### ✅ Tunnel Running
- Terminal 2 shows tunnel URL
- Both terminals remain open (don't close them)

### ✅ Android Constants Updated
- `TUNNEL_HOST` has current tunnel domain (no `https://`)
- `USE_TUNNEL = true`
- App rebuilt and reinstalled

### ✅ Test Sign Up / Login
- Open app on mobile
- Try sign up (should receive OTP email)
- Try login (should succeed)
- Backend terminal should show API requests

### ✅ Test WebSocket Connection
- After login, backend should show:
  ```
  ✅ User XX connected via WebSocket (socket-id)
  ```

### ✅ Test Real-Time Messages
- Send message from mobile
- Message should save and send immediately
- If you have web app running, message should appear there instantly

---

## 7. Common Errors & Solutions

### Error: "Unable to resolve host 'https'"
**Cause**: `TUNNEL_HOST` includes `https://`

**Fix**: Remove `https://` from `TUNNEL_HOST`:
```kotlin
// ❌ WRONG
private const val TUNNEL_HOST = "https://abc-xyz.trycloudflare.com"

// ✅ CORRECT
private const val TUNNEL_HOST = "abc-xyz.trycloudflare.com"
```

### Error: "Connection refused"
**Causes**:
1. Backend not running
2. Tunnel not running
3. Wrong tunnel URL in `Constants.kt`

**Fix**:
1. Check Terminal 1 - backend should be running
2. Check Terminal 2 - tunnel should show URL
3. Copy-paste tunnel URL carefully (domain only, no `https://`)

### Error: "cloudflared command not found"
**Fix**:
```powershell
# Reinstall cloudflared
winget uninstall --id Cloudflare.cloudflared
winget install --id Cloudflare.cloudflared --source winget

# Reopen PowerShell
cloudflared --version
```

### Messages Not Updating in Real-Time
**Causes**:
1. Socket not connected
2. Missing socket listener in ChatActivity

**Fix**: Socket connection code in `ChatActivity.kt`:
```kotlin
// Add in onCreate() before registering listeners
val token = sessionManager.fetchAuthToken()
if (token != null && !SocketManager.isConnected()) {
    SocketManager.connect(token)
}
```

---

## 8. Daily Workflow

### Starting Development

1. **Start Backend** (Terminal 1):
   ```powershell
    cd C:\Users\randall\AndroidStudioProjects\DormEaseMobile\Backend
   npm run dev
   ```

2. **Start Tunnel** (Terminal 2):
   ```powershell
    cd C:\Users\randall\AndroidStudioProjects\DormEaseMobile
    cloudflared --config NUL tunnel --url http://localhost:3000
   ```

3. **Copy new tunnel URL** from Terminal 2 output

4. **Update `Constants.kt`** with new tunnel domain (remove `https://`)

5. **Rebuild Android app**

6. **Test on mobile**

### Stopping Development

1. Stop tunnel: Press `Ctrl+C` in Terminal 2
2. Stop backend: Press `Ctrl+C` in Terminal 1
3. No need to change `Constants.kt` (leave as is for next session)

---

## 9. Alternative: Local Network Testing

If both PC and mobile are on **same WiFi network**, you can skip the tunnel:

### Update Constants.kt
```kotlin
object Constants {
    private const val USE_TUNNEL = false  // ← Set to false
    
    private const val TUNNEL_HOST = "your-tunnel-here.trycloudflare.com"
    private const val LOCAL_HOST = "192.168.1.100:3000"  // ← Your PC's IP
    
    val BASE_URL = if (USE_TUNNEL) "https://$TUNNEL_HOST" else "http://$LOCAL_HOST"
    val SOCKET_URL = if (USE_TUNNEL) "https://$TUNNEL_HOST" else "http://$LOCAL_HOST"
}
```

### Find Your PC's Local IP
```powershell
ipconfig
# Look for "IPv4 Address" under your WiFi adapter
```

### Only Start Backend (no tunnel needed)
```powershell
cd C:\Users\randall\AndroidStudioProjects\DormEaseMobile\Backend
npm run dev
```

**Rebuild app and test.**

---

## 10. Quick Reference

| Command | Purpose |
|---------|---------|
| `npm run dev` | Start backend server |
| `cloudflared --config NUL tunnel --url http://localhost:3000` | Start tunnel |
| `cloudflared --version` | Check cloudflared version |
| `ipconfig` | Find PC's local IP address |
| Build → Make Project | Rebuild Android app |

| File | Purpose |
|------|---------|
| `Backend/src/index.ts` | Backend API server |
| `Backend/src/websocket.ts` | WebSocket real-time messaging |
| `app/.../Constants.kt` | Android API/Socket endpoints |
| `app/.../ChatActivity.kt` | Chat screen with real-time updates |
| `app/.../SocketManager.kt` | WebSocket client manager |

---

## 11. Notes

- **Tunnel URL changes** every time you restart `cloudflared tunnel`
- **Always update** `Constants.kt` with new tunnel URL and rebuild app
- **Keep both terminals open** while testing mobile app
- **Use local network** (skip tunnel) when testing on same WiFi for faster development
- **Firewall**: No firewall changes needed with tunnel approach

---

## Support

If you encounter issues:
1. Check both backend and tunnel terminals are running
2. Verify `TUNNEL_HOST` has no `https://` prefix
3. Confirm app was rebuilt after updating `Constants.kt`
4. Check Logcat for connection errors
5. Verify backend shows WebSocket connection message after login

---

**Last Updated**: 2026-03-07
