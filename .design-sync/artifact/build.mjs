#!/usr/bin/env node
// Turns the .ds-sync build (ds-bundle/) into the Design System artifact's
// `project/` tree, and writes the exact Artifact-tool calls that publish it.
//
//   node .design-sync/artifact/build.mjs --live-paths
//       → the paths to read from the artifact before building (JSON, for `Artifact read paths=`)
//   node .design-sync/artifact/build.mjs --index <live design-system.json> --live <folder the read saved to>
//       --note "<what changed>" [--bundle ./ds-bundle] [--out .design-sync/.cache/artifact] [--by Daan]
//       [--purge-legacy] [--discard-live <published path>]...
//
// Nothing here invents a value: colours, type, shapes and motion come from
// design-mirror's tokens.json (itself generated from :core:designsystem), the
// prose from .design-sync/artifact/README.md and token-usage.json, the previews
// from .design-sync/previews/ via the .ds-sync build. The artifact type's own
// contract — file paths, the @dsCard marker, the list-shaped tokens.json — is
// documented in the SKILL.md the artifact serves at its own URL.
//
// Truth runs one way, repo → artifact, but the artifact's page is editable and a
// publish replaces every path unconditionally — which is how a cover redesigned on
// the page was put back to the old one by the next re-sync (2026-09-19). So the
// build refuses to overwrite anything edited there: `published.json` beside this
// file records the sha256 of every file the last build staged, `--live` is the
// folder an `Artifact read paths=` of those same paths saved to, and a live file
// matching neither its last-published hash nor what this build wrote was changed on
// the page. The build stops and names the repo source each one belongs in.

import { readFileSync, writeFileSync, mkdirSync, readdirSync, statSync, rmSync, copyFileSync, existsSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { join, dirname, resolve, basename } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const repo = resolve(here, '../..')
const mirror = join(repo, 'design-mirror')

const argv = process.argv.slice(2)
const flag = (name, fallback) => {
  const i = argv.indexOf(`--${name}`)
  return i < 0 ? fallback : argv[i + 1]
}
const has = (name) => argv.includes(`--${name}`)

const BUNDLE = resolve(flag('bundle', join(repo, 'ds-bundle')))
const OUT = resolve(flag('out', join(here, '../.cache/artifact')))
const INDEX = flag('index')
const NOTE = flag('note')
const BY = flag('by', 'Daan')
const LIVE = flag('live')
const DISCARD_LIVE = new Set(argv.flatMap((a, i) => (a === '--discard-live' ? [argv[i + 1]] : [])))
const ARTIFACT_URL = 'https://claude.ai/artifact/Dmjit3682NXRcbiuwninnt'

/** sha256 per published path of what the last build staged — tracked, committed with each re-sync. */
const MANIFEST = join(here, 'published.json')

const read = (p) => readFileSync(p, 'utf8')
const posix = (p) => p.replace(/\\/g, '/')

if (has('live-paths')) {
  if (!existsSync(MANIFEST)) {
    console.error(`${posix(MANIFEST)} does not exist — read call 1 of the last publish.json instead, then build once to create it`)
    process.exit(2)
  }
  console.log(JSON.stringify(Object.keys(JSON.parse(read(MANIFEST)))))
  process.exit(0)
}

if (!INDEX || !NOTE || !LIVE) {
  console.error(
    'usage: build.mjs --index <live design-system.json> --live <folder the Artifact read saved to> --note "<what changed>"\n' +
      '       build.mjs --live-paths   (the paths that read must fetch)',
  )
  process.exit(2)
}
if (!existsSync(join(LIVE, 'project'))) {
  console.error(`--live ${LIVE} has no project/ folder — it should be where "Artifact read paths=[…]" saved the live files`)
  process.exit(2)
}
const kebab = (s) =>
  s
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/\./g, '-')
    .toLowerCase()

rmSync(OUT, { recursive: true, force: true })
const project = join(OUT, 'project')
mkdirSync(project, { recursive: true })

