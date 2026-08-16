plugins {
    alias(libs.plugins.flightplanner.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    application
}

// This module builds the shipped airport database asset. It is never on the
// app classpath — see the module table in the plan.
application {
    mainClass.set("com.github.daanbouwman.flightplanner.airportdb.MainKt")
}

dependencies {
    implementation(libs.sqlite.jdbc)
    implementation(libs.kotlin.csv.jvm)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.assertions.core)
}
