package kr.lanthanide.wanderland

import com.charleskorn.kaml.Yaml
import kr.lanthanide.wanderland.command.BoneTest
import kr.lanthanide.wanderland.command.Give
import kr.lanthanide.wanderland.command.Stop
import kr.lanthanide.wanderland.event.PlayerEvent
import kr.lanthanide.wanderland.player.WanderPlayer
import kr.lanthanide.wanderland.world.IslandManager
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.EventNode
import net.minestom.server.event.item.PickupItemEvent
import net.minestom.server.event.item.PlayerBeginItemUseEvent
import net.minestom.server.event.item.PlayerCancelItemUseEvent
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.server.ServerListPingEvent
import net.minestom.server.extras.MojangAuth
import java.io.File
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
fun main() {
    val configFile = File("config.yml")
    // Update config file
    configFile.writeText(Yaml.default.encodeToString(WanderlandConfigYaml.serializer(), CONFIG))

    val server = MinecraftServer.init()
    MojangAuth.init()
    MinecraftServer.getConnectionManager().setPlayerProvider { connection, gameProfile ->
        WanderPlayer(connection, gameProfile)
    }

    // TODO switch to CompositeInstance whose data is got from DB using its uuid on `WanderlandConfig#mainWorldUUID`
    val instanceManager = MinecraftServer.getInstanceManager()
    val instance = IslandManager.createNewIslandInstance(1)
    instance.time = 4000
    instance.timeRate = 0

    // Event Setup
    // TODO make more complex and performant per-world/per-player event nodes
    val eventNode = EventNode.all("wanderland-master")
    eventNode.addListener(
        EventListener.builder(ServerListPingEvent::class.java)
        .handler { event: ServerListPingEvent ->
            event.responseData.favicon = CONFIG.faviconBase64
            event.responseData.description = MiniMessage.miniMessage().deserialize(CONFIG.motd)
        }
        .build())

    eventNode.addListener(AsyncPlayerConfigurationEvent::class.java, PlayerEvent::playerConfiguration)
    eventNode.addListener(PlayerSpawnEvent::class.java, PlayerEvent::playerSpawn)
    eventNode.addListener(PlayerMoveEvent::class.java, PlayerEvent::playerMove)
    eventNode.addListener(PlayerBeginItemUseEvent::class.java, PlayerEvent::beginUseItem)
    eventNode.addListener(PlayerCancelItemUseEvent::class.java, PlayerEvent::cancelUseItem)
    eventNode.addListener(PlayerBlockBreakEvent::class.java, PlayerEvent::breakBlock)

    eventNode.addListener(
        EventListener.builder(PickupItemEvent::class.java)
        .handler {
            if (it.livingEntity is Player) {
                (it.livingEntity as Player).inventory.addItemStack(it.itemStack)
            }
        }
        .build())

    MinecraftServer.getGlobalEventHandler().addChild(eventNode)

    val commandManager = MinecraftServer.getCommandManager()
    commandManager.register(Give())
    commandManager.register(Stop())
    commandManager.register(BoneTest())

    server.start("0.0.0.0", 25565)
}