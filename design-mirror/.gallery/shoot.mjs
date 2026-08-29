import { chromium } from 'playwright'
import { pathToFileURL } from 'node:url'
import { resolve } from 'node:path'

const url = pathToFileURL(resolve('.gallery/index.html')).href
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1760, height: 1200 }, deviceScaleFactor: 1 })

const errors = []
page.on('pageerror', (e) => errors.push(String(e)))
page.on('console', (m) => {
  if (m.type() === 'error') errors.push(m.text())
})

await page.goto(url, { waitUntil: 'networkidle' })
await page.waitForTimeout(600)

await page.screenshot({ path: '.gallery/gallery.png', fullPage: true })

// Every cell that rendered to nothing is a defect, and the one failure mode that
// looks identical to success in a green build.
const empty = await page.evaluate(() =>
  [...document.querySelectorAll('#root > div > div > div')]
    .map((cell) => {
      const label = cell.previousElementSibling?.textContent ?? '?'
      const r = cell.getBoundingClientRect()
      return { label, w: Math.round(r.width), h: Math.round(r.height) }
    })
    .filter((c) => c.h < 40),
)

console.log(errors.length ? 'ERRORS:\n' + errors.join('\n') : 'no console errors')
console.log(empty.length ? 'EMPTY CELLS: ' + JSON.stringify(empty) : 'no empty cells')

await browser.close()
