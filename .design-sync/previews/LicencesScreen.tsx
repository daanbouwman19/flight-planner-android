import { LicencesScreen } from '@flightplanner/design-mirror'

/**
 * The open-source licences.
 *
 * A legal obligation rather than a design opportunity: the job is that every
 * bundled dependency is named with its licence, legibly, and that the list is
 * scannable. Do not decorate it.
 */
export const Default = () => (
  <LicencesScreen
    licences={[
      { name: 'AndroidX Compose', licence: 'Apache-2.0', copyright: '© The Android Open Source Project' },
      { name: 'Material Components', licence: 'Apache-2.0', copyright: '© Google LLC' },
      { name: 'Kotlin Standard Library', licence: 'Apache-2.0', copyright: '© JetBrains s.r.o.' },
      { name: 'Ktor', licence: 'Apache-2.0', copyright: '© JetBrains s.r.o.' },
      { name: 'OurAirports data', licence: 'Public domain', copyright: 'Released into the public domain' },
      { name: 'Roboto', licence: 'Apache-2.0', copyright: '© Google LLC' },
      { name: 'Natural Earth', licence: 'Public domain' },
    ]}
  />
)
