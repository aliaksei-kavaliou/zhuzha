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

## Half 2 — in-app reporting: NOT BUILT. This is a release blocker.

> Apps that generate content using AI must contain in-app user reporting or
> flagging features that allow users to report or flag offensive content to
> developers without needing to exit the app.

There is no report affordance anywhere in InkFirst.

### The awkward part: there is no backend to report TO

The app has no server by design and that is not being reconsidered. The
requirement is about the REPORTING SURFACE being in-app, not about where the
report ultimately lands. The shape that satisfies it without a backend:

1. A **"Report this page"** row in the reader's own menu — the menu is already
   the one place a reader operates the book from, and a report is about the
   page in front of them.
2. An in-app sheet, not a browser and not a mailto straight away: reason
   (offensive / harmful / wrong / other) plus an optional free-text box.
3. **Write the report locally** alongside the book — it is the same
   plain-files principle as everything else, and it means the reader keeps a
   record of what they flagged.
4. THEN offer to send it. An email intent is acceptable as the delivery
   mechanism; what must not require leaving the app is the REPORTING itself.

### Why this is cheap here

The report needs the course, chapter and page, and the reader menu already
knows all three. The passage can come from the same block addressing that
search and the reader record already use. No new data model.

### Also worth doing, not strictly required

- A line in the chapter front matter or the Contents saying the book is
  AI-generated. The App Promotion policy requires the listing to reflect
  functionality accurately; being explicit in-product costs nothing and this
  book LOOKS like a real textbook, which is exactly when a reader should be
  told.
- Declare AI-generated content in the Play Console "App content" section and
  answer the IARC rating questions about it honestly.

## Sequencing

This is a code change, so it belongs with the targetSdk/signing work rather
than with the listing paperwork — it must exist in the build that goes to the
12 closed testers, not be added later.
