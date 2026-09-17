#!/usr/bin/env node
// Turns the .ds-sync build (ds-bundle/) into the Design System artifact's
// `project/` tree, and writes the exact Artifact-tool calls that publish it.
//
//   node .design-sync/artifact/build.mjs --index <live design-system.json> --note "<what changed>"
//       [--bundle ./ds-bundle] [--out .design-sync/.cache/artifact] [--by Daan] [--purge-legacy]
//
// Nothing here invents a value: colours, type, shapes and motion come from
// design-mirror's tokens.json (itself generated from :core:designsystem), the
// prose from .design-sync/artifact/README.md and token-usage.json, the previews
// from .design-sync/previews/ via the .ds-sync build. The artifact type's own
// contract — file paths, the @dsCard marker, the list-shaped tokens.json — is
// documented in the SKILL.md the artifact serves at its own URL.

import { readFileSync, writeFileSync, mkdirSync, readdirSync, statSync, rmSync, copyFileSync, existsSync } from 'node:fs'
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
const ARTIFACT_URL = 'https://claude.ai/artifact/Dmjit3682NXRcbiuwninnt'

if (!INDEX || !NOTE) {
  console.error('usage: build.mjs --index <live design-system.json> --note "<what changed>"')
  process.exit(2)
}

const read = (p) => readFileSync(p, 'utf8')
const posix = (p) => p.replace(/\\/g, '/')
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

const FRAME_TAGS =
  /^\s*<(?:link rel="stylesheet" href="\.\.\/\.\.\/\.\.\/(?:styles|_ds_bundle)\.css"|script src="\.\.\/\.\.\/\.\.\/(?:_vendor\/react(?:-dom)?|_ds_bundle|_preview\/[A-Za-z0-9]+)\.js"><\/script)>\s*\n/gm

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

  // The .ds-sync slicer attaches each story's JSDoc to the *previous* story's
  // fence. Cut those trailing comments and put every story's own doc above its
  // fence, as prose, from the preview source.
  const at = md.indexOf('\n## Examples\n')
  if (at < 0) return md
  const docs = storyDocs(name)
  const head = md.slice(0, at)
  const examples = md
    .slice(at + '\n## Examples\n'.length)
    .split(/\n(?=### )/)
    .map((block) => {
      const m = /^### ([A-Za-z0-9]+)\n\n```jsx\n([\s\S]*?)\n```\n?$/.exec(block.trim() + '\n')
      if (!m) return block
      const [, story, code] = m
      const cleaned = code.replace(/\n\n\/\*\*[\s\S]*?\*\/\s*$/, '').trimEnd()
      const doc = docs.get(story)
      return `### ${story}\n\n${doc ? doc + '\n\n' : ''}\`\`\`jsx\n${cleaned}\n\`\`\`\n`
    })
  return `${head}\n## Examples\n${examples.join('\n')}`
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
const tone = (scheme) => (scheme === 'brandDark' || scheme === 'cockpit' ? 'dark' : 'light')

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

// ---------------------------------------------------------------------------
// The index — read live right before, sent last, every other key kept
// ---------------------------------------------------------------------------

const index = JSON.parse(read(INDEX))
if (!index.createdOnFiles && !index.convertedFrom) throw new Error(`${INDEX} is not a design-system index`)
index.groups = []
index.assetGroups = {}
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
  { url: ARTIFACT_URL, root: posix(OUT), file_path: posix(join(OUT, first)), files: Object.fromEntries(others.map((p) => [p, source(p)])) },
  {
    url: ARTIFACT_URL,
    root: posix(OUT),
    file_path: posix(join(OUT, indexRel)),
    files: Object.fromEntries(deletes.map((p) => [p, null])),
  },
]
for (const call of calls) {
  if (Object.keys(call.files).length + 1 > 256) throw new Error('a publish call exceeds 256 paths — split it')
}
writeFileSync(join(OUT, 'publish.json'), JSON.stringify({ calls }, null, 2) + '\n')

console.log(
  `artifact: ${components.length} components, ${color.length} colours, ${radius.length} radii, ${motion.length} motion, ` +
    `${groups.length * 3} type styles, ${fontFiles.length} fonts → ${posix(OUT)}\n` +
    `publish: ${written.length} files in call 1, index + ${deletes.length} deletions in call 2`,
)
