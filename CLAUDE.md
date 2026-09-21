# InkFirst

AI-generated study books for e-ink readers. Personal project by Aliaksei (aliaksei.kavaliou@kiloverse.com), may be open-sourced later. This file is the handoff context for Claude Code sessions — read it fully before doing anything.

## The idea (elevator pitch)

Chat AI is a bad container for learning: answers scroll away, there's no pacing, no structure, no way to go back and forth. InkFirst inverts this — the AI **compiles a book**, and the chat lives *around* the book instead of *being* the product.

The user says "I want to learn X, I have N minutes/day." The app (via Gemini API) generates a course outline, then chapters one at a time, in a pedagogical style the user picks from built-in presets at course-creation time. Presets ship in `:core` as named system-prompt templates:

- **headfirst-inspired** (default) — conversational, redundant on purpose, exercises every few pages, concrete-before-abstract. Inspired by the Head First pedagogy (do NOT use O'Reilly's "Head First" branding or trade dress; that's their IP).
- **textbook** — structured, formal, definition → example → practice.
- **practical** — minimal theory, code/hands-on heavy, project-oriented.

The chosen style key is stored in `course.json` and drives prompt selection for all generation calls. The user reads paginated chapters, selects text to ask the AI about it, takes notes (typed first, stylus handwriting later), and tracks progress chapter by chapter.

Name origin: "Ink First" = e-ink-first design + a nod to Head First. GitHub name checked as free in Aug 2026.

## Target device & constraints

- Primary device: **Onyx Boox Go 7 (black, 2025 gen)** — 7" e-ink, Android-based, stylus support, sideloaded APK.
- E-ink rules: **no animations, no scrolling** in reading UI. Paginated everything. High contrast, no gradients, generous tap targets. This is a feature, not a limitation — the book-page metaphor is the whole point.
- Standard Android app; Boox runs Android (Go 7: Android 12). Develop/test on emulator or any Android phone, verify on device via sideload.
- Onyx SDK (onyxsdk-pen 1.5.4) provides low-latency raw pen input. **Integrated and working**
  as of 2026-09-15, after three failed attempts — it is sparsely documented and several of its
  behaviours are counter-intuitive enough to have cost whole sessions. Read design.md §11 and
  `docs/onyx-pen-thread.md` before changing anything in `OnyxInkOverlay.kt`.

## Architecture decisions (already made — don't relitigate without asking)

1. **No backend.** Single Android app. Gemini API called directly from the device with the user's own API key (entered in settings, stored in Android Keystore/EncryptedSharedPreferences — never in files or git). BYO-key keeps costs zero and makes open-sourcing trivial.
2. **Local-first, plain files.** Courses live on disk as inspectable files: generated chapters as Markdown/HTML, outline + progress as JSON, ink strokes (later) in a simple custom JSON format. SQLite only for indexing/metadata if needed. Rationale: user's study material must be greppable, backupable, and readable in 10 years even if the app dies.
3. **Generation logic in its own Gradle module** (`:core` or similar), separate from UI. Not because a backend is planned (it isn't), but so the door stays open and the code stays testable.
4. **Stack: Kotlin + Jetpack Compose.** Native Android. No KMP, no Flutter (Flutter fights e-ink rendering).
5. **Figures are illustrations, not diagrams.** Phase 3 was originally specced as generated *diagrams*; that was reframed on 2026-09-10. The pictures exist to make a study page feel alive (the Head First trick), not to be reference material — so they're loose greyscale sketches from an image-capable Gemini model ("Nano Banana"), drawn per figure slot, with the house style in one constant in `:core`. A semantic diagram DSL rendered natively was designed and rejected: accurate, boring, and not what the pictures are for. See docs/design.md §7.
6. **Chapter generation is async**: request → outline/chapter generated → rendered → then read (possibly offline). Fits e-ink usage patterns; no need for streaming UI.

## Critical context: the user is NEW to Kotlin

Aliaksei's main language is PHP; Python is a language he likes but rarely uses; some frontend — **zero Kotlin experience**. Implications for every session:

- You write the Kotlin; explain non-obvious idioms briefly as you go (coroutines, sealed classes, null-safety, Compose recomposition). When an analogy helps, map from PHP (or web dev generally), not Python.
- Prefer boring, readable Kotlin over clever Kotlin.
- When a design choice involves a Kotlin/Android-ecosystem tradeoff the user can't be expected to evaluate yet, state your recommendation and the one-line reason; don't present unexplained option menus.
- Set up the project so `./gradlew` works from CLI — the user works in VS Code + Claude Code CLI, not necessarily Android Studio.

## MVP scope (build in this order)

1. **Project scaffold**: Android app + `:core` module, Gradle wrapper, min SDK ~26, target current. Runs on emulator.
2. **Course creation flow**: topic + daily minutes + prior-knowledge blurb → Gemini generates course outline (JSON: chapters, objectives, estimated time each).
3. **Chapter generation**: generate chapter N from outline + running course memory (see design.md — cross-chapter memory is the interesting problem). Output: structured Markdown with exercise blocks.
4. **Reading view**: paginated (page-turn taps, no scroll), renders chapter Markdown. This is the heart of the app — get typography right for e-ink.
5. **Selection-anchored chat**: select text → side panel/overlay chat with Gemini; prompt includes selection + chapter + course context. Q&A saved per-chapter.
6. **Progress tracking**: chapter done/in-progress, simple course dashboard.
7. Typed notes per chapter.

**Explicitly NOT in MVP**: handwriting/stylus (phase 2, via Onyx pen SDK — *since built, see
design.md §11*), other platforms (iPad was discussed and deliberately deferred; not a goal),
accounts/sync/backend (never, unless this becomes a product).

## Working agreements

- Gemini model choice: default to a Flash-tier model for generation (cheap, fast); make the model name a settings constant, not hardcoded everywhere.
- Never commit API keys; `.gitignore` from the first commit.
- Keep `docs/design.md` updated when architecture actually changes; keep this file updated when *decisions* change.
- Dogfooding is the success metric: "Aliaksei reads a generated chapter on the Boox in the evening" beats any feature. Bias toward getting the read-loop working end-to-end early, even ugly.

## Where things stand (2026-09-15)

The app is **built and in daily use on the Boox Go 7**. MVP items 1–7 all ship; several
post-MVP slices ship on top. Treat the list below as the current truth — the "nothing built
yet" note that used to live here was two months stale.

- [x] MVP 1–7: scaffold, course creation, chapter generation with cross-chapter memory,
      paginated reader, selection-anchored chat, progress tracking, notes.
- [x] **Phase 3 figures** — hand-drawn chapter illustrations, device-verified (see design.md §7).
- [x] **Badges** — a per-course mascot marking exercise / recap / answer / code blocks (§9).
- [x] **Chapter covers** — an opening picture of the problem each chapter solves (§10).
- [x] **Phase 2 handwriting** — the Onyx raw-ink path works: drawing with no lag, stroke
      persistence, tap-on-stroke chip, delete, page navigation (§11).
- [x] **Erase by sweep** — device-verified 2026-09-17, along with a long chain of panel
      fixes found while testing it: the Onyx driver keeps its own ink layer below Android's
      framebuffer, and EPD repaints had been aimed at the transparent ink SurfaceView rather
      than the view actually showing the page. Read `docs/onyx-pen-thread.md` §T7 before
      touching anything about repainting, ghosting, or tap handling — it is the session log
      and it records what was measured rather than assumed.
- [x] **Selection precision** — a pen mark now selects what it points at: whole words with
      clipped ends kept only if half-covered, an underline marking exactly one line (this was
      the "Ask AI returns the line below" defect), a circle marking the line it encloses, and
      strokes anchoring by their middle rather than by wherever the pen landed. A margin bar
      is a passage-marking gesture that reads across blocks, and asking leaves a `?` marker
      in the margin so a question is visible on the page.
- [x] **Reader signals (§13)** — `reader.md` per course records how each chapter was READ
      (questions asked and the passages they were asked about, passages marked without a
      question, notes written), and feeds forward into the next chapter's prompt under
      explicit bounds. Assembled from stored data, so no API call and covered by unit tests.
      Not yet surfaced in the UI: the file is inspectable at
      `<courses-root>/<slug>/reader.md` and is meant to be editable by hand.
- [x] **One rule for touch.** `app/.../ui/TapRouting.kt` owns what a contact means —
      `resolveTap` for taps, `resolveSwipe` for drags — with no Compose in it, so it
      unit-tests. The two DETECTORS still cannot merge (the SDK owns the host view's touch
      stream, so the overlay has to observe its own contacts), but neither decides anything.
      Anything a finger can hit that is not a page — a margin marker, a "Show answer"
      button — has to be hit-tested and passed IN as a rung, because on the pen path a
      Compose `clickable` under the overlay never runs.
- [x] **Side buttons are taken at the Activity's door** (`MainActivity.dispatchKeyEvent` +
      `ui/PageKeys.kt`), not by a Compose `onKeyEvent`. A Compose key handler only fires
      while its subtree holds focus, and on the pen path focus moves constantly — so the
      buttons kept reverting to raising Android's volume slider. Screens claim the keys
      with `PageKeys.register`; last registration wins, which is how the Ask panel takes
      them from the reader underneath it.
- [x] **Pen reaches the whole page (2026-09-21).** The ink overlay used to sit inside the
      reader's padded column, so margins were not drawable, a line's last word could not be
      underlined, and a stroke running off the surface turned a page. The inset is on the
      text now, not the page; stroke coordinates stay TEXT-relative so the on-disk format
      and existing ink are unchanged. See `docs/onyx-pen-thread.md` §T8.
- [x] **Repaint scoping is a QUEUE, not a rect (2026-09-21).** It failed merged (bounding
      box of two far-apart regions blinks half the page) and it failed as two slots (one
      writer displaces another, ink left on the glass). Every region gets its own
      invalidate. Do not try to express N regions in one field a third time. §T8.
- [x] **First line of a page not drawable — closed 2026-09-21, did not reproduce.** The
      symptom named the cause and the standing suspicion (anchor maths) was wrong: "takes a
      page" is a page turn, and the overlay used to resolve a finger contact on its
      ACTION_DOWN. Fixed as a side effect of making swipes work. Evidence and the remaining
      latent risk are in `docs/onyx-pen-thread.md` §T7.
- [x] **Delete, search, rewrite (2026-09-21)** — the three gaps that read as "unfinished"
      to anyone who is not Aliaksei, named on 2026-09-15 and built together. Deleting a
      course is the only destructive op in `:core` and refuses anything that is not a
      course directory directly under the root. Search runs over parsed BLOCKS, so a hit
      opens at the right page whatever the font size; it searches chapters, notes and the
      passages that were asked about. Rewriting a chapter archives the old body as
      `<slug>.previous.md` and its Q&A as `<slug>.previous.qa.md` — one generation deep,
      because a numbered chain promises a version history nothing can navigate — then
      starts the chapter over:
      annotations, bookmarks, ink, inline figures, the transcript and that chapter's
      reader.md section all go. Only the cover and closing pictures stay, because they
      come from the outline. A first attempt at keeping "records" while dropping
      "pointers" was wrong — the transcript quotes passages, and the question history is
      split across two files, so keeping one left half a record behind. See design.md §1.
- [x] **Questions have their own section in the Contents (2026-09-21)** — they used to
      render under "Notes" with the write-glyph and an empty body, because an ask carries
      no note text. The split is about volume, not taxonomy: a question is one gesture and
      a note is typed, so merged lists drown the scarcer thing.
- [x] **Orphaned bookmarks (2026-09-21)** — a bookmark was SET as a page's first-beginning
      block and RESOLVED by containment, and those two relations part company as soon as
      the margin or font changes. It opened its page and showed no ribbon there, offering
      "Bookmark this page" for a page that already was. `bookmarkOnPage` now asks the
      question the same way the jump asks it. Stored indices are never rewritten to heal —
      silently editing a saved position on a font change is worse than the bug.
- [ ] **Ink does not follow a font-size change** — strokes are anchored to a block, text
      reflows, the ink does not. Known and accepted, not a bug to fix on sight: marks that
      carry meaning are consumed into an underline + margin glyph, which DO reflow, so what
      drifts is standalone drawing. Cause and the shape of a fix are in design.md §11.

**Device tip that paid for itself:** `adb exec-out screencap -p` is a differential test on
the Boox, not just a screenshot. It captures Android's framebuffer, which the pen driver
bypasses — so an artefact visible in the capture is real app state, and one visible on the
glass but absent from the capture is driver ink or EPD residue. That distinction resolved
several "ghost" reports in minutes.

**Generation settings are per COURSE, not per device** — `language` and `units` both live in
`course.json`, chosen at creation and fixed for the life of the book. A course whose chapter 1
measures in Celsius and whose chapter 6 measures in Fahrenheit is worse than either. Left
unsaid, the model writes US customary units, because most of its training text does.

See `docs/design.md` for data model, file formats, and the generation strategy.