/** Paths written this run, relative to OUT, in the order they were emitted. */
const written = []
function emit(rel, content) {
  const p = join(OUT, rel)
  mkdirSync(dirname(p), { recursive: true })
  if (typeof content === 'string') writeFileSync(p, content)
  else copyFileSync(content.copy, p)
  written.push(posix(rel))
}

/** The preview frame inlines these itself; a copy in a card would end or escape it. */
function refuseUnsafe(what, text) {
  if (/<\/script/i.test(text) || /<!--/.test(text)) {
    throw new Error(`${what} contains "</script" or "<!--", which the artifact refuses`)
  }
}

// ---------------------------------------------------------------------------
// Components: ds-bundle/components/<group>/<Name>/ → project/components/<Name>/
// ---------------------------------------------------------------------------

const components = []
for (const group of readdirSync(join(BUNDLE, 'components')).sort()) {
  const groupDir = join(BUNDLE, 'components', group)
  if (!statSync(groupDir).isDirectory()) continue
  for (const name of readdirSync(groupDir).sort()) {
    const dir = join(groupDir, name)
    if (statSync(dir).isDirectory()) components.push({ group, name, dir })
  }
}
if (components.length === 0) throw new Error(`no components under ${BUNDLE}/components — run the .ds-sync build first`)

// ds-bundle/ is gitignored build output, so it survives a branch switch: a
// component built while another branch was checked out can silently sit here
// after switching back. design-mirror/src/index.ts is the checked-out source's
// own export list, so a name ds-bundle has that index.ts does not export means
// ds-bundle is stale relative to what's actually on this branch.
{
  const currentExports = read(join(mirror, 'src/index.ts'))
  const stale = components.filter((c) => !new RegExp(`\\b${c.name}\\b`).test(currentExports))
  if (stale.length > 0) {
    throw new Error(
      `${BUNDLE} has component(s) design-mirror/src/index.ts does not export on this branch: ` +
        `${stale.map((c) => c.name).join(', ')} — rebuild design-mirror and .ds-sync from the checked-out ref first.`,
    )
  }
}

// _preview/<Name>.css (a story-local .module.css import) and
// _vendor/preview-decorators.js (Storybook decorators) are the other two tags
// .ds-sync/lib/emit.mjs can put in a card's <head>; both must strip here too,
// or the "a relative asset reference survived" guard below fails the build.
const FRAME_TAGS =
  /^\s*<(?:link rel="stylesheet" href="\.\.\/\.\.\/\.\.\/(?:styles|_ds_bundle|_preview\/[A-Za-z0-9]+)\.css"|script src="\.\.\/\.\.\/\.\.\/(?:_vendor\/react(?:-dom)?|_vendor\/preview-decorators|_ds_bundle|_preview\/[A-Za-z0-9]+)\.js"><\/script)>\s*\n/gm

function previewHtml({ group, name, dir }) {
  const src = read(join(dir, `${name}.html`))
  const [marker, ...rest] = src.split('\n')
  const m = /^<!-- @dsCard group="([^"]+)"(?: viewport="(\d+)x\d+")? -->$/.exec(marker)
  if (!m) throw new Error(`${name}.html: unexpected marker "${marker}"`)
  const previewJs = join(BUNDLE, '_preview', `${name}.js`)
  const floor = !existsSync(previewJs)
  const attrs = [`group="${m[1]}"`]
  if (m[2]) attrs.push(`width=${m[2]}`)
  if (floor) attrs.push('floor')

  let body = rest.join('\n').replace(FRAME_TAGS, '')
  if (/href="\.\.\/|src="\.\.\//.test(body)) throw new Error(`${name}.html: a relative asset reference survived`)
  // The frame's own page background is the theme's; a white body would frame a
  // dark picker theme in a white border.
  body = body.replace('background:#fff}', 'background:var(--fp-background)}')
  if (floor) {
    body = body.replace(`<code>${name}.prompt.md</code>`, '<code>README.md</code>')
  } else {
    const js = read(previewJs)
    refuseUnsafe(`_preview/${name}.js`, js)
    body = body.replace(`  <script>\n    var h=React.createElement`, `<script>${js}</script>\n  <script>\n    var h=React.createElement`)
    if (!body.includes(`<script>${js.slice(0, 40)}`)) throw new Error(`${name}.html: could not place the inlined preview module`)
  }
  return `<!-- @dsCard ${attrs.join(' ')} -->\n${body}`
}

