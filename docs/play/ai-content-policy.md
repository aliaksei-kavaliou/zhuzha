# Play's AI-Generated Content policy — what InkFirst owes

InkFirst's entire product is generative-AI output, so this policy applies in
full. It has two halves. We are strong on one and have nothing on the other.

## Half 1 — prevent Restricted Content: ALREADY BUILT

Play requires that apps generating AI content prohibit and prevent the
generation of Restricted Content. This is the three-gate screener (design.md
§19), and it is a better story than most apps can tell:

- **Request gate** — screens the course request before anything is written
- **Outline gate** — screens the model's own reading of the request, which is
  far more concrete than the sentence that produced it
- **Chapter gate** — reads the actual generated text before it is saved, and
  sits ABOVE the rewrite teardown so a refusal leaves the book untouched
- `JUDGING_TEMPERATURE = 0.0`, because the verdict was a coin toss until it was
  pinned
- The refuse criterion is a PRINCIPLE followed by examples, because an
  enumeration let "how to kill people quickly" through

Worth describing in the listing and keeping ready if review asks. It is
evidence of deliberate design, not a filter bolted on.

Note the deliberate fail-open: the gates allow on API error. That is right for
a BYO-key personal reader and is worth being able to explain if asked — it is
not an attempt to evade moderation, it is a refusal to block a reader because
their connection dropped.

## Half 2 — in-app reporting: BUILT 2026-10-09

> Apps that generate content using AI must contain in-app user reporting or
> flagging features that allow users to report or flag offensive content to
> developers without needing to exit the app.

Two entry points, one dialog:

- **Reader menu → Chapter → "Report this page"**, next to Rewrite, because
  those are the two answers to the same discovery — this page is wrong.
  Rewrite is what you do about YOUR copy; report is what you do about the
  model that wrote it.
- **The Ask panel, under any answer.** Aliaksei's suggestion, and right about
  WHEN people report: a fresh answer is the likeliest thing to be offensive
  and the easiest moment to flag. It is the second entry point rather than
  the only one, because the chapters are the bulk of what the app generates
  and a reader mid-page needs somewhere to go.

### The ordering is the design

There is no backend and there is not going to be one. The requirement is
about the reporting SURFACE being in-app, not about where the report lands —
so the report is WRITTEN FIRST, to `reports.md`, and sending is a separate
offer afterwards. Declining to send still leaves a complete record. A report
that only exists if an email client opens is not an in-app report.

Sending uses `ACTION_SENDTO` with a `mailto:` URI, so the chooser offers mail
clients rather than every app that can take text, and a device with no mail
client loses the convenience and not the record.

### Two decisions worth not re-litigating

**The file sits at the courses ROOT, not inside the book.** A report must
outlive what it complains about — deleting a book you reported should not
delete the complaint — and one file is what you can actually send. The
backup's shelf archive already sweeps everything at the root, so this is
backed up without teaching the backup anything new.

**A report QUOTES the passage.** "Chapter 2, page 9 was offensive" is a
dangling pointer within one tap of being made: rewriting a chapter replaces
every word and repaginates. The quote is what keeps the report meaningful
afterwards, and it is capped so a report cannot become a chapter.

It also records WHICH of the three AI outputs it is about — page, answer or
picture — because that decides what would be fixed: the chapter prompt,
`QaGenerator`, or the illustrator and its house style.

### Verified

Ten unit tests on the format and the file, including the one that matters:
every line of a multi-paragraph passage stays inside the block quote, or the
remainder renders as the reader's own note and puts words in their mouth.

Walked end to end on the emulator at 320x640 — menu, dialog, four reasons,
note field, saved state, and the written `reports.md` read back off the
device. NOT yet exercised: the email intent (no mail client on the emulator;
it fails closed by design) and the Ask-panel entry point (needs a book with
existing Q&A).

## Sequencing

This is a code change, so it belongs with the targetSdk/signing work rather
than with the listing paperwork — it must exist in the build that goes to the
12 closed testers, not be added later.
