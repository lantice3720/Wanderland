package kr.lanthanide.wanderland.util

import kotlin.math.abs

infix fun ClosedFloatingPointRange<Double>.step(step: Double): Sequence<Double> {
    require(step > 0.0) { "Step must be positive, was: $step." }
    val self = this
    return sequence {
        var current = self.start
        // Considering floating point error, check slightly bigger value than endInclusive
        val epsilon = step * 0.0001
        while (current <= self.endInclusive + epsilon) {
            yield(current)
            current += step
        }
    }
}