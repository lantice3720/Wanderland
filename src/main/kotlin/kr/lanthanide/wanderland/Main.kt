package kr.lanthanide.wanderland

import com.charleskorn.kaml.Yaml
import kr.lanthanide.wanderland.command.CommandGive
import kr.lanthanide.wanderland.command.CommandStop
import kr.lanthanide.wanderland.event.PlayerEvent
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

    // TODO switch to CompositeInstance whose data is got from DB using its uuid on `WanderlandConfig#mainWorldUUID`
    val instanceManager = MinecraftServer.getInstanceManager()
    val instance = IslandManager.createNewIslandInstance(1)
    instance.time = 4000
    instance.timeRate = 0

    // TODO this is just for noise function test. switch to Procedural Island Generator.
//    instance.setGenerator { unit ->
//        val start: Point = unit.absoluteStart()
//
//        val noise = SimplexNoise(0)
//        val noise2 = SimplexNoise(1)
//
//        var x = 0.0
//        var z = 0.0
//
//        while (x < unit.size().x()) {
//            while (z < unit.size().z()) {
//                val bottom: Point = start.add(x, 0.0, z)
//
//                val height = noise.noise(bottom.x()/128, bottom.z()/128).pow(2) * 16 +
//                        noise2.noise(bottom.x()/64, bottom.z()/64).pow(9) * 8
//                unit.modifier().fill(bottom, bottom.add(1.0, 0.0, 1.0).withY(height), Block.STONE)
//                z++
//            }
//            x++
//            z = 0.0
//        }
//    }

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
    commandManager.register(CommandGive())
    commandManager.register(CommandStop())

    server.start("0.0.0.0", 25565)
}