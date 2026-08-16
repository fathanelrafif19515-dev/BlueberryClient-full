# Cursor AI Prompt - Blueberry Client Android Completion

## Context
Kami sedang membangun **Blueberry Client** - companion APK untuk Minecraft Bedrock dengan fitur:
- **Replay Recording** (server-side event storage)
- **Proximity Chat** (voice relay dengan ambient effects)
- **Module System** (300+ fitur modular)

Saat ini: **Server C# 90% done, APK 40% done**. Cursor perlu melanjutkan implementasi APK dan server.

---

## What We Have

### Server Backend (C# - 90% Complete)
- ✅ SQLite database (replays, events, player positions)
- ✅ WebSocket server listening on `ws://0.0.0.0:9999/`
- ✅ ReplayHandler (start/stop/pause recording, stream playback)
- ✅ ProximityChatHandler (relay voice by distance, ambient effect detection)
- ✅ Full packet routing

**Status:** Ready to compile & run. Needs minor tweaks & testing.

### Android APK (Kotlin/Java - 40% Complete)
- ✅ Module system (IModule interface, ModuleRegistry)
- ✅ OverlayService (draw transparent window on top of game)
- ✅ FPS Counter module (working proof-of-concept)
- ✅ ReplayModule (logic to send/receive from server)
- ✅ ProximityChatModule (AudioRecord capture + Opus encode/decode + playback)
- ✅ OkHttpNetworkClient (WebSocket connection to server)
- ✅ LauncherActivity (entry point, detect Minecraft, grant permissions)

**What's Missing:**
- ❌ ClickGUI (UI to toggle 300 modules)
- ❌ Replay Editor (timeline viewer, playback controls)
- ❌ Server connection in LauncherActivity (currently hardcoded, no UI input)
- ❌ RECORD_AUDIO permission request at runtime
- ❌ 10-20 simple modules (coords, health, armor, hotkey, etc)
- ❌ Pause menu integration (REC/PAUSE/STOP buttons in pause menu)
- ❌ Integration with module system for replay/proximity

---

## Detailed Tasks for Cursor

### 1. Finish Server C# (20% remaining)
**File:** `BlueberryServer/src/Program.cs`, `WebSocketServer.cs`

**Do:**
- [ ] Add command-line argument parsing for server URL (default `http://*:9999/`)
- [ ] Add console logging with timestamps
- [ ] Add graceful shutdown (Ctrl+C handler)
- [ ] Test all packet routes (replay_start, replay_stop, voice_chunk, etc)
- [ ] Add error handling for malformed JSON packets
- [ ] Add health check endpoint (HTTP 200 / "Blueberry Server OK")

**Code pattern:**
```csharp
// In Program.cs
var serverUrl = args.Length > 0 ? args[0] : "http://*:9999/";
Console.WriteLine($"[Server] Starting on {serverUrl}");
var server = new BlueberryWebSocketServer(serverUrl);
await server.StartAsync(cts.Token);
```

---

### 2. APK: Server Connection UI (LauncherActivity)
**Files:** `LauncherActivity.kt`, `layout/activity_launcher.xml`

**Do:**
- [ ] Add EditText for server URL input (default "ws://192.168.1.X:9999" or localhost for dev)
- [ ] Add "Connect to Server" button that:
  - Connects OkHttpNetworkClient to the URL
  - Shows status (connecting → connected → disconnected)
  - Saves last server URL to SharedPreferences
- [ ] Auto-load saved server URL on app start
- [ ] Show connection status with color (🟢 connected, 🔴 disconnected, 🟡 connecting)

**Layout additions:**
```xml
<!-- Add to activity_launcher.xml -->
<EditText
    android:id="@+id/serverUrlInput"
    android:hint="Server URL (ws://192.168.1.100:9999)"
    android:inputType="text" />
<Button
    android:id="@+id/btnConnect"
    android:text="Connect to Server" />
<TextView
    android:id="@+id/connectionStatus"
    android:text="Disconnected"
    android:textColor="#FF0000" />
```

---

### 3. APK: ClickGUI (Module Toggle UI)
**New file:** `ui/ClickGuiActivity.kt`

