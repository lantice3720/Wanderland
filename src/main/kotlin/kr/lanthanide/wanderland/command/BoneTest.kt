package kr.lanthanide.wanderland.command

import kr.lanthanide.wanderland.animation.Bone
import kr.lanthanide.wanderland.util.toVector3f
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandExecutor
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.PI

class BoneTest: Command("bonetest") {
    init {
        defaultExecutor = CommandExecutor { sender, context ->
            sender.sendMessage("Testing bones!")

            val player = sender as Player

            val parent = Bone("parent")
            val child1 = Bone("child1", Vector3f(1f, 0f, 0f), parent = parent)
            val child2 = Bone("child2", Vector3f(0f, 1f, 0f), parent = child1)

            val parentAttach = Entity(EntityType.ITEM_DISPLAY)
            parentAttach.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                meta.itemStack = ItemStack.of(Material.IRON_SWORD)
                meta.isHasNoGravity = true
            }
            parent.attachMinestomEntity(parentAttach)


            val child1Attach = Entity(EntityType.ITEM_DISPLAY)
            child1Attach.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                meta.itemStack = ItemStack.of(Material.GOLDEN_SWORD)
                meta.isHasNoGravity = true
            }
            child1.attachMinestomEntity(child1Attach)

            val child2Attach = Entity(EntityType.ITEM_DISPLAY)
            child2Attach.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                meta.itemStack = ItemStack.of(Material.DIAMOND_SWORD)
                meta.isHasNoGravity = true
            }
            child2.attachMinestomEntity(child2Attach)

            parent.updateWorldTransform(Matrix4f().translation(player.position.toVector3f()))
            parent.updateWorldTransform(Matrix4f()
                .translation(player.position.toVector3f())
                .rotate(AxisAngle4f(PI.toFloat() / 2, 0f, 1f, 0f)))

            parent.attachments.forEach { attach -> attach.spawn(player.instance) }
            child1.attachments.forEach { attach -> attach.spawn(player.instance) }
            child2.attachments.forEach { attach -> attach.spawn(player.instance) }

            sender.sendMessage(
                "child1: ${child1Attach.position}\n" +
                "child2: ${child2Attach.position}"
            )
        }
    }
}