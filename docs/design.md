# InkFirst — Design Notes

Companion to `../CLAUDE.md` (read that first for context and decisions). This doc holds the concrete design: on-disk formats, data model, generation strategy, UI shape. It's a living document — update it when the design actually changes.

## 1. On-disk course format

Everything a course is lives under one folder. Plain, inspectable, greppable, syncable-by-anything.

```
/storage/.../InkFirst/courses/<course-slug>/
├── course.json          # outline, metadata, progress — single source of truth
├── memory.md            # running course memory (see §3)
├── chapters/
│   ├── 01-intro.md      # generated chapter, structured Markdown
│   ├── 02-basics.md
│   └── ...
├── notes/
│   ├── 01-intro.notes.md    # typed notes per chapter
│   └── 01-intro.qa.md       # saved selection-anchored Q&A per chapter
├── ink/                 # phase 2: stroke data, one file per chapter
│   └── 01-intro.ink.json
├── figures/             # phase 3: generated chapter illustrations
│   ├── 01-intro-1.png       # <chapter-slug>-<nth figure in that chapter>
│   └── 01-intro-cover.png   # the chapter's opening picture (§10)
└── badges/              # per-course mascot marks (§9), drawn once for the book
    ├── exercise.png
    ├── recap.png
    ├── answer.png
    ├── code.png
    └── mascot.txt       # one-line description, so a redraw stays on-model
```

Badges and covers are addressed by *name* rather than by a pointer in the Markdown: nothing in
the chapter text references them, so the file name is the key (block kind, or chapter slug).
Figures are the opposite — the fence carries `src=`.

### course.json (sketch)

```json
{
  "version": 1,
  "title": "Kotlin for Python Developers",
  "topic_prompt": "the user's original ask, verbatim",
  "prior_knowledge": "user's self-description",
  "daily_minutes": 45,
  "style": "headfirst-inspired",
  "model": "gemini-flash-<current>",
  "language": "English",
  "units": "metric",
  "expectations": "optional free text — what the user wants out of the course",
  "created": "2026-08-07",
  "bookmarks": [{ "chapter_slug": "01-intro", "block_index": 42, "created": "2026-09-01" }],
  "annotations": [
    {
      "chapter_slug": "01-intro",
      "block_index": 42,
      "quote": "the passage the note is anchored to",
      "note": "what the user wrote",
      "kind": "text",
      "created": "2026-09-01",
      "at": "2026-09-01T21:14:07"
    }
  ],
  "chapters": [
    {
      "n": 1,
      "slug": "01-intro",
      "title": "Why Kotlin feels weird coming from Python",
      "objectives": ["...", "..."],
      "est_minutes": 40,
      "status": "done | reading | generated | pending",
      "last_block_index": 42
    }
  ]
}
```

`units` is `metric` or `imperial`, chosen at course creation beside the language and fixed for
the life of the book — a course whose chapter 1 measures in Celsius and whose chapter 6
measures in Fahrenheit is worse than either. Left unsaid the model defaults to US customary,
because most of its training text does, so the book quietly assumes an American reader.
Absent from a course written before the field existed, which reads as metric.

`created` on an annotation is a DATE and identifies it (deletion matches on quote + created);
`at` is the full timestamp and only orders it. The Q&A feed merges two sources — the `.qa.md`
transcript and the `kind: "ask"` annotations — and with date granularity everything made in
one evening tied, so the feed fell back to the order the two sources happened to be
concatenated in. `at` is empty on anything written before it existed; `created` is the
fallback, which correctly sorts such entries before the same day's stamped ones.

Positions are stored as **block indices, never page numbers** — pages recompute whenever the
font size, line spacing, margin or screen changes, so a page number is meaningless a day later.
The same index anchors bookmarks, annotations and ink strokes, which is why inserting or
removing a block is a breaking change to a user's existing annotations (see §2).

A page is identified by **the first block that BEGINS on it**, not simply its first block
(`anchorForPage`). Since paragraphs can split, two consecutive pages can carry the same index,
so "which page holds block 12" has two answers — and a bookmark set on the second of them
resolved back to the first. The first block that starts on a page appears on no earlier one.

**A mark is RESOLVED against the current pagination, never compared to it** (`bookmarkOnPage`,
2026-09-21). Setting a bookmark stores `anchorForPage`; going to one calls
`findPageContainingBlock`. Those two relations agree only while the pagination that produced
them is still in force — increase the margin, the font size or the line spacing and a stored
anchor can stop being any page's first-beginning block. The bookmark then still opened its
page, because the jump uses containment, while the ribbon and the menu compared against the
anchor and saw nothing: a page you could navigate to that offered to bookmark itself all over
again. Asking the question the same way the jump asks it is what stops the two drifting apart.
The stored index is never rewritten to heal it — resolving correctly makes healing
unnecessary, and silently editing a reader's saved position on a font change is worse than the
bug.

