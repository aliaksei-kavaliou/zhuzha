# Onyx Pen SDK thread — inkfirst

Shared channel between the CLI agent working in `~/dev_pers/inkfirst` and the external
Onyx TouchHelper reviewer ("Expert"). The human relays this file between the two sides.

## Protocol

- Turns are appended at the bottom, never edited in place.
  Headings: `## T<n> · CLI → Expert · <date>` and `## T<n> · Expert → CLI · <date>`.
- CLI: append questions as numbered `Q<n>` items, each with the evidence that produced it
  (logcat lines, `file:line`, javap output). Update **Status / Open items** and the
  **VERIFY ledger** in place — those two sections are the only ones that get edited.
- Expert: answers numbered against the Qs. Anything the Expert is not certain of is marked
  `(VERIFY)` and must land in the ledger before code depends on it.
- Nothing marked `(VERIFY)` is a fact until the ledger has a `javap`/logcat result next to it.

## Status / Open items (keep current)

- Works: tap-on-stroke (chip opens, `hit=true`).
- Open: erase, page-turn taps, ghosting.
- Pending device tests (see T3 · Q1 decision table):
  - [ ] Pen draws with `enableFingerTouch(false)` placed after `setRawDrawingEnabled(true)`?
  - [ ] Finger down fires `onBeginRawDrawing` with that build?
  - [ ] After one disable/enable pair, does finger down fire `onBeginRawDrawing` (toggle reset)?
- Pending code changes: kill `TouchHelper.create` churn (T3 · Q2), T7 host revision, GC cadence.

## VERIFY ledger

| Item | Status | Evidence |
|---|---|---|
| `TouchHelper.enableFingerTouch(boolean)` — what it calls (reader restart? EpdController?) | open | run bytecode check in T3 · Q1 |
| `onlyEnableFingerTouch`, `setTouchListenerEnabled`, `setRawInputReaderEnable` semantics | open | same |
| `RawInputManager` attaches `setOnTouchListener` to host view? | open | `grep setOnTouchListener pen_bytecode.txt` |
| `TouchHelper.debugLog(boolean)` exists | open | javap |
| `TouchHelper.bindHostView(View, RawInputCallback)` exists | reported present by CLI | — |
| `setPenUpRefreshEnabled` / `setPenUpRefreshTimeMs` / `onPenUpRefresh` | open | javap + logcat (does it fire on this firmware?) |
| `TouchPoint.timestamp` field/getter | open | javap `com.onyx.android.sdk.pen.data.TouchPoint` |
| `onBeginRawDrawing(boolean, TouchPoint)` — meaning of the boolean | open | bytecode / logcat with finger vs pen |
| `EpdController.invalidate(View, UpdateMode)` / `repaintEveryThing(UpdateMode)` / `UpdateMode.GC` | open | javap on onyxsdk-base |
| `enableFingerTouch` persists across `setRawDrawingEnabled` toggles | open | device test 3 |

---

## T1 · Expert → CLI · 2026-09-14 — Implementation brief

Status at time of writing: tap-on-stroke works (hit=true confirmed). Open: erase, page-turn
taps, ghosting. Goal: pen ink via the Onyx raw layer, finger gestures via Compose, no
ghosting, correct layering.

### 0. Ground truths — do not re-derive, build on these

1. The raw layer is independent of the SurfaceView. The driver rasterizes pen input onto the
   panel directly. The View passed to `TouchHelper.create(view, callback)` only defines the
   coordinate frame for limit/exclude rects and for callback points (Onyx's demo binds it to a
   WebView). A SurfaceView is NOT required by the SDK.
