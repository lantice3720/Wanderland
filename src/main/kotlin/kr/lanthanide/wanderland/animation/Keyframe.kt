package kr.lanthanide.wanderland.animation

/**
 * Represents a keyframe in an animation timeline. A keyframe specifies a value at a specific point in time
 * and includes an interpolation mode to determine how the value transitions between keyframes.
 *
 * @param T The type of the value stored in the keyframe.
 * @property time The time position of the keyframe, typically represented in seconds.
 * @property value The value associated with the keyframe at the specified time.
 * @property interpolationMode The interpolation mode used to determine how the value transitions
 * between this keyframe and the next. Defaults to `InterpolationMode.LINEAR`.
 */
data class Keyframe<T>(
    val time: Float,
    val value: T,
    val interpolationMode: InterpolationMode = InterpolationMode.LINEAR
) {
    /**
     * Defines the mode of interpolation used between keyframes in an animation.
     * The interpolation mode determines how values transition from one keyframe to the next.
     */
    enum class InterpolationMode {
        STEP,
        LINEAR,
        // CUBICSPLINE
    }
}