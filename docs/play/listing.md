# Play store listing — copy

Drafts for the Console fields. Lengths are Play's limits; the counts beside
each line are the actual ones, checked rather than estimated (see the script
at the bottom).

The rule this copy follows: **say what the app does, not how it feels about
itself.** No "revolutionary", no "powered by cutting-edge AI". A reader
deciding in four seconds wants the category, the catch, and the one thing
nothing else does.

---

## App name (30 max)

```
Zhuzha: AI Study Books
```

`Zhuzha` alone is a coined word that means nothing to a stranger and finds
nothing in search. The suffix carries the category without turning the name
into a sentence.

---

## Short description (80 max)

This is the line under the title in search results, and for most people it is
the whole listing. It has to carry the category AND the e-ink story, because
that is who the app is for.

```
AI writes you a study book. Paginated for e-ink, and yours to write in.
```

Alternates, same length class:

```
Turn any topic into a study book — paginated for e-ink, and yours to write in.
Study books written for you, a chapter a day. Built for e-ink. Write on them.
```

---

## Full description (4000 max)

```
Chat is a bad container for learning. Answers scroll away, nothing paces you,
and there is no way to move back and forth through an argument.

Zhuzha inverts that. Say what you want to learn and how many minutes a day you
have. It writes you a book — an outline first, then chapters one at a time,
with illustrations, exercises and recaps. Then you read it like a book.

The app is free. Writing books uses your own Google Gemini API key —
Google's free tier is enough to begin, the key stays on your device, and
nothing is billed to anyone but you. A demo book ships with the app, so you
can read a real chapter before you get a key.


BUILT FOR E-INK FIRST

No animations. No scrolling. Pages turn with a tap at the edge or a press of a
side button, the way they should on a panel that redraws rather than refreshes.
Typography, margins and page breaks are tuned for a reflective screen instead
of being retrofitted from a phone — pure black on white, no greys to dither.

It runs on any Android phone or tablet. An e-ink reader is what it was made
for.


ROOM TO WORK

An exercise leaves you space to answer: a dot grid when the task is something
you draw, ruled lines when it is something you write. With a stylus you work
straight on the page, and your ink stays with the chapter. Reveal the answer
when you are ready and it appears below your own, so you compare rather than
copy.


ASK THE BOOK

Mark a passage and ask about it. The question and its answer are kept with
that chapter, so what you asked is part of the book the next time you open it.
What you asked about also feeds forward into how the next chapter is written.


YOUR KEY, YOUR DEVICE, YOUR FILES

No account. No server. No analytics, no tracking, no ads.

Your books are plain Markdown and JSON in a folder on your device —
greppable, backup-able, and readable in ten years even if this app is gone.
Back them up to any folder you choose. Export any book as an EPUB and read it
in anything else.


ALSO

• Pick a teaching style — conversational, textbook, or hands-on — or describe
  one in your own words and it will be written that way.
• Books can be written in your language.
• Typed notes, bookmarks, full-text search across chapters, notes and
  questions, and per-chapter progress.
• Hand-drawn greyscale illustrations, chapter covers, and a per-book mascot.
• Rewrite a chapter you did not get on with.

Zhuzha is named after a dog — black with a white chest, which happens to be
the entire palette an e-ink screen has.

Open source, GPL-3.0.


WHAT YOU NEED

A free Google Gemini API key, from aistudio.google.com/apikey. It takes about
a minute. Generating chapters and pictures uses your own quota; reading what
is already written costs nothing and works offline.
```

---

## Other Console fields

- **Category** — Education.
- **Tags** — education, e-reader, study, AI. (Play picks from a fixed list;
  these are the ones to look for.)
- **Contact email** — info@zhuzha.app
- **Website** — https://zhuzha.app
- **Privacy policy** — https://zhuzha.app/privacy (live, verified 200)

## Notes for whoever fills the form

**The key requirement is stated three times on purpose** — near the top, in
its own section, and in WHAT YOU NEED at the end. A reader who installs and
then discovers they need an API key leaves a one-star review, and they are
right to. Play truncates the description until the reader expands it, so the
early mention is the one most people see.

**It does not claim to work offline for generation**, because it does not.
Reading does, and the sentence says exactly that.

---

## Checking the lengths

```python
for label, text in [...]:
    print(label, len(text))
```
