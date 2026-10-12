# Zhuzha

AI-generated study books for e-ink readers.

Chat is a bad container for learning. Answers scroll away, there is no pacing, no
structure, and no way to move back and forth through an argument. Zhuzha inverts
that: the AI **compiles a book**, and the chat lives *around* the book instead of
*being* the product.

You say what you want to learn and how many minutes a day you have. The app generates
an outline, then chapters one at a time, in a teaching style you pick. You read
paginated pages on e-ink, mark passages with a stylus, ask about them, and the book
notices how you read — and carries that forward into the next chapter.

> Personal project, built for one reader on one device. It is in daily use, not for
> sale, and has no accounts, no server, and no telemetry.

## Why it looks like this

The design rules come from the display, not from taste:

- **No scrolling, no animation.** E-ink renders every intermediate frame, so a smooth
  scroll arrives as a crawling smear. Everything paginates — the reader, and the Q&A
  panel too, which jumps a screenful at a time.
- **Pure black on white.** No gradients, no tinted backgrounds. A grey that looks
  subtle on an LCD is mud at 300 DPI on paper-white.
- **Generous targets, page-turn taps.** Narrow bands at the screen edges turn pages;
  the middle half is deliberately inert, because with a stylus in hand most contacts
  with the middle of a page are accidental.

The e-ink constraint is treated as the point rather than a limitation. The book-page
metaphor is the whole idea.

## What it does

- **Course creation** — topic, daily minutes, and what you already know become an
  outline with chapters, objectives, and time estimates.
- **Chapter generation** — chapters are written one at a time against a running
  course memory, so chapter 6 knows what chapter 2 taught.
- **Teaching styles** — chosen per course and stored with it. Conversational
  (concrete before abstract, repeated framings, frequent exercises), formal textbook
  (definition → example → practice), or practical (minimal theory, hands-on first).
- **Illustrations** — loose greyscale sketches drawn per chapter, plus a cover picture
  of the problem each chapter solves, and a per-course mascot marking exercises and
  recaps. These exist to make a study page feel alive, not to be reference diagrams.
- **Reading** — paginated chapters with typography tuned on the actual panel,
  adjustable size, spacing and margins, bookmarks, and hardware page-turn buttons.
- **Marking and asking** — select text with a finger or mark it with the stylus, then
  ask about that exact passage. Questions and answers are kept per chapter as a
  readable transcript.
- **Handwriting** — raw stylus ink with no perceptible lag on supported hardware,
  including erase-by-sweep and tap-a-stroke-to-act-on-it.
- **Reader signals** — the book records how each chapter was actually read: what you
  asked, which passages you marked without asking, what you wrote. That feeds into the
  next chapter's prompt. It is a plain file you can read and edit.

## Architecture

Four decisions carry most of the design:

**No backend, ever.** A single Android app calling the Gemini API directly with your
own key. Nothing to run, nothing to pay for, nothing to shut down.

**Bring your own key.** Your API key is entered in Settings and stored in the Android
Keystore. It is never written to a file, never synced, and never leaves the device
except to Google.

**Local-first, plain files.** Courses are inspectable files on disk, not a database:

```
<course-slug>/
  course.json          outline, progress, annotations, bookmarks
  memory.md            running course memory used to write later chapters
  reader.md            how the chapters were actually read
  chapters/<slug>.md   generated chapter text
  notes/<slug>.qa.md   questions and answers, as readable Markdown
  ink/<slug>.ink.json  stylus strokes
  figures/, badges/    generated pictures
```

The reason is durability. Your study material should be greppable, backupable, and
readable in ten years even if this app stops existing.

**Generation logic is a separate module.** `:core` holds prompts, generation, parsing
and storage, and touches no Android APIs — so it is testable on the JVM, and the door
stays open even though no backend is planned.

Stack: Kotlin and Jetpack Compose, min SDK 26.

## Build and run

Requires JDK 17 and the Android SDK.

```sh
./gradlew assembleDebug                 # build
./gradlew test                          # unit tests (:core and :app)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then open Settings and paste a [Gemini API key](https://aistudio.google.com/apikey).
Nothing works without one, and generation costs whatever Google charges for the models
in use — a Flash-tier text model and a lite image model by default, both configurable
in Settings.

## Hardware

Developed for the **Onyx Boox Go 7**, a 7" Android e-ink device with a stylus. Onyx's
pen SDK is what makes ink land without lag, by drawing straight to the display
controller beneath the normal rendering path.

It is an ordinary Android app, so it installs and runs elsewhere — but the low-latency
pen path is Onyx-specific, and the typography is tuned for 300 DPI paper-white rather
than a phone screen.

## Documentation

- [`docs/design.md`](docs/design.md) — data model, file formats, generation strategy.
- [`docs/onyx-pen-thread.md`](docs/onyx-pen-thread.md) — a working log of the stylus
  integration. Sparsely documented SDK, several counter-intuitive behaviours, and a
  record of what was measured rather than assumed. Read it before touching anything
  about repainting, ghosting, or touch handling.

## A note on names

Zhuzha is the author's dog. She is black with a white chest, which happens to be the
entire palette an e-ink screen has. The name means nothing else, on purpose: every
word that meant something was already an app.

The default teaching style is inspired by the Head First pedagogy. This project is not
affiliated with or endorsed by O'Reilly Media, and uses none of their branding or
trade dress.