**Requirements:**
- Floating window yang bisa di-drag
- List semua module (dari ModuleRegistry.all())
- Toggle ON/OFF per module
- Search/filter modules
- Collapse category buttons (HUD, Utility, Combat, etc)
- Persistent position (SharedPreferences)

**Architecture:**
```kotlin
class ClickGuiActivity : AppCompatActivity() {
    private val registry = ModuleRegistry  // static access
    private val modules = registry.all()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // RecyclerView dengan ModuleAdapter
        // ModuleAdapter: each item = toggle switch + module name + description
    }
}
```

**Module list structure (RecyclerView):**
- Category header (collapsible)
  - Module A (toggle)
  - Module B (toggle)
  - Module C (toggle)

---

### 4. APK: Replay Editor Activity
**New file:** `ui/ReplayEditorActivity.kt`, `layout/activity_replay_editor.xml`

**Requirements:**
- Show list of recorded replays (get from server via `replay_get_list`)
- Each replay item: player name, duration, date, [PLAY] [DELETE] buttons
- PLAY button → ReplayViewerActivity

**Layout:**
```xml
<RecyclerView android:id="@+id/replayList" />
<!-- each item: -->
<LinearLayout>
    <TextView android:text="Replay name" />
    <TextView android:text="Duration: 10:30" />
    <Button android:text="PLAY" />
    <Button android:text="DELETE" />
</LinearLayout>
```

---

### 5. APK: Replay Viewer Activity
**New file:** `ui/ReplayViewerActivity.kt`, `layout/activity_replay_viewer.xml`

**Requirements:**
- Receive `replay_events` from server (streaming batch 100)
- Display playback controls: [◀◀] [◀] [►] [►►] [⏸]
- Timeline scrubber (seek to timestamp)
- Speed control (0.5x, 1x, 2x)
- Show metadata: duration, players involved
- Reconstruct world state & render locally (basic - just show events for now)

**Minimal playback logic:**
```kotlin
class ReplayViewerActivity : AppCompatActivity() {
    private var currentReplay: ReplaySession? = null
    private var events = mutableListOf<ReplayEvent>()
    private var playbackIndex = 0
    private var isPlaying = false
    
    fun onPlayClick() {
        isPlaying = true
        startPlaybackLoop()
    }
    
    private fun startPlaybackLoop() {
        thread {
            while (isPlaying && playbackIndex < events.size) {
                val evt = events[playbackIndex]
                updateUIForEvent(evt)  // render block change, player move, etc
                playbackIndex++
                Thread.sleep(16)  // 60 FPS
            }
        }
    }
}
```

---

### 6. APK: Pause Menu Integration (CRITICAL)
**New file:** `ui/PauseMenuOverlay.kt`

**Requirements:**
- Hook ke Minecraft pause menu (butuh Accessibility Service to detect)
- Show replay control buttons: [REC] [PAUSE] [STOP]
- Minimal UI, matches Minecraft aesthetic
- Send signals ke ReplayModule.onRecClicked(), etc

**How it works:**
```kotlin
class PauseMenuOverlay {
    fun showReplayControls(replayModule: ReplayModule) {
        // Create overlay with 3 buttons
        val recBtn = Button(context).apply { 
            text = "⏺ REC"
            setOnClickListener { replayModule.onRecClicked() }
        }
        // Similar for PAUSE, STOP
        overlayController.addView("pause_replay_controls", layout)
    }
}
```

**Challenge:** Detect pause menu is open
- Option A: Use Accessibility Service (scan window title)
- Option B: Hook key event (ESC = pause)
- Recommended: Option B (simpler, less permission)

---

### 7. APK: Simple Modules (10-20 easy ones)
**Folder:** `modules/`

**Easy modules to add (copy FpsCounterModule pattern):**