/** `export const <Name> = …` preceded by a JSDoc: the doc, as prose. */
function storyDocs(name) {
  const p = join(repo, '.design-sync/previews', `${name}.tsx`)
  if (!existsSync(p)) return new Map()
  const docs = new Map()
  const re = /\/\*\*([\s\S]*?)\*\/\s*\nexport const ([A-Z][A-Za-z0-9]*)\b/g
  for (const match of read(p).matchAll(re)) {
    const prose = match[1]
      .split('\n')
      .map((l) => l.replace(/^\s*\*\s?/, '').trimEnd())
      .join('\n')
      .trim()
    docs.set(match[2], prose)
  }
  return docs
}

function componentReadme({ name, dir }) {
  let md = read(join(dir, `${name}.prompt.md`))
  md = md.replace('(bundle loaded from the root `_ds_bundle.js`)', '(`components/bundle.js`)')
  // TSDoc `{@link X}` survives the .ds-sync slicer verbatim; the page renders markdown.
  md = md.replace(/\{@link ([^}|]+?)(?:\|([^}]+))?\}/g, (_, target, label) => `\`${(label ?? target).trim()}\``)

  // The .ds-sync slicer attaches each story's JSDoc to the *previous* story's
  // fence. Cut those trailing comments and put every story's own doc above its
  // fence, as prose, from the preview source. A shape this doesn't recognize
  // must fail loud: silently shipping the unrepaired markdown reintroduces the
  // exact mispairing bug this function exists to fix, with nothing to catch it.
  const at = md.indexOf('\n## Examples\n')
  // A floor card has no authored preview, so the slicer writes no examples and
  // there is nothing to re-pair. Only that case passes; a preview with no
  // Examples heading still fails.
  if (at < 0 && !existsSync(join(repo, '.design-sync/previews', `${name}.tsx`))) return md
  if (at < 0) throw new Error(`${name}.prompt.md: no "## Examples" heading — story/doc re-pairing cannot run`)
  const docs = storyDocs(name)
  const head = md.slice(0, at)
  const examples = md
    .slice(at + '\n## Examples\n'.length)
    .split(/\n(?=### )/)
    // The heading is followed by a blank line, so the split's first piece is
    // whitespace. Skipped rather than matched: it is the gap, not an example.
    .filter((block) => block.trim() !== '')
    .map((block) => {
      const m = /^### ([A-Za-z0-9]+)\n\n```jsx\n([\s\S]*?)\n```\n?$/.exec(block.trim() + '\n')
      if (!m) throw new Error(`${name}.prompt.md: an example block didn't match the expected "### Name\\n\\n\`\`\`jsx" shape: ${block.slice(0, 80)}...`)
      const [, story, code] = m
      const cleaned = code.replace(/\n\n\/\*\*[\s\S]*?\*\/\s*$/, '').trimEnd()
      const doc = docs.get(story)
      return `### ${story}\n\n${doc ? doc + '\n\n' : ''}\`\`\`jsx\n${cleaned}\n\`\`\`\n`
    })
  return `${head}\n## Examples\n\n${examples.join('\n')}`
}

for (const c of components) {
  emit(`project/components/${c.name}/preview.html`, previewHtml(c))
  emit(`project/components/${c.name}/README.md`, componentReadme(c))
  emit(`project/components/${c.name}/${c.name}.d.ts`, { copy: join(c.dir, `${c.name}.d.ts`) })
}

// ---------------------------------------------------------------------------
// The bundle, its stylesheet, the cover, the fonts, the brand book
// ---------------------------------------------------------------------------

{
  const js = read(join(BUNDLE, '_ds_bundle.js'))
  const nl = js.indexOf('\n')
  const header = /^\/\* @ds-bundle: (\{.*\}) \*\/$/.exec(js.slice(0, nl))
  if (!header) throw new Error('_ds_bundle.js has no @ds-bundle header')
  const { namespace, components: listed } = JSON.parse(header[1])
  const stamped = `/* @ds-bundle: ${JSON.stringify({ format: 4, namespace, components: listed.map(({ name }) => ({ name })) })} */`
  const rest = js.slice(nl + 1)
  refuseUnsafe('_ds_bundle.js', rest)
  emit('project/components/bundle.js', `${stamped}\n${rest}`)
}

{
  const css = read(join(BUNDLE, '_ds_bundle.css'))
  const start = css.indexOf('/* @schemes:start */')
  const end = css.indexOf('/* @schemes:end */')
  if (start < 0 || end < start) throw new Error('_ds_bundle.css has no @schemes sentinels — rebuild design-mirror')
  const stripped = css.slice(0, start) + css.slice(end + '/* @schemes:end */'.length)
  if (!/\[data-theme="fp-system"\]/.test(stripped) || !/linear\(/.test(stripped)) {
    throw new Error('the stripped stylesheet lost fp-system or the spring easings')
  }
  if (/<\/style/i.test(stripped)) throw new Error('bundle.css contains "</style"')
  emit(
    'project/components/bundle.css',
    `/* The four named schemes are not here: the page compiles them into tokens.css from tokens.json.\n` +
      `   This sheet carries what tokens.json cannot say — fp-system under prefers-color-scheme, the\n` +
      `   sampled spring easings — plus every component style. Generated; see design-mirror/build.mjs. */\n` +
      stripped,
  )
}

emit('project/components/Cover/preview.html', { copy: join(here, 'cover.html') })
emit('project/README.md', { copy: join(here, 'README.md') })

const fontFiles = readdirSync(join(mirror, 'fonts')).filter((f) => f.endsWith('.woff2'))
for (const f of fontFiles) emit(`project/fonts/${f}`, { copy: join(mirror, 'fonts', f) })

// ---------------------------------------------------------------------------
// tokens.json — the list shape the page reads
// ---------------------------------------------------------------------------

const tokens = JSON.parse(read(join(mirror, 'src/tokens/tokens.json')))
const usage = JSON.parse(read(join(here, 'token-usage.json')))
const styles = read(join(BUNDLE, '_ds_bundle.css'))

/** Every token needs its note, and every note its token — a stale entry is drift. */
function noted(family, name) {
  const note = usage[family]?.[name]
  if (!note) throw new Error(`token-usage.json: no ${family} usage for "${name}"`)
  return note
}
const seen = { color: new Set(), radius: new Set(), motion: new Set(), type: new Set() }

const THEMES = [
  ['brandLight', 'fp-brand-light', 'Brand Light'],
  ['brandDark', 'fp-brand-dark', 'Brand Dark'],
  ['cockpit', 'fp-cockpit', 'Cockpit'],
  ['chart', 'fp-chart', 'Chart'],
]
if (Object.keys(tokens.schemes).sort().join() !== THEMES.map(([k]) => k).sort().join()) {
  throw new Error(`tokens.json schemes changed: ${Object.keys(tokens.schemes)}`)
}
const perTheme = (pick) => Object.fromEntries(THEMES.map(([scheme, id]) => [id, pick(scheme)]))
// tokens.schemeTone comes from ThemeChoice.isDark() in Theme.kt, not a guess.
const tone = (scheme) => tokens.schemeTone[scheme]

const color = []
const colorToken = (name, pick) => {
  seen.color.add(name)
  color.push({ name, value: perTheme(pick), usage: noted('color', name) })
}
for (const role of Object.keys(tokens.schemes.brandLight)) colorToken(`fp-${kebab(role)}`, (s) => tokens.schemes[s][role])
for (const key of Object.keys(tokens.sky.brandLight)) colorToken(`fp-sky-${kebab(key)}`, (s) => tokens.sky[s][key])
for (const cat of Object.keys(tokens.flightRules.light)) {
  colorToken(`fp-${cat}-container`, (s) => tokens.flightRules[tone(s)][cat].container)
  colorToken(`fp-${cat}-on-container`, (s) => tokens.flightRules[tone(s)][cat].onContainer)
}
// The page paints the ground behind every preview from a token named exactly
// `background` (or `bg`, `page`, `canvas`, `paper`); without one it guesses light
// or dark from the theme's *name*, which puts Cockpit on a cream well. An alias
// keeps one value and gives the page the name it looks for.
colorToken('background', () => '{fp-background}')

const radius = Object.entries(tokens.shapes).map(([name, dp]) => {
  const id = `fp-shape-${kebab(name)}`
  seen.radius.add(id)
  return { name: id, value: `${dp}px`, usage: noted('radius', id) }
})

// Durations are settle times computed by design-mirror/build.mjs from the spring
// constants; read them back from the stylesheet rather than recomputing.
const motion = []
for (const [, name, ms] of styles.matchAll(/^\s+--(fp-motion-[a-z-]+-duration|fp-stagger): (\d+)ms;$/gm)) {
  if (seen.motion.has(name)) continue
  seen.motion.add(name)
  motion.push({ name, value: `${ms}ms`, usage: noted('motion', name) })
}

const SANS = "Roboto, system-ui, -apple-system, 'Segoe UI', sans-serif"
const MONO = "'Roboto Mono', ui-monospace, SFMono-Regular, Menlo, monospace"
for (const stack of [SANS, MONO]) {
  if (!styles.includes(`font-family: ${stack};`)) throw new Error(`font stack not in the stylesheet: ${stack}`)
}
const fonts = []
for (const face of read(join(mirror, 'fonts/fonts.css')).split('@font-face').slice(1)) {
  const family = /font-family: '([^']+)'/.exec(face)[1]
  const weight = /font-weight: ([\d ]+);/.exec(face)[1]
  const file = /url\('\.\/([^']+)'\)/.exec(face)[1]
  if (!fontFiles.includes(file)) throw new Error(`fonts.css names ${file}, which is not in design-mirror/fonts`)
  fonts.push({ family, file: `fonts/${file}`, weight, style: 'normal' })
}

