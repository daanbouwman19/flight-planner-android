import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room wiring.
 *
 * `schemaDirectory` is not optional here: the ETL in `:tools:airportdb` reads the
 * exported schema JSON to emit DDL and the `room_master_table` identity hash for
 * the prepackaged airport database. Without exported schemas the asset cannot be
 * generated correctly, and a mismatch crashes on first open on every device.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("androidx.room")
        pluginManager.apply("com.google.devtools.ksp")

        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }

        dependencies {
            add("implementation", libs.findLibrary("room-runtime").get())
            add("implementation", libs.findLibrary("room-ktx").get())
            add("implementation", libs.findLibrary("sqlite-bundled").get())
            add("ksp", libs.findLibrary("room-compiler").get())
        }
    }
}