#### HUD Modules:
```kotlin
// CoordinatesModule.kt - show X Y Z on screen
class CoordinatesModule : IModule {
    override val displayName = "Coordinates"
    // onTick() → update textView dengan "X: 100 Y: 64 Z: 200"
    // Need source: hook getPlayerPos() atau dari ReplayModule.lastX/Y/Z
}

// HealthModule.kt - show health bar
class HealthModule : IModule {
    // Draw red bar, update dari... server? (belum ada sumber)
}

// ArmorModule.kt - show armor items
class ArmorModule : IModule {
    // Display armor slots
}

// PingModule.kt - show latency
class PingModule : IModule {
    // setInterval(1000) → query server ping
    fun getPing() {
        val start = System.currentTimeMillis()
        network.send("ping", mapOf())
        // server respond "pong" immediately
        val latency = System.currentTimeMillis() - start
    }
}

// CpsModule.kt - clicks per second
class CpsModule : IModule {
    // Use Accessibility Service to detect touch events
}
```

**Pattern untuk semua module (COPY THIS):**
```kotlin
class ExampleModule : IModule {
    override val id = "example"
    override val displayName = "Example"
    override val category = ModuleCategory.HUD
    override var isEnabled = false
    
    private lateinit var ctx: ModuleContext
    private var textView: TextView? = null
    
    override fun onLoad(context: ModuleContext) {
        ctx = context
        // Init
    }
    
    override fun onEnable() {
        // Create + add view to overlay
        val tv = TextView(ctx.overlayController.let { /* get Android Context */ })
        ctx.overlayController.addView(id, tv)
        textView = tv
    }
    
    override fun onDisable() {
        ctx.overlayController.removeView(id)
    }
    
    override fun onTick() {
        // Update textView every frame
        textView?.text = "Value: ${getCurrentValue()}"
    }
}
```

---

### 8. APK: Request RECORD_AUDIO Permission at Runtime
**File:** `LauncherActivity.kt`

**Do:**
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_RECORD)
    }
}

override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
    super.onRequestPermissionsResult(code, perms, results)
    if (code == REQUEST_CODE_RECORD && results.isNotEmpty() &&
        results[0] == PackageManager.PERMISSION_GRANTED) {
        // Enable proximity chat
        proximityModule.isEnabled = true
    }
}
```

---

### 9. APK: Update build.gradle Dependencies
**File:** `app/build.gradle`

**Add:**
```gradle
dependencies {
    // OkHttp for WebSocket
    implementation 'com.squareup.okhttp3:okhttp:4.11.0'
    
    // Opus codec
    implementation 'com.github.usabilla:concentus:1.1.7'
    
    // JSON
    implementation 'com.google.code.gson:gson:2.10.1'
    
    // Coroutines (untuk async networking)
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1'
}
```

---

### 10. APK: Connect OverlayService to Server
**File:** `service/OverlayService.kt`

**Do:**
```kotlin
class OverlayService : Service() {
    override fun onCreate() {
        super.onCreate()
        
        val networkClient = OkHttpNetworkClient()
        val moduleContext = ModuleContext(overlayController, networkClient)
        
        // Connect to server (get URL dari SharedPreferences)
        val serverUrl = getServerUrl()  // "ws://192.168.1.X:9999/"
        networkClient.connect(
            serverUrl,
            onConnected = {
                Log.d("OverlayService", "Connected to server")
                // Send player_connect packet
                networkClient.send("player_connect", mapOf(
                    "playerId" to getDeviceId(),
                    "displayName" to getPlayerName()
                ))
            },
            onError = { error ->
                Log.e("OverlayService", "Connection failed: $error")
                // Show error toast
            }
        )
        
        ModuleRegistry.init(moduleContext)
        // Register modules...
    }
    
