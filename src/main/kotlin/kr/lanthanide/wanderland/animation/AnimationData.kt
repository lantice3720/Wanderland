package kr.lanthanide.wanderland.animation

import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Represents an animation clip that can be applied to a set of bones for skeletal animations.
 *
 * @property name The unique name of the animation clip.
 * @property duration The duration of the animation clip in seconds.
 * @property boneAnimations A mapping of bone names to their corresponding animations. Each entry
 * represents the animation data (position, rotation, and scale) for a specific bone.
 * @property loop Whether the animation should loop when it reaches the end. Defaults to false.
 */
data class AnimationClip(
    val name: String,
    val duration: Float,
    val boneAnimations: Map<String, BoneAnimation>,
    val loop: Boolean = false
)

data class BoneAnimation(
    val boneName: String,
    val positionTrack: AnimationTrack<Vector3f>? = null,
    val rotationTrack: AnimationTrack<Quaternionf>? = null,
    val scaleTrack: AnimationTrack<Vector3f>? = null
)

/**
 * An animation track consisting of keyframes, where each keyframe defines a value
 * at a specific time in the animation timeline. The track supports interpolation between keyframes
 * to compute values for arbitrary points in time.
 *
 * @param T The type of the values stored in the keyframes.
 * @property keyframes A list of keyframes, which must be sorted by time in ascending order.
 * @throws IllegalArgumentException if the keyframes are not sorted by time.
 */
data class AnimationTrack<T>(
    // 시간순으로 정렬된 키프레임 리스트
    val keyframes: List<Keyframe<T>>
) {
    init {
        // 키프레임 시간순 정렬 확인
        require(keyframes.sortedBy { it.time } == keyframes) { "Keyframes must be sorted by time." }
    }

    /**
     * 주어진 시간에 보간된 값을 반환합니다.
     * @param time 현재 시간
     * @param clipDuration 애니메이션 클립의 전체 길이 (루핑 처리용)
     * @param loop 이 트랙을 반복할지 여부
     * @return 보간된 값, 키프레임이 없으면 null
     */
    fun getInterpolatedValue(time: Float, clipDuration: Float, loop: Boolean): T? {
        if (keyframes.isEmpty()) return null
        if (keyframes.size == 1) return keyframes.first().value

        var effectiveTime = time
        if (loop) {
            effectiveTime = time % clipDuration
            if (effectiveTime < 0) effectiveTime += clipDuration // 음수 시간 처리
        } else {
            // 루핑하지 않을 경우, 시간 범위를 벗어나면 첫/마지막 키프레임 값 반환
            if (time < keyframes.first().time) return keyframes.first().value
            if (time > keyframes.last().time) return keyframes.last().value
        }

        // 현재 시간에 맞는 두 키프레임(이전 키프레임, 다음 키프레임) 찾기
        var prevIndex = -1
        for (i in keyframes.indices) {
            if (keyframes[i].time <= effectiveTime) {
                prevIndex = i
            } else {
                break // 다음 키프레임이 현재 시간보다 뒤에 있음
            }
        }

        // 적절한 키프레임 인덱스 처리
        if (prevIndex == -1) return keyframes.first().value // 시간상 첫 키프레임 이전 (루핑 시 발생 가능)

        val kf1 = keyframes[prevIndex]

        if (kf1.interpolationMode == Keyframe.InterpolationMode.STEP || prevIndex == keyframes.size - 1) {
            return kf1.value // STEP 모드이거나 마지막 키프레임인 경우
        }

        // LINEAR 보간을 위한 다음 키프레임 찾기 (루핑 고려)
        val kf2 = if (prevIndex < keyframes.size - 1) {
            keyframes[prevIndex + 1]
        } else if (loop) { // 마지막 키프레임이고 루핑하는 경우, 첫 번째 키프레임과 보간
            keyframes.first().copy(time = kf1.time + (clipDuration - kf1.time + keyframes.first().time) ) // 가상 시간 조정
        } else {
            return kf1.value // 루핑 안하면 마지막 값
        }


        val tRatio: Float = if (kf2.time == kf1.time) { // 두 키프레임 시간이 같은 극단적 경우
            0f
        } else {
            (effectiveTime - kf1.time) / (kf2.time - kf1.time)
        }

        // 타입에 따른 보간 처리
        return when (kf1.value) {
            is Vector3f -> (kf1.value as Vector3f).lerp(kf2.value as Vector3f, tRatio, Vector3f()) as T
            is Quaternionf -> (kf1.value as Quaternionf).slerp(kf2.value as Quaternionf, tRatio, Quaternionf()) as T
            // is Float -> (kf1.value * (1 - tRatio) + (kf2.value as Float) * tRatio) as T // 예시
            else -> if (tRatio < 0.5f) kf1.value else kf2.value // 기본값 또는 예외 처리
        }
    }
}