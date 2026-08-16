package com.github.daanbouwman.flightplanner.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SurfaceKindsTest {

    @Test
    fun `the common asphalt and concrete spellings are all hard`() {
        listOf("ASP", "ASPH", "asphalt", "ASPH-G", "CON", "CONC", "PEM", "Asfalt")
            .forEach { SurfaceKinds.classify(it) shouldBe SurfaceKind.HARD }
    }

    @Test
    fun `engineered matting counts as hard surface`() {
        listOf("PSP", "MATS", "ROOF-TOP", "ALUM")
            .forEach { SurfaceKinds.classify(it) shouldBe SurfaceKind.HARD }
    }

    @Test
    fun `grass and gravel spellings are distinguished`() {
        listOf("GRS", "TURF", "Gras").forEach { SurfaceKinds.classify(it) shouldBe SurfaceKind.GRASS }
        listOf("GVL", "GRVL", "GRAV", "CORAL", "Caliche")
            .forEach { SurfaceKinds.classify(it) shouldBe SurfaceKind.GRAVEL }
    }

    @Test
    fun `water and snow are recognised so amphibious and ski operations can be filtered`() {
        SurfaceKinds.classify("WATER") shouldBe SurfaceKind.WATER
        SurfaceKinds.classify("SNOW") shouldBe SurfaceKind.SNOW_ICE
        SurfaceKinds.classify("ICE") shouldBe SurfaceKind.SNOW_ICE
    }

    @Test
    fun `ambiguous single letter codes stay unknown rather than being guessed`() {
        // These appear in nationally sourced rows with no documented meaning.
        // Guessing would silently mislabel thousands of runways.
        listOf("G", "X", "N", "C", "S", "B", "L", "", "UNK")
            .forEach { SurfaceKinds.classify(it) shouldBe SurfaceKind.UNKNOWN }
    }

    @Test
    fun `classification is case and whitespace insensitive`() {
        SurfaceKinds.classify("  asph  ") shouldBe SurfaceKind.HARD
        SurfaceKinds.classify("Turf") shouldBe SurfaceKind.GRASS
    }
}
