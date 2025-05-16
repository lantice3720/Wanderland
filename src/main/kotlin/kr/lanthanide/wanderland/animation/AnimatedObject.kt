package kr.lanthanide.wanderland.animation

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task // Minestom의 Task 사용
import net.minestom.server.timer.TaskSchedule // Minestom의 TaskSchedule 사용
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

class AnimatedObject(
    val armature: Armature, // 이 객체가 사용할 Armature
    private val animationClips: Map<String, AnimationClip> // <애니메이션 이름, AnimationClip>
) {
    // AnimatedObject 자체의 월드 변환 정보
    var worldPosition: Pos = Pos.ZERO
        private set
    var worldRotation: Quaternionf = Quaternionf() // JOML Quaternion, 항등(identity)으로 초기화
        private set
    var worldScale: Vector3f = Vector3f(1f, 1f, 1f) // JOML Vector3f, 기본 크기
        private set

    // Armature에 최종적으로 적용될 월드 변환 행렬 (worldPosition, worldRotation, worldScale로부터 계산됨)
    private val currentArmatureWorldMatrix: Matrix4f = Matrix4f()

    // 현재 애니메이션 재생 상태
    var currentAnimationName: String? = null
        private set
    var currentAnimation: AnimationClip? = null
        private set
    var currentAnimationTime: Float = 0f // 현재 애니메이션의 재생 시간 (초)
        private set
    var animationSpeed: Float = 1.0f    // 애니메이션 재생 속도 배율
    var isPlaying: Boolean = false
        private set
    var isLooping: Boolean = false      // 현재 애니메이션을 반복할지 여부

    private var tickTask: Task? = null     // Minestom 스케줄러 Task
    private var lastTickTimeNanos: Long = 0L // 정확한 deltaTime 계산용

    /**
     * AnimatedObject를 월드의 지정된 위치와 회전으로 스폰합니다.
     * 내부 Armature와 모든 엔티티들이 활성화됩니다.
     */
    fun spawn(instance: Instance, initialPosition: Pos, initialRotation: Quaternionf = Quaternionf()) {
        this.worldPosition = initialPosition
        this.worldRotation.set(initialRotation) // JOML 객체는 set으로 값 변경
        // this.worldScale은 기본값(1,1,1)을 유지하거나 필요시 설정

        updateCurrentArmatureWorldMatrix() // Armature에 적용할 월드 행렬 계산
        armature.spawn(instance, currentArmatureWorldMatrix) // Armature 스폰

        this.isPlaying = currentAnimation != null // 스폰 시 재생 중이던 애니메이션이 있으면 계속 재생
        this.lastTickTimeNanos = System.nanoTime()

        // 틱 작업이 없거나, 비활성 상태이거나, 취소된 경우 새로 생성 및 시작
        if (tickTask == null || !(tickTask!!.isAlive || !tickTask!!.isAlive)) {
            tickTask = MinecraftServer.getSchedulerManager().buildTask { tick() }
                .repeat(TaskSchedule.tick(1)) // 매 서버 틱마다 실행 (Minestom 1.20.1+ 권장 방식)
                // .repeat(MinecraftServer.TICK_MS) 이전 방식
                .schedule()
        }
    }

    /**
     * AnimatedObject를 월드에서 제거합니다.
     * 내부 Armature와 모든 엔티티들이 제거되고, 틱 작업이 중지됩니다.
     */
    fun despawn() {
        armature.despawn()
        tickTask?.cancel()
        this.isPlaying = false
    }

    /**
     * AnimatedObject의 현재 월드 위치, 회전, 크기를 기반으로
     * Armature에 적용될 최종 월드 변환 행렬을 업데이트합니다.
     */
    private fun updateCurrentArmatureWorldMatrix() {
        currentArmatureWorldMatrix.identity()
            .translate(worldPosition.x().toFloat(), worldPosition.y().toFloat(), worldPosition.z().toFloat())
            .rotate(worldRotation)
            .scale(worldScale)
    }

    /**
     * AnimatedObject 전체를 새로운 위치와 회전으로 즉시 이동시킵니다.
     */
    fun teleport(newPosition: Pos, newRotation: Quaternionf = this.worldRotation) {
        this.worldPosition = newPosition
        this.worldRotation.set(newRotation)
        // 틱이 실행 중이라면 다음 틱에서 변경된 위치가 반영됩니다.
        // 만약 즉각적인 시각적 업데이트가 필요하다면 (틱 외부에서), 다음 두 줄을 수동 호출:
        // updateCurrentArmatureWorldMatrix()
        // armature.updateAndApplyAllTransforms(currentArmatureWorldMatrix)
    }

    /**
     * 지정된 이름의 애니메이션을 재생합니다.
     * @param name 재생할 AnimationClip의 이름
     * @param loopOverride 기본 반복 설정을 무시하고 반복 여부를 강제할지 여부 (null이면 클립 기본값 따름)
     * @param speed 재생 속도 (1.0f가 기본 속도)
     * @param startTime 애니메이션을 시작할 시간 (초)
     * @param transitionTime 현재 포즈에서 새 애니메이션으로 부드럽게 전환하는 시간 (초) - 향후 구현
     */
    fun playAnimation(
        name: String,
        loopOverride: Boolean? = null,
        speed: Float = 1.0f,
        startTime: Float = 0f,
        transitionTime: Float = 0.0f // 현재 버전에서는 사용되지 않음 (즉시 전환)
    ) {
        val clipToPlay = animationClips[name]
        if (clipToPlay == null) {
            println("경고: 애니메이션 클립 '$name'을(를) 찾을 수 없습니다.")
            return
        }

        currentAnimationName = name
        currentAnimation = clipToPlay
        // startTime은 0 이상, clipToPlay.duration 이하로 제한하는 것이 좋음
        currentAnimationTime = startTime.coerceIn(0f, clipToPlay.duration)
        animationSpeed = speed
        isLooping = loopOverride ?: clipToPlay.loop // loopOverride가 null이면 클립의 기본 loop 설정 사용
        isPlaying = true

        if (transitionTime > 0f) {
            println("참고: 애니메이션 전환(블렌딩) 기능은 아직 구현되지 않았습니다.")
        }

        // 스폰되어 있고 틱이 실행 중이라면, 첫 프레임을 즉시 적용해볼 수 있음
        if (tickTask?.isAlive == true) {
            applyAnimationPoseToArmature(currentAnimationTime)
            updateCurrentArmatureWorldMatrix() // Armature 행렬 최신화
            armature.updateAndApplyAllTransforms(currentArmatureWorldMatrix) // 즉시 시각적 업데이트
        }
    }

    /** 현재 재생 중인 애니메이션을 중지합니다. */
    fun stopAnimation() {
        isPlaying = false
        // 선택적으로 currentAnimation = null 등으로 상태를 초기화할 수 있으나,
        // 마지막 재생 정보를 유지하는 것이 좋을 수도 있습니다.
    }

    /** 애니메이션 재생을 일시 중지합니다. */
    fun pause() {
        isPlaying = false
    }

    /** 일시 중지된 애니메이션 재생을 재개합니다. */
    fun resume() {
        if (currentAnimation != null) { // 재생할 애니메이션이 설정되어 있을 때만
            isPlaying = true
        }
    }

    /**
     * 매 틱 호출되어 애니메이션 상태를 업데이트하고 Armature에 적용합니다.
     */
    private fun tick() {

        val localCurrentAnimation = currentAnimation // Null-safety를 위해 로컬 변수에 할당
        if (!isPlaying || localCurrentAnimation == null) {
            // 재생 중이 아닐 때도 AnimatedObject의 위치가 변경되었다면 Armature를 업데이트해야 할 수 있습니다.
            // 여기서는 단순화를 위해 재생 중이 아니면 Armature 업데이트를 생략하거나,
            // 혹은 항상 Armature의 월드 변환을 현재 객체 상태에 맞추도록 할 수도 있습니다.
            // 예: 항상 업데이트하려면 아래 주석 해제
            // updateCurrentArmatureWorldMatrix()
            // armature.updateAndApplyAllTransforms(currentArmatureWorldMatrix)
            return
        }

        // 애니메이션 시간 업데이트
        currentAnimationTime += MinecraftServer.TICK_MS.toFloat() / 1000 * animationSpeed
        val animDuration = localCurrentAnimation.duration

        // 애니메이션 시간 범위 및 루핑 처리
        if (currentAnimationTime >= animDuration) {
            if (isLooping) {
                currentAnimationTime %= animDuration // 나머지 연산으로 루핑
            } else {
                currentAnimationTime = animDuration // 마지막 프레임에서 멈춤
                isPlaying = false
                // TODO: 애니메이션 종료 이벤트 발생 (onAnimationEnd)
            }
        } else if (currentAnimationTime < 0f) { // 역재생 또는 되감기 시
            if (isLooping) {
                currentAnimationTime = (currentAnimationTime % animDuration + animDuration) % animDuration
            } else {
                currentAnimationTime = 0f // 첫 프레임에서 멈춤
                isPlaying = false
            }
        }

        // 현재 시간에 맞는 애니메이션 포즈를 Armature의 Bone들에 적용
        applyAnimationPoseToArmature(currentAnimationTime)

        // AnimatedObject의 현재 월드 변환을 기반으로 Armature의 월드 행렬 업데이트
        updateCurrentArmatureWorldMatrix()

        // Armature에게 모든 Bone의 최종 월드 변환을 계산하고 연결된 엔티티들에 적용하도록 지시
        armature.updateAndApplyAllTransforms(currentArmatureWorldMatrix)
    }

    /**
     * 현재 애니메이션의 특정 시간에 해당하는 포즈를 Armature의 각 Bone에 적용합니다.
     * @param time 현재 애니메이션 시간
     */
    private fun applyAnimationPoseToArmature(time: Float) {
        val clip = currentAnimation ?: return // 현재 애니메이션 클립이 없으면 아무것도 안 함

        for ((boneName, boneAnim) in clip.boneAnimations) {
            val targetBone = armature.getBoneByName(boneName)
            if (targetBone == null) {
                // 애니메이션 데이터에 정의된 Bone이 실제 Armature에 없을 경우 경고 (자주 발생 가능)
                // println("경고: Bone '$boneName' (애니메이션 '${clip.name}')을 Armature에서 찾을 수 없습니다.")
                continue
            }

            // 각 트랙(위치, 회전, 크기)에서 현재 시간에 해당하는 보간된 값을 가져와 Bone의 로컬 변환에 적용
            // AnimationTrack의 getInterpolatedValue는 루핑 여부(isLooping)를 알아야 정확한 값을 반환할 수 있음
            boneAnim.positionTrack?.getInterpolatedValue(time, clip.duration, isLooping)?.let {
                targetBone.localPosition.set(it)
            }
            boneAnim.rotationTrack?.getInterpolatedValue(time, clip.duration, isLooping)?.let {
                targetBone.localRotation.set(it)
            }
            boneAnim.scaleTrack?.getInterpolatedValue(time, clip.duration, isLooping)?.let {
                targetBone.localScale.set(it)
            }
        }
    }
}