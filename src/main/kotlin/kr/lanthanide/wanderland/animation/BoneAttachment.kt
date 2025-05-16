package kr.lanthanide.wanderland.animation

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.instance.Instance
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc

class BoneAttachment(
    val minestomEntity: Entity, // 이 첨부물에 의해 제어될 Minestom 엔티티
    val parentBone: Bone,       // 이 첨부물이 속한 부모 Bone
    initialOffsetPosition: Vector3fc = Vector3f(),
    initialOffsetRotation: Quaternionfc = Quaternionf(),
    initialOffsetScale: Vector3fc = Vector3f(1f, 1f, 1f)
) {
    val offsetPosition: Vector3f = Vector3f(initialOffsetPosition)
    val offsetRotation: Quaternionf = Quaternionf(initialOffsetRotation)
    val offsetScale: Vector3f = Vector3f(initialOffsetScale)

    // 이 첨부된 엔티티의 최종 월드 변환 행렬 (계산됨)
    private val finalEntityWorldTransform: Matrix4f = Matrix4f()

    // 오프셋 변환으로부터 로컬 변환 행렬을 계산 (이 첨부물의 Bone 기준 상대 변환)
    private fun getOffsetTransformMatrix(dest: Matrix4f = Matrix4f()): Matrix4f {
        return dest.identity()
            .translate(offsetPosition)
            .rotate(offsetRotation)
            .scale(offsetScale)
    }

    /**
     * 부모 Bone의 월드 변환과 이 첨부물의 오프셋을 기반으로
     * Minestom 엔티티의 최종 월드 변환을 계산하고 적용합니다.
     * 이 메소드는 Bone의 worldTransform이 업데이트된 후에 호출되어야 합니다.
     */
    fun updateAndApplyTransformToEntity() {
        // 1. 이 첨부물의 오프셋 변환 행렬을 가져옵니다.
        val offsetMatrix = getOffsetTransformMatrix() // 새 Matrix4f에 오프셋 변환 저장

        // 2. 부모 Bone의 월드 변환과 오프셋 변환을 곱하여 엔티티의 최종 월드 변환을 계산합니다.
        // finalEntityWorldTransform = parentBone.worldTransform * offsetMatrix
        parentBone.worldTransform.mul(offsetMatrix, finalEntityWorldTransform)

        // 3. 최종 월드 변환 행렬(finalEntityWorldTransform)을 위치, 회전, 크기로 분해합니다.
        val worldPosition = Vector3f()
        finalEntityWorldTransform.getTranslation(worldPosition)

        val worldRotation = Quaternionf()
        finalEntityWorldTransform.getNormalizedRotation(worldRotation) // 정규화된 회전값

        val worldScale = Vector3f()
        finalEntityWorldTransform.getScale(worldScale)

        // 4. 분해된 컴포넌트들을 Minestom 엔티티에 적용합니다.
        // Minestom Pos는 double을 사용하므로 float에서 변환 필요
        val minestomPos = Pos(worldPosition.x.toDouble(), worldPosition.y.toDouble(), worldPosition.z.toDouble())
        minestomEntity.teleport(minestomPos)

        val meta = minestomEntity.entityMeta
        if (meta is AbstractDisplayMeta) {
            // Display 엔티티의 경우, setTransformation을 사용하여 회전과 크기를 설정합니다.
            // teleport로 월드 위치는 이미 맞췄으므로, Transformation 내부의 translation은 (0,0,0)이 됩니다.
            val displayTranslation = Vec.ZERO // 이미 teleport로 위치를 맞췄기 때문
            val displayLeftRotation = floatArrayOf(
                worldRotation.x,
                worldRotation.y,
                worldRotation.z,
                worldRotation.w
            )
            // Display.Transformation은 rightRotation도 받지만, 보통 leftRotation만 사용하거나
            // 특정 효과를 위해 사용합니다. 여기서는 항등 쿼터니언으로 설정합니다.
            val displayRightRotation = floatArrayOf(0f, 0f, 0f, 1f)
            val displayScale = Vec(worldScale.x.toDouble(), worldScale.y.toDouble(), worldScale.z.toDouble())

            meta.translation = displayTranslation
            meta.rightRotation = displayRightRotation
            meta.scale = displayScale
            meta.transformationInterpolationDuration = 0
            meta.transformationInterpolationStartDelta = 0
        } else {
            // 다른 타입의 엔티티는 회전이나 크기 적용이 제한적일 수 있습니다.
            // 예를 들어 ArmorStand는 부분적으로 회전 가능 (setHeadRotation, setBodyRotation 등)
        }
    }

    /**
     * 이 첨부된 엔티티를 현재 인스턴스에 스폰합니다.
     * 초기 위치와 변환은 updateAndApplyTransformToEntity()를 통해 설정되어야 합니다.
     */
    fun spawn(instance: Instance) {
        minestomEntity.setInstance(instance)
        updateAndApplyTransformToEntity() // 스폰 시점에 정확한 위치/회전/크기 적용
    }
}