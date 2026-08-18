# Third-party notices

This repository redistributes the material below. Everything here is MIT
licensed, as is this project — see [LICENSE](LICENSE) — and each entry keeps the
copyright and permission notice its licence requires.

## Vendored agent skills — `.agents/skills/`

Reference documentation used while building this app, checked in so that a clone
builds and reasons the same way. Provenance and content hashes are recorded in
[`skills-lock.json`](skills-lock.json).

| Skill | Source | Licence |
| --- | --- | --- |
| `compose-expert` | [vitorpamplona/amethyst](https://github.com/vitorpamplona/amethyst) | MIT |
| `kotlin-specialist` | [jeffallan/claude-skills](https://github.com/jeffallan/claude-skills) | MIT |
| `material-3` | [hamen/material-3-skill](https://github.com/hamen/material-3-skill) — © Ivan Morgillo | MIT |

The MIT permission notice, which applies to each of the three:

> Permission is hereby granted, free of charge, to any person obtaining a copy of
> this software and associated documentation files (the "Software"), to deal in
> the Software without restriction, including without limitation the rights to
> use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
> the Software, and to permit persons to whom the Software is furnished to do so,
> subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
> FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
> COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
> IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
> CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Bundled data

Both datasets are public domain, so neither requires attribution. They are
credited because knowing where a dataset came from is part of trusting it.

| Data | Source | Status |
| --- | --- | --- |
| Airports and runways — `app/src/main/assets/databases/` | [OurAirports](https://ourairports.com/data/) | Public domain |
| Land outline — `app/src/main/assets/maps/land.outline` | [Natural Earth](https://www.naturalearthdata.com/) `ne_110m_land` | Public domain |
