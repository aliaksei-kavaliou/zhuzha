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
└── ink/                 # phase 2: stroke data, one file per chapter
    └── 01-intro.ink.json
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
- ` ```figure ` — placeholder now (renders the alt-text prominently); becomes real generated diagrams in phase 3. Generating with figure *slots* from day one means phase 3 doesn't require re-generating courses.

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
- **Phase 3**: generated figures filling the `figure` slots (image model output post-processed to high-contrast e-ink-friendly line art), spaced-repetition hooks from exercises.
- **Someday/maybe (explicit non-goals for now)**: other platforms (iPad), EPUB export, sharing courses, any backend.

## 7. Open questions (revisit while building)

- Exercise answers: reveal-in-place vs. end-of-chapter answer pages?
- Should daily pacing split chapters into "sessions" (reading positions with time estimates), or is chapter granularity enough for v1? (Lean: chapter granularity is enough.)
- Regeneration UX: "this chapter didn't land, redo it with feedback" — where does user feedback slot into the prompt? (Probably appended to the chapter spec + noted in memory.md.)
- Gemini structured-output mode for outline JSON vs. parse-from-text? (Lean: structured output.)