const groups = ['display', 'headline', 'title', 'body', 'label'].map((group) => ({
  name: group[0].toUpperCase() + group.slice(1),
  family: 'sans',
  styles: ['large', 'medium', 'small'].map((size) => {
    const slot = `${group}${size[0].toUpperCase()}${size.slice(1)}`
    const s = tokens.typography[slot]
    if (!s) throw new Error(`typography slot ${slot} missing`)
    const name = `${group}-${size}`
    seen.type.add(name)
    const note = usage.type?.[name]
    if (!note?.usage || !note?.sample) throw new Error(`token-usage.json: no type sample/usage for "${name}"`)
    return {
      name,
      fontSize: `${s.fontSize}px`,
      lineHeight: `${s.lineHeight}px`,
      letterSpacing: `${s.letterSpacing}px`,
      fontWeight: s.fontWeight ?? 400,
      sample: note.sample,
      usage: note.usage,
    }
  }),
}))

for (const family of Object.keys(seen)) {
  for (const name of Object.keys(usage[family] ?? {})) {
    if (!seen[family].has(name)) throw new Error(`token-usage.json: ${family} "${name}" no longer exists — remove its note`)
  }
}

emit(
  'project/tokens.json',
  JSON.stringify(
    {
      name: 'Flight Planner',
      version: 1,
      color: { themes: THEMES.map(([, id, name]) => ({ id, name })), tokens: color },
      type: { fonts, families: { sans: SANS, mono: MONO }, groups },
      radius: { tokens: radius },
      motion: { tokens: motion },
    },
    null,
    2,
  ) + '\n',
)