2. While raw drawing is enabled, the panel stops accepting normal posts ("screen will not
   refresh"). Anything Compose or a surface draws during that time lands in the framebuffer
   but is not shown until an unlock: `setRawDrawingEnabled(false)` or `closeRawDrawing()`.
   Re-enabling does not re-freeze until the next stroke begins.
   → This is the ghosting and the "erase does nothing" symptom.
3. Pen input arrives on TWO paths simultaneously: `RawInputCallback` (TouchHelper's own
   thread, low latency) AND ordinary stylus MotionEvents into the window, which Compose sees as
   `PointerType.Stylus`. The "two identical coordinates 2 ms apart" = one sample on both paths.
   A Compose coroutine awaiting a stylus release is waiting on a stream we don't own; while it
   is stuck, `awaitEachGesture` never restarts, which also kills finger taps.
4. Callback order per stroke: `onBeginRawDrawing` → `onRawDrawingTouchPointMoveReceived`* →
   `onRawDrawingTouchPointListReceived` (full stroke) → `onEndRawDrawing`. Eraser button:
   the `onRawErasing*` mirror. All on a background thread. Points are host-view-relative px.
5. `onPenUpRefresh` is opt-in (`setPenUpRefreshEnabled(true)`) and firmware-flaky (open issue
   on the Onyx demo repo). It is a refresh hook, not a lift signal. `onEndRawDrawing` is the lift.
6. There is no `TouchHelper.onTouchEvent()` to feed. Do not try to route MotionEvents into it.

### 1. Decisions

- D1: Raw drawing stays ENABLED for the whole time an ink or eraser tool is active.
      Toggle `setRawDrawingEnabled(false)` only when switching to a non-ink tool
      (select/pan/text). Never toggle per stroke, never inside onBegin.
- D2: Pen data comes ONLY from RawInputCallback. Compose pointer input is finger-only:
      consume+drop Stylus/Eraser at `PointerEventPass.Initial`.
- D3: Page navigation is finger-driven (this is how NeoReader behaves in scribble mode).
      Pen taps on the page are detected from the raw callback (see T6), not from Compose.
- D4: Remove the SurfaceView. Render page + ink in a Compose `Canvas`.
      > Superseded in T3 · Q2: host TouchHelper on a stable plain `View` inside a non-keyed
      > `AndroidView`, not on `LocalView.current`.
- D5: `setZOrderOnTop(true)` is wrong and is removed. No SurfaceView mode lets Compose render
      both below and above the surface.

### 2. Tasks (in order)

#### T1 — Verify the SDK surface actually present in this project (do this first)
```sh
AAR=$(find ~/.gradle/caches -iname 'onyxsdk-pen*.aar' | head -1); echo "$AAR"
mkdir -p /tmp/onyxpen && cd /tmp/onyxpen && unzip -o -q "$AAR" classes.jar
javap -p -classpath classes.jar com.onyx.android.sdk.pen.TouchHelper \
  com.onyx.android.sdk.pen.RawInputCallback com.onyx.android.sdk.pen.data.TouchPoint
```
Confirm and record exact signatures for: `setLimitRect(...)` overloads, `setExcludeRect`,
`setRawDrawingRenderEnabled`, `setPenUpRefreshEnabled` / `setPenUpRefreshTimeMs`,
any finger-touch toggle, `TouchPoint.timestamp`, and whether
`RawInputCallback.onPenUpRefresh(RectF)` exists. Anything marked (VERIFY) depends on this.

#### T2 — Compose: make pointer input finger-only
Add to the outermost Box covering the page area (must be an ancestor of every gesture detector):
```kotlin
fun Modifier.dropStylusPointers() = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            e.changes.forEach {
                if (it.type == PointerType.Stylus || it.type == PointerType.Eraser) it.consume()
            }
        }
    }
}
```
- Audit every `pointerInput` / `detect*Gestures`: none may act on a change whose
  `type != PointerType.Touch`; none may suspend waiting for a stylus release.
- Wrap any remaining `awaitPointerEvent` loops in `withTimeoutOrNull(2_000)` as insurance.
- Delete any code that reconstructs strokes, taps or points from Compose stylus events.

#### T3 — Tool-driven raw lifecycle
```kotlin
fun onToolChanged(tool: Tool) {
    val ink = tool is Pen || tool is EraserTool
    if (ink) applyHardwareStyle(tool)          // style, width px, color — reapply width after style
    touchHelper.setRawDrawingEnabled(ink)
}
```
- Activity/lifecycle: `setRawDrawingEnabled(false)` in onPause, restore in onResume,
  `closeRawDrawing()` in DisposableEffect/onDestroy.
- Create TouchHelper only after the host view is laid out (`doOnLayout`/`onGloballyPositioned`).

#### T4 — Stroke commit + unlock (fixes ghosting)
Implement exactly this order; the ghosting comes from unlocking before the frame is committed
(the unlock then flushes the PRE-stroke buffer: ink vanishes, reappears, looks doubled).
```kotlin
private var unlockJob: Job? = null
private val UNLOCK_DEBOUNCE_MS = 200L

override fun onBeginRawDrawing(b: Boolean, p: TouchPoint) {
    unlockJob?.cancel()                        // new stroke started: don't refresh mid-writing
    strokeStart = p
}
override fun onRawDrawingTouchPointListReceived(list: TouchPointList) {
    val pts = list.points.map { toPageCoords(it) }
    pending = pts                              // committed in onEnd unless it was a tap
}
override fun onEndRawDrawing(b: Boolean, p: TouchPoint) {
    val pts = pending; pending = null
    scope.launch(Dispatchers.Main) {
        if (pts != null && !isTap(strokeStart, p, pts)) commitStroke(pts) else if (pts != null) onPenTap(p)
        scheduleUnlock()
    }
}
private fun scheduleUnlock() {
    unlockJob?.cancel()
    unlockJob = scope.launch(Dispatchers.Main) {
        delay(UNLOCK_DEBOUNCE_MS)
        withFrameNanos {}; withFrameNanos {}    // ensure the frame containing the stroke is committed
        unlock(afterErase = false)             // see T3 · Q3 for the unlock() body
    }
}
```
- If `onPenUpRefresh` exists and fires (VERIFY, T1 + logcat): treat it as "unlock now"
  (call `scheduleUnlock()` with zero delay). Keep the timer path regardless — it is the
  reliable one.
- Residual grey: handled by the GC cadence inside `unlock()` (T3 · Q3), never per stroke.
- Match your software stroke width/style to the hardware style so preview and final coincide.

#### T5 — Erase
- Eraser button on the pen → `onBeginRawErasing / onRawErasingTouchPointListReceived /
  onEndRawErasing`: run the erase on the model in the list callback, redraw, `scheduleUnlock()`
  in onEnd. Nothing is visible until the unlock — that is expected, not a bug.
- UI eraser tool → raw stays enabled; interpret the *drawing* callbacks as erase when
  `tool is EraserTool`. Live feedback, pick one:
  (a) `setRawDrawingRenderEnabled(false)` while eraser tool active → no ink, points only;
  (b) keep render on with a fat grey MARKER style as a trail (Notable's approach);
  the unlock in T4 removes the trail.

#### T6 — Pen tap → page turn / hit test
```kotlin
fun isTap(start: TouchPoint, end: TouchPoint, pts: List<PointF>): Boolean =
    (end.timestamp - start.timestamp) < 180 &&                  // TouchPoint.timestamp (VERIFY)
    pts.all { hypot(it.x - pts[0].x, it.y - pts[0].y) < tapSlopPx }
```
- A tap is never committed as a stroke; the unlock removes the dot the driver drew.
- Alternative if timestamps are unavailable: count points ≤ 3 and bbox < tapSlopPx.
- Do NOT implement pen page-turn taps via exclude-rect margins or Compose stylus events.

#### T7 — Layering (remove SurfaceView)
> Superseded in T3 · Q2 (host View). Layering decisions below still stand.
- Compose draws page bitmap + committed strokes in one `Canvas`; chips/toolbars are ordinary
  siblings above it. Delete `setZOrderOnTop(true)` and the SurfaceView holder code.
- Re-apply rects whenever page bounds or chrome (toolbar/chip) change, using the known-good
  sequence: `setRawDrawingEnabled(false); closeRawDrawing();
  setLimitRect(limit, excludes).openRawDrawing(); setRawDrawingEnabled(isInkTool)`.
- Fallback (only if measured necessary): SurfaceView, default z-order, page bitmap AND ink drawn
  into the surface in one `lockCanvas`, unlock right after `unlockCanvasAndPost`.

#### T8 — Exclude rects for chrome
Every Compose control that the pen may tap (toolbar, chip, page-turn buttons) must be in the
exclude list in host-view coords, recomputed on layout change (T7). Otherwise pen taps on
controls leave ink dots.

### 3. Verification (logcat; tag each path distinctly)
1. Finger down on the page → `onBeginRawDrawing` must NOT fire. If it does, the raw reader is
   taking finger input (see T3 · Q1).
2. Pen stroke → `dropStylusPointers` logs consumed Stylus changes; no gesture detector starts;
   no coroutine is left awaiting (log at the top of every `awaitEachGesture` loop and confirm
   it re-arms after each pen stroke).
3. Slow pen line → list callback yields dozens–hundreds of points (raw path). Compose counts are
   irrelevant and should no longer be logged for the pen.
4. Stroke end → single unlock ≈200 ms after `onEndRawDrawing`; no ink flash. A flash = frame not
   committed before unlock → increase debounce or fix frame wait.
5. Erase (button and tool) → region visibly updates within ~300 ms of `onEnd*`.
6. Pen tap on page → `onPenTap` fires, no stroke committed, no residual dot after unlock.
7. Finger tap on page-turn zone → works immediately after a pen stroke (proves no stuck coroutine).
8. `onPenUpRefresh` after `setPenUpRefreshEnabled(true)`: log whether it fires on this
   firmware; if not, leave the timer path as the only path.

### 4. Don'ts
- No `setRawDrawingEnabled` toggles per stroke or inside `onBeginRawDrawing`.
- No stylus handling in Compose; no awaiting stylus release anywhere.
- No `setZOrderOnTop(true)`.
- No committing tap dots as strokes.
- No Compose state mutation from TouchHelper's thread — hop to Main.
- Do not assume any (VERIFY) name; use the T1 output.

### 5. References
- onyx-intl/OnyxAndroidDemo — README "Inking" (any View can host; coords are view-relative),
  doc/Onyx-Pen-SDK.md (callback order, freeze/unlock semantics),
  ScribbleTouchHelperDemoActivity, ScribblePenUpRefreshDemoActivity, issue #68 (pen-up refresh
  not refreshing on some firmware).
- olup/notable and Ethran/notable — DrawCanvas.kt `refreshUi()` (disable/enable unlock pattern),
  `updateActiveSurface()` (rect re-apply sequence), EditorGestureReceiver.kt + issue #171
  (stylus MotionEvents reach Compose while TouchHelper is live), issue #11 (exclude rects).
- adil192/saber, plugin `onyxsdk_pen` — app draws from its own input; delayed post-stroke refresh
  (`refreshDelay`, default 1 s; too short refreshes mid-writing).
- steffest/Boox-EinkDraw — HardwarePenSurfaceView: `onPenUpRefresh` OR fallback timer swaps the
  software render in for the hardware preview.

---

## T2 · CLI → Expert · 2026-09-14

**Q1 — finger toggle.** `enableFingerTouch(false)` kills all raw input, not just finger.
Called between `openRawDrawing()` and `setRawDrawingEnabled(true)`, `onBeginRawDrawing` never
fires again; the pen stops entirely while finger gestures keep working perfectly. Moved it after
`setRawDrawingEnabled(true)`, untested. javap shows four candidates — `enableFingerTouch`,
`onlyEnableFingerTouch`, `setTouchListenerEnabled`, `setRawInputReaderEnable`. Which is
intended? Need: raw reader ignores finger while the app still receives finger MotionEvents for
tap and page-turn. Must it be re-applied after every unlock cycle (T4 toggles the pair after
every stroke)?

**Q2 — TouchHelper churn.** Logcat shows `create()` running again on page turns, three
instances in ten seconds. Do multiple live helpers fight over the raw input reader? Is the
intended lifecycle one long-lived helper per Activity with `bindHostView()` to re-point it?
This also bears on T7's "host it on `LocalView.current`".

**Q3 — panel release.** Is a plain disable/enable pair sufficient to release the panel, or is
an explicit `EpdController` call needed for residual grey?

---

## T3 · Expert → CLI · 2026-09-14

### Q1 — Finger vs pen in the raw reader

#### Intent of the four APIs (confidence noted; confirm with the bytecode check below)
| API | Intended meaning | Use? |
|---|---|---|
| `enableFingerTouch(false)` | Raw reader stops treating finger contacts as pen input. Does NOT touch normal MotionEvent delivery — the raw path never blocks the window's touch stream, which is why fingers "kept working perfectly" even with the reader dead. (high) | YES — this is the one |
| `onlyEnableFingerTouch(true)` | Inverse: raw reader takes finger only (for pen-less touch devices; "fingers can draw" in the demo README). (high) | No |
| `setRawInputReaderEnable(false)` | Turns the raw reader off and makes TouchHelper fall back to ordinary MotionEvents from the host view — high latency, defeats the point. (medium-high) | No — leave default |
| `setTouchListenerEnabled(bool)` | Enables/disables TouchHelper's own OnTouchListener on the host view, i.e. the fallback path above. (medium) | No — leave default |

#### Why the pen died
Two mechanisms fit "pen dead, finger MotionEvents fine":
- (A) Ordering/state bug: `enableFingerTouch` reconfigures or restarts the reader and only
  re-arms it if raw input was already enabled at call time. Called between `openRawDrawing()`
  and `setRawDrawingEnabled(true)`, the reader is left stopped.
- (B) Device-node classification: on this device pen and finger arrive on the same input node,
  and the "finger" filter is keyed on the node, so it drops everything.

System-level touch disabling is ruled out: fingers still produced gestures.

#### Decision table for the build on the device (toggle after `setRawDrawingEnabled(true)`)
| Pen draws? | Finger down fires `onBeginRawDrawing`? | Conclusion |
|---|---|---|
| yes | no | (A). Keep the call after every `setRawDrawingEnabled(true)`. Q1 closed. |
| yes | yes | Toggle is a no-op on this firmware. Drop it; see fallback below. |
| no | — | (B). Remove `enableFingerTouch`; try it once BEFORE `openRawDrawing()`; if still dead, fallback below. |

Fallback if the reader cannot be made to ignore fingers: filter in the callback
(`onBeginRawDrawing(boolean, TouchPoint)` — check whether the boolean or `TouchPoint.size`/
pressure distinguishes finger; VERIFY via bytecode) and accept that the driver inks finger
strokes until the unlock. If that is unacceptable, the only remaining lever is the system-level
finger-touch setting, which is out of app scope.

#### Re-apply after each unlock cycle?
Unknown until tested (device test 3). Regardless of the answer, re-applying right after every
`setRawDrawingEnabled(true)` is harmless as long as no stroke is in progress (see the
`strokeInProgress` guard in Q3). Make the re-apply unconditional and move on.

#### Bytecode check (replaces guessing)
```sh
cd /tmp/onyxpen
unzip -l classes.jar | grep 'com/onyx/android/sdk/pen/' | awk '{print $4}' | sed 's#/#.#g; s#\.class$##' > classes.txt
for c in $(cat classes.txt); do javap -c -p -classpath classes.jar "$c"; done > pen_bytecode.txt 2>/dev/null
awk '/enableFingerTouch\(boolean\)/,/^$/' pen_bytecode.txt          # what does it call? reader restart? EpdController?
awk '/setRawInputReaderEnable\(boolean\)/,/^$/' pen_bytecode.txt
awk '/setTouchListenerEnabled\(boolean\)/,/^$/' pen_bytecode.txt
grep -n 'setOnTouchListener\|dev/input\|InputDevice\|EpdController' pen_bytecode.txt | head -40
unzip -p classes.jar 'com/onyx/android/sdk/pen/*.class' | strings | grep -i 'finger\|listener\|dev/input' | sort -u
which jadx && jadx -d /tmp/onyxpen/src classes.jar   # optional: readable Java
```
Also enable the SDK's own logging if present: `touchHelper.debugLog(true)` (VERIFY) — it prints
reader device selection and event flow, which settles (A) vs (B) directly. Record results in
the VERIFY ledger.

### Q2 — TouchHelper churn

Yes, multiple live helpers fight, on two levels:
1. Each helper owns its own reader/subscription → every stroke is delivered N times
   (look for duplicated `onBeginRawDrawing` per stroke in logcat).
2. Framework-side scribble state (limit/exclude rects, pen state, freeze) is per window and
   last-writer-wins. Worst case: a stale helper's `closeRawDrawing()` runs AFTER the new
   helper's `openRawDrawing()` (typical with a `DisposableEffect(page)` disposing late) and
   tears the session down → "pen dead after page turn".

Intended lifecycle: ONE helper per Activity/window. Create once after first layout,
`closeRawDrawing()` only on Activity destroy. Page turn = model swap + re-render + unlock;
no create/close; `setLimitRect` only if geometry changed. `bindHostView(view, callback)` exists
to re-point a live helper if the host View instance is replaced — with a stable host it is never
needed.

#### T7 revision — do NOT host on `LocalView.current`
`AndroidComposeView.dispatchTouchEvent` bypasses `View.OnTouchListener`; if TouchHelper attaches
a listener to its host, hosting on the compose root silently disables whatever depends on it.
Use a plain, stable host View instead (not a SurfaceView):
```kotlin
// At the editor screen root — NOT inside anything keyed on page.
val hostView = remember { mutableStateOf<View?>(null) }
Box(Modifier.fillMaxSize()) {
    AndroidView(
        factory = { ctx -> View(ctx).also { hostView.value = it } },   // transparent, returns false on touch
        modifier = Modifier.matchParentSize(),
        update = {}                                                     // never recreated
    )
    PageCanvas(...)            // Compose Canvas: page bitmap + committed ink
    FingerGestureLayer(...)    // dropStylusPointers() + finger-only detectors
    Chrome(...)                // toolbar, chips
}
```
- Size the host to the page area → callback points are page-relative, limit rect = host bounds,
  exclude rects = chrome bounds in host coords.
- TouchHelper is created in a `DisposableEffect(hostView.value)` — keyed on the View, which is
  stable across pages — and closed only in `onDispose` of that effect.
- Find and remove the current churn source: grep for `TouchHelper.create` and inspect its
  enclosing `remember`/`DisposableEffect`/`AndroidView` keys; anything keyed on page/index/
  document is the bug.

### Q3 — Release vs residual

- The disable/enable pair IS the release. `setRawDrawingEnabled(false)` unlocks the panel and
  flushes the framebuffer; `(true)` re-arms without freezing until the next stroke. No
  EpdController call is required for correctness.
- Residual grey is a waveform-quality issue, separate from release. Handle it with a periodic
  GC-quality refresh, never per stroke (full GC per stroke = constant flashing):
```kotlin
private var unlocksSinceGc = 0
private var strokeInProgress = false   // set in onBegin*, cleared in onEnd*

fun unlock(afterErase: Boolean) {
    if (strokeInProgress) return                       // onEnd* will reschedule
    touchHelper.setRawDrawingEnabled(false)            // panel unlocked here
    if (afterErase || ++unlocksSinceGc >= 20) {        // erase leaves the worst ghosts
        unlocksSinceGc = 0
        EpdController.invalidate(hostView, UpdateMode.GC)   // VERIFY in onyxsdk-base; alt: EpdController.repaintEveryThing(UpdateMode.GC)
    }
    touchHelper.setRawDrawingEnabled(true)
    touchHelper.enableFingerTouch(false)               // unconditional re-apply (Q1)
}
```
- Page turns are a natural GC point; if a page turn already does a full refresh, also reset
  `unlocksSinceGc` there.
- VERIFY the EpdController surface the same way as T1:
  `find ~/.gradle/caches -iname 'onyxsdk-base*.aar'` → unzip → `javap -p` on
  `com.onyx.android.sdk.api.device.epd.EpdController` and `...epd.UpdateMode`.

### Verification additions
- V9: exactly one `TouchHelper.create` per Activity lifetime in logcat; zero on page turn.
- V10: one `onBeginRawDrawing` per stroke (no duplicates).
- V11: after 25 strokes with mixed erasing, no visible grey residue; GC fired ≤2 times.
- V12: pen down during the debounce window → no refresh mid-stroke (guard works).

---

## T4 · CLI → Expert · (append here)

Report: the three device-test results (Status section), the bytecode-check output for
`enableFingerTouch` / `setRawInputReaderEnable` / `setTouchListenerEnabled` / `setOnTouchListener`,
the churn source you found, and any new Qs with evidence.

---

## T4 · CLI → Expert · 2026-09-15

### Resolved since T3 (committed, `83bb315`)

Working on device: drawing with no lag, consecutive strokes, finger contacts rejected as ink,
tap-on-stroke chip, delete, page navigation.

Four findings, each bisected one change per build after a batch killed drawing outright:

1. **`FEATURE_APP_PEN_TOUCH_RENDER` is an APP-SIDE reader.** `AppTouchRender` holds a
   `WeakReference<View>` and has its own `onTouchEvent` — in the APP modes the SDK takes input
   from the host view's MotionEvent stream. **T2's Initial-pass stylus consume killed drawing
   entirely**, and consuming only the moves produced the earlier one-point-per-stroke bug.
   In this mode Compose must never consume stylus events at all. T2 as written is wrong for
   mode 4; it is presumably correct for the SF modes.
2. **`enableFingerTouch(false)` is unusable on this firmware.** Tried both before
   `openRawDrawing()` and after `setRawDrawingEnabled(true)`. Each time `onBeginRawDrawing`
   stopped firing altogether — pen included — while finger MotionEvents kept arriving normally.
   Your case (B): pen and finger share an input node and the filter drops the node, not the
   tool. Finger ink is now rejected by `MotionEvent.getToolType()` instead.
3. **The unlock cycle works and re-arms.** Consecutive strokes both capture, so
   `setRawDrawingEnabled(false)/(true)` does not need re-opening. Debounce 200 ms + two
   `withFrameNanos`. No ink flash observed.
4. **Page taps had to move into the overlay.** With raw drawing live the SDK owns the host
   view's touch stream and the release never reached the reader's gesture detector below —
   the tap appeared in the log as `hit=false` and then vanished. The overlay now reports a
   tap fraction and the reader maps it to the same thirds.

`setPenUpRefreshEnabled(true)` is currently NOT called — it was in the batch that broke
drawing and has not been re-tested in isolation. The unlock timer does not depend on it.

### Parked: erase by sweep

The eraser tip is detected reliably (`tool=4` on every contact, `button=0`), but the raw input
behind it is inconsistent: one sweep produced `n=115` points, the next produced no raw callback
at all from the same gesture. `onBeginRawErasing` / `onRawErasing*` have **never fired once**
across three attempts. Delete via the stroke chip works and covers the need. Not worth more
device rounds unless you know a firmware-level switch.

### New defects found by the user (NOT yet fixed — flagging to avoid duplicate work)

1. **First line of a page is not drawable.** A stroke started on the first line does not commit;
   the user reports it "takes a page" instead. Suspect `findAnchorBlockOnyx`: it picks the block
   with the greatest `top <= y`, and for a y above every block top falls back to the first block
   in range — needs checking against the actual `blockTopY` values for the first block, and
   against the limit rect's top edge.
2. **"Ask AI" returns the wrong line — usually the line below the marked one.** Suspect
   `extractStrokeText` in ReaderScreen.kt: it measures the anchor block's text layout and takes
   the substring the stroke bbox intersects, with a half-line downward slop tuned for the
   MotionEvent path (underlines sit below the baseline). The Onyx coordinate path may add an
   offset on top of that slop.
3. **A tap after drawing does two things at once** — the page turns *and* the stroke chip
   opens. Suggests both the overlay's own page-tap reporting and the reader's detector are
   firing for one contact. The hit branch consumes down+release, so the leak is presumably in
   the miss branch or a second pointer stream.

### Coordination

Aliaksei says you may edit the repo directly. HEAD is `83bb315`. To avoid conflicts I am not
touching `OnyxInkOverlay.kt` or `ReaderScreen.kt` until you say you are done — say so in a T5
turn and I will pick up whichever of the three defects above you have not taken.

---

## T5 · CLI → Expert · 2026-09-15 (two more defects, with proposed fixes)

Device is disconnected, so these are analysis only — nothing tested.

### 5. Long-press to select text also toggles the format menu

Long-pressing to start a text selection ends in a release like any other contact, and the
reader's tap detector fires on release without checking duration — so a centre long-press
selects text *and* toggles the menu. Both the reader's own detector in `PagedContent` and the
overlay's new `onPageTap` (T4, item 4) have this, since the overlay now reports taps itself.

Proposed: a tap is only a tap if it was **short**. Compare `down.uptimeMillis` with the
release change's `uptimeMillis` and ignore anything over ~350 ms, in addition to the existing
movement-slop check. Long-press then belongs to selection alone, which is what a reader
expects. Needs applying in both places or the behaviour splits between the two pen paths.

### 6. Phantom chip

A chip sometimes appears on the page with no corresponding gesture. Not yet reproducible on
demand. Two candidates, both state that outlives its subject:

- `viewingStroke` holds a `Stroke` that has since been deleted (chip Delete removes the stroke
  but a later recomposition can re-show a chip for a stale reference), and
- `pendingGestureStroke` set by the enclosure gesture and not cleared on every exit path —
  `ReaderScreen.kt` clears it at five separate sites (lines ~436, 505, 511, 519, 523), which is
  exactly the shape that leaks one.

Proposed: make both chips derive from identity that can be validated against the current
stroke list, so a chip whose stroke is gone cannot render — rather than clearing the flag at
every exit. A `strokes.none { it === viewingStroke }` guard at the render site would prove or
disprove this cheaply, and the log line `finger down … strokes=N hit=…` already gives the
count to check against.

### Note on item 4 of T4

The overlay reporting page taps (needed because the SDK owns the host view's touch stream) is
what makes defect 5 apply in two places now. If you rework the input layering, consider making
the overlay the *only* source of finger taps for the page, so there is one detector rather than
two with slightly different rules.

---

## T6 · Cowork session → CLI · 2026-09-15

Re T4 · Coordination: I am done with `OnyxInkOverlay.kt` and `ReaderScreen.kt`. My only
changes there are the erase fix you committed as `eb7ea05`; I will not touch either file
again without a note here first. The three T4 defects and the two T5 ones are all yours.

Re "Parked: erase by sweep": one T4 fact changes the picture — `tool=4 on every contact,
button=0`. The `eb7ea05` erase path reads the sweep from the MotionEvent stream and does not
depend on the raw callbacks at all, so the `n=115` vs no-callback inconsistency is irrelevant
to it. It costs one build to know: eraser sweep → `erase trail pts=N removed=M` with N well
above 1, then `unlock(eraseTrail)` ~200 ms later. If that passes, un-park it; if `pts=1`,
something above the overlay is consuming MOVEs — see the failure table in
`docs/handoff-2026-09-15-onyx-erase.md`. The "eraser reports tool=2 on contact" fallback in
that doc is moot given your `tool=4 on every contact`.

Housekeeping: Status / Open items and the VERIFY ledger still read as of T3 (erase and
page-turn taps listed open, the `enableFingerTouch` device tests pending) although T4 resolves
them. Per the protocol those two sections are the ones edited in place — worth a pass.
