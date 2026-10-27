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

**`jarsigner -verify` PRINTS A WALL OF WARNINGS AND THE BUNDLE IS FINE.** Read
the verdict line, `jar verified.`, and ignore the rest. All four are expected
for an Android bundle and will appear on every upload:

- **~1100 × "Entry … is signed in JarFile but is not signed in
  JarInputStream".** One cause: `META-INF/MANIFEST.MF` is not the first entry
  in the zip — `BUNDLE-METADATA/…` is. `JarInputStream` reads sequentially and
  needs the manifest early to count later entries as signed; `JarFile` seeks
  and finds it anyway. The two readers disagree about every entry and it lists
  them all. That is how bundletool writes an AAB.
- **"certificate chain is invalid … signer certificate is self-signed."**
  Correct. An Android signing key IS a self-signed keystore cert; there is no
  CA and Play does not want one.
- **"signatures that do not include a timestamp … expire as early as
  2054-02-28."** Fine: Play wants validity past 2033. Play validates against
  the upload certificate registered with it, not a timestamp authority.
- **"POSIX file permission and/or symlink attributes."** Cosmetic.

What is worth checking locally is the SIGNER, and on an APK that needs
`apksigner`, not `jarsigner` — an APK is v2/v3-signed and has no jar manifest,
so `jarsigner` answers "no manifest" and looks like a failure:

    $ANDROID_HOME/build-tools/<ver>/apksigner verify --print-certs app-release.apk
    Signer #1 certificate DN: CN=Zhuzha, O=Zhuzha

The AAB is the jar-signed one, so `jarsigner -verify` is right for it. Beyond
that, the upload is the real test: Play rejects a mis-signed bundle at once.

**versionCode must rise on every upload, including rejected ones.** Play will
not take 1 twice.

## What is actually left (2026-10-27)

**The app is created in the Console and every question below is ANSWERED.**
Store listing, screenshots, content rating, data safety, target audience,
ads, app access — all entered. The ticked boxes below are a record of what
was answered, kept because the reasoning behind several of them is the
valuable part and will be asked for again at the next version.

Two things remain, both of them tracks rather than forms:

- [x] **A working version uploaded and PUBLISHED to internal testing**
      (2026-10-27). versionCode 1, versionName 0.1.0, built from `0c65334`.
      Two warnings on the review screen, both expected and neither blocking:
      no deobfuscation file (there is no R8, so none exists) and native code
      without debug symbols (all of it is the Onyx pen SDK's prebuilt `.so`,
      which has no symbols to give). The opt-in link is issued.
      **versionCode 1 is now spent** — the repo is already on 2.
- [ ] **Release name is REQUIRED and does not auto-fill.** Use Play's own
      convention — versionCode with versionName in brackets: `1 (0.1.0)`,
      then `2 (0.1.1)`, and so on. Console-internal, never shown to a tester
      or on the store, 50 characters. Keeping the shape matters only because
      versionCode must rise on every upload, so the releases sort and read
      correctly instead of being invented one at a time.
      Release notes may also block the save; one line is enough for internal.
- [x] **Play App Signing needs nothing from you.** For an app created in the
      Console since 2021 it is automatic and mandatory — there is NO enrolment
      prompt, and this file said there was one. Play holds the key that signs
      what users install; the key the bundle was signed with is the UPLOAD
      key, which only proves the upload is from you.
      Confirm it at Setup → App integrity → App signing, which lists both
      certificates. The upload one should read:
      `CN=Zhuzha, O=Zhuzha`, SHA-256
      `A4:E4:83:9C:F1:76:92:6F:9D:70:66:74:24:29:C3:03:7F:DB:3F:EC:2B:E8:84:FD:BC:E2:2A:B6:66:7D:2B:61`,
      valid to 2054.
      **The keystore is therefore recoverable.** Losing it costs an upload key
      reset request, not the app — the old self-signed world, where a lost key
      meant a new listing, is not the one this app is in.
- [ ] **Install from the Play link on the Boox.** The one check nothing
      local can make: the bundle Play processed and RE-SIGNED is a different
      artifact from the APK verified here. Until it installs and opens a book
      from the real channel, the build is unproven.
      The device currently holds a DEBUG build signed with the debug key, so
      Play's copy will not install over it. Back the books up first — the
      Gemini key cannot be backed up, it is Keystore-bound to the app's UID,
      so expect to re-enter it:

          adb -s 178414c8 pull /sdcard/Android/data/app.zhuzha/files/courses \
              ~/zhuzha-courses-backup

- [ ] **Then promote to closed testing.**
      **Internal app sharing is the better tool once bug reports start.** It
      is a separate feature from this track: upload any build, get a link at
      once, NO versionCode rules, no release, no tester list. The track costs
      a bump and a release for every fix; sharing costs a link.
- [ ] **Countries/regions on the CLOSED track.** Per-track, and the track is
      not available until at least one is set. Internal testing is not
      country-targeted, so there is nothing to choose there and nothing
      carries over. Select all: the app is free, no IAP, no ads, nothing
      varies by market. Just make sure wherever the testers actually are is
      included, or they meet "item not available in your country".
      Not a reason to restrict, but worth knowing: **the Gemini API is not
      available everywhere.** A tester in an unsupported region can install,
      open the app and read the demo book, but cannot obtain a key and so
      cannot generate anything. Check their region before chasing "it won't
      make a book" as a bug.
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
- ~~PocketBook on the EPUB.~~ **Tested 2026-10-27. Nothing to fix.** The
  answer links resolve to the RIGHT page — an exercise on p.28 reaches its
  answer — and NeoReader lands exactly. PocketBook lands at the start of the
  reflowed page instead of on the anchor.
  **The differential test is what settles it: PocketBook's OWN bookmarks
  behave identically.** At a larger font one page becomes several, and the
  reader returns to the page start rather than the sub-page position, for
  marks it set itself. A reader whose native bookmarks cannot do this is not
  failing to honour our `id` — it is not resolving sub-page positions at all.
  The anchor is found; only the final scroll is lost, inside a reflow the
  reader owns.
  The only thing that would land every link exactly is one spine document per
  answer, which costs dozens of tiny XHTML files and stops the "Answers" page
  being a readable list — to buy precision in one reader that its own
  bookmarks do not reach. Not worth it. Do not re-open this without a new
  symptom.
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
  from a book exported through the app itself) and in PocketBook and NeoReader
  (2026-10-27). PocketBook lands answer links on the right page but at the
  page start rather than the anchor — its own bookmarks do the same, so it is
  the reader, not the file. See the carried-forward note.