// tokens.css is what the preview frame actually loads. The page recompiles it
// from tokens.json only on its own save, so a publish alone would leave previews
// on the previous compile — pointing at fonts that no longer exist. Write the
// same compile the page would, in its own format, so a publish is complete.
{
  const css = (v) => (v.startsWith('{') ? `var(--${v.slice(1, -1)})` : v)
  const decl = (t, id) => `  --${t.name}: ${css(t.value[id])};`
  const first = THEMES[0][1]
  const lines = ['/* Flight Planner — generated from tokens.json */']
  lines.push(`:root, [data-theme="${first}"] {`, ...color.map((t) => decl(t, first)), '}')
  for (const [, id] of THEMES.slice(1)) lines.push(`[data-theme="${id}"] {`, ...color.map((t) => decl(t, id)), '}')
  lines.push(':root {', ...[...radius, ...motion].map((t) => `  --${t.name}: ${t.value};`), '}')
  for (const f of fonts) {
    lines.push(
      '@font-face {',
      `  font-family: "${f.family}";`,
      `  src: url("${f.file}") format("woff2");`,
      `  font-weight: ${f.weight};`,
      `  font-style: ${f.style};`,
      '  font-display: swap;',
      '}',
    )
  }
  emit('project/tokens.css', lines.join('\n') + '\n')
}

