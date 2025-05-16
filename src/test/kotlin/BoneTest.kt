import kr.lanthanide.wanderland.animation.Bone
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertEquals

class BoneTest {
    @Test
    fun transform_applies() {
        val parent = Bone("parent")
        val child1 = Bone("child1", Vector3f(1f, 0f, 0f), parent = parent)
        val child2 = Bone("child2", Vector3f(0f, 1f, 0f), parent = child1)

        parent.updateWorldTransform(Matrix4f().rotate(AxisAngle4f(PI.toFloat() / 2, 0f, 1f, 0f)))

        val child1Position = Vector3f()
        child1.worldTransform.getTranslation(child1Position)
        assertEquals(child1Position.x, 0f, "Child1 X should be 0")
        assertEquals(child1Position.y, 0f, "Child1 Y should be 0")
        assertEquals(child1Position.z, -1f, "Child1 Z should be 1")

        val child2Position = Vector3f()
        child2.worldTransform.getTranslation(child2Position)
        assertEquals(child2Position.x, 0f, "Child2 X should be 0")
        assertEquals(child2Position.y, 1f, "Child2 Y should be 1")
        assertEquals(child2Position.z, -1f, "Child2 Z should be -1")
    }
}