**Who writes the chapter title.** The byline block always prints "Chapter N"; the title itself
comes from whichever heading the chapter opens with, or from the outline if it opens with
none. "Opens with a heading" means **level 1 or 2**, not level 1 alone — the model writes the
title as `##` often enough, and reading that as "no title here" made the app inject one from
the outline and then render the model's heading below it: the same chapter titled twice, the
second time carrying "Chapter 6:" (seen on the first real rewrite, 2026-09-21). Level 3 and
deeper are section headings, and a chapter opening straight into one genuinely has no title.

The opening heading is normalised to level 1 and has any leading "Chapter N:" stripped
(`stripChapterNumber`), so a title is set in title type and numbered once, whatever the model
typed. Normalising happens by COPYING the block in place — never by inserting or removing one,
which would slide every annotation and ink anchor after it. The prompt also asks for `#` and
for the number to be left out, but that is variance reduction, not the fix.

**Two index spaces, one block index.** `parseMarkdown` numbers a chapter as WRITTEN; the
reader numbers it as DISPLAYED, and prepends exactly one synthetic block — the italic byline,
which also carries the title when the model forgot to write one. Bookmarks, annotations, ink
and the pager all live in the displayed space, because the reader is what created them.
Anything crossing the line converts through `readerBlockIndex` (`READER_LEADING_BLOCKS`).
Search hits are the first thing that ever had to: they come from the file. The closing picture
is appended at the END for exactly this reason — adding to the tail shifts no existing index.

Rules: `course.json` is the only file the app mutates on ordinary progress updates. Chapters
are immutable once generated — never silently overwrite something the user annotated.

**Rewriting a chapter (built 2026-09-21)** keeps the second half of that rule and inverts the
first. The live file stays at `<slug>.md` and the previous body is renamed to `<slug>.r1.md`,
`.r2.md` and so on (`CourseStore.archiveChapter`), rather than the new revision taking the
numbered name and a pointer moving. The pointer version was specced first and dropped on
contact with the rest of the app: every reader, paginator and anchor path resolves a chapter
by its slug, so a revision pointer would have added an indirection to all of them to serve a
feature with no UI for reading an old revision. Archiving costs a rename and keeps the promise
that matters — the text is still on disk, greppable, for a reader who wants it back.

**A rewritten chapter starts over.** Everything the reader did in it goes: annotations,
bookmarks, ink, inline figures, the Q&A transcript, and that chapter's section of `reader.md`.
One rule, stated in one sentence in `RewriteChapterDialog` before anything happens, with a
count of what that means in this particular chapter.

The first version of this tried to be cleverer — keep what is a RECORD, drop what is a
POINTER — and kept the Q&A on the grounds that a conversation which really happened is still
true about the reader. That was wrong twice over, and the second reason is the decisive one:

1. Every exchange in the transcript quotes a **passage**. It is a pointer like any other, and
   on its own it is a set of quotations from a text nobody can read any more.
2. The reader's question history is **split across two files** — answered questions in
   `<slug>.qa.md`, marked-without-ever-asking in `course.json`'s annotations. Keeping one and
   clearing the other left half a history behind, and which half was decided by nothing but
   where it happened to be stored. Both `QaTranscript.feed` and `ReaderSignals.notesForChapter`
   read both sources, so both would have shown a partial record and called it complete.

`reader.md` could not simply be left alone either, for a mechanical reason:
`appendReaderNotes` refuses a chapter it has already recorded, so a section left in place
would freeze — the old reading's observations kept forever, the new reading of the same
chapter never recorded at all. `ReaderNotes.removeChapter` takes the section out so the
rewritten chapter can be read afresh.

Nothing is destroyed on disk. `archiveChapter` sets the body and the transcript aside as
`<slug>.previous.md` and `<slug>.previous.qa.md` — together, because the transcript only means
anything next to the prose it quotes.

**One generation, not a chain.** `.r1`, `.r2`, `.r3` was the first attempt and it promised a
version history that nothing can navigate: the app has no rollback and is not going to grow a
revision browser. It also fills a directory the reader is meant to browse with files nobody
can act on, which costs the very value the archive defends. Recovery is by hand — rename the
file back — and in practice a rewrite that missed is noticed at once, not five rewrites later.

The storage argument points the same way but is not the reason, because the measured numbers
are lopsided: on the first real course a chapter is about **10 KB** of Markdown while its
inline figures are about **1.9 MB**, and figures are 89% of the 12.6 MB course. A rewrite
deletes the figures and keeps the text, so it frees roughly two hundred times what it stores.
Archiving was never going to be what filled the device.

The cover and closing pictures survive in place: they illustrate the chapter's title and
objectives, and those come from the outline, which a rewrite does not touch. Inline figures do
not: they are stored as `<chapter>-<ordinal>` and matched by slot ordinal, so a stale one would
silently illustrate an unrelated paragraph.

