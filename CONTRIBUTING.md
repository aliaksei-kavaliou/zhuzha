# Contributing

This is one person's project that happens to be readable in public. Issues and
patches are welcome; so is reading the code and taking ideas out of it.

## Pull requests

**Nothing merges without my explicit approval.** There is no auto-merge, no
merge-on-green, and a passing CI run is not consent. If a change is sitting
open it is because I have not read it yet, not because it has been refused.

Before opening one, open an issue. Most of the decisions in this codebase were
argued somewhere — `CLAUDE.md` is a log of ninety-odd of them with the wrong
turns left in — and a patch that reverses one of those arguments without
knowing it had been made is a sad way to spend an afternoon.

## What a good change looks like here

- **Boring Kotlin over clever Kotlin.** This code is read far more often than it
  is written, usually months later and usually while something is broken.
- **Say what you proved and what you assumed.** This project has a standing
  habit of distinguishing "measured on the device" from "reasoned about", because
  several bugs here were invisible on an emulator and obvious on the panel. If
  you changed anything about layout, ink, or repainting, say where you saw it
  work.
- **Give the reason, not just the change.** Commit messages here explain why the
  previous behaviour was wrong. A diff that only says what it does is half a
  patch.
- **E-ink rules are not preferences.** No animation, no scrolling in the reader,
  no greys. Each of those is in `CLAUDE.md` with the measurement that produced
  it.

## Bug reports

The useful ones name the device, the font scale, and the page. "Chapter 2 page
11 at font 1.3 on a Boox Go 7" has been enough to find most things in this
repository; "the layout looks wrong" has not.

## Licensing of contributions

The project is GPL-3.0, and your contribution is too.

By submitting one you confirm that you wrote it, or otherwise have the right to
submit it, and you grant Aliaksei Kavaliou a perpetual, worldwide,
non-exclusive, royalty-free and irrevocable licence to use, reproduce, modify
and distribute it, **including the right to release it under different terms,
including proprietary ones**.

**You keep the copyright in what you wrote.** This grant is non-exclusive — it
takes nothing away from you, and you may do whatever you like with your own
work elsewhere.

The reason it is here: GPL only stays a meaningful choice while one person can
still relicense the whole. Without this, a handful of merged patches would
permanently remove the option of ever offering the project on other terms, and
that decision would have been made by accident rather than on purpose.
