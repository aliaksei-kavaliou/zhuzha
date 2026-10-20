# Console checklist — first closed test

Everything here is form-filling in the Play Console, which needs your login.
What this file is for is so no answer has to be invented at the keyboard.

## Before you open the Console

- [ ] **Decide whether `pre-test-fixes` is merged first.** The signed bundle
      described below was built from that branch's tip. Uploading a build that
      is not on `main` is fine, but then `main` is not what the testers have,
      which gets confusing about four days in.

## The build

`app/build/outputs/bundle/release/app-release.aab` — 16 MB, `jar verified`
against the upload key, `versionCode 1`, `versionName 0.1.0`, minSdk 26,
targetSdk 36.

Rebuild with `./gradlew :app:bundleRelease`. The keystore properties live in
`~/.gradle/gradle.properties`; without them the task still succeeds and
produces an UNSIGNED bundle, which Play rejects — so check `jarsigner -verify`
rather than assuming.

**versionCode must rise on every upload, including rejected ones.** Play will
not take 1 twice.

## Store listing

Copy is in `listing.md`, counted against Play's limits. Assets are on
Aliaksei's machine at `~/dev_pers/screenshots/play/` and deliberately not in
this repo (see `screenshots.md`).

- [ ] App name, short description, full description — `listing.md`
- [ ] App icon — `graphics/icon-512.png` (512x512, 32-bit PNG **with** alpha)
- [ ] Feature graphic — `graphics/feature-graphic-1024x500.png` (24-bit, **no**
      alpha)
- [ ] Phone screenshots — `phone/`, 8 of them, 1080x1920
- [ ] Tablet screenshots — `tablet-7in/`, 8 of them, 1264x1680, into the
      7-inch slot
- [ ] Category: Education · Contact email: info@zhuzha.app ·
      Website: https://zhuzha.app
- [ ] Privacy policy: https://zhuzha.app/privacy

## App content declarations

- [ ] **Privacy policy URL** — as above, live and reachable without a login.
- [ ] **Ads** — No. There are none, and no ad SDK is on the dependency list.
- [ ] **App access** — all functionality is available without logging in.
      There are no credentials to give a reviewer. Say so: the app needs the
      reader's own Gemini API key to WRITE a book, and ships with a demo book
      so a reviewer can read one without obtaining a key. Put that sentence in
      the instructions field — a reviewer who meets an unexplained key prompt
      and cannot get past it is a rejection.
- [ ] **Content rating** — the IARC questionnaire. Answers that are settled:
      no violence, sex, profanity, controlled substances, gambling or
      purchases in the app itself; **no user-to-user interaction, no sharing,
      no location**. The one that needs care is the AI-generated-content
      question, which Play asks about directly now: the app generates text and
      pictures on the reader's request. Answer it as yes and lean on what is
      actually built — the three screening gates (request, outline, chapter)
      and the in-app report path — rather than claiming the output is bounded.
      Under-declaring here is the common way to get re-rated later.
- [ ] **Target audience — 18+ ONLY.** Not a judgement call: the Gemini API
      Additional Terms say "You must be 18 years of age or older to use the
      APIs", and go on to prohibit using the services through "applications
      directed toward or likely to be accessed by individuals under 18".
      Ticking 13-15 or 16-18 is declaring, in Google's own Console, that the
      app is directed at under-18s, while the Gemini terms it runs on forbid
      exactly that — two contradictory declarations in the same company's
      paperwork. 16-18 does not escape it: 16- and 17-year-olds are under 18.
      It is also simply true, since the app cannot write anything without a
      key its user may not hold until they are 18.
      This checklist said "18+, or 13+ at the lowest" until 2026-10-19, which
      was wrong, and wrong in the direction that gets noticed.
      Consistent with `data-safety.md` answering "Committed to Play Families
      Policy: No".
- [ ] **Tick "prevent access for users who declared they are minors".** Play
      marks it optional; here it is the measure that answers the second half
      of the Gemini clause. The 18+ declaration covers "directed toward"; this
      covers "likely to be accessed by", which asks for something done rather
      than something stated. It relies on self-reported account age, so it is
      not a perfect gate — but declining an available safeguard while running
      on an API that demands one is the awkward combination, and it costs
      nothing: anyone who may not hold a Gemini key cannot use the app.
- [ ] **Data safety** — answers drafted in `data-safety.md`, including the one
      genuinely debatable call (shared: YES, collected: NO) with the reasoning
      written out. Re-read Play's current wording before signing; it changes.
- [ ] **Government apps / financial features / health** — none apply.
- [ ] **AI-generated content policy** — `ai-content-policy.md`. Both halves are
      built: prevention (the three gates) and an in-app report path on both a
      page and an answer.

## Closed testing itself

- [ ] **Twelve testers, opted in, for fourteen continuous days.** It counts
      people who are opted IN, not people who open the app daily — so the
      clock is not at risk from quiet testers, but the feedback is.
- [ ] Easiest list is a Google Group; an email list works too. Testers must
      use the Google account on their device, which is the usual reason
      somebody "cannot see" the app.
- [ ] Send with it: the app needs a free Gemini key to write anything; there
      is a demo book to read first; and what feedback is wanted. Most testers
      are on phones, so say plainly that the stylus features are for e-ink
      hardware and that "Finger draws ink" in Settings is how a phone writes in
      an exercise — and that it takes over page-turning while it is on.

## Known, and fine to ship into a closed test

- The pen chip can ink on the Boox. Stylus-only, so no tester will meet it.
- Ink does not reflow when the font size changes. Known and documented.
- The EPUB fixes of 2026-10-16 render correctly in Books.app (verified 2026-10-20,
  from a book exported through the app itself). Not yet re-read in PocketBook,
  which is the reader that reported them.
