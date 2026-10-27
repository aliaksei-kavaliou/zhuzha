# Console checklist — first closed test

Everything here is form-filling in the Play Console, which needs your login.
What this file is for is so no answer has to be invented at the keyboard.

## Before you open the Console

- [x] **`pre-test-fixes` is merged into `main`** (checked 2026-10-25 with
      `git branch --merged main`). The question this asked — whether the
      testers would get something that is not on `main` — is settled, and the
      bundle below is built from `main` itself.

## The build

`app/build/outputs/bundle/release/app-release.aab` — 16.6 MB, `jar verified`
against the upload key, `versionCode 1`, `versionName 0.1.0`, minSdk 26,
targetSdk 36.

**Rebuilt 2026-10-25 from `main`.** The previous bundle was from 2026-10-20
and predated three code commits — the EPUB answer anchors and cover page, the
shelf wordmark, and the language picker — so it no longer matched what the
device was being read on. `versionCode` stays 1: nothing has been uploaded to
any track yet, so Play has not seen it.

Rebuild with `./gradlew :app:bundleRelease`. The keystore properties live in
`~/.gradle/gradle.properties`; without them the task still succeeds and
produces an UNSIGNED bundle, which Play rejects — so check `jarsigner -verify`
rather than assuming.

**versionCode must rise on every upload, including rejected ones.** Play will
not take 1 twice.

## What is actually left (2026-10-27)

**The app is created in the Console and every question below is ANSWERED.**
Store listing, screenshots, content rating, data safety, target audience,
ads, app access — all entered. The ticked boxes below are a record of what
was answered, kept because the reasoning behind several of them is the
valuable part and will be asked for again at the next version.

Two things remain, both of them tracks rather than forms:

- [ ] **A working version uploaded.** `app/build/outputs/bundle/release/app-release.aab`,
      rebuilt 2026-10-27 from `main` at `0c65334`, `jar verified`,
      versionCode 1, never uploaded to any track.
- [ ] **Internal testing, then closed testing.** Internal first: it has no
      review wait and no tester minimum, and it is the only way to confirm
      that the bundle Play PROCESSES and re-signs installs and runs — which
      is not the same artifact as the APK tested locally. Promote to closed
      once it does.
- [ ] **Twelve testers, opted in, fourteen continuous days** on the closed
      track. The only item here measured in weeks; everything else is an
      afternoon.

## Store listing

Copy is in `listing.md`, counted against Play's limits. Assets are on
Aliaksei's machine at `~/dev_pers/screenshots/play/` and deliberately not in
this repo (see `screenshots.md`).

- [x] App name, short description, full description — `listing.md`
- [x] App icon — `graphics/icon-512.png` (512x512, 32-bit PNG **with** alpha)
- [x] Feature graphic — `graphics/feature-graphic-1024x500.png` (24-bit, **no**
      alpha)
- [x] Phone screenshots — `phone/`, 8 of them, 1080x1920
- [x] Tablet screenshots — `tablet-7in/`, 8 of them, 1264x1680, into the
      7-inch slot. Exactly at Play's cap of 8 per device type; adding one
      means retiring one. The two this set replaced are kept, not deleted, in
      `tablet-7in/superseded/`.
- [x] Category: Education · Contact email: info@zhuzha.app ·
      Website: https://zhuzha.app
- [x] Privacy policy: https://zhuzha.app/privacy

## App content declarations

- [x] **Privacy policy URL** — as above, live and reachable without a login.
- [x] **Ads** — No. There are none, and no ad SDK is on the dependency list.
- [x] **App access** — all functionality is available without logging in.
      There are no credentials to give a reviewer. Say so: the app needs the
      reader's own Gemini API key to WRITE a book, and ships with a demo book
      so a reviewer can read one without obtaining a key. Put that sentence in
      the instructions field — a reviewer who meets an unexplained key prompt
      and cannot get past it is a rejection.
- [x] **Content rating** — the IARC questionnaire. Answers that are settled:
      no violence, sex, profanity, controlled substances, gambling or
      purchases in the app itself; **no user-to-user interaction, no sharing,
      no location**. The one that needs care is the AI-generated-content
      question, which Play asks about directly now: the app generates text and
      pictures on the reader's request. Answer it as yes and lean on what is
      actually built — the three screening gates (request, outline, chapter)
      and the in-app report path — rather than claiming the output is bounded.
      Under-declaring here is the common way to get re-rated later.