Nothing enumerates the chapters directory — a chapter is addressed by the exact slug held in
`course.json`, and slugs are kebab-case with no dot — so the archive is inert without any
filtering on read.

The general form of the rule, for whenever whole-book regeneration arrives: **a regenerated
thing starts over, and what it replaces is archived rather than deleted.** Deleting the course
is already the way to start a book over, and it says what it destroys too.

The order in `compileChapter` is load-bearing: generate first, destroy second. A rewrite that
fails at the API must leave the book exactly as it was.

`memory.md` is rewritten in place too (`CourseMemory.replaceChapter`), not appended to. It is
read whole by every later chapter, so an old summary left in it teaches the book prose nobody
will ever read. The prompt for a rewrite also hides that chapter's own memory block from the
model — reading back a summary of the draft being thrown away is the surest way to get the
same chapter again — and deliberately does NOT include the old text: the aim is a fresh route
to the same objectives, and a model shown its own previous draft edits it instead of
rethinking it.

## 2. Chapter format

Structured Markdown with a small set of fenced custom blocks the renderer understands:

- ` ```exercise ` … ` ``` ` — interleaved exercise; renderer shows it as a boxed activity with a "reveal answer" page-flip (answer stored in the block, hidden by default).
- ` ```recap ` — end-of-section redundancy blocks (the Head First-style "repeat it differently" trick).
- ` ```figure ` — a generated illustration. The `alt="…"` param is written by the chapter model and doubles as the *drawing brief*; once drawn, a `src="…"` param naming a file in `figures/` is written back into the same fence. A slot with no `src` yet renders the alt text in a bordered box, which is what every figure looked like before phase 3.
- ` ```python `, ` ```bash `, … — a code fence carries its **language as the fence kind**. The
  renderer boxes it with the language as the label. This matters for badges: badging fence
  kinds naively would draw a separate mascot per language, so `badgeKindFor()` collapses every
  known language word to one `code` badge (§9).
- Any other word the model invents (` ```gotcha `, ` ```warning `) renders as a bordered block
  labelled with that word, and earns its own badge.

Two block kinds exist that never appear in the Markdown:

- **`meta`** — a synthetic block the reader prepends to every chapter, carrying the
  minutes/day-range/status byline, and the chapter title when the model forgot to write one
  (two of six chapters in the first real course did). It also carries the cover picture (§10).
  It is synthetic *and index 0*, which is deliberate: putting the title and cover here rather
  than prepending real blocks means anchors in chapters that lack a title don't shift.
- **`Block.Rule`** — a thematic break (`---`). The chapter model emits these as section
  separators several times per chapter; before this existed they fell through to a paragraph
  and rendered as a literal "---" on the page. It draws as whitespace, not a line: in practice
  every one is immediately followed by a heading, so a visible rule separated something that
  already separates. The block is *kept* rather than dropped at parse time because removing it
  would slide every annotation and ink anchor after it.

  Filling a slot rewrites exactly one line of the chapter file and leaves every other byte alone, so block count and block indices don't move and existing annotations and ink strokes stay anchored. That's also what makes an already-written course illustratable without regenerating a word of its prose — generating with figure *slots* from day one paid off exactly as intended.

Everything else is vanilla Markdown. Rationale: chapters stay readable as plain text in any editor, and the custom-block vocabulary can grow without format migration.

## 3. Generation strategy — the cross-chapter memory problem

The failure mode to design against: chapter 9 re-explains chapter 3, or contradicts it, because each generation call is stateless. Sending all previous chapters in every prompt doesn't scale and buries the signal.

Approach — a compiler with a symbol table:

1. **Outline pass**: one call generates the full course outline (chapter list, objectives per chapter, dependency notes: "ch7 builds on the ownership metaphor from ch3"). Stored in `course.json`.
2. **Memory file** (`memory.md`): after each chapter is generated, a cheap follow-up call summarizes *what was taught and how*: concepts introduced, the specific metaphors/examples used, terminology chosen, exercises given. Appended per-chapter, ~150–300 words each.
3. **Chapter pass**: generating chapter N sends: system style-prompt + outline + full `memory.md` + the chapter-N spec from the outline. Never full prior chapter texts.
4. Metaphor continuity is the point of storing *how* things were taught: if ch3 explained coroutines as "a kitchen with one cook", ch9 should be able to say "remember the kitchen?"

Style system-prompts live in `:core` as named presets — prompt changes are code changes, reviewable in git. Built-in presets:

- **headfirst-inspired** (default) — conversational, redundant on purpose, exercises every few pages, concrete-before-abstract.
- **textbook** — structured, formal, definition → example → practice.
- **practical** — minimal theory, code/hands-on heavy, project-oriented.

The `style` field in `course.json` selects the preset; the generation pipeline reads it and injects the matching system prompt. New presets can be added as constants without format changes.

