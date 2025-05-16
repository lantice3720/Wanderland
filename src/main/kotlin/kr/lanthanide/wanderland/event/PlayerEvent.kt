package kr.lanthanide.wanderland.event

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityPose
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.item.PlayerBeginItemUseEvent
import net.minestom.server.event.item.PlayerCancelItemUseEvent
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.item.ItemComponent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.UseCooldown

object PlayerEvent {
    fun playerConfiguration(event: AsyncPlayerConfigurationEvent) {
        val player = event.player
        val instance = MinecraftServer.getInstanceManager().instances.first()
        event.spawningInstance = instance
        player.respawnPoint = Pos(0.0, 80.0, 0.0)
        player.gameMode = GameMode.CREATIVE
    }

    fun playerSpawn(event: PlayerSpawnEvent) {
        val player = event.player
        player.isAutoViewable = false

//        val entity = Entity(EntityType.MARKER)
//        entity.setInstance(event.instance)
//        entity.teleport(player.position)
//        player.spectate(entity)
    }

    fun playerMove(event: PlayerMoveEvent) {
        val player = event.player


    }


    fun beginUseItem(event: PlayerBeginItemUseEvent) {
        if (!event.itemStack.has(ItemComponent.CUSTOM_DATA) || event.hand == PlayerHand.OFF) return
        val customData = event.itemStack.get(ItemComponent.CUSTOM_DATA)!!.nbt

        if (customData.getString("id") == "test_gun") {
            event.isCancelled = true
            val newStack = event.itemStack
                .with { builder -> builder.material(Material.CROSSBOW) }
                .with(ItemComponent.USE_COOLDOWN, UseCooldown(1f, "gun"))
                .with(ItemComponent.CHARGED_PROJECTILES, listOf(ItemStack.of(Material.ARROW)))
            event.player.setItemInHand(PlayerHand.MAIN, newStack)
        }
    }

    fun cancelUseItem(event: PlayerCancelItemUseEvent) {
        if (!event.itemStack.has(ItemComponent.CUSTOM_DATA) || event.hand == PlayerHand.OFF) return
        val customData = event.itemStack.get(ItemComponent.CUSTOM_DATA)!!.nbt

        if (customData.getString("id") == "test_gun") {
            val newStack = event.itemStack
                .withMaterial(Material.JIGSAW)
                .without(ItemComponent.USE_COOLDOWN)
                .without(ItemComponent.CHARGED_PROJECTILES)
            event.player.setItemInHand(PlayerHand.MAIN, newStack)
        }
    }

    fun breakBlock(event: PlayerBlockBreakEvent) {
        println(event.block.stateId())

        val entity = ItemEntity(ItemStack.of(event.block.registry().material() ?: Material.AIR, 1))
        entity.setInstance(event.instance, event.blockPosition)
    }
}