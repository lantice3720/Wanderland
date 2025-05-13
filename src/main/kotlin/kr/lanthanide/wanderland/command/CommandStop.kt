package kr.lanthanide.wanderland.command

import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command

class CommandStop: Command("stop") {
    init {
        setDefaultExecutor { sender, _ ->
            sender.sendMessage("Stopping server...")
            sender.sendMessage("Stopped.")
            MinecraftServer.getInstanceManager().instances.forEach { instance ->
                instance.saveInstance().join()
                instance.saveChunksToStorage().join()
            }
            MinecraftServer.stopCleanly()
        }
    }
}