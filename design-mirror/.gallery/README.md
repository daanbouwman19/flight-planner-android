# A local render check for the mirror

`/design-sync` runs its own Playwright pass, but it can only be started by the user.
This is the same check on demand, and it is the one that catches the failure a green
build cannot: a component that compiles, typechecks and renders to nothing. The
`Locale.ROOT` bug in `DesignTokenExport` — every Expressive shape path emitted with
comma decimal separators — was invisible until a screenshot was looked at.

```bash
cd design-mirror
npm i --no-save playwright        # chromium is already in the user cache
npx esbuild .gallery/entry.tsx --bundle --outfile=.gallery/bundle.js \
  --jsx=automatic --format=iife
node .gallery/shoot.mjs           # writes .gallery/gallery.png
```

`shoot.mjs` fails loudly on a console error and lists any cell under 40px tall —
an empty cell is the defect that looks identical to success in a passing build.

`?t=cockpit`, `?t=chart` and `?t=brandDark` on the page switch schemes; the four
were checked together, because a colour chosen against one ground can vanish on
another.

The bundle and the screenshots are generated and gitignored. `entry.tsx` is not
exhaustive — it holds whatever was last being verified.