// ---------------------------------------------------------------------------
// The page-side guard — nothing edited on the artifact is published over
// ---------------------------------------------------------------------------
//
// For every path this build would publish: a live copy equal to what this build
// wrote is a no-op; one equal to what the last build staged is the ordinary
// case — the repo moved, the page did not; anything else was changed on the page
// since the last publish and is not ours to overwrite. The Artifact tool's own
// refusal covers the narrower race of a path changing between the read and the
// publish; this covers the weeks in between.

/** Where a page-side edit has to go to survive the next re-sync. */
function sourceOf(rel) {
  if (rel === 'project/components/Cover/preview.html') return 'verbatim: copy the live copy over .design-sync/artifact/cover.html'
  if (rel === 'project/README.md') return 'verbatim: copy the live copy over .design-sync/artifact/README.md'
  const c = /^project\/components\/([A-Za-z0-9]+)\/(preview\.html|README\.md|[A-Za-z0-9]+\.d\.ts)$/.exec(rel)
  if (c) return `generated from .design-sync/previews/${c[1]}.tsx and design-mirror/src — re-express the change there, ${c[2]} is not a source`
  if (rel.startsWith('project/components/bundle.')) return 'generated from design-mirror/src — re-express the change there'
  if (rel.startsWith('project/fonts/')) return 'generated by design-mirror/fetch-fonts.mjs'
  if (rel.startsWith('project/tokens.')) return 'generated from :core:designsystem through tokens.json and token-usage.json — change the Kotlin'
  return 'no repo source — see NOTES.md'
}

const sha256 = (p) => createHash('sha256').update(readFileSync(p)).digest('hex')
const previous = existsSync(MANIFEST) ? JSON.parse(read(MANIFEST)) : {}
const staged = {}
const unfetched = []
const conflicts = []
for (const rel of [...written].sort()) {
  staged[rel] = sha256(join(OUT, rel))
  const livePath = join(LIVE, rel)
  if (!existsSync(livePath)) {
    // Published before, so it exists on the artifact: the read left it out.
    // Never published: there is nothing on the page to protect.
    if (previous[rel]) unfetched.push(rel)
    continue
  }
  const live = sha256(livePath)
  if (live !== staged[rel] && live !== previous[rel]) conflicts.push(rel)
}
for (const rel of DISCARD_LIVE) {
  if (!conflicts.includes(rel)) throw new Error(`--discard-live ${rel}: that path is not in conflict — drop the flag`)
}
if (unfetched.length) {
  throw new Error(
    `no live copy under --live for paths the last publish wrote — read them all first (build.mjs --live-paths):\n  ${unfetched.join('\n  ')}`,
  )
}
const kept = conflicts.filter((rel) => !DISCARD_LIVE.has(rel))
if (kept.length) {
  throw new Error(
    `edited on the artifact since the last publish — not publishing over:\n` +
      kept.map((rel) => `  ${rel}\n    live copy: ${posix(join(LIVE, rel))}\n    ${sourceOf(rel)}`).join('\n') +
      `\n\nBring each into its source and rebuild, or --discard-live <path> to lose it on purpose.`,
  )
}
writeFileSync(MANIFEST, JSON.stringify(staged, null, 2) + '\n')

