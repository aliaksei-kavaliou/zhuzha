# Handoff — Onyx pen erase fix (2026-09-15, Cowork session)

Written for the Claude Code CLI session working in this repo. These changes are
**uncommitted and device-untested**: they were made from a Cowork session whose
shell has no Android SDK, so nothing here has been compiled. Build and flash
before trusting any of it. Base commit: `83bb315` (Pen: Onyx path working — draw,
tap, delete, navigate). Everything below is `git diff HEAD` on top of that.

## What was wrong (two independent things)

### 1. `pointerInteropFilter { …; false }` only ever sees ACTION_DOWN
The interop filter follows `View.onTouchEvent` rules: return `false` to a DOWN and
Compose marks that pointer stream "not dispatching" and delivers nothing further
from it — no MOVE, no UP — until the next contact. The erase-trail code committed in
`83bb315` (MOVE collects points, UP hit-tests and unlocks) lived inside a lambda that
always returned `false`, so those branches were dead code: the trail never grew past
its DOWN point and the UP handler never ran.

Side effect: `eraseModeRef` was made sticky and only cleared on UP/CANCEL — which
never arrived. After the first eraser (or barrel) DOWN the flag stayed `true` for the
life of the composable, and `emitStroke` sent every later pen stroke down its
`erase sweep` branch instead of committing ink. Fingerprint in logcat: after one
`erase mode armed`, strokes log `erase sweep n=… removed=…` instead of
`emit(moves) n=…`.

### 2. `ReaderScreen.onStrokeErased` was either/or
With a chip open (`viewingStroke`/`pendingGestureStroke` non-null) the handler only
dismissed the chip and returned; the hit stroke was never removed. The manual test
flow was "tap the line to select it, then erase it", which is exactly the case that
did nothing. The post-erase unlock then wiped the driver's grey sweep, so it looked
like a no-op.

## What changed

### `app/src/main/java/com/inkfirst/app/ui/OnyxInkOverlay.kt`
- **Import**: `android.os.SystemClock`.
- **Header KDoc**: the "Erase currently DOES NOT fire" paragraph replaced with a
  description of the MotionEvent-owned erase path.
- **State** (declarations near `eraseModeRef`):
  - `eraseModeRef` — now true only from an eraser contact's DOWN to its UP; set and
    cleared exclusively by the interop filter.
  - `eraseTrailRef` (state-backed immutable list) → `eraseTrail = remember { mutableListOf<Pair<Float, Float>>() }`.
    Plain list: nothing recomposes on it.
  - New `eraseEndedAtRef = mutableStateOf(0L)` — `SystemClock.uptimeMillis()` of the
    last eraser UP.
- **The interop filter** (`.pointerInteropFilter { event -> … }`) is rewritten as a
  `when (event.actionMasked)` expression whose branch values are the lambda's return:
  - `ACTION_DOWN`: `erasing = tool == TOOL_TYPE_ERASER || (stylus && BUTTON_STYLUS_PRIMARY held)`.
    Sets `eraseModeRef = erasing`, clears the trail, seeds it with the DOWN point when
    erasing, logs `erase mode armed (tool=… button=…)`. **Returns `erasing`** — `true`
    claims the stream (MOVE/UP now arrive), `false` for pen/finger leaves the raw path
    and finger gestures exactly as before.
  - `ACTION_MOVE`: when erasing, appends historical samples + current point. Returns
    `eraseModeRef.value`.
  - `ACTION_UP` / `ACTION_CANCEL`: when erasing — appends the release point, hit-tests
    the whole trail with `hitTestForErase`, logs `erase trail pts=N removed=M`, clears
    trail + flag, stamps `eraseEndedAtRef`, calls `erasedRef.value` per hit (or once
    with `null` on a miss, so an eraser tap in empty space still dismisses a chip —
    same contract as `InkOverlay`), then `unlockRequestRef.value?.invoke("eraseTrail")`.
    Returns `true`.
  - anything else (hover actions etc.): `false`.
  - `lastToolRef`/`penContactRef` updates kept at the top, unchanged in meaning.
- **`emitStroke` erase check**: was `eraseModeRef.value || lastTool == ERASER`; now
  also `|| sinceEraseUp < ERASE_COMMIT_GRACE_MS` where
  `sinceEraseUp = SystemClock.uptimeMillis() - eraseEndedAtRef.value`. Reason: this
  callback runs on the SDK thread and can land a few ms after the MotionEvent UP has
  already cleared the flag; for the barrel-button case the tool type says stylus, so
  without the grace the sweep could be committed as ink. Eraser-tip case is still
  caught by the tool type alone.
