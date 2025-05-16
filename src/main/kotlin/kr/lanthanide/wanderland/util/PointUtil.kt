package kr.lanthanide.wanderland.util

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import org.joml.Vector3f

fun Point.toVector3f(): Vector3f = Vector3f(x().toFloat(), y().toFloat(), z().toFloat())
fun Vector3f.toVec(): Vec = Vec(x.toDouble(), y.toDouble(), z.toDouble())