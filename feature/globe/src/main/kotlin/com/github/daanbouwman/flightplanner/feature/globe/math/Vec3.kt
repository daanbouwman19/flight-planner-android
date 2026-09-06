package com.github.daanbouwman.flightplanner.feature.globe.math

import kotlin.math.sqrt

/**
 * The three-component vector the globe's geometry is written in.
 *
 * A hand-rolled type rather than one from a linear-algebra library, for the
 * reason the Rust original gives: the globe needs a dot, a cross and a
 * normalise, and pulling in a dependency to get them would put a versioning
 * story behind three lines of arithmetic.
 *
 * The axes match the Rust reference exactly, because every projection test and
 * every tile bound is written against them: **y is the polar axis**, z runs out
 * through the prime meridian at the equator, and x runs out through 90° east.
 * That is not the convention a graphics text would pick; it is the one
 * [latLonToWorld] has always used, and changing it would silently invalidate a
 * ported test suite that currently passes.
 *
 * Floats rather than doubles throughout, again matching the reference. The
 * sphere is a unit sphere, so a float carries about 7 significant digits over a
 * radius of 1 — roughly a metre on Earth, which is finer than any tile this
 * draws and finer than the sub-pixel agreement
 * `CameraMatrixConsistencyTest` demands.
 */
internal class Vec3(val x: Float, val y: Float, val z: Float) {

    infix fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    infix fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )

    operator fun plus(o: Vec3): Vec3 = Vec3(x + o.x, y + o.y, z + o.z)

    operator fun minus(o: Vec3): Vec3 = Vec3(x - o.x, y - o.y, z - o.z)

    operator fun times(s: Float): Vec3 = Vec3(x * s, y * s, z * s)

    operator fun unaryMinus(): Vec3 = Vec3(-x, -y, -z)

    fun length(): Float = sqrt(this dot this)

    /** Returns the vector unchanged when it is too short to have a direction. */
    fun normalize(): Vec3 {
        val len = length()
        return if (len < 1e-10f) this else this * (1f / len)
    }

    override fun equals(other: Any?): Boolean =
        other is Vec3 && x == other.x && y == other.y && z == other.z

    override fun hashCode(): Int = (31 * (31 * x.hashCode() + y.hashCode())) + z.hashCode()

    override fun toString(): String = "($x, $y, $z)"
}
