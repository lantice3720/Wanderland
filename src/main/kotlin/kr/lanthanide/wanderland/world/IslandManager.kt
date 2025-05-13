package kr.lanthanide.wanderland.world

import net.minestom.server.MinecraftServer
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.world.DimensionType
import java.util.*

object IslandManager {
    val activeIslandInstances = mutableMapOf<UUID, InstanceContainer>() // 인메모리 캐시

    fun createNewIslandInstance(islandSeed: Long): InstanceContainer {
        val instanceManager = MinecraftServer.getInstanceManager()
        val instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD)
        val islandUuid = instance.uuid

        val generator = ProceduralIslandGenerator(islandSeed)
        instance.setGenerator(generator)
        instance.setChunkSupplier(::LightingChunk)
        instance.enableAutoChunkLoad(true)

//        for (chunkCoord in generator.footprint.activeChunkCoords) {
//            instance.loadChunk(chunkCoord.first, chunkCoord.second)
//        }

        activeIslandInstances[islandUuid] = instance
        println("Created InstanceContainer for island $islandUuid")
        return instance
    }
}