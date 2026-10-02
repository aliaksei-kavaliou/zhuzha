# InkFirst

AI-generated study books for e-ink readers. Personal project by Aliaksei (aliaksei.kavaliou@kiloverse.com), may be open-sourced later. This file is the handoff context for Claude Code sessions — read it fully before doing anything.

## The idea (elevator pitch)

Chat AI is a bad container for learning: answers scroll away, there's no pacing, no structure, no way to go back and forth. InkFirst inverts this — the AI **compiles a book**, and the chat lives *around* the book instead of *being* the product.

The user says "I want to learn X, I have N minutes/day." The app (via Gemini API) generates a course outline, then chapters one at a time, in a pedagogical style chosen at course-creation time — either a built-in preset or one described in the user's own words. Presets ship in `:core` as named system-prompt templates:

- **headfirst-inspired** (default) — conversational, redundant on purpose, exercises every few pages, concrete-before-abstract. Inspired by the Head First pedagogy (do NOT use O'Reilly's "Head First" branding or trade dress; that's their IP).
- **textbook** — structured, formal, definition → example → practice.
- **practical** — minimal theory, code/hands-on heavy, project-oriented.
- **custom** — the user describes it ("told as a fairy tale"), and the sentence is expanded into a full instruction once, at creation, then stored in `course.json` and editable by hand. See design.md §3.

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
- [x] **Backup and restore (2026-09-22)** — `CourseBackup` in `:core` had been sitting with
      no UI; it has one now. A folder chosen through Android's document picker, remembered
      with a persistable grant, "Back up everything" in one button, restore per book behind a
      confirm. The trap worth knowing: `createDocument` treats the mime type as authoritative
      and the filename as a suggestion, so a name whose extension disagrees with the type
      comes back with the provider's extension appended — and filenames ARE the app's index.
      Derive the type from the extension. See design.md §15 — and §15.1, which replaced the
      file-by-file copy with one zip per book. **Proved end to end on the
      emulator, no device needed**: 23 files copied byte-identical, a second run copied
      nothing, a chapter deleted from the device came back with its original md5 and opened.
- [x] **One palette, in one place (2026-09-22)** — the app had no theme: `MaterialTheme { }`
      with no scheme, so Library, Settings, New course and Backup rendered in M3's purple,
      and the five colours the book screens use were declared privately in seven files.
      `ui/InkTheme.kt` owns both now. The purple reached further than the obvious buttons —
      the text-field focus ring, the `Switch`, and the text-selection handles in the READER,
      which is how a passage gets asked about. Watch `surfaceTint`: it defaults to `primary`,
      so a black primary greys every elevated surface unless it is pointed at white. See
      design.md §16.
- [x] **Pictures have a budget (2026-09-22)** — there was no limit on them at all: the
      illustrator drew every slot the model wrote, and a chapter that asked for twelve
      pictures spent ~36¢ without saying so. `FigurePolicy` is now a per-course setting in
      `course.json` (as needed / generous / on request / none), the ceiling is ENFORCED in
      `illustrate` rather than requested in the prompt, and every row that spends names its
      price. The overflow costs nothing: an undrawn slot renders its alt text and the menu
      still offers to draw it. Same commit teaches the chapter model what this panel can
      carry — shape and arrangement, never colour or browning — because nothing did, and
      "the finished risotto, creamy and golden" is a brief no illustrator can fill here.
      design.md §7.
- [x] **A style you describe rather than pick (2026-09-24)** — the fourth choice is a
      sentence, expanded into a system instruction once and stored beside the wish that
      produced it, so the two can be compared. Three fields because three consumers need
      three things: the wish (never sent, kept so the expansion is reviewable), the prompt,
      and a mascot line, since badges branch on the style KEY and a described style matches
      no arm. The expansion runs before the outline so chapter titles land in the style too,
      and once per WISH rather than per attempt — re-expanding on "Try again" would mean
      approving an outline in one style while a differently-worded one got stored. Two rules
      in its prompt are load-bearing: it must not touch language, chapter length, Markdown or
      pictures (it lands in the SYSTEM channel and would outrank them), and a named series
      becomes its pedagogy, never its name — same trademark handling as Head First, applied
      at the one place a user-typed brand name could reach an illustrator. It THROWS where
      `LabelGenerator` swallows, and `StylePresets.CUSTOM` is deliberately absent from `ALL`
      so `byKey` can never hand back a preset with an empty system prompt. design.md §3.

- [x] **Export (2026-09-24)** — a book leaves the app as an EPUB, from the Contents screen.
      EPUB rather than PDF because it reflows and is already the shape of what is stored; a PDF
      means owning pagination against a bare canvas, and the reader's paginator does not
      transfer (it paginates to a SCREEN). Answers move to the back of each chapter, numbered
      and linked both ways, because a reflowable book has no "next page" to hide one on.
      Annotations are opt-in and default OFF: export is irreversible, and a file handed to
      someone carries whatever is in it. Ink never travels at all — strokes are anchored to
      block indices — and the screen says so rather than letting it be discovered. The four
      things that bite (mimetype first + STORED, XHTML is XML so `&` must be escaped, xml:lang
      needs a CODE not a language name, extension must agree with the picker's mime type) are
      each a test. Verified by exporting every real book off the emulator and validating the
      files, then again end to end through the picker. design.md §18.
- [x] **Labels are topped up, not skipped (2026-09-24)** — `ensureLabels` asked "has this
      language been translated?", so every string added to `BOOK_CHROME_STRINGS` after a
      language's first translation stayed English in that book forever, silently. It now asks
      which strings are MISSING and translates only those; anything that comes back unusable is
      stored as itself, because `keepUsable`'s rejections are deterministic and retrying costs a
      call per app open. Found while checking a Russian EPUB.

- [x] **Requests are screened before anything is written (2026-09-24)** — one cheap call at
      course creation, ahead of the outline. It IS the protection, not just a nicer failure
      mode: the first write-up claimed the chapter model refuses the genuinely harmful anyway,
      and Aliaksei disproved that the same day — walking past the gate produced chapters with
      real instructions. It also buys the failure mode (a sentence when you typed it, rather
      than a broken chapter three chapters and several pictures in).
      The line is OPERATIONAL INSTRUCTIONS, never subjects, and the prompt names the legitimate
      ones (toxicology, military history, lock-picking, offensive security, harm reduction…)
      because a false refusal is the failure nothing downstream catches. It is explicitly not
      an age rating, a taste filter, a quality check or a legality check. Three verdicts:
      refuse stops on the form, ask gets a page with "Write it anyway", allow is everything
      else. It FAILS OPEN on any error — a gate that blocks on a dropped connection gets
      resented and routed around. **The refuse criterion must stay a PRINCIPLE followed by
      examples**: written first as a bare list of five categories, the model read the list as
      the definition, and "how to kill people quickly" matched none of them (no named victim,
      no named method) so it came back as a walk-past `ask`. A request can be more obviously
      harmful and LESS specific at once, and an enumeration inverts exactly that case. Probed
      live after the rewrite: "how to kill people quickly" and an untabled "undetectable ways
      to hurt someone" both refuse; explosives chemistry, SQL injection, car-door opening and
      self-defence all still allow. **The verdict was also a COIN TOSS** until 2026-09-24:
      nothing set a temperature anywhere, so the judging call ran at the model's default (~1.0)
      and "how to rubber the bank" was allowed on Aliaksei's run and refused on the identical
      input here. `JUDGING_TEMPERATURE = 0.0` now pins it (4/4 identical after). The same
      report widened the principle — robbery is a serious crime that is not primarily about
      injuring someone, so it sat outside "harm to people" as written — and added a SECOND
      GATE on the outline, which is the model's own reading of the request and far more
      concrete than the sentence that produced it. A THIRD GATE screens the generated
      chapter before it is saved — the only one that reads actual text — with a deliberately
      narrower question (the subject is already settled; has this text become a working
      procedure?), sitting ABOVE the rewrite teardown so a refusal leaves the book untouched.
      Verified both ways on device: a real explosives-chemistry chapter compiles and saves; a
      forced refusal shows its reason and leaves the chapter `pending` with nothing on disk.
      design.md §19.
- [x] **Export says "Done" rather than closing itself (2026-09-24)** — the result line is the
      only evidence the save happened, and popping on success is the "generate did nothing"
      defect in another costume. Exporting twice (clean copy to send, annotated copy to keep)
      is also a real errand worth one toggle rather than a re-navigation.

- [x] **`CompilePrompt` scrolls its front matter (2026-09-24)** — found while testing the
      chapter gate: it was a `Column(fillMaxSize())`, so on a 320x640 emulator a chapter with
      several objectives pushed "Compile chapter" off-screen and the chapter could not be
      written at all. I first filed it as the same class as the menu below and deferred it;
      that was wrong — `isPending` does `return@Column` before the ink overlay and TapRouting
      exist, so there was no gesture-path blocker, only a swipe detector on the same Column.
      Fixed the way `OutlineReviewPage` already solves this: content scrolls, action is pinned.
      The detector now ignores a drag a child consumed, or scrolling the objectives also turned
      the chapter. **Vertical-swipe navigation is gone from this screen** as a result — a
      vertical drag is the scroll now; edge taps, horizontal swipes and the side buttons all
      still navigate, and each was checked on device rather than assumed.

- [x] **A book is one file in the backup (2026-09-24)** — loose files were the shape §1
      argues for and the wrong one for the device: the Boox indexes shared storage, so 96
      backup files turned up in its Images and Documents libraries, one tidy-up away from
      the bin. Each book now travels as `<slug>.zip` (plus `_shelf.zip` for `last_read.json`
      and `labels/`, which belong to no book). The grain of a decision moved from a file to a
      BOOK — a zip cannot be written half-way — so an evening's reading repacks that book
      rather than copying a few kilobytes; books were already the unit of restore. Two rules
      pay for that: an archive is judged by what is INSIDE it rather than by its timestamp (a
      run interrupted mid-book leaves a zip NEWER than the book and holding half of it, which
      a stamp cannot see and a listing cannot miss), and every archive written is READ BACK
      past a CRC before the run says it worked. Both reuse `CourseBackup.plan`, so "up to
      date" and "arrived intact" are one rule asked twice. Restoring puts the archive's
      timestamps back, or the next backup would repack everything it just restored. Older
      loose backups still restore, and the screen says which folders are now redundant —
      nothing at the far end is ever deleted by the app. Device-verified on the real shelf.
      design.md §15.1.
- [x] **The old loose copies can be swept (2026-09-25)** — §15.1 named the redundant folders
      and left the reader to delete them with a file manager, on the device where that is
      worst, for a mess the app made. One row now does it. **It is the only place the app
      deletes at the far end**, so the fence is the feature: it RE-READS the folder rather
      than trusting the screen's listing, opens each archive `deep` so every byte goes past a
      CRC before the other copy stops existing, and deletes a group only when the archive
      holds every path in it. The comparison is BY PATH and deliberately not `plan` — every
      other check in the backup asks "is the destination as new as the source", which here
      refuses every real case, because the archive is the newer copy while the loose files
      carry the moment they were COPIED. Root by root rather than group by group, so a stray
      file the reader put in the folder is kept and named without stranding `last_read.json`
      beside it. A loose folder with no archive is still never offered — it is a book that
      has left the device. Device-verified both ways: refused a book for one orphan chapter
      and named it, then swept clean once that file went; all four archives byte-identical
      and CRC-clean after. design.md §15.2.
- [x] **A slug keeps words in any script (2026-09-25)** — `courseSlug` and `chapterSlug` both
      kebab-cased with `[^a-z0-9]+`, which deletes every character outside ASCII. A title with
      no Latin in it reduced to the empty string and took the fallback, so **every Russian book
      was called `course`** (then `course-2`) and its chapters came out as `01-chapter`,
      `03-c`, `04-am`, `06-f` — the index, plus whichever chord names happened to be spelled in
      Latin. The slug is the folder name, the backup's filename and the name inside an exported
      EPUB, so a reader whose language is not written in Latin met this on their first book.
      Found from Aliaksei asking why the backup folder's names were confusing: the backup was
      reporting the defect, not causing it. Both now share ONE `kebabOf`, because the rule had
      already drifted into two copies and would otherwise be fixed twice again; the class is
      letters + digits + MARKS, since a Devanagari or Thai vowel sign is part of the word
      rather than punctuation. ASCII titles are untouched, so nothing existing moved.
      `chapterSlug` is called only by `OutlineGenerator` at the moment an outline is first
      written, so no existing chapter is renamed — a rewrite reuses the slug from
      `course.json`. **Device-verified rather than assumed**: per the ICU lesson, the pattern
      was run on the Boox under `app_process` from a dex, not just in a JVM test, and gave
      identical results. The one book that existed was migrated by hand (directory + the
      `slug` field + a repack); its old `course.zip` was checked file-by-file against the new
      archive before being removed. **Existing Russian chapter slugs were NOT migrated** —
      figures, ink, notes and bookmarks are all addressed by chapter slug, so that is a real
      data migration and not a rename.
- [x] **An exported answer can be found (2026-09-25)** — reported from PocketBook: "show
      answer" on page 11 jumped to page 13 showing a DIFFERENT exercise. The anchors were all
      CORRECT, and the export was still wrong — the answers section began two paragraphs after
      the last exercise, so the landing page's visible top was that exercise's question. The
      link worked; the landing was unreadable. **Fixed twice**: first with a CSS page break,
      which Aliaksei reported still jumped two pages — a reader rendering in its own typeface
      (what happens once you change the font size) need not apply the publisher's stylesheet
      at all. **A fix that depends on the reader honouring CSS is not a fix.** The answers are
      now their OWN DOCUMENT in the spine (`ch02-answers.xhtml`), which is a page boundary
      nothing can ignore. An answer is just its number and the answer — reprinting the
      question above it was tried and read as bloat, correctly, since the separate document
      had already solved what the reprint was for. Every anchor carries its chapter
      (`c2-ans1`)
      because the exercise counter restarts per chapter and a reader that flattens the spine
      would send every "show answer" in the book to chapter one — latent, not the reported
      bug. Recap and exercise blocks now print their own name too: they were distinguished
      only by `border-left-style: double` against `solid`, which most readers render alike.
      Font size is deliberately still unset — that belongs to the reader. design.md §18.1.
- [x] **A letter that is not the letter it looks like (2026-09-25)** — the Russian answers
      heading was stored as `Οтветы`, first letter GREEK OMICRON. Invisible on the page, which
      is how it survived; it breaks in-book search and text-to-speech. `keepUsable` now
      REPAIRS rather than rejects — rejecting falls back to English, which is visibly worse
      than a word that looks right — translating minority letters into whichever of
      Latin/Greek/Cyrillic the rest of the word uses, and refusing only a word with no
      majority. Those three scripts only: Japanese and Chinese mix scripts inside a word
      normally. **It also runs on READ**, because `ensureLabels` only asks for MISSING strings,
      so a label already on disk is never re-examined and would stay broken for ever.
      design.md §18.2.
- [x] **Pictures: what is in front hides what is behind (2026-09-25)** — the most visible
      defect in a `show` figure, named by Aliaksei. A right-hand strumming figure had the
      ukulele's strings drawn straight ACROSS the thumb, so the hand read as sunk inside the
      instrument. Added to the shared house style as a rule rather than a dial, with the
      failure named. The same picture also ignored the brief's "remaining fingers in a relaxed
      fist" and drew an open hand, so `SHOW_STYLE` gained a second rule: the pose in the brief
      is the subject, not a suggestion. **Verified on real output the same day**: the ukulele
      strumming figure redrawn with the new rules came back with the strings correctly hidden
      behind the hand, and with the brief's relaxed fist actually drawn — one finger extended
      to the string, the rest curled. Both rules earned their place.
      **Two gaps found while running that test.** The first is now FIXED (see below). The
      second stands: "drawn" is judged from the markdown's `src=` attribute while RENDERING is
      judged from the file, so deleting a figure file leaves the menu insisting everything is
      drawn while the page shows alt text.
- [x] **A figure can be redrawn (2026-09-25)** — `Иллюстрации` reported "all N drawn" and did
      nothing, so a picture that came back wrong was permanent short of rewriting the chapter,
      which deliberately KEEPS its pictures. Opening and Closing got a redraw on 2026-09-23 and
      the figures did not; nobody noticed until the house style improved and there was no way
      to apply it. The row now also offers `Перерисовать`, opening a PICKER — a chapter has
      several figures and they are told apart by their briefs, so a yes/no confirm is the wrong
      shape. `ChapterIllustrator.illustrate` takes `only: Set<Int>?`, which selects by ordinal
      regardless of `isPending` and ignores the per-chapter ceiling: both of those govern what
      the app spends on its own initiative, and this is a row the reader tapped with the price
      on it. The picker lives in a DIALOG rather than in the menu sheet, which is what lets it
      scroll — a dialog is its own window and does not meet `TapRouting`'s CloseMenu the way
      the sheet does. `pictureVersion++` on completion, or the reader draws the old bytes from
      cache under the same file name. **Unit-tested; NOT device-verified — the Boox was
      unplugged before it could be driven.**
- [x] **The reader speaks the book's language too (2026-09-25)** — the block labels were
      hardcoded, so **every exercise in a Russian book said "TRY THIS"**, every recap "RECAP",
      every undrawn figure "FIGURE", and the reader's own "Show answer" button stayed English
      while the EPUB's said «Показать ответ». Three of those were never in
      `BOOK_CHROME_STRINGS` at all — the one piece of book chrome nobody thought of as chrome,
      because it is drawn as part of the page rather than as a control. Three more ("Ask about
      this", "Add note", "Cancel") WERE declared and simply never wired to `labels`, in nine
      places. Found by cross-referencing every `Text("…")` literal in `ReaderScreen` against
      the declared set — worth re-running after any UI work. `labels` now threads through
      `PagedContent` → `RenderBlock` → the block composables.
      **A counted string must also JOIN cleanly**: "figures: {n}" composed into
      "создать: рис.: 1", two labels stuck together, so counts are written "×{n}".
      And note the trigger: `ensureLabels` runs from the CONTENTS screen only, so a reader who
      always cold-starts into the reader never gets newly added strings translated.
- [x] **"Drawn" and "rendered" now ask the same question (2026-09-25)** — pending-ness was read
      from the Markdown's `src=` while RENDERING was read from the file, so deleting a picture
      left the menu insisting everything was drawn while the page showed alt text, with no way
      back. A slot is outstanding when it has no `src=` OR the file that `src=` names is
      missing, and the draw row hands those exact ordinals to `illustrate` via `only` — the row
      draws precisely what it counted. Device-verified by deleting a figure: the row changed
      from «всего 4» to «Создать: 1 · ~3¢».
- [x] **Which hand it is, is part of the instruction (2026-09-25)** — Aliaksei: the strumming
      figure shows a LEFT hand where the brief says «правая рука», and most people are
      right-handed. Seen from the back with the fingers pointing left, a right hand's thumb is
      on the LOWER side; that drawing has it upper. A mirrored hand teaches the posture
      backwards and on an instrument swaps the two hands' jobs. Added to `SHOW_STYLE` with the
      thumb check written out, plus "assume a right-handed person unless the brief says
      otherwise". **Not yet verified against output.**
- [x] **The cover draws the chapter's own opening (2026-09-28)** — reported from the
      neural-networks book: chapter 4 opened with a woman tasting cookies and adjusting the
      recipe, and one page later the chapter itself opened with a paper plane thrown, bent
      and thrown again. Both are good pictures of training a model; together they are two
      books. Structural rather than a bad generation — the cover brief was written from the
      title and objectives, so two models invented two metaphors for one idea, and a chapter
      model taught concrete-before-abstract will ALWAYS have invented one already. Nothing
      had to be generated to fix it, only PASSED: pictures are drawn after the prose is
      saved in every flow, so the chapter's opening was already on disk each time the cover
      was briefed. Two details are the fix rather than decoration — the old "invent a
      metaphor" paragraph is REPLACED when an opening is present rather than stacked under
      the new rule (a contradiction gets resolved however the model likes, which is the
      behaviour being fixed), and the chapter's own figure briefs ride along under "do not
      describe one of these again", because anchoring the cover to the opening parks it
      beside the chapter's first inline figure. The closing picture inherits it free, being
      a rewrite of the cover's brief — and the Closing row now says `no longer matches the
      opening` when the cover has been redrawn under it, read off file timestamps rather
      than stored. **Unit-tested and the built prompt read back in full; NOT verified
      against drawn output — that needs an API call on a real chapter.** design.md §10.1.
- [x] **A chapter title is its name, not its number (2026-09-29)** — a live outline came
      back with every chapter titled "Chapter 1: Letting Go of the Fear…", so the Contents
      read `1. Chapter 1: Letting Go…`. The rule existed in the CHAPTER prompt, where it
      stops the h1 repeating the number, and had never been said to the OUTLINE model — so
      whether a book got prefixed titles was luck, and the existing books happened to come
      back clean. Fixed twice, like the figure ceiling: the prompt asks, and
      `withoutChapterNumber` enforces in `withDerivedSlugs`, because an outline is written
      once and then names the folder, the header, the Contents and the EPUB for the life of
      the book. The strip is deliberately NARROW and tested — `1984`, `10 Ways to Draw a
      Cat` and `Chapter and Verse` all survive untouched, and a strip that would empty a
      title keeps the original instead.
- [x] **The Contents is a contents again (2026-09-29)** — Aliaksei: "TOC goes after search,
      progress and export, so on the emulator it is not even visible". He was right, and the
      screen's own doc contained the argument against itself: it calls itself the book's
      FRONT MATTER, and in a book the front matter is the title page and then the contents —
      the apparatus goes at the BACK. Chapters now follow the title immediately, then the
      reader's own notes, questions and bookmarks, then Search / reading record / Export
      last. The rule is: everything that points INTO the book first, everything you do TO it
      last. Search loses least by moving, since it is also in the reader's own menu under
      "Go to", which is where you want it mid-paragraph.
      **The bigger half is that it now opens at the chapter you are READING.** Every route
      into a book lands here — the Library row has no continue-reading path of its own — so
      resuming a book four chapters in cost three actions: turn a page, find the chapter,
      tap it. It jumps ONCE, not on every rebuild of the pages, or deleting a note would
      drag you back; and a book with nothing read has no current chapter, so it stays on the
      title page, which is the right opening for a book you have not started.
- [x] **A chapter entry is measured at the width it actually gets (2026-09-29)** — Aliaksei:
      "TOC has 6. but no title". The Contents paginator measured a chapter title against
      `contentWidth − 24dp`, but the renderer gives the title `weight(1f)` in a Row that also
      carries the ▸ marker, the "Day 3–4" label and the ✎ glyph, each with its own 8dp gap —
      so it measured against getting on for TWICE the width the title has. The estimate said
      two lines where the page drew five, the entry overflowed the budget, and the last one
      on a page was clipped to its number. Not caused by the reorder above, but exposed by
      it: moving the apparatus changed which entry lands at a page boundary, and the longest
      title shows it first. It measures the marker, the day label and the glyph for real
      now, at their WIDE forms even when the blank one is drawn — erring wide makes the
      title narrower, hence taller, hence more conservative, which is the safe direction for
      a budget whose only failure mode is clipping.
- [x] **A chapter opens on a title page (2026-09-29)** — Aliaksei: page 1 of chapter 2, the
      last line sliced in half by the footer. Instrumenting the paginator gave it in four
      lines: the byline's keep-with-next check asked "will the heading fit beside me?" (369
      of a 410 budget — yes) and placed itself on that promise, but the HEADING runs its own
      check when its turn comes and demands room for the paragraph too. That fails, it opens
      a new page, and the byline is left alone — so `mergeLonelyOpeners` glues the two back
      together AFTER layout, where there is no budget left to check against. The sting:
      **the title costs 306px at the top of a page and 346px once something sits above it**,
      because it regains its 40dp top spacer. The merge added 40px nobody measured. 452
      against 410. Three changes and they only work together: a cover-bearing heading is not
      a "lonely opener" (a title page is content — design.md §10); while a page holds
      nothing but openers the next block is FORCED onto it, so the byline is never stranded
      and everything after re-flows; and a forced paragraph gives up the lines that fit
      rather than being placed whole, which is what makes the force safe.
- [x] **A bordered block can be cut across a page (2026-09-29)** — Aliaksei: chapter 2's
      "try this" ran off the page and was not continued, "so it just corrupted". The
      paginator could split a PARAGRAPH but not a FENCE, so one taller than a whole page hit
      the oversize branch — placed anyway, clipped by the page — and the end of the task
      plus its "Show answer" button were unreachable. Real content loss, in the block a
      study book leans on most, on exactly the small screens the testers will use.
      `splitFence` handles exercise and recap only: a figure is a picture and cannot be cut,
      and an unknown kind might be anything. The answer travels with the final piece WHOLE,
      because it is reserved space even while hidden. Head and tail keep the same block
      index, the pattern `splitParagraph` already set, so annotations, bookmarks and ink stay
      anchored. **The `!atTop` guard is gone** — refusing to split at the top of a page is
      what sent an oversized block to the branch that clips it.
      Rendering follows the print convention plus one concession to the medium: the box is
      left OPEN at the break (square corners there, since a rounded corner on a box that does
      not end reads as an ending), and the continuation is NAMED — "TRY THIS (continued)",
      no mascot — because on a one-page-at-a-time reader you never see the spread, so the
      open edge alone is a weaker signal than it is on paper.
      **Continuation is TWO independent bits, not a head/tail flag.** A long block splits
      more than twice and the middle piece is both a continuation and continues; with one
      flag the second split overwrote it and the middle piece introduced itself as a fresh
      exercise, mascot and all.
- [x] **The top of the page was a fifth of the page (2026-09-29)** — Aliaksei measured it by
      eye and was exactly right: the first line of text started at y=144 on a 640px screen.
      Broken down, the largest slice was not ours at all — **the Android status bar, ~5% of
      every page given to a clock**. The reader hides it now and restores it on the way out,
      so Library and Settings keep theirs; the NAVIGATION bar stays, because hiding both
      would take the system back gesture away from a screen whose own back affordance is one
      "≡ Contents". Also: the header's `TextButton` was an unrestyled M3 default carrying a
      40dp minimum height for a 20px label, in an app whose rule is to restyle every one of
      them, and the 36dp gap below the header went to 24dp — the separation argument survives
      at 24, what does not survive is paying for it twice. **First text now starts at y=100:
      22.5% → 15.6%.** `INK_TOP_MARGIN` (28dp) was deliberately left alone — it is the room
      the pen needs to stroke ABOVE the first line.
      **Then a second pass, which found the principle the first one missed: the header's
      bottom gap and `INK_TOP_MARGIN` are the SAME GAP.** Both are white space between the
      running head and the first line; they were argued for separately — one to detach the
      header, one to give the pen room to start a stroke above the first line — so they
      stacked, 24 + 28, for one visual job. The ink margin is the one that cannot shrink, so
      it does the separating and the header's gap drops to 8dp (and 4dp above it, since with
      the status bar gone there is nothing up there to be separated from). First text lands
      at y=80: **22.5% → 12.5%**, and chapter 2 went 24 pages → 22 → 21 on the same content.
- [x] **An exercise has somewhere to write the answer (2026-09-29)** — from Aliaksei
      describing his own loop: "I use stylus to mark answer, or write on the page then open
      and compare". That reframed what looked like a defect — the blank area under an
      exercise is not dead space, it is the WORKING AREA, and it had simply never been drawn.
      DOTTED lines under the task now, sized from the answer the book is holding, which is
      something a paper workbook cannot do. **They earn their place with no stylus too**:
      Head First prints these knowing most readers never fill them in, because they mark a
      beat to stop and think, break the wall of text, and set the page's mode to workbook.
      **The answer reveals BELOW the lines, never into them** — ink is anchored to a block
      and does not reflow, so printed text landing where someone wrote would bury their
      handwriting; after the tap the two sit one above the other, which is the entire point
      of writing first. The lines stay drawn after the reveal for the same reason. Height is
      defined ONCE (`answerSpaceLineCount` + `ANSWER_RULE_SPACING`) and used by both the
      renderer and the paginator, because an estimate that computes independently of its
      renderer is the bug this file produced twice on 2026-09-29 alone.
      **Found immediately on the sketching book**: a task that says "draw the outline of the
      mug in one unbroken line" gets ruled lines, which is wrong — a DO task wants blank
      space, only a SAY task wants rules. **Fixed the same day**: the chapter model marks a
      making-task as ```exercise space="blank"```, the same way it already picks
      `kind="show"` for a figure — it wrote both the task and the answer, so it is the one
      that knows. Same reserved height either way, only the treatment differs. Ruled is the
      default because most answers are written AND because it is the milder failure: blank
      space under a question still reads as somewhere to answer, where rules across a
      drawing read as a mistake. Verified by hand-editing the fence on the device rather
      than paying for a regeneration. **Dotted rather than solid** (Aliaksei's call): the box
      already has solid hairline borders, so solid rules inside it read as table rows —
      structure rather than an invitation — while a dotted rule is the established "fill this
      in" mark and cannot be mistaken for the frame. Defined in dp, so it scales to the
      Boox's panel; verified at 1x on the emulator only.
      **Sizing from the answer only works when the answer is what you would WRITE** (Aliaksei:
      "probably not all tasks has an answer like this one"). A making-task's stored answer
      describes what success looks like — "a continuous, messy, but remarkably accurate line
      drawing" — and its character count says nothing about the room a drawing of a mug
      needs; deriving from it gave four lines because the sentence happened to be 150
      characters. A blank space is sized flat and generously instead. And an exercise with NO
      answer draws no space and no reveal button, but was still reserving 48dp plus a badge
      for a control that never appears.
      **A making-task usually needs no `answer:` at all**, and the prompt now says so. Chapter
      1 offered "draw a line that wanders as much as it wants" and then an answer reading "a
      long, meandering, delightfully crooked line that zig-zags down the page" — describing
      the reader's own artefact back at them, which they are looking at. An answer earns its
      place on a making-task only when it says what to CHECK, what a common mistake looks
      like, or how to tell a good result from a plausible-looking bad one.
      **The classification does not have to be perfect, because the failure is asymmetric**:
      you can write on a dot grid, but you cannot comfortably draw on writing lines. So the
      default is INVERTED (2026-09-30, Aliaksei's call) — the room is a dot grid unless the
      model writes `space="lines"`, with `space="none"` for a task that produces nothing.
      Forgetting to mark a fence now lands on the harmless side. The param is read at RENDER
      time, so this restyles every exercise in every book already on disk; the SIZE is still
      taken from the answer whenever there is one, so nothing repaginates — only the marks
      change. Verified on a chapter written months before any of this: same three rows, now
      a grid.
      **A making-task gets a DOT GRID, not empty space** (Aliaksei: "still better then empty
      space", and "some editors uses grids to make hand drawing easier"). It is what a
      sketchbook actually uses — dots give proportion and alignment to work against without
      lines cutting through the drawing — and for this book's subject it is a genuine drawing
      aid rather than only a mark of intent. Steps by the same measure as the dotted rules,
      so the two treatments share a vertical rhythm; that step is also about 5mm, which is
      what dot-grid paper uses.
- [x] **A split cuts at a sentence, not a line (2026-09-30)** — Aliaksei on page 17: "still
      has 4 sentensies looks quite silly". The last piece read "about mistakes; just keep
      the ink flowing." — a fragment. I first diagnosed it as a GREEDY split and wrote
      balancing code; that was wrong and the code was reverted. **The last piece is
      inherently short**: it shares its page with the writing space, the reveal button and
      the room reserved for the answer, so it holds a line or two of task whatever the
      earlier cuts do. Its length was never the problem — starting mid-sentence was. The cut
      now backs up to the last sentence boundary, capped at two lines' worth so a long
      sentence straddling the break stays the ordinary case. Same page, same length, reads
      as a whole thought.
- [x] **A box should prefer to move, not split (2026-09-30)** — the other half of the
      `!atTop` removal. Prose SPLITS; a bordered box that would fit on a FRESH page belongs
      there whole, and only one exceeding a whole page is cut. Removing the guard was
      necessary for the oversize case and took that distinction with it; it is restored on
      the thing that actually matters — whether the box fits a fresh page — rather than on
      where the layout happens to be standing when it meets one. `splitFence` is now the
      LAST resort instead of the first.
      It cannot loop, and `!atTop` is what makes that true: `newPage()` appends
      unconditionally, so moving a box off an already-empty page would spin. The other half
      is that a fence's estimate does not depend on `atPageTop` (only a heading's does, for
      its top spacer), so a box measuring within budget measures the same on the empty page
      and is placed there by the fit branch.
      **Measured on the emulator at 320x640 rather than reasoned about** — a temporary
      `Log.d` in the loop, which is the method note earning its place again. Budget 468.
      The chapter's `recap` (h=454, met at height=131) now MOVES; under the old code it was
      cut and put a `RECAP (continued)` overleaf. The two `exercise` boxes measure 762 and
      622 — genuinely bigger than a whole page — so they still split, three pieces and two,
      which is the carve-out this item asked for. Confirmed on the glass: page 20 draws the
      recap whole, rounded top and bottom, one label.
      **The cost is visible and was accepted**: the page before a moved box loses its tail,
      and page 19 is four lines then two-thirds blank. Unavoidable here rather than a flaw —
      a 454 box against a 468 budget needs essentially a whole page, so the only alternative
      was the split we just rejected. Print does the same with an unbreakable box.
      **Those exercises are oversized largely BECAUSE of their stale answers**: an answer
      present means space reserved and sized from it, and both are making-tasks that should
      carry none under today's rules. Regenerating the chapter will likely drop them under
      the budget, at which point this fix moves them whole too — the demo regeneration and
      this change compound rather than merely queueing.
- [x] **The running head lines up with the text (2026-09-30)** — Aliaksei, from the Boox:
      "top and bottom status bars has bigger margin then a content". Measured off a
      screenshot rather than eyeballed: header 53.9dp from the edge against the body's
      40.5dp, right margin 50.1 against 41.1. The footer was already aligned (1dp, glyph
      side-bearing). Cause was touch padding doing double duty — the `≡ Contents` Box adds
      12dp horizontal for its tap target and the chapter counter 8dp at the end, on top of
      the margin the parent column already applies. Both are cancelled with an equal
      `offset` rather than removed, because the tap target is the point of them; the target
      now extends INTO the margin, which is the easiest place to hit a header. The counter's
      end padding became conditional — it is a separator from the bookmark ribbon, so it
      exists only when the ribbon does.
- [x] **E-ink has no grey (2026-09-30)** — Aliaksei, on the dot grid: "almost invisible on
      device". They were 0.75dp of `Hairline` (0xFF888888), which at the Boox's 300dpi is a
      disc ~2.8px across with NO fully covered pixel — an antialiased mid-grey smudge. An
      LCD draws that as a faint dot; the panel thresholds and dithers, so it can resolve to
      nothing. Now `Ink`, never smaller than a whole pixel. **The reason it escaped is worth
      more than the fix**: it was "verified at 1x on the emulator only", and 1x is precisely
      where it could not show up — at density 160 a 0.75dp radius is SUB-PIXEL, so the
      emulator was not a weaker test of the same thing, it was a test of something else.
      Generalise: a dp-defined hairline or dot is not verified until it has been seen on the
      panel, because greys and sub-pixel geometry are exactly what e-ink does not reproduce.
      A dot grid on PAPER is light grey because ink reproduces greys; the equivalent
      perceptual weight here is a small BLACK dot, ~0.3mm, which is what dot-grid paper uses.
      Confirmed on the device.
- [x] **"No working room" is not the same as "not on this page" (2026-10-01)** — Aliaksei,
      reading chapter 1 on the device: "check task on page 10, isn't it require a space to
      draw?" It was marked ```exercise space="none"``` — a ten-second warm-up scribble with
      no working area at all — and the page CONTRADICTED ITSELF: the very next line under the
      box read "Look at what you just made." The book pointed at an artefact the layout had
      decided did not exist.
      The prompt's criterion was "needs no working room at all, because nothing is produced",
      which silently asks TWO questions — is anything made, and does it belong on this page.
      The task says "on your paper", so the model answered the second and withheld the room.
      Same chapter, the circles task says "on your page" and got a grid: one rule, two
      answers, which is the signature of an ambiguous principle rather than a bad generation.
      Same shape as the screener's `refuse` criterion — the examples were fine, the PRINCIPLE
      was the problem.
      Now: `space="none"` is only for a task that leaves NO MARK ANYWHERE, and the prompt
      says explicitly that work on the reader's own paper still earns room, because on a
      stylus device many will work on the page — plus the asymmetry, since an unused grid
      costs a little blank space while a missing one leaves a reader with nowhere to work.
      Unit-tested. The demo chapter was HAND-EDITED rather than regenerated: the same
      generation got the answer discrimination, the figures and the cover match right, and
      re-rolling all of that to fix one param is a bad trade.
      **Device-verified**: the task now carries a grid, and the box exceeds a page so it
      splits — "TRY THIS (continued)" then the grid, then "Look at what you just made". Note
      the interaction with the open item below: a BIGGER drawing area makes this split more
      common, and an oversized box is the one case that no longer moves whole.
- [x] **The working room is sized from the TASK, and the page can take it back
      (2026-10-01)** — Aliaksei's idea from 2026-09-30, built after he found the defect it
      predicted. `answerSpaceLineCount` sized the room from the ANSWER whatever the
      treatment. That is right for something you WRITE — a printed workbook guesses how many
      lines to rule and this one knows — and says nothing about the room a drawing needs: a
      mug takes more page than a sentence about a mug does. Seen on the Boox: a reader drew
      eleven circles across a grid sized by a 180-character sentence, and the revealed answer
      printed straight over the bottom row of them, breaking the one rule the feature was
      built on.
      Two measures now. Ruled lines keep sizing from the answer, clamped 2..6. A grid gets a
      flat `GRID_ROWS = 8`, deliberately generous — **and generosity is only safe because the
      page is allowed to take it back.**
      **A LADDER OF COURTESIES.** When a box will not fit a page, what yields is what the
      reader loses least by: the room to work in first (all the way to nothing — a phone has
      no stylus, and a box with no room still asks its question and shows its answer, where a
      clipped one does neither), then the answer's mascot, because a 72dp badge sits BESIDE
      the answer text and on a 320dp page takes nearly a quarter of the line from it, turning
      a seven-line answer into twelve. The task and the answer never yield. Carried as
      `FENCE_SPACE_ROWS` / `FENCE_PLAIN_ANSWER`, pagination-written params the model never
      writes and the file never holds — the pattern `FENCE_CONT_FROM` already set. Both
      honoured by the estimate AND the renderer, which is this file's standing rule.
      **And when even the ladder cannot make it fit, the oversize branch places the RELAXED
      box, not the original.** Getting that wrong was the bug twice over in one sitting:
      having given up the room and the mascot, the code fell through and clipped the
      full-sized box, so the reader lost the same words AND kept the decoration that cost
      them. Least-clipped beats most-clipped.
      **Device-verified on the emulator at 320x640**: the answer that was sliced in half now
      renders complete, ", you did it right." and the bottom border included; the exercise
      that fits keeps its full 8-row grid untouched. **NOT re-verified on the Boox** — it
      came off the cable before the build went over. Expect the ladder never to fire there:
      the box already fit with room to spare at the old size.

- [x] **A mark in the working room is an answer, not a selection (2026-10-02)** — Aliaksei,
      doing the ten-circles exercise on the Boox: "I got chip appeared all the time". Every
      circle he drew AS THE ANSWER popped the Ask/Add-note chip. The gesture test already
      required a shape AND text — `(enclosing || verticalMark) && !annotatedText.isNullOrBlank()`
      — and the text half was passing, because a circle in the grid is anchored to the
      exercise block and the near-miss fallback let it claim the question's last line; a
      vertical mark did worse, reading ACROSS blocks and quoting a confident, wrong passage
      from a neighbour. So the guard was there and the thing it guarded against was not
      "no text" but "text the reader never pointed at".
      `strokeIsInWorkingRoom` is the third test: a mark whose vertical CENTRE is below the
      question text of a box that offers room is the reader working. Asked GEOMETRICALLY
      rather than from the `space` param, because the room is offered whatever the treatment
      — writing an answer on ruled lines is not a selection either. Centre rather than top,
      so a circle drawn AROUND the last line still marks it while one in the first grid row
      does not. Decided at the dispatch site rather than inside the extractors, since it
      disqualifies the gesture whatever shape it had.
      **Verified on the emulator by injecting stylus events**: a vertical mark in the grid
      produces `workingRoom=true` and no chip (the log shows it still resolved text, which
      is exactly the trap), and a margin bar on a prose page still produces `workingRoom=false`
      and its chip. **NOT verified: a deliberate underline or circle INSIDE an exercise's
      question**, which must still raise the chip. The emulator cannot answer it — injected
      stylus events mis-anchor (seen anchoring to block 15 while the page showed 10-11), and
      the real path is the Onyx overlay. **Check that on the Boox before trusting this.**
- [x] **The book speaks its language on the chip and the overlays too, and a TEST now says
      so (2026-10-02)** — the pen chip's "Delete" sat between a translated "Ask about this"
      and a translated "Cancel", in English. Pulling that thread found eleven more: the whole
      compile prompt ("Back", "Try again", "Compile chapter" — a page OF the book, it carries
      the running head), every button on the note overlays, the Ask panel's "Older"/"Newer"
      and its placeholder, and both note placeholders. **"Cancel" and "Save" were already
      DECLARED and simply never wired**, which is exactly the September miss repeating.
      The real fix is the guard, not the eleven. `BookChromeLiteralsTest` scans the book's
      own screens for `Text("…")` literals and fails with file:line for any string not in
      `BOOK_CHROME_STRINGS`. This defect had shipped three times, always silently — a label
      that is never declared stays English in every translated book for ever, and nothing
      downstream notices. `BOOK_CHROME_STRINGS`'s own doc argues a build-time source scan is
      not worth it for deriving the LIST, and that still stands: this is the cheaper half,
      which only refuses to let the two drift apart.
      **It paid for itself on the first run**, catching two the hand-grep had missed because
      they open with a glyph: `Text("← Close")` in the Ask panel and `Text("≡ Contents")` in
      the notes screen. Both are now `"← " + t("Close")`, the pattern the reader header
      already used.
      Scope is the BOOK's screens only. Library, Settings, New course and Backup belong to
      the person rather than to a book and stay in English — the line already drawn for
      `language` and `units`.
      **`ExportScreen` is NOT done and is deliberately left whole-English**: it has a dozen
      literals including sentences, and a half-translated screen is worse than a consistent
      English one. It is outside the test's list with this comment as the record.
- [ ] **A rewrite keeps a cover drawn from an opening it no longer has** — the other half of
      the above, left undone on purpose. §1 keeps the cover and the closing picture through
      a rewrite because they came from the outline; they come from the chapter's opening
      now, and a rewrite is explicitly told to change its metaphors. Redrawing both every
      time spends ~6¢ nobody asked for, so this is a decision about money rather than a bug
      to fix on sight. The Opening row already offers the redraw.
- [x] **The reader menu scrolls (2026-09-28)** — open since 2026-09-22 and closed because
      430 colleagues are about to open this app on phones. It sized to its content, so below
      ~700px everything after "Chapter" was off-screen and unreachable — Rewrite, Figures,
      Opening, Closing, Badges — invisible on the Boox, which is tall enough. `MenuSheet` is
      now the one container all three panes share (they were three identical `Column` headers
      drifting apart), and **the cap and the scroll are ONE fix**: a scroll with no cap fills
      the screen and takes the dismiss target with it, since tapping the page is the only way
      out of the root pane; a cap with no scroll is the bug again with a shorter sheet.
      The gesture half is NOT in `TapRouting`, deliberately. `verticalScroll` CONSUMES the
      drag, so the page's detector now drops any gesture a child consumed — a `consumedByChild`
      flag rather than the old check on the final change, because **a scrolling child consumes
      the deltas as they arrive and has nothing left to consume on release**. That was why a
      scroll container alone would not have worked: scrolling arrived at the page as a bare
      swipe and resolved to `CloseMenu`. The DECISION stays in TapRouting; the detector deals
      with its own transport, which is what its own doc already said its job was.
      **Device-verified on the emulator at 320x640 and 320x480**, all five: every row reachable;
      a drag inside the menu no longer closes it; a drag on the PAGE still closes it and does
      not also turn; swipe-to-turn still works with the menu closed; and the content genuinely
      scrolls once it overflows.
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
