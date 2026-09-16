# The rules the app itself owes R8. Everything a library needs for itself arrives
# with that library: AGP merges each dependency's bundled `proguard.txt` /
# `META-INF/proguard/*.pro` / `META-INF/com.android.tools/r8/*.pro` into the run.
# The merged result — every rule, with the file it came from — is written to
# `app/build/outputs/mapping/release/configuration.txt` after `assembleRelease`;
# read that, not this file, when asking "is X kept, and by whom". `seeds.txt` beside
# it lists what the keeps pinned, `usage.txt` what shrinking removed, and AGP's
# `configanalyzer.html` flags rules that match nothing or too much.
#
# What is deliberately *not* here, because it was here once and was a duplicate:
#
# - kotlinx.serialization. The serializer-on-companion rules (`-if @Serializable
#   class ** -keepclassmembers … Companion`, `… KSerializer serializer(...)`) ship
#   inside kotlinx-serialization-core since 1.6 as `kotlinx-serialization-common.pro`
#   and `kotlinx-serialization-r8.pro`; both appear in configuration.txt. The
#   copy this file carried was the pre-1.6 README snippet.
# - Filament. `filament-android` ships rules keyed on its own `@UsedByNative` /
#   `@UsedByReflection` annotations plus the two `utils` classes `env->FindClass`
#   reaches by name. The blanket `-keep class com.google.android.filament.** { *; }`
#   this file had overrode them and pinned 3,402 Filament classes and members that
#   the bundled rules let shrinking drop (181 seeds after; 262 KB off the APK).
# - Enum-by-name settings. `SettingsRepository` matches the stored `ThemeChoice`,
#   `UnitSystem` and `WeatherProvider` strings with `entries.firstOrNull { it.name == … }`.
#   R8 never renames enum constants or breaks `name()`, so no rule is needed and
#   none should be added.
# - Hilt, Room, OkHttp, DataStore, Navigation, WorkManager, Glance, sqlite-bundled,
#   coroutines: all bundled, or need nothing.

# AGP's `proguard-android-optimize.txt` keeps annotations, Signature and
# InnerClasses but not the line table, so a release stack trace named methods and
# no lines. Keep the lines; rename the source-file attribute so the trace does not
# also leak every original file name. Measured: the dex is not larger for it.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