- **New constant** `ERASE_COMMIT_GRACE_MS = 150L` next to `UNLOCK_DEBOUNCE_MS`.
- Untouched: the raw `RawInputCallback` (draw path, `scheduleUnlock`/`cancelUnlock`),
  `onPageTap` handling, `hitTestForErase`, `findAnchorBlockOnyx`, all TouchHelper setup.

### `app/src/main/java/com/inkfirst/app/ui/ReaderScreen.kt`
- `onStrokeErased` lambda (ReaderScreen, ~line 429): always nulls
  `pendingGestureStroke` and `viewingStroke`, **and** removes `hit` when non-null.
  Previously either/or.

## Why this shape and not the alternatives
- Owning the stream is exactly what `InkOverlay` does on the MotionEvent path, and
  erase is proven to work there on this device.
- Owning it is safe for the raw path: the filter sits on the Box *above* the
  `AndroidView`, so the SurfaceView (and whatever TouchHelper attaches to it) simply
  never sees the eraser contact. If the driver inks the sweep anyway, the unlock on UP
  wipes it. The reader's page-turn detector sees `isConsumed` on the release and does
  nothing (its existing `if (change.isConsumed) break`).
- The overlay's own `pointerInput` needs no change: an owned eraser DOWN either hits
  the `Stylus/Eraser` branch (consumes the already-consumed down, bounded wait) or the
  `Touch` branch with `lastToolRef == ERASER` (returns immediately).
- No timing heuristic decides *whether* a contact is an erase — that is read from the
  DOWN's tool/button state. The only timing is the 150 ms commit grace, which can only
  ever match the sweep itself.

## Verify on device (logcat, tag `OnyxInkOverlay`), in this order
1. Eraser DOWN → `erase mode armed (tool=4 …)` (tool=2 with `button=32` for barrel).
2. Eraser UP → `erase trail pts=N removed=M`, N well above 1.
   - N == 1 means MOVEs still are not reaching the filter → something above the
     overlay is consuming them; report, do not guess.
3. ~200 ms later → `unlock(eraseTrail)`; the stroke and the driver's grey sweep vanish
   together. A flash means the frame wasn't committed before the unlock (same knob as
   T4 in the thread: `UNLOCK_DEBOUNCE_MS`).
4. Draw a normal stroke afterwards → `emit(moves) n=…` (NOT `erase sweep`). Proves the
   latch is gone.
5. Select a stroke (chip up), then erase it → chip closes AND stroke goes.
6. Eraser tap in empty space with a chip up → chip closes, nothing erased, `removed=0`.

## If it fails
| Symptom | Meaning | Next step |
|---|---|---|
| Step 1 fires, then a later contact commits as an ordinary stroke | The "eraser reports tool=2 on contact, tool=4 only on the arming/hover contact" theory from the old comment is real | Track the tool type from hover events (proximity), not from the DOWN. Do NOT bring back the sticky latch. |
| Step 2 shows `pts=1` | MOVEs consumed above us | Audit gesture detectors above the overlay for MOVE consumption. |
| Erase logs `removed=1` but the stroke stays visible | Unlock not happening or too early | Check for `unlock(eraseTrail)`; if absent, `unlockRequestRef` was null (factory not run yet) — unlikely after a draw. |
| Eraser sweep gets committed as ink (barrel only) | Grace too short for this firmware | Raise `ERASE_COMMIT_GRACE_MS`; log `sinceEraseUp` to size it. |

## Not done / follow-ups
- `setRawDrawingRenderEnabled(false)` on eraser DOWN, `(true)` on UP, so the driver
  never inks the sweep. Needs `javap` on onyxsdk-pen 1.5.4 to confirm the method and a
  device test that toggling mid-contact is safe (add to the thread's VERIFY ledger).
- `hitTestForErase` measures distance to stored *points* (24 px slop); a fast raw
  stroke with wide gaps can be swept between samples and missed. Point-to-segment
  distance would close that.
- `docs/onyx-pen-thread.md` still lists erase as open. Update Status once step 1–6 pass;
  the T4 slot is the place to report the device results.
- `CLAUDE.md` "Where things stand" is stale (says nothing is built).

## Process notes
- Nothing committed; `git diff HEAD` shows exactly the changes above plus your own
  in-flight `AskTextToolbar.kt` edits, which I did not touch.
- The Cowork shell cannot delete files by default. An earlier `git status` from it left
  an empty `.git/index.lock`; it is gone now. Read-only git from that shell should use
  `GIT_OPTIONAL_LOCKS=0` so it cannot happen again.
