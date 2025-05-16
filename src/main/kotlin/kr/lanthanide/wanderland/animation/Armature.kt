package kr.lanthanide.wanderland.animation

import net.minestom.server.instance.Instance
import net.minestom.server.entity.Entity
import org.joml.Matrix4f

class Armature(
    val rootBone: Bone // 이 Armature를 구성하는 최상위 Bone들
) {
    // Armature에 속한 모든 Bone들을 쉽게 찾기 위한 맵 (이름 -> Bone)
    // lazy는 처음 접근될 때 {} 안의 코드가 실행되어 값을 초기화합니다.
    val allBonesMap: Map<String, Bone> by lazy {
        val map = mutableMapOf<String, Bone>()
        val bonesToVisit = ArrayDeque<Bone>() // 탐색할 Bone들을 담는 큐
        bonesToVisit.add(rootBone)

        while (bonesToVisit.isNotEmpty()) {
            val currentBone = bonesToVisit.removeFirst()
            if (map.containsKey(currentBone.name)) {
                // 이름 충돌 시 경고 또는 예외 처리 (필요에 따라)
                println("경고: Armature 내에 중복된 Bone 이름이 있습니다: ${currentBone.name}")
            }
            map[currentBone.name] = currentBone
            bonesToVisit.addAll(currentBone.children) // 자식 Bone들도 탐색 대상에 추가
        }
        map
    }

    // Armature에 속한 모든 BoneAttachment들을 쉽게 순회하기 위한 리스트
    val allAttachments: List<BoneAttachment> by lazy {
        val attachments = mutableListOf<BoneAttachment>()
        allBonesMap.values.forEach { bone -> // 모든 Bone을 순회하며
            attachments.addAll(bone.attachments) // 각 Bone의 attachment들을 리스트에 추가
        }
        attachments
    }

    // Armature가 생성하고 관리하는 모든 Minestom 엔티티 목록 (despawn 시 사용)
    private val managedMinestomEntities: List<Entity> by lazy {
        allAttachments.map { it.minestomEntity }.distinct() // 중복 제거
    }

    /**
     * Armature와 연결된 모든 Minestom 엔티티들을 지정된 인스턴스에 스폰하고,
     * 초기 월드 변환을 기준으로 각 엔티티의 위치/회전/크기를 설정합니다.
     *
     * @param instance 엔티티들을 스폰할 Minestom 인스턴스
     * @param initialArmatureWorldMatrix Armature 전체의 초기 월드 변환 행렬 (월드에서의 위치, 회전, 크기)
     */
    fun spawn(instance: Instance, initialArmatureWorldMatrix: Matrix4f) {
        // 1. Armature의 초기 상태에 따라 모든 Bone들의 worldTransform을 업데이트합니다.
        rootBone.updateWorldTransform(initialArmatureWorldMatrix)

        // 2. 모든 BoneAttachment에 연결된 Minestom 엔티티들을 스폰합니다.
        for (attachment in allAttachments) {
            attachment.spawn(instance) // 내부적으로 setInstance 및 updateAndApplyTransformToEntity 호출
        }
    }

    /**
     * Armature 전체의 현재 월드 변환을 기준으로 모든 Bone들의 worldTransform을 업데이트하고,
     * 이어서 모든 연결된 Minestom 엔티티들의 모습을 갱신합니다.
     * 애니메이션 로직이 각 Bone의 localTransform을 변경한 후 매 틱 호출되어야 합니다.
     *
     * @param currentArmatureWorldMatrix Armature 전체의 현재 월드 변환 행렬
     */
    fun updateAndApplyAllTransforms(currentArmatureWorldMatrix: Matrix4f) {
        // 1. 모든 Bone들의 worldTransform을 업데이트합니다.
        rootBone.updateWorldTransform(currentArmatureWorldMatrix)

        // 2. 업데이트된 worldTransform을 기반으로 모든 BoneAttachment의 엔티티 모습을 갱신합니다.
        for (attachment in allAttachments) {
            attachment.updateAndApplyTransformToEntity()
        }
    }

    /**
     * Armature와 연결된 모든 Minestom 엔티티들을 월드에서 제거(despawn)합니다.
     */
    fun despawn() {
        for (entity in managedMinestomEntities) {
            entity.remove()
        }
    }

    /**
     * 이름으로 Bone을 찾습니다.
     * @param name 찾고자 하는 Bone의 이름
     * @return 찾은 Bone 객체, 없으면 null
     */
    fun getBoneByName(name: String): Bone? {
        return allBonesMap[name]
    }
}