    private fun getServerUrl(): String {
        val prefs = getSharedPreferences("blueberry", MODE_PRIVATE)
        return prefs.getString("server_url", "ws://localhost:9999/") ?: "ws://localhost:9999/"
    }
}
```

---

## Testing Checklist

### Server Testing
- [ ] `dotnet run` starts server
- [ ] Can telnet to `localhost 9999`
- [ ] WebSocket upgrade works
- [ ] Receives `player_connect` packet
- [ ] Can record replay events to DB
- [ ] Can playback events in order
- [ ] Proximity chat relay works (test with 2 clients at different distances)
- [ ] Database persists across restarts

### APK Testing
- [ ] App launches, shows LauncherActivity
- [ ] Can input server URL and connect (shows 🟢 connected)
- [ ] Can start OverlayService
- [ ] FPS counter appears on screen
- [ ] Can tap REC → timer starts
- [ ] Can tap PAUSE → timer pauses
- [ ] Can tap STOP → timer stops, sends to server
- [ ] ClickGUI appears, can toggle modules
- [ ] Proximity chat: mic captures audio (check logcat)
- [ ] Proximity chat: server relays to nearby player
- [ ] Proximity chat: audio plays with ambient filter applied
- [ ] Can view replay list, watch playback

---

## Common Pitfalls

1. **WebSocket over `localhost`** - Mobile APK can't reach `localhost:9999`. Use actual IP: `ws://192.168.X.X:9999/`
2. **Permissions** - Foreground service + RECORD_AUDIO must be requested at runtime (Android 6+)
3. **Audio thread safety** - AudioRecord/AudioTrack threads must not block UI thread
4. **Module State** - ModuleRegistry is static; if service restarts, re-register modules
5. **Opus frame size** - Must be exactly 960 samples @ 48kHz or decode fails

---

## File Locations Summary

```
BlueberryServer/
├── BlueberryServer.csproj          ← Add dependencies
├── src/
│   ├── Program.cs                  ← Add CLI parsing + logging
│   ├── WebSocketServer.cs          ← Add error handling
│   ├── Models/Models.cs            ✅
│   ├── Database/DbHandler.cs       ✅
│   └── Handlers/
│       ├── ReplayHandler.cs        ✅
│       └── ProximityChatHandler.cs ✅

BlueberryClient/app/src/main/
├── AndroidManifest.xml             ← Add RECORD_AUDIO permission
├── java/com/blueberry/client/
│   ├── core/
│   │   ├── IModule.kt              ✅
│   │   ├── ModuleRegistry.kt       ✅
│   │   ├── OverlayController.kt    ✅
│   │   ├── NetworkClient.kt        ← Interface only, OkHttpNetworkClient exists
│   │   └── OkHttpNetworkClient.kt  ✅
│   ├── modules/
│   │   ├── FpsCounterModule.kt     ✅
│   │   ├── ReplayModule.kt         ✅
│   │   ├── ProximityChatModule.kt  ✅
│   │   ├── CoordinatesModule.kt    ← NEW
│   │   ├── HealthModule.kt         ← NEW
│   │   └── ...more modules         ← NEW
│   ├── service/
│   │   ├── OverlayService.kt       ← Update for server connection
│   │   └── BlueberryAccessibilityService.kt
│   └── ui/
│       ├── LauncherActivity.kt     ← Add server URL input
│       ├── ClickGuiActivity.kt     ← NEW
│       ├── ReplayEditorActivity.kt ← NEW
│       └── ReplayViewerActivity.kt ← NEW
└── res/
    ├── layout/
    │   ├── activity_launcher.xml   ← Add EditText, status
    │   ├── activity_clickgui.xml   ← NEW
    │   ├── activity_replay_editor.xml ← NEW
    │   └── activity_replay_viewer.xml ← NEW
    └── values/
        ├── strings.xml
        └── themes.xml
```

---

## Success Criteria

When done, you should be able to:

1. **Start server:** `dotnet run` → "WebSocket listening on ws://0.0.0.0:9999/"
2. **Launch APK:** Input server URL `ws://192.168.1.X:9999/` → Connect button → 🟢 Connected
3. **Record replay:** Press REC (in pause menu) → speak → Press STOP → events saved to DB
4. **Playback:** View → Select replay → PLAY → hear yourself talking back
5. **Proximity chat:** 2 players @ 30 blocks apart → Player 1 speaks → Player 2 hears with volume reduction
6. **Toggle modules:** ClickGUI → See 300 module slots (most disabled) → Toggle FPS counter → appears on screen

---

## Next Session Goals

After Cursor finishes this:
- [ ] Add 50+ more simple modules (all same pattern)
- [ ] Add animation system (keyframe choreography)
- [ ] Add custom cosmetics (render via RP)
- [ ] Polish UI/UX

Good luck! 🚀
