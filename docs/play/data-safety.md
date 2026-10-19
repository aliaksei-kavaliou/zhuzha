# Play Data safety — draft answers

The Data safety form is a **declaration you sign**, and under-declaring is a
common cause of enforcement action. The awkward question for Zhuzha is the
BYO-key one, and it is answered conservatively below. Verify against the live
form text before submitting — Play's wording changes.

## The one genuinely debatable answer

> Does your app collect or share any of the required user data types?

**Yes — shared, not collected.**

The temptation is to answer "no": there is no backend, the developer receives
nothing, and the user supplies their own key, so the traffic is arguably
between the user and Google directly. That reasoning is why this needs writing
down rather than deciding twice.

It is still "shared" under Play's definition, because the APP transfers
user-entered text to a third party (Google's Gemini API) off the device. Play's
carve-outs do not cover it:

- **Not on-device processing** — the text genuinely leaves the device.
- **Not "ephemeral processing"** — that carve-out requires the data not be used
  for anything beyond the immediate request and not be persisted; we cannot
  assert that about Google's side on the user's behalf.
- **Not "user-initiated transfer"** — that covers the user choosing to send
  data somewhere (a share sheet, an export). Generating a chapter is the app's
  own network call.

"Collected" is NO: the developer has no server and receives nothing.

## Data types

| Type | Collected | Shared | Purpose | Optional? |
|---|---|---|---|---|
| **App activity → other user-generated content** | No | **Yes** | App functionality | Required — it is the product |

What falls under it: the topic and prior-knowledge text typed at course
creation, questions asked about passages, the passages themselves, notes, and
the reading record used to shape later chapters.

Everything else is **No / No**: no name, email, address, phone, user IDs, no
location, no financial info, no health, no contacts, no calendar, no photos or
videos, no audio, no files or docs, no browsing history, no search history, no
installed apps, no device or other IDs, no crash logs, no diagnostics, no
advertising data.

## Security practices

- **Encrypted in transit:** Yes — HTTPS to the Gemini API, which is the only
  endpoint the app contacts.
- **Can users request data deletion:** Yes — deleting a book removes its files;
  uninstalling removes everything including the stored key. There is no server
  copy to request deletion of.
- **Committed to Play Families Policy:** No — not a children's app.
- **Independent security review:** No.

## Notes for the listing

- The API key is NOT a declarable data type (it is a credential the user
  supplies and which never reaches the developer), but say plainly in the
  listing that the app requires one. A reviewer meeting an unexplained
  credential prompt is a rejection risk.
- Privacy policy URL is mandatory and must be publicly reachable without a
  login. **Done:** https://zhuzha.app/privacy is live and returns 200 without a
  login (checked 2026-10-16). The repo is public now, so the note that used to
  stand here — "it must be published somewhere public" — is settled.

## Checked against the code, 2026-10-19

Before signing a declaration, the claims above were verified rather than
recalled:

- **The only endpoint the app contacts is `generativelanguage.googleapis.com`.**
  Every other URL in the source is an XML namespace or the `aistudio.google.com`
  link the key dialog offers the reader to tap.
- **No analytics, crash-reporting or ad SDK is on the dependency list** —
  nothing matching firebase, crashlytics, analytics, admob, appsflyer, sentry,
  bugsnag or facebook.
- **The MERGED release manifest declares INTERNET, ACCESS_NETWORK_STATE and
  WAKE_LOCK, and nothing else** (plus an AndroidX-generated receiver
  permission that is not user-visible). The Onyx SDK's ten extra permissions
  are removed and stay removed — read off
  `app/build/intermediates/merged_manifest/release/`, which is where the
  merged set is legible, not off our own manifest, which only shows the
  removal directives.
