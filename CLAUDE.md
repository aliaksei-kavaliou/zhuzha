# Zhuzha

AI-generated study books for e-ink readers. Personal project by Aliaksei Kavaliou (info@zhuzha.app), GPL-3.0. This file is the handoff context for Claude Code sessions — read it fully before doing anything.

## The idea (elevator pitch)

Chat AI is a bad container for learning: answers scroll away, there's no pacing, no structure, no way to go back and forth. Zhuzha inverts this — the AI **compiles a book**, and the chat lives *around* the book instead of *being* the product.

The user says "I want to learn X, I have N minutes/day." The app (via Gemini API) generates a course outline, then chapters one at a time, in a pedagogical style chosen at course-creation time — either a built-in preset or one described in the user's own words. Presets ship in `:core` as named system-prompt templates:

- **headfirst-inspired** (default) — conversational, redundant on purpose, exercises every few pages, concrete-before-abstract. Inspired by the Head First pedagogy (do NOT use O'Reilly's "Head First" branding or trade dress; that's their IP).
- **textbook** — structured, formal, definition → example → practice.
- **practical** — minimal theory, code/hands-on heavy, project-oriented.
- **custom** — the user describes it ("told as a fairy tale"), and the sentence is expanded into a full instruction once, at creation, then stored in `course.json` and editable by hand. See design.md §3.

The chosen style key is stored in `course.json` and drives prompt selection for all generation calls. The user reads paginated chapters, selects text to ask the AI about it, takes notes (typed first, stylus handwriting later), and tracks progress chapter by chapter.

Name: **Zhuzha**, Aliaksei's dog — black with a white chest, which is the whole palette an e-ink screen has, and the launcher icon is her head. It means nothing else on purpose; nine rounds of naming proved every word that MEANS something is already somebody's app. Renamed from InkFirst on 2026-10-12 (a real iOS app owns that name); `applicationId` is `app.zhuzha`, domain `zhuzha.app`. Dated entries below predate the rename and still say InkFirst — they are a record of what happened, not current naming.

## Target device & constraints

- Primary device: **Onyx Boox Go 7 (black, 2025 gen)** — 7" e-ink, Android-based, stylus support, sideloaded APK.
- E-ink rules: **no animations, no scrolling** in reading UI. Paginated everything. High contrast, no gradients, generous tap targets. This is a feature, not a limitation — the book-page metaphor is the whole point.
- Standard Android app; Boox runs Android (Go 7: **Android 13, API 33** — read off the device 2026-10-02; this said Android 12 and was wrong). Develop/test on emulator or any Android phone, verify on device via sideload.
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

## Code conventions

- Prefer boring, readable Kotlin over clever Kotlin. This code is read far more often
  than it is written, usually months later and usually while something is broken.
- Explain a non-obvious idiom where it appears — coroutines, sealed classes,
  null-safety, Compose recomposition — rather than assuming the next reader has the
  same Kotlin in their head you do.
- When a design choice turns on a Kotlin or Android-ecosystem tradeoff, state the
  recommendation and the one-line reason. An unexplained menu of options is not a
  decision.
- `./gradlew` must work from the command line. This is developed in an editor and a
  terminal, not necessarily Android Studio; anything that only works from the IDE is
  broken.

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

## Where things stand (2026-10-21)

The app is **built and in daily use on the Boox Go 7**. MVP items 1–7 all ship, and a long
run of post-MVP slices ship on top: figures, badges, chapter covers, Onyx handwriting with
erase, selection precision, reader signals, search, rewrite, backup/restore, themed palette,
figure budgets, described styles, EPUB export, request screening, in-app reporting, and a
reader typography pass that occupied most of October.

**The closed record moved to `docs/worklog.md` on 2026-10-20** — 95 entries, verbatim, with
the measurements kept. It had grown to ~146 KB of this ~153 KB file, and this file is loaded
in full at the start of every session. Go there when a fixed defect comes back, when a rule
below looks arbitrary and you want the case that produced it, or before re-litigating a
decision: a good number of those entries record an approach that was tried, measured and
REJECTED, and the reason is usually the valuable half.

What stays here is what still binds: the rules below, and the open items after them.

## Standing rules

Each of these was paid for by a defect. The story is in `docs/worklog.md` under the date
given; what follows is only the rule.

**Pagination — the single richest source of regressions in this project.**

- **NOTHING A READER DOES MAY REPAGINATE.** Pagination may depend on the chapter, the font,
  the page size and the reveal state, and on nothing the reader is doing right now. A reader
  drawing, selecting or marking must never move the page under their own hand. (2026-10-07)
- **Anything the paginator measures must be drawn from a style the paginator can reproduce
  exactly — pass the whole `TextStyle`, never the parts.** `Text(fontSize = …)` MERGES onto
  the ambient `LocalTextStyle` and keeps what it carries, Material's `letterSpacing = 0.5sp`
  included; `Text(style = …)` replaces it. Half a point per character is ~15px a line.
  (2026-10-06)
- **An estimate that computes independently of its renderer is this file's recurring bug.**
  Any value that affects height is defined ONCE and honoured by both — `FENCE_SPACE_ROWS`,
  `FENCE_PLAIN_ANSWER`, `FLOW_INDENT_DP`, the answer-space line count. A piece's explicit row
  count outranks `showsReveal` in the estimate AND the renderer. (2026-09-29, 2026-10-06)
- **The ladder of courtesies.** When a block will not fit, what yields is what the reader
  loses least by: working room first (to `MIN_WORKING_ROWS`), then the answer's mascot; the
  task and the answer never yield. A room the MODEL asked for is never taken by the polite
  rung, only by the last resort. The ladder runs ABOVE the move/split decision. When even it
  fails, the oversize branch places the RELAXED box, not the original — least-clipped beats
  most-clipped. (2026-10-01, 2026-10-06)
- **A requested room is all-or-nothing** — it fits beside its task or takes the next page
  whole. You cannot draw across a page break, so half a grid is worth nothing. (2026-10-07)
- **`MIN_SPLIT_LINES = 1`** — one line carries; paper books do this, and the hole costs more
  than the widow on a single column with no facing page. A heading keeps the stricter
  `MIN_LINES_UNDER_HEADING = 2`. (2026-10-07)
- **Prose splits; a figure and a code fence move whole.** A flow block (exercise, recap) has
  no frame, label or padding to pay again, so it splits freely and must not claim a fresh
  page. Cuts land on a sentence boundary, with the backup budget measured off the layout, and
  closers `"'’”)]»` may stand between the stop and the space. (2026-09-30, 2026-10-06)
- **The answer is its own page piece**, carrying the source block's index, and it does not
  report bounds to the ink overlay — two pieces of one block reporting one top is how a
  reader gets two copies of one drawing. (2026-10-07)
- **A slice ends where its own box ends.** Ink ownership and hit-testing use the renderer's
  `blockVisibleBottom`, never the next block's top. (2026-10-07)
- **A zero step is an infinite grid.** Guard any loop whose bound comes out of a division —
  `Float / 0` is `Infinity` and `Infinity.toInt()` is `Int.MAX_VALUE`. (2026-10-06)

**E-ink.**

- **E-ink has no grey, and it bites twice.** Greys dither and can vanish; worse, a grey FILL
  forces the controller to escalate a page turn to a full greyscale refresh, because fast
  update modes are 1-bit — a visible flash on every turn. The app has no greys. `Wash` was
  built, shipped for one evening and removed. (2026-09-30, 2026-10-07)
- **A dp-defined hairline or dot is not verified until it has been seen on the panel.** At
  density 160 a sub-pixel radius is not a weaker test of the same thing, it is a test of
  something else. (2026-09-30)
- **`adb exec-out screencap -p` is a differential test on the Boox, not just a screenshot.**
  It captures Android's framebuffer, which the pen driver bypasses — so an artefact visible
  in the capture is real app state, and one visible on the glass but absent from the capture
  is driver ink or EPD residue. That distinction has resolved several "ghost" reports in
  minutes.
- No animations, no scrolling in the reading UI. Paginated everything.

**Pen and touch.** Read `docs/onyx-pen-thread.md` §T7–§T10 before changing `OnyxInkOverlay.kt`.

- **Repaint scoping is a QUEUE, not a rect.** Every region gets its own invalidate. It failed
  merged (a bounding box blinks half the page) and it failed as two slots (one writer
  displaces another). Do not try to express N regions in one field a third time. (2026-09-21)
- **`lockCanvas(dirty)` WRITES BACK through its argument, and the queue holds that same
  `Rect`.** The Surface expands it to what it will actually hand over, and when the surface
  has not been drawn to yet it ignores the request and returns the whole thing — so the pill
  a chip queued came back grown to the full overlay and the GC after it repainted the page.
  Measured: queued `Rect(452,1328-812,1458)`, repainted `Rect(0,0-1264,1497)`. Visible only
  on the FIRST clear after the surface is created, which is why showing a chip blinked while
  closing the same chip did not, and why this has come back more than once wearing a
  different face. **Lock against a copy; never hand a mutable rect to a framework call and
  then read it again.** The flash is the SCOPE. (2026-10-27)
- **`setLimitRect`'s second argument is a list of HOLES** — floating UI must be excluded or
  the pen inks on it. Never hand it an EMPTY array: the firmware appears to read zero-length
  as "nothing to do" rather than "clear what you have". (2026-10-05)
- **Logging that we ASKED is not evidence it happened.** `holes=[]` printed faithfully and
  truthfully every time while the request was dropped a layer below anything we can log.
- **One rule for touch.** `ui/TapRouting.kt` decides what a contact MEANS — `resolveTap`,
  `resolveSwipe` — with no Compose in it. The two detectors cannot merge (the SDK owns the
  host view's touch stream) but neither decides. Anything a finger can hit that is not a page
  must be hit-tested and passed IN as a rung: on the pen path a Compose `clickable` under the
  overlay never runs. A detector drops any gesture a child consumed — checked as the deltas
  arrive, since a scrolling child has nothing left to consume on release.
- **Side buttons are taken at the Activity's door** (`MainActivity.dispatchKeyEvent` +
  `ui/PageKeys.kt`). A Compose key handler only fires while its subtree holds focus, and on
  the pen path focus moves constantly. Screens claim the keys with `PageKeys.register`; last
  registration wins.
- **Both overlays take `drawingEnabled`** and stand down for a menu, dialog or panel. An
  overlay that can claim the pointer stream must also be able to let it go, or the only
  dismiss target on the screen stops working. **Taps navigate, drags draw.** (2026-10-20)
- A mark whose vertical CENTRE is in an exercise's working room is the reader answering, not
  selecting. Decided at the dispatch site, geometrically, whatever shape the stroke had.
  (2026-10-02)

**Generation and prompts.**

- **Any call that JUDGES must pin `temperature = 0`.** Left at the default the same input
  came back allowed on one run and refused on another. (`JUDGING_TEMPERATURE = 0.0`)
- **A refuse criterion must be a PRINCIPLE followed by examples, never an enumeration.** A
  request can be more obviously harmful and LESS specific at once, and a list inverts exactly
  that case. The same shape bit the `space="none"` criterion: a rule that silently asks two
  questions gets whichever answer the model likes.
- **Three gates — request, outline, chapter — and all of them FAIL OPEN.** A gate that blocks
  on a dropped connection gets resented and routed around. The line is operational
  instructions, never subjects; it is not an age rating, a taste filter or a legality check.
- **Fixed twice: the prompt asks and the code enforces.** The figure ceiling, the chapter-
  number strip, the slug. A prompt is a request, not a guarantee.
- **A picture must not invent data.** No number, label, price, date or measurement in a brief
  unless that exact value is in the prose beside it; ask for the SHAPE of a quantity instead.
- **Arithmetic is written out as an operation** ("400 − 150 = 250", never a bare result) and
  `ArithmeticCheck` reads every one. It only ever REPORTS — a study book prints wrong
  arithmetic on purpose, and no rule here can tell that from a slip.
- **Generation settings are per COURSE, not per device.** `language` and `units` live in
  `course.json`, chosen at creation and fixed for the life of the book. Left unsaid, the model
  writes US customary units.
- **Every row that spends names its price**, and an estimate says "~".

**Localisation.**

- **The line is what gets TYPESET, not what is a control.** 17 book-chrome strings follow the
  BOOK (block labels, byline, the words the EPUB prints); everything else is
  `res/values/strings.xml` and follows the DEVICE. The shelf is the shelf and the book is the
  book — that is what Kindle and Books do. (2026-10-02)
- **The UI is English only, and that is a decision, not an omission.** Another language is one
  `values-xx` folder. `BookChromeLiteralsTest` guards both halves.
- A counted string must not inflect AND must join cleanly — write `×{n}`.
- `keepUsable` REPAIRS homoglyphs rather than rejecting, and runs on READ: `ensureLabels` only
  asks for MISSING strings, so a label already on disk is never re-examined.

**Files and data.**

- **One rule, one place.** `kebabOf` and `Block.plainText()` both exist because the same rule
  had drifted into copies and would otherwise be fixed twice again.
- **A slug is the folder name, the backup filename and the name inside the EPUB.** Letters,
  digits and MARKS — a Devanagari or Thai vowel sign is part of the word.
- **Backup: a book is one zip, judged by what is INSIDE it, and read back past a CRC** before
  the run says it worked. A timestamp cannot see a run interrupted mid-book. The loose-copy
  sweep is the ONLY place the app deletes at the far end: it re-reads the folder rather than
  trusting the screen, and deletes a group only when the archive holds every path in it.
- **Stored indices are never rewritten to heal.** Silently editing a saved position on a font
  change is worse than the bug.
- Deleting a course is the only destructive op in `:core`, and it refuses anything that is not
  a course directory directly under the root.

**EPUB export.** (`docs/design.md` §18)

- **No side border, ever, and a test asserts it.** When a reader fragments a box, a LEFT or
  RIGHT border is painted on every fragment while a TOP or BOTTOM one is painted only at the
  real edges. Every block is a label band plus an `<hr class="block-end"/>`. (2026-10-16)
- **A fix that depends on the reader honouring CSS is not a fix.** A reader rendering in its
  own typeface need not apply the publisher's stylesheet at all — which is why the answers are
  their own spine document rather than a CSS page break, and why the closing rule is markup.
- **Every anchor carries its chapter** (`c2-ans1`): the exercise counter restarts per chapter,
  and a reader that flattens the spine would send every "show answer" to chapter one.
- **An anchor exists only where something points at it, and it sits on a `<p>`.** An
  unanswered exercise takes no id: a reader was pairing the Nth link into a document with the
  Nth anchor IN it, which is right by coincidence until one exercise has no answer. `<aside>`
  is an HTML5 element an older engine may skip, id and all. (2026-10-21)
- **The cover is a DOCUMENT, not a declaration.** `cover-image` in the OPF is the shelf
  thumbnail and buys nothing else; `cover.xhtml` is what a reader opens. The book's name is
  printed ABOVE the picture, because a reader that does not scale the illustration pushes
  everything under it to page two. (2026-10-21)
- The four that break a file, each a test: mimetype first and STORED; XHTML is XML so `&` must
  be escaped; `xml:lang` needs a CODE; the extension must agree with the picker's mime type.
- **A figure's `alt=` is the illustrator's BRIEF** — it belongs in the attribute, never as a
  visible caption. (An UNDRAWN slot still prints its brief: a silent hole is worse.)

**Method.**

- **Measure before deciding, and verify the claim where it can actually be seen.** The
  emulator cannot judge greys, sub-pixel geometry, or anything the pen driver draws; the panel
  cannot be reached from a unit test. Say what was PROVED and what was inferred.
- **An app that spins is a stack trace, not a bisect.** `adb shell kill -3 <pid>` dumps
  `/data/anr/` and the main thread's frames name the culprit in one step.
- **A rule argued from a cost should be re-read when the cost goes.** The frame came off the
  blocks at noon one day, and three of that afternoon's fixes were rules written to pay for it.
- **When a fix wants to ADD something, check whether the right move is taking a decision
  away.** Twice in one sitting the answer was "do not split this" and "ask the thing that
  knows", not a new height or a new bound.

## Open items

- [ ] **Chip ghost: diagnosed, the first cure REVERTED (2026-10-05)** — Aliaksei, after a
      weekend of reading: "now you block the whole space where it can appear... but when there
      is no chip I still can't draw. Only some lines first appeared but on page back and force
      the image appeare. I would say current decision worth than bug we are trying to fix."
      He is right and it is backed out. A dead band of every page beats a ghost on a pill.
      **The diagnosis still stands.** The pen DOES ink the chip: raw drawing is a hardware
      path, so the driver marks wherever the nib lands inside the limit rect, below Android's
      framebuffer and regardless of what Compose drew on top — which is why only the stylus
      ever did it and why it was random. `setLimitRect`'s second argument is the exclude list
      and cutting the pill out of it was the right shape of fix.
      **What broke is the RELEASE, not the cut.** The chip is `BottomCenter`, so it occupies
      nearly the same rectangle every time and one fixed band stopped taking ink for the rest
      of the session. The symptom pair names the layer: nothing inks live, but the stroke is
      there after a page turn — so the points arrived, the stroke saved, and only the driver's
      live ink was missing. Decompiling the SDK (`javap` on the cached aars) shows the two
      halves parting company on empty: `safeAddAll(list, holes, true)` clears the SDK's own
      in-process list correctly, while the firmware is handed `new int[0]`, and a zero-length
      region array is almost certainly read as "nothing to do" rather than "clear what you
      have".
      **The lesson generalises past this bug: logging that we ASKED is not evidence it
      happened.** `limitRect(chipClose) holes=[]` printed faithfully every time and was true;
      the request was dropped a layer below anything we can log.
      Two candidates for the next attempt, in order, written up with the call chain in
      `docs/onyx-pen-thread.md` §T10: (1) never send an empty array — hand back one degenerate
      `Rect(0,0,1,1)` so the firmware always gets a list that REPLACES the last; (2) pause the
      driver's rendering while the chip is up (`setRawDrawingRenderEnabled` → `PEN_PAUSE` /
      `PEN_DRAWING`), a symmetric pair that is told a state in both directions. Neither is
      visible to `screencap`; neither ships without eyes on the glass, and not in the same
      build as the other. **(2) is built, on the branch `pen-chip-render-pause`, waiting for
      the device.**
      **Still latent from §T9:** every `scheduleUnlock` cancels the last, so a pen event inside
      the 200ms leaves a repaint armed but unperformed. Real, not this symptom.
- [ ] **Ink does not follow a font-size change** — strokes are anchored to a block, text
      reflows, the ink does not. Known and accepted, not a bug to fix on sight: marks that
      carry meaning are consumed into an underline + margin glyph, which DO reflow, so what
      drifts is standalone drawing. Cause and the shape of a fix are in design.md §11.

## Where the rest is written down

- `docs/design.md` — data model, file formats, the generation strategy, and the numbered
  sections the rules above cite (§3 styles, §7 figures, §10 covers, §11 ink, §15 backup,
  §18 export, §19 screening).
- `docs/worklog.md` — the 95 closed entries, verbatim, with their measurements.
- `docs/onyx-pen-thread.md` — the pen session log. §T7–§T10 are load-bearing; read before
  touching repainting, ghosting or tap handling.
- `docs/play/` — Play Console material, including `ai-content-policy.md`.

Keep `docs/design.md` current when architecture changes, and this file current when
*decisions* change. A finished piece of work goes to `docs/worklog.md`; it only earns a line
up here if it leaves behind a rule that binds the next change.
