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

**The text lives in `full-description.txt`, not in a fenced block here.**
Open that file, select all, copy, paste. The fence used to hold it and
copying out of one flattened the line breaks, which is most of why the first
paste came out as a slab.

### No HTML. Plain text only.

Sources say the field takes `<b>`, `<i>`, `<br>` and friends. **The Console
showed them literally** when pasted on 2026-10-20 — see
`~/dev_pers/screenshots/Screenshot 2026-10-20 at 13.52.52.png`, where
`<b>BUILT FOR E-INK FIRST</b>` renders with its angle brackets in the text.
Whatever the documentation says, that is the field in front of us, so the
description carries no tags at all.

What does the formatting instead:

- **A blank line between every paragraph.** This is the only separator that
  matters, and the only one that can be lost in a bad copy — which is what
  happened. If the pasted result runs together, the newlines did not survive;
  re-copy from the `.txt`.
- **Section headings in CAPITALS on their own line.** Not real formatting, but
  it survives anything.
- **Bullets as the character `•`**, one per line.

2739 characters.

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
