# zhuzha.app

One static page, no build step. Deploy this folder as-is.

- `index.html` — the page
- `privacy.html` — a copy of `../play/privacy-policy.html`, which is the source of
  truth. Play links to this URL, so re-copy it whenever the policy changes:
  `cp docs/play/privacy-policy.html docs/site/privacy.html`
- `icon.svg` — the launcher mark, also the favicon

The only external request is Google Fonts (Literata, IBM Plex Mono). Both have real
fallbacks, so the page still reads if that request fails.
