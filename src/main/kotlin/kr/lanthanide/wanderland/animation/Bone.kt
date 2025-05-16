package kr.lanthanide.wanderland.animation

import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc

class Bone(
    val name: String,
    initialLocalPosition: Vector3fc = Vector3f(),       // 부모 뼈 기준 상대 위치
    initialLocalRotation: Quaternionfc = Quaternionf(), // 부모 뼈 기준 상대 회전
    initialLocalScale: Vector3fc = Vector3f(1f, 1f, 1f), // 부모 뼈 기준 상대 크기
    var parent: Bone? = null // 부모 뼈, 루트일 경우 null
) {
    // 로컬 변환 값 (수정 가능하도록 var로 선언)
    val localPosition: Vector3f = Vector3f(initialLocalPosition)
    val localRotation: Quaternionf = Quaternionf(initialLocalRotation)
    val localScale: Vector3f = Vector3f(initialLocalScale)

    // 자식 뼈 목록
    val children: MutableList<Bone> = mutableListOf()

    // 이 뼈의 최종 월드 변환 행렬 (Armature 원점 기준)
    // 매번 업데이트되므로, 초기값은 단위 행렬로 설정
    val worldTransform: Matrix4f = Matrix4f() // 단위 행렬로 초기화

    // 뼈에 연결된 엔티티 목록
    val attachments: MutableList<BoneAttachment> = mutableListOf()

    init {
        parent?.addChild(this)
    }

    /**
     * 자식 뼈를 추가하고, 이 뼈를 자식의 부모로 설정합니다.
     */
    fun addChild(child: Bone) {
        if (!children.contains(child)) {
            children.add(child)
            child.parent = this
        }
    }

    /**
     * 자식 뼈를 제거합니다.
     */
    fun removeChild(child: Bone) {
        if (children.remove(child)) {
            child.parent = null
        }
    }

    /**
     * 이 뼈의 로컬 변환(위치, 회전, 크기)으로부터 로컬 변환 행렬을 계산합니다.
     * @param dest 결과를 저장할 Matrix4f 객체 (옵셔널, 새로 생성 가능)
     * @return 계산된 로컬 변환 행렬
     */
    fun getLocalTransformMatrix(dest: Matrix4f = Matrix4f()): Matrix4f {
        return dest.identity() // 단위 행렬로 초기화
            .translate(localPosition)
            .rotate(localRotation)
            .scale(localScale)
    }

    /**
     * 부모 뼈의 월드 변환을 기반으로 이 뼈와 모든 자식 뼈들의 월드 변환을 재귀적으로 업데이트합니다.
     * @param parentWorldTransform 부모 뼈의 월드 변환 행렬. 루트 뼈의 경우 Armature의 월드 변환 또는 단위 행렬.
     */
    fun updateWorldTransform(parentWorldTransform: Matrix4f) {
        // 1. 자신의 로컬 변환 행렬을 계산합니다.
        val localMatrix = getLocalTransformMatrix() // 새 Matrix4f 인스턴스에 로컬 변환 저장

        // 2. 자신의 월드 변환을 계산합니다: worldTransform = parentWorldTransform * localMatrix
        parentWorldTransform.mul(localMatrix, this.worldTransform) // 결과를 this.worldTransform에 저장

        // 3. 모든 자식 뼈들에 대해 재귀적으로 월드 변환을 업데이트합니다.
        for (child in children) {
            child.updateWorldTransform(this.worldTransform)
        }
    }

    /**
     * Minestom 엔티티를 이 뼈에 부착합니다.
     * 부착된 엔티티는 뼈의 움직임에 따라 함께 움직입니다.
     *
     * @param entity 부착할 Minestom 엔티티
     * @param offsetPosition 뼈의 위치로부터의 상대적인 위치 오프셋
     * @param offsetRotation 뼈의 회전으로부터의 상대적인 회전 오프셋
     * @param offsetScale 뼈의 크기로부터의 상대적인 크기 오프셋
     * @return 생성된 BoneAttachment 객체. 이를 통해 부착된 엔티티를 관리할 수 있습니다.
     */
    fun attachMinestomEntity(
        entity: Entity,
        offsetPosition: Vector3fc = Vector3f(),
        offsetRotation: Quaternionfc = Quaternionf(),
        offsetScale: Vector3fc = Vector3f(1f, 1f, 1f)
    ): BoneAttachment {
        val attachment = BoneAttachment(entity, this, offsetPosition, offsetRotation, offsetScale)
        attachments.add(attachment)
        return attachment
    }
}