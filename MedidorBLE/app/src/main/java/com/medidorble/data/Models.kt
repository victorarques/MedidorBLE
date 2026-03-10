package com.medidorble.data

import kotlin.math.*

data class Point2D(val x: Double, val y: Double)

/**
 * A single wall segment.
 * @param turnAngle degrees clockwise to turn FROM previous wall direction
 *                  (0 for the first wall of a room → goes East)
 */
data class Wall(
    val id: Int,
    var label: String,
    val length: Double,
    val turnAngle: Double = 90.0
)

data class Room(
    val id: Int,
    var name: String,
    val walls: MutableList<Wall> = mutableListOf()
) {
    /** Polygon vertices in metres. Direction 0° = East, 90° = South (screen). */
    fun vertices(): List<Point2D> {
        val pts = mutableListOf<Point2D>()
        var x = 0.0; var y = 0.0; var dir = 0.0
        pts.add(Point2D(x, y))
        for (w in walls) {
            dir += w.turnAngle
            val rad = Math.toRadians(dir)
            x += w.length * cos(rad)
            y += w.length * sin(rad)
            pts.add(Point2D(x, y))
        }
        return pts
    }

    /** Shoelace formula – returns m² */
    fun area(): Double {
        val v = vertices(); if (v.size < 3) return 0.0
        var sum = 0.0
        for (i in v.indices) {
            val j = (i + 1) % v.size
            sum += v[i].x * v[j].y - v[j].x * v[i].y
        }
        return abs(sum) / 2.0
    }

    fun perimeter(): Double = walls.sumOf { it.length }

    /** Returns true when last vertex is within 5 cm of first */
    fun isClosed(): Boolean {
        val v = vertices(); if (v.size < 3) return false
        return hypot(v.last().x - v.first().x, v.last().y - v.first().y) < 0.05
    }
}

data class Project(
    val id: Long = System.currentTimeMillis(),
    var name: String,
    val rooms: MutableList<Room> = mutableListOf()
) {
    fun totalArea() = rooms.sumOf { it.area() }
}

/** In-memory repository shared between activities */
object ProjectRepository {
    val projects = mutableListOf<Project>()
}