- [x] **Target audience — 18+ ONLY.** Not a judgement call: the Gemini API
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
- [x] **Tick "prevent access for users who declared they are minors".** Play
      marks it optional; here it is the measure that answers the second half
      of the Gemini clause. The 18+ declaration covers "directed toward"; this
      covers "likely to be accessed by", which asks for something done rather
      than something stated. It relies on self-reported account age, so it is
      not a perfect gate — but declining an available safeguard while running
      on an API that demands one is the awkward combination, and it costs
      nothing: anyone who may not hold a Gemini key cannot use the app.
- [x] **Data safety** — answers drafted in `data-safety.md`, including the one
      genuinely debatable call (shared: YES, collected: NO) with the reasoning
      written out. Re-read Play's current wording before signing; it changes.
- [x] **Government apps / financial features / health** — none apply.
- [x] **AI-generated content policy** — `ai-content-policy.md`. Both halves are
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

## Where this stood at the end of 2026-10-20

Done: the bundle (signed, from `main`), the listing copy, every Console
declaration, and 19 store assets that validate. The Console app draft is
created; what remains there is the closed-testing track itself.

Carried forward, neither blocking the upload:

- ~~The Boox pass on the margin-bar fix.~~ **Done 2026-10-25, all six cases
  pass**, read off the `InkGesture` log rather than judged by eye. The one
  that mattered was the continuation paragraph, since the risk was the fix
  being too STRICT: block 16 has six lines and the bar returned lines 2-5 —
  exactly the part that page was showing. The blank-page case returned
  `text=<null>` with ZERO extract lines, so the guard fired before the walk
  started and never touched a stale block.

  **It found five real defects, all since fixed**, and that is the argument
  for doing these passes on the device:
    1. A marked paragraph reflowed and lost its last line under the folio —
       the marker gutter came out of the text column. (`f1968b5`)
    2. A passage marked across paragraphs underlined in none of them.
       (`107adeb`)
    3. Ink in a page's blank foot saved, then rendered nowhere and could not
       be erased. (`284365d`)
    4. Opening the Ask panel wrote the mark whether or not you asked
       anything. (`5cbe56a`)
    5. Showing a chip repainted the whole panel — `lockCanvas` mutating the
       queued rect, latent long before any of this. (`a3b18a5`)
  Ask, selection, underline and the Q&A were re-tested on the device
  afterwards and behave.
- **PocketBook on the EPUB.** Books.app renders it correctly, but PocketBook
  is the reader that filed the original report and the one that ignored our
  CSS. Re-export and re-read there.
- ~~The inked exercise answers the wrong question.~~ **Done 2026-10-25.**
  `02-you-write-in-the-book` was a finished mug under an exercise asking for
  the top opening alone. Reshot on the Boox against the ten-circles exercise
  instead — "let your pen spin around two or three times for each circle,
  overlapping the lines freely" — which the ink now answers. One shot does
  both jobs, so the empty-grid `03-room-to-work` retired with it and the set
  is back at 8. Both superseded files are in `tablet-7in/superseded/`.
  The rule that made it work is worth keeping: **`screencap` cannot see live
  driver ink**, so the strokes must be saved and re-rendered before capturing
  — ink that appears in a framebuffer grab is proof it is real saved state.

Added since: `09-how-it-teaches-you` and `10-any-language` are the two
dropdowns that note asked for; `04-your-shelf` came back from `boox-m5.png`,
captured after the shelf learned to sign its own name, so it carries her head;
and `02-you-write-in-the-book` was reshot 2026-10-25. **Every tablet shot is a
real device capture** — `phone/` is the emulator at 1080x1920, `tablet-7in/`
is the Boox, all eight. Raws are at `~/dev_pers/screenshots/go7/raw-20261021/`
and `raw-20261025/`, beside the earlier `go7/Screenshot_2026*.png`.

A trap worth not repeating: **do not date a build from `git log`.** Both the
shelf icon and the language picker were on the device BEFORE their commits
were written, because the code was built and installed first and committed
minutes later. Read `lastUpdateTime` from `dumpsys package`, or the clock in
the status bar of a capture.

Rebuild the bundle before uploading if any code lands after this date:
`./gradlew :app:bundleRelease`, then check `jarsigner -verify`.

## Known, and fine to ship into a closed test

- The pen chip can ink on the Boox. Stylus-only, so no tester will meet it.
- Ink does not reflow when the font size changes. Known and documented.
- The EPUB fixes of 2026-10-16 render correctly in Books.app (verified 2026-10-20,
  from a book exported through the app itself). Not yet re-read in PocketBook,
  which is the reader that reported them.