## 4. App structure

```
:app    — Compose UI, Android glue
:core   — pure Kotlin (no Android deps where possible):
          course model, file store, Gemini client, prompt builders,
          markdown chapter parser. Unit-testable on JVM.
```

Screens (MVP):

1. **Library** — list of courses + "new course".
2. **New course** — topic, daily minutes, prior knowledge → shows generated outline for approval before creating (cheap moment to catch a bad outline; regenerating an outline costs cents, regenerating a course costs evenings).
3. **Course** — chapter list with status; tap to read; "generate next chapter" action.
4. **Reader** — paginated chapter view. Tap **left / centre / right third** = page back /
   menu / page forward (the halves in the original sketch left no room for a menu), plus the
   Boox side buttons, which arrive as `VolumeUp`/`VolumeDown` and must be *consumed* or Android
   shows its volume popup. Text selection → "Ask" action → chat panel. E-ink discipline:
   full-page refresh on page turn is fine; never animate.
5. **Chat panel** — anchored to a selection (quoted at top) or chapter-general. History persisted to `notes/<ch>.qa.md`.
6. **Notes** — **passage-anchored annotations**, not the free-form per-chapter page originally
   sketched here. The user selects a passage (finger, or a stylus enclosure gesture), and the
   note is stored against that block index with the quote. It renders as an inline underline
   with a margin marker, and the Contents screen lists notes with a quote preview.
7. **Settings** — Gemini API key (Keystore-encrypted), text model, image model, preferred
   language.

Navigation note: every "≡ X" link must **name its destination** rather than pop the back
stack. Auto-resume cold-starts into `[Library, Reader]` with no Contents in between, so a pop
from Contents landed back in the chapter the user was trying to leave. This has now been fixed
twice, in the Reader header and in the Contents screen's Library link.

## 5. Pagination approach (the main renderer risk)

Options, in order of preference — spike this first when building the reader:

1. Native Compose: measure rendered Markdown into page-sized chunks (custom layout that binary-searches text breakpoints per page). Full control, no web stack, best e-ink behavior. More work.
2. WebView + CSS paged/column tricks (render chapter HTML, page = viewport-width column offsets). Faster to ship, heavier, and WebView on Boox can be quirky.

**Decided: native Compose (option 1), and built.** `Paginator.kt` measures each block with a
`TextMeasurer` and packs whole blocks into pages — it never splits a block, so some pages end
with visible whitespace. Two consequences worth knowing before touching it:

- Every measurement must match what the renderer actually draws. The paginator carries its own
  copy of the heading paddings, paragraph spacing, badge sizes and figure ratio; a mismatch
  shows up as a clipped last line. A 10% safety budget absorbs font-metric slop.
- Paragraph splitting is still unbuilt (v2.1). It is the main reason a page with a figure or a
  long first paragraph can look half empty.

## 6. Phasing

- **Phase 1 (MVP)**: everything in CLAUDE.md's MVP list. Success = one full course read on the Boox.
- **Phase 2**: stylus notebook — *done* (see §11). Raw Onyx ink, per-chapter strokes,
  tap-on-stroke chip, enclosure gesture. Still open: erase by sweep, curve-select for text.
- **Phase 3**: generated figures filling the `figure` slots — *done* (see §7), extended with
  badges (§9) and chapter covers (§10). Still open: spaced-repetition hooks from exercises.
- **Someday/maybe (explicit non-goals for now)**: other platforms (iPad), EPUB export, sharing courses, any backend.

## 7. Figure illustration (phase 3)

Built 2026-09-10. The original plan here said "image model output post-processed to high-contrast e-ink-friendly line art", framed around *diagrams*. That framing was wrong and the decision was revisited deliberately.

**What the figures are for.** Not reference material — mood. Head First's pictures are what stop a study page reading as a wall of text: a silly visual metaphor, a character mid-reaction. Correct-but-boring boxes-and-arrows lose to charming-and-approximate for that job. A semantic diagram DSL rendered natively in Compose was designed and rejected on exactly this ground: it would have produced accurate schematics nobody enjoys looking at.

**How it works.**

