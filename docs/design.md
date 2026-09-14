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
└── figures/             # phase 3: generated chapter illustrations
    └── 01-intro-1.png   # <chapter-slug>-<nth figure in that chapter>
```

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
  "created": "2026-08-07",
  "chapters": [
    {
      "n": 1,
      "slug": "01-intro",
      "title": "Why Kotlin feels weird coming from Python",
      "objectives": ["...", "..."],
      "est_minutes": 40,
      "status": "done | reading | generated | pending",
      "last_page": 12
    }
  ]
}
```

Rules: `course.json` is the only file the app mutates on ordinary progress updates. Chapters are immutable once generated (regeneration writes a new revision, e.g. `01-intro.r2.md`, and updates the slug pointer — never silently overwrite something the user annotated).

## 2. Chapter format

Structured Markdown with a small set of fenced custom blocks the renderer understands:

- ` ```exercise ` … ` ``` ` — interleaved exercise; renderer shows it as a boxed activity with a "reveal answer" page-flip (answer stored in the block, hidden by default).
- ` ```recap ` — end-of-section redundancy blocks (the Head First-style "repeat it differently" trick).
- ` ```figure ` — a generated illustration. The `alt="…"` param is written by the chapter model and doubles as the *drawing brief*; once drawn, a `src="…"` param naming a file in `figures/` is written back into the same fence. A slot with no `src` yet renders the alt text in a bordered box, which is what every figure looked like before phase 3.

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
4. **Reader** — paginated chapter view. Tap right/left half = page forward/back. Text selection → "Ask" action → chat panel. E-ink discipline: full-page refresh on page turn is fine; never animate.
5. **Chat panel** — anchored to a selection (quoted at top) or chapter-general. History persisted to `notes/<ch>.qa.md`.
6. **Notes** — typed per-chapter notes (phase 2 replaces/augments with ink canvas).
7. **Settings** — Gemini API key (Keystore-encrypted), model name, storage location.

## 5. Pagination approach (the main renderer risk)

Options, in order of preference — spike this first when building the reader:

1. Native Compose: measure rendered Markdown into page-sized chunks (custom layout that binary-searches text breakpoints per page). Full control, no web stack, best e-ink behavior. More work.
2. WebView + CSS paged/column tricks (render chapter HTML, page = viewport-width column offsets). Faster to ship, heavier, and WebView on Boox can be quirky.

Decision deferred until the spike; whichever is chosen, the reader consumes the same chapter Markdown.

## 6. Phasing

- **Phase 1 (MVP)**: everything in CLAUDE.md's MVP list. Success = one full course read on the Boox.
- **Phase 2**: stylus notebook (Onyx pen SDK, raw ink, per-chapter canvas), note ↔ chapter linking, notes context available to chat ("track selection in chapter and notes window").
- **Phase 3**: generated figures filling the `figure` slots — *done for standalone sketches* (see §7). Still open: spaced-repetition hooks from exercises.
- **Someday/maybe (explicit non-goals for now)**: other platforms (iPad), EPUB export, sharing courses, any backend.

## 7. Figure illustration (phase 3)

Built 2026-09-10. The original plan here said "image model output post-processed to high-contrast e-ink-friendly line art", framed around *diagrams*. That framing was wrong and the decision was revisited deliberately.

**What the figures are for.** Not reference material — mood. Head First's pictures are what stop a study page reading as a wall of text: a silly visual metaphor, a character mid-reaction. Correct-but-boring boxes-and-arrows lose to charming-and-approximate for that job. A semantic diagram DSL rendered natively in Compose was designed and rejected on exactly this ground: it would have produced accurate schematics nobody enjoys looking at.

**How it works.**

- `ChapterIllustrator` (`:core`) walks a chapter's pending figure slots and calls an image-capable Gemini model ("Nano Banana", `gemini-3.1-flash-lite-image` by default, overridable in Settings) once per slot. Roughly 3 cents a picture; a 12-chapter course lands near $1.50.
- Requests go through the same `generateContent` endpoint as every text call — `KtorGeminiClient.generateImage` differs only in asking for the `IMAGE` response modality and reading `inlineData` instead of `text`. Aspect ratio and size go in `generationConfig.imageConfig` as plain strings (`"4:3"`, `"1K"`). **Not** the sibling `generationConfig.responseFormat.image`: that field exists and looks equivalent, but it wants protobuf enum names (`ASPECT_RATIO_FOUR_BY_THREE`, `IMAGE_SIZE_ONE_K`) and 400s on the human-readable forms. A 400 on the image config retries once without it, so a version skew costs the aspect ratio rather than the picture.
- `SKETCH_HOUSE_STYLE` in `FigurePrompt.kt` is appended verbatim to every request. It is the single thing standing between a coherent-looking book and a pile of generic AI art, so it lives in one constant and is tuned in one place. It asks for loose greyscale pen sketches, forbids fine detail (a 7" panel throws small marks away) and forbids lettering of any kind (an image model's rendering of words can't be trusted in something someone is learning from).
- Everything is best-effort and idempotent. Pictures are drawn *after* the prose is saved, so a failure can never cost the user their chapter; slots that already have a `src` are skipped, so a retry only fills what's missing. The reader's ≡ menu grows a "Draw N figures" row whenever a chapter has outstanding slots.

**Verified on the emulator 2026-09-10** against the live API: prose → figure slot → generated sketch → `src=` written back → picture on the page, plus the alt-text fallback for an unfilled slot and the menu's retry path. The one output defect found was a light grey background wash showing as a visible rectangle on the white page — fixed by tightening the background rule in the house style, which is exactly the kind of thing that constant exists to absorb.

**Constraints that shaped it.** Figures are requested and displayed at a fixed 4:3 (`FIGURE_ASPECT`), which is what lets the paginator reserve a figure's height without opening the file. Rendering forces saturation to zero — the panel is greyscale, and unlike the UI chrome (pure black and white, to dodge dither patterns) a sketch is *meant* to dither, but a colour cast would muddy it.

**Deliberately not done yet.** A recurring cast of characters across a course — the image models support it via reference-image consistency, and it's a large part of why Head First feels alive, but it needs reference generation, storage and per-call plumbing. Standalone pictures first. Also unbuilt: `type=` on the fence to dispatch different figure kinds (a structural renderer could still slot in beside the sketch path later), and any in-image text such as speech bubbles.

## 8. Open questions (revisit while building)

- Exercise answers: reveal-in-place vs. end-of-chapter answer pages?
- Should daily pacing split chapters into "sessions" (reading positions with time estimates), or is chapter granularity enough for v1? (Lean: chapter granularity is enough.)
- Regeneration UX: "this chapter didn't land, redo it with feedback" — where does user feedback slot into the prompt? (Probably appended to the chapter spec + noted in memory.md.)
- Gemini structured-output mode for outline JSON vs. parse-from-text? (Lean: structured output.)