// ---------------------------------------------------------------------------
// The index — read live right before, sent last, every other key kept
// ---------------------------------------------------------------------------

const index = JSON.parse(read(INDEX))
if (!index.createdOnFiles && !index.convertedFrom) throw new Error(`${INDEX} is not a design-system index`)
index.groups ??= []
index.assetGroups ??= {}
index.lastChange = { by: BY, at: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'), via: 'design-mirror re-sync', note: NOTE }
const indexRel = 'project/design-system.json'
writeFileSync(join(OUT, indexRel), JSON.stringify(index, null, 2) + '\n')

// ---------------------------------------------------------------------------
// publish.json — the Artifact-tool calls, content first and the index last
// ---------------------------------------------------------------------------

const deletes = []
if (has('purge-legacy')) {
  // What the 2026-09-16 migration carried over from the claude.ai/design layout
  // and nothing reads: the old compiler's raw outputs, the vendored runtime,
  // the .jsx re-export stubs, the migration notes.
  deletes.push(
    'project/docs/_ds_bundle.css',
    'project/docs/_ds_bundle.js',
    'project/docs/_ds_manifest.json',
    'project/docs/_vendor/react.js',
    'project/docs/_vendor/react-dom.js',
    'project/publish-progress.json',
    'project/assets/notes/MIGRATION-REPORT.md',
    'project/assets/notes/README.legacy.md',
    'project/fonts/roboto-400.woff2',
    ...components.map((c) => `project/components/src/${c.group}/${c.name}/${c.name}.jsx`),
  )
}

// `.d.ts` is not a served extension; the artifact keeps types as text/plain.
const source = (p) => (p.endsWith('.d.ts') ? { from: p, contentType: 'text/plain' } : p)
const [first, ...others] = written
const calls = [
  // Nothing in this recipe reads or lists these paths first — the driving
  // session only ever reads project/design-system.json — so every path this
  // call touches must be declared here or the Artifact tool refuses the call.
  {
    url: ARTIFACT_URL,
    root: posix(OUT),
    file_path: posix(join(OUT, first)),
    files: Object.fromEntries(others.map((p) => [p, source(p)])),
    overwrite_unread: [first, ...others],
  },
  {
    url: ARTIFACT_URL,
    root: posix(OUT),
    file_path: posix(join(OUT, indexRel)),
    files: Object.fromEntries(deletes.map((p) => [p, null])),
    overwrite_unread: deletes,
  },
]
for (const call of calls) {
  if (Object.keys(call.files).length + 1 > 256) throw new Error('a publish call exceeds 256 paths — split it')
}
writeFileSync(join(OUT, 'publish.json'), JSON.stringify({ calls }, null, 2) + '\n')

console.log(
  `artifact: ${components.length} components, ${color.length} colours, ${radius.length} radii, ${motion.length} motion, ` +
    `${groups.length * 3} type styles, ${fontFiles.length} fonts → ${posix(OUT)}\n` +
    `publish: ${written.length} files in call 1, index + ${deletes.length} deletions in call 2\n` +
    `page-side guard: ${Object.keys(previous).length ? 'checked against' : 'no'} published.json, ${conflicts.length} conflict(s)` +
    (DISCARD_LIVE.size ? ` — discarding the page's version of: ${[...DISCARD_LIVE].join(', ')}` : ''),
)