- `ChapterIllustrator` (`:core`) walks a chapter's pending figure slots and calls an image-capable Gemini model ("Nano Banana", `gemini-3.1-flash-lite-image` by default, overridable in Settings) once per slot. Roughly 3 cents a picture; a 12-chapter course lands near $1.50.
- Requests go through the same `generateContent` endpoint as every text call — `KtorGeminiClient.generateImage` differs only in asking for the `IMAGE` response modality and reading `inlineData` instead of `text`. Aspect ratio and size go in `generationConfig.imageConfig` as plain strings (`"4:3"`, `"1K"`). **Not** the sibling `generationConfig.responseFormat.image`: that field exists and looks equivalent, but it wants protobuf enum names (`ASPECT_RATIO_FOUR_BY_THREE`, `IMAGE_SIZE_ONE_K`) and 400s on the human-readable forms. A 400 on the image config retries once without it, so a version skew costs the aspect ratio rather than the picture.
- `SKETCH_HOUSE_STYLE` in `FigurePrompt.kt` is appended verbatim to every request. It is the single thing standing between a coherent-looking book and a pile of generic AI art, so it lives in one constant and is tuned in one place. It asks for pure black line art — no grey wash, which the model set differently on every
  render — forbids fine detail (a 7" panel throws small marks away), and *encourages* a short
  shouted speech bubble. **The original rule forbidding lettering was reversed**: these models
  render words well now, and a character saying something is the most Head First device there
  is. What makes it work is a numeric target rather than an adjective — each capital at least
  one twelfth of image height, a bubble at least a third of the image width — because "large
  lettering" alone produced poster-scale composition that vanished at display size.
- Everything is best-effort and idempotent. Pictures are drawn *after* the prose is saved, so a failure can never cost the user their chapter; slots that already have a `src` are skipped, so a retry only fills what's missing. The reader's ≡ menu grows a "Draw N figures" row whenever a chapter has outstanding slots.

**Verified on the Boox 2026-09-14** (and on the emulator 2026-09-10) against the live API: prose → figure slot → generated sketch → `src=` written back → picture on the page, plus the alt-text fallback for an unfilled slot and the menu's retry path. The one output defect found was a light grey background wash showing as a visible rectangle on the white page — fixed by tightening the background rule in the house style, which is exactly the kind of thing that constant exists to absorb.

On the panel the speech bubbles read instantly, and so did small caption words far below the
1/12 floor — the emulator's 320×640 screen had made legibility look far worse than it is,
because a figure is downscaled ~4× there and barely at all on the device. **Do not tune the
house style against emulator screenshots.**

**Constraints that shaped it.** Figures are requested and displayed at a fixed 4:3 (`FIGURE_ASPECT`), which is what lets the paginator reserve a figure's height without opening the file. Rendering forces saturation to zero — the panel is greyscale, and unlike the UI chrome (pure black and white, to dodge dither patterns) a sketch is *meant* to dither, but a colour cast would muddy it.

### Two kinds of figure (2026-09-21)

The fence now carries `kind="show"`, which is the dispatch the previous paragraph had parked.
It was forced by a real page: a chapter teaching how to hold a pencil — fingers back from the
tip, resting on the middle joint — was illustrated with *"a very relaxed capybara holding a
giant wooden pencil in a sunny meadow"*. A good picture that teaches nothing about the grip,
and not the model's fault: the chapter prompt said to describe a concrete SCENE and used
"a diagram of the classification process" as the worked example of what to refuse.

So "mood, not reference" was too strong as a blanket rule. It is right for most of a book and
wrong for the passages where the reader has to SEE the thing to do it.

- **MOOD** (default, unchanged) — a scene, one vivid subject, a shouted speech bubble, a smile.
- **SHOW** — the thing itself. The brief describes the object and the relationship that
  matters, not a scene. Style rules diverge: no setting, no character, no speech bubble, plus
  an explicit accuracy rule and a rule about hands, which are the known failure mode. An
  abstraction made concrete belongs here too — if the prose says a weight is "like a knob you
  turn", the knob is a show figure.

**This is not the §7 rejection being reopened.** What was rejected was a semantic diagram DSL
rendered natively in Compose — boxes and arrows, accurate and boring. A SHOW figure is still a
hand-drawn sketch from the image model in the same house style; only its job on the page
differs. `SKETCH_HOUSE_STYLE` is now `SHARED_STYLE + MOOD_STYLE`, with `SHARED_STYLE` holding
everything that keeps a book looking like one book, so the two kinds can never drift apart on
medium, colour, line or framing.

Figure COUNT also stopped being flat. The prompt said "two or three per chapter is plenty",
which is stingy for a physical skill; it now scales with how visual the subject is — five or
six for hands-on work, two for a chapter of pure ideas.

**Unproven at time of writing.** A pencil grip is among the hardest subjects an image model is
given, and a hand with six fingers does not read as charmingly loose. Loose ink line art is far
more forgiving than photorealism, which is the only reason this is worth trying — but expect
the same tuning rounds the grey wash and the lettering needed.

**Deliberately not done yet.** A recurring cast of characters across *figures* — though the
mechanism now exists and is used by badges (§9), so this is mostly plumbing.

## 9. Badges (2026-09-14)

A small mascot doodle beside each attention block — the Head First trick of giving every
special section a character. One per block kind, drawn **once per course** into `badges/` and
reused by every chapter, so a twenty-chapter course pays for four pictures rather than four per
chapter.

**The set is discovered, not fixed.** `badgeKindsIn(markdown)` reads a chapter and returns the
kinds it actually contains, so a book that turns out to use code blocks grows a `code` badge
the first time one is opened. `badgeKindFor()` normalises: every known language word collapses
to one `code` badge (a code fence's kind is its language, §2), `figure` and `meta` get none —
a figure is already a picture — and anything else the model invents keeps its own, capped at
`MAX_BADGE_KINDS` so a model in a naming mood can't run up a bill.

**Consistency is the whole trick.** Two independent image calls give two unrelated doodles,
which reads as a mistake rather than a house style. So the first badge *invents* the mascot and
is asked to describe itself in words in the same call — image responses interleave text and
picture, so the description costs nothing — and every later badge is drawn from that
description **with the first picture attached as a reference**
(`GeminiClient.generateImage(referenceImage = …)`). The description persists in
`badges/mascot.txt` so a redraw months later stays on-model.

**Badges need their own house style, not the figure one.** A figure renders ~1100px on the
panel; a badge renders ~135px. Hatching turns to mud and lettering is hopeless at that size, so
`BADGE_HOUSE_STYLE` demands thick strokes, under twenty of them, and **no text at all** — the
exact opposite of the figure rule. As with figures, the rule that works is numeric: *every
stroke at least one fiftieth of the image width*, plus an explicit statement that the drawing
will be shrunk to a tenth of its size. "Thick, heavy outlines" alone produced thin art, because
the model reads it at full scale.

Two display lessons, both learned the hard way: `Modifier.size().padding()` insets the image
*inside* its box, so a "52dp" badge drew at 44dp; and a hard contrast filter (2.6) at render
time *wrecks* downscaled line art — the strokes land in the light-grey range the filter pushes
to white, so the filter meant to erase a paper background erased the drawing instead. 1.5 is
the setting that clears JPEG "paper" without eating ink.

## 10. Chapter covers (2026-09-15)

A chapter opens with a picture of the **problem it is about to solve**, the way a chapter plate
does in a printed book. Stored as `figures/<chapter-slug>-cover.png` and rendered by the
synthetic `meta` block (§2), below the title.

Asked for in **two steps**: the text model writes the scene brief from the chapter title and
objectives, then the image model draws it. One step does not work — handing an image model a
title and a bullet list produces a literal illustration of the words rather than a scene. The
brief prompt asks specifically for the *difficulty the reader arrives with*, not a summary of
the answer, and for a physical everyday metaphor with a character in it. House style is the
figure one, since a cover is a full-width figure.

Side effect worth knowing: a title plus a cover usually doesn't leave room for the first
paragraph, so page 1 of a chapter is a title page. That is the right shape for an opener, but
it is emergent rather than designed — the paginator simply couldn't fit the paragraph.

## 11. Stylus ink on the Onyx raw path (phase 2, 2026-09-15)

Low-latency ink via `onyxsdk-pen:1.5.4`'s `TouchHelper`, which rasterises pen input straight to
the EPD controller. `USE_ONYX_PEN` in `ReaderScreen.kt` switches back to the plain MotionEvent
path, which has every gesture working but visible lag. Three attempts failed before this one;
`docs/onyx-pen-thread.md` is the full log, including a consulted second opinion. The findings
that matter, because none of them are documented by the vendor:

- **`FEATURE_APP_PEN_TOUCH_RENDER` (=4) is an APP-SIDE reader.** It takes its input from the
  host view's MotionEvent stream. So anything Compose does to stylus events starves it:
  consuming the moves gave one point per stroke, and consuming at `PointerEventPass.Initial`
  killed drawing outright. **Compose must never consume stylus events in this mode.** It is
  also the only mode where finger events reach Compose at all, which is what defeated the two
  earlier attempts.
- **Raw drawing freezes the panel.** While enabled the driver owns the display and ordinary
  posts are not shown, so everything Compose draws lands in the framebuffer invisibly. This one
  fact explains three symptoms that looked unrelated: driver ink surviving an app restart, the
  Compose redraw doubling with the driver's copy, and erase appearing to do nothing while
  correctly editing the model. `setRawDrawingEnabled(false)` then `(true)`, after a debounce and
  two `withFrameNanos`, releases it; re-enabling does not re-freeze until the next stroke.
- **`setLimitRect` wants VIEW-LOCAL bounds** (the SDK adds the view's position itself), but raw
  **points arrive in view-local coordinates too** — an earlier reading had them as screen space
  and pushed every stored stroke 256px from where a finger taps.
- **`enableFingerTouch(false)` is unusable on this firmware.** In either call position it stops
  `onBeginRawDrawing` firing altogether, pen included: pen and finger share an input node and
  the filter drops the node, not the tool. Finger ink is rejected by `MotionEvent.getToolType()`
  instead — the raw callbacks carry no tool information.
- **`onRawErasing*` never fires**, so erase is read off the MotionEvent (eraser tip, or barrel
  button via `buttonState`) exactly as `InkOverlay` does.
- **`pointerInteropFilter` follows `View.onTouchEvent` rules**: answer `false` to a DOWN and
  Compose delivers nothing further from that contact. An erase contact must be claimed with
  `true` on its DOWN or its path and release are never seen.
- **Page taps had to move into the overlay.** With raw drawing live the SDK owns the host view's
  touch stream and the release never reaches the reader's gesture detector.

Erase by sweep and EPD ghosting are both resolved as of 2026-09-17, device-verified. The
ghosting work turned on two findings worth carrying: the driver keeps its own ink layer
below Android's framebuffer (invisible to `screencap`, untappable, survives a page turn),
and repainting the transparent ink SurfaceView does nothing because the page is drawn by
Compose underneath it. Wiping that layer and refreshing the panel are separate needs — a
page turn wants only the wipe, an erase wants both scoped to the rectangle it touched.
See `docs/onyx-pen-thread.md` §T7; remaining defects are listed in CLAUDE.md.

### Ink is anchored to a block, not to words (known, accepted 2026-09-21)

A stroke stores device pixels relative to its **block's** top. Change the font size and the
text reflows while the ink stays put, so an underline drawn under the second line is no longer
under the second line. Observed on device 2026-09-20 and deliberately not fixed.

Two reasons it is liveable. Marks that mean something are no longer ink: asking about a passage
or noting it **consumes** the stroke and leaves an underline on the text plus a `?` or `✎` in
the margin, and both of those are anchored to the words and reflow correctly. What survives as
ink is standalone drawing — and a doodle beside a paragraph belongs to the paragraph, which is
exactly what block anchoring expresses. Font size is also a set-once decision in practice.

If it does become worth fixing, the shape is re-anchoring by the text the stroke covers rather
than scaling coordinates. Scaling is wrong: wrapping changes with the font, so a paragraph
gains lines and position does not move linearly. `Stroke.annotatedText` already records what a
stroke sits over, captured at draw time; what is missing is the *scale* it was drawn at, so the
old layout can be reconstructed, the stroke's line and x-offset found in it, and the same
offset located in the current layout. Two new `Stroke` fields plus a re-measure pass in the
render path — and it touches stroke positioning on the pen path, so it wants the device.

## 13. Reader signals — adapting the next chapter (built 2026-09-17)

`memory.md` remembers what the book *taught*. This is the other half: what the *reading* looked
like. Proposed 2026-09-15, built 2026-09-17 — `ReaderSignals` in `:core`, written to
`reader.md`, fed into `buildChapterPrompt`. What shipped differs from the proposal below in
three ways, each deliberate:

**No model call.** The note said this should ride along with the `MemoryUpdater` call. It is
assembled deterministically from what the reader did instead: questions asked (parsed from the
chapter's `notes/<slug>.qa.md`), passages marked with Ask where no question was typed, and
notes written. That costs nothing and is unit-testable, but the real reason is that it states
only what the reader actually did — a model pass would reintroduce exactly the inference this
section exists to avoid, one indirection further from the evidence.

**Recorded when the NEXT chapter is compiled**, not at chapter end. That makes "feed forward
only" true by construction rather than by discipline, needs no new trigger, and survives the
app being closed mid-chapter. `CourseStore.appendReaderNotes` refuses a chapter it has already
recorded, so the pass runs over the whole course on every compile and is idempotent.

**Three signals, not five.** Questions, marked-but-unasked passages, and notes are all
persisted already. Exercise reveals are in-memory only, and re-reads need a history of
`last_block_index` that nothing keeps — both need new capture, and neither is worth adding
until the three cheap ones prove useful. Recurrence across chapters is still open below;
`reader.md` accumulates, so the model sees repeats even though nothing detects them.

The premise: every time the reader selects a passage and asks about it, they have told us
something — they stopped reading, and here is the sentence that stopped them. Today that signal
is used once, to answer the question, and then discarded. Feeding it forward lets chapter N+1
open with the recap the reader actually needs, which is the thing a tutor does and a chat window
structurally cannot.

### Store observations, not scores

**Do not ask the model for a confidence number.** An LLM rating "how well did they understand
this?" from one exchange is poorly calibrated, and a number invites false precision: `0.6` vs
`0.7` means nothing but reads as if it does, and every downstream prompt will treat it as fact.

Store what was asked instead. *"Asked twice what a gradient actually is, and whether it is the
same thing as slope"* is more useful in a prompt than `gradients: 0.6`, and it fails honestly —
a wrong observation can be read and seen to be wrong, where a wrong number just looks precise.

### The signals already exist and are currently discarded

All of these are captured today and thrown away. They are observations of behaviour rather than
a guess about a person, which makes them better evidence as well as cheaper:

- the **exact passage** selected — quote and block index are both stored already
- **how many questions** a chapter drew, and where in the chapter they clustered
- **recurrence** — the same concept asked about again in a later chapter. The strongest signal
  available, and the one that most clearly means "the first explanation did not land"
- **exercise reveals** — `revealed` is tracked; revealing without attempting is meaningful
- **re-reads** — `last_block_index` moving backwards

### Shape

A `reader.md` beside `memory.md`, per course. Plain, human-readable, and **editable by the
user** — which matters more here than anywhere else in the app: if the book is going to form
opinions about someone's understanding, they need to be able to read them and cross one out.
It also keeps the promise made in §1, that a course stays inspectable.

Written by the same cheap follow-up call that already appends to `memory.md` at chapter end
(`MemoryUpdater`), so this adds a section to an existing call rather than a new pipeline stage.

```markdown
## Chapter 5 — how the reading went
- Asked twice about what a gradient *is*, both times on the "slope" paragraph.
- Revealed the step-size exercise without attempting it.
- Re-read the foggy-mountain section.
- Did not ask anything about loss functions (introduced ch4, used freely here).
```

### Two boundaries

1. **Bound the effect.** "Open with a short recap of X, which the reader asked about twice" is
   useful. Letting the generator restructure a chapter around a perceived weakness gets
   patronising quickly, and the reader has no way to say "I have understood that since".
2. **Feed forward only.** Use it for the *next* chapter; never silently regenerate earlier
   ones. A book that rewrites itself underneath the reader is disorienting, and those pages
   carry their annotations and ink.

### Open

- Does a question about a passage mean confusion, or curiosity? Probably both, and the
  distinction may not matter — either way it is a place worth revisiting.
- Recurrence detection needs concept identity across chapters, which is the same problem
  `memory.md` already solves informally. Reuse rather than invent.
- Should the reader be *told* the next chapter was adapted? Leaning yes, in one line — silent
  adaptation is where this starts to feel like surveillance rather than teaching.

## 14. Library housekeeping (built 2026-09-21)

The three gaps that made the app read as a prototype to anyone who was not its author —
named on 2026-09-15, built together.

**Delete a course.** `CourseStore.deleteCourse` is the only destructive operation in the
store, and it is deliberately fussy: the directory has to sit directly under the courses root
and contain a `course.json`, so a recursive delete can never be aimed at something else. It
also clears `last_read.json` when that pointed at the doomed course, or the next cold start
opens a book that is not on disk. The confirm dialog counts what is about to go — written
chapters, notes, questions, bookmarks, inked chapters — because the files ARE the book and
there is no trash to fish it back out of.

**Search.** `CourseSearch`, over the same BLOCKS the reader paginates rather than raw file
offsets, so a hit carries a block index and opens at the right page whatever the current font
size. Case-insensitive substring of the whole query: not stemming, not fuzzy, not
all-terms-anywhere — a reader typing "gradient descent" means those two words in that order,
and a search that quietly broadens the question is one you cannot predict or narrow. Fence
bodies are searched, because a recap is often where a concept got its clearest one-line
statement. The reader's own marks lead the results: there are few of them and they are their
own words, so a note about the thing you are looking for beats the paragraph that prompted it.
No index is built — reading every chapter of one book off disk per keystroke is milliseconds,
and an index is a thing that can fall out of step with the files.

Results scroll, unlike the Contents. The no-scrolling rule is about the reading surface: a
page of a book has a fixed extent and pretending otherwise is what makes e-readers feel wrong.
A result list has no such nature; it is machinery, like the Library.

**Questions split from notes in the Contents.** They shared a "Notes" section, which meant a
question rendered with the write-glyph and an empty body — asks carry no note text, their
words live in the chapter's Q&A file. The split is not taxonomy, it is volume: a question is
one gesture and a note is typed on an e-ink keyboard, so over a book the questions vastly
outnumber the notes and merged lists drown the scarcer, more valuable thing. Notes lead;
questions follow, each showing the question it asked, matched to the transcript by passage.
A passage marked with no question typed says "no question yet" — a real state, which
`ReaderSignals` reads as "stopped here but could not put the difficulty into words".

## 12. Open questions (revisit while building)

- Exercise answers: reveal-in-place vs. end-of-chapter answer pages?
- Should daily pacing split chapters into "sessions" (reading positions with time estimates), or is chapter granularity enough for v1? (Lean: chapter granularity is enough.)
- ~~Regeneration UX: "this chapter didn't land, redo it with feedback" — where does user feedback slot into the prompt?~~ **Settled 2026-09-21**: its own `THIS IS A REWRITE` section after the chapter spec, carrying the reader's reason verbatim and asking for a different route to the same objectives. Not noted in `memory.md` — that file records what the book taught, and a rejected draft taught nothing.
- Gemini structured-output mode for outline JSON vs. parse-from-text? (Lean: structured output.)
