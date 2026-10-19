# zhuzha.app

One static page, no build step. Deploy this folder as-is.

- `index.html` — the page
- `privacy.html` — a copy of `../play/privacy-policy.html`, which is the source of
  truth. Play links to this URL, so re-copy it whenever the policy changes:
  `cp docs/play/privacy-policy.html docs/site/privacy.html`
- `delete.html` — the data-deletion page Play's Data safety form demands a URL
  for. Served at `/delete`. It is not a form: there is no account and no server,
  so there is nothing to request — the page says how to delete everything
  yourself and gives the contact address for anyone who doubts it.
- `icon.svg` — the launcher mark, also the favicon
- `zhuzha.svg` — her portrait, in the page body. A DIFFERENT image from the mark
  and from the launcher icon, which are unchanged and stay as they are.

The only external request is Google Fonts (Literata, IBM Plex Mono). Both have real
fallbacks, so the page still reads if that request fails.

Pages serves `privacy.html` at `/privacy` and 308-redirects the `.html` form, so the
canonical URL — the one to give Google Play — is `https://zhuzha.app/privacy`.
The same applies to `delete.html` at `https://zhuzha.app/delete`.

**Both URLs are filed with Google Play and must keep resolving.** A privacy
policy or deletion URL that 404s is an enforcement matter, not a broken link.

Deploy: `npx wrangler pages deploy docs/site --project-name zhuzha --branch main`


## Redrawing `zhuzha.svg`

Traced from a pencil sketch of her, one bit, no greys — which is the reason it is
on a page that argues against greys. The sketch is not in the repo: her likeness is
carved out of the GPL (see the root `README.md`), so it stays out of a public tree.

    sips -c 1646 1619 --cropOffset 212 159 sketch.jpg --out c.bmp -s format bmp
    mkbitmap -n -s 1 -t 0.50 c.bmp -o c.pbm        # flat 1-bit, no filter
    potrace -s -t 200 -O 0.6 c.pbm -o zhuzha.svg   # -t 200 for a bolder read

Then three background construction strokes are removed by hand. potrace emits one
`<path>` element per region, so each stroke comes out whole — find them by bounding
box, they are the ones hard against the left edge.

Three things that bite, each learned the hard way:

- **Trace at full resolution.** Downscaling before the threshold merges her eye
  highlights and she comes out a flat mask instead of a portrait.
- **An XML comment cannot contain `--`**, and every flag above has one. An SVG in an
  `<img>` is parsed as strict XML, so the whole file silently fails to render. That is
  why the commands live here and not in the file's own header.
- **Do not invert it for dark mode.** An abstract mark inverts happily; a portrait
  inverted is a photographic negative, and she comes out a white dog with a black
  chest above a caption saying the opposite. It keeps a white ground in both themes,
  the way a plate in a printed book does.
