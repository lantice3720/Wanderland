package kr.lanthanide.wanderland.world

import de.articdive.jnoise.generators.noisegen.opensimplex.FastSimplexNoiseGenerator
import de.articdive.jnoise.pipeline.JNoise
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.Generator
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.operation.union.UnaryUnionOp
import kotlin.random.Random

class ProceduralIslandGenerator(private val seed: Long): Generator {
    private val random = Random(seed)
    private val geometryFactory: GeometryFactory = GeometryFactory()

    private val islandShapeNoise: JNoise = JNoise.newBuilder().fastSimplex(FastSimplexNoiseGenerator.newBuilder()
        .setSeed(random.nextLong()).build()).scale(1 / 16.0).addModifier { v -> (v + 1)/2 }.build()
    private val terrainHeightNoise: JNoise = JNoise.newBuilder().fastSimplex(FastSimplexNoiseGenerator.newBuilder()
        .setSeed(random.nextLong()).build()).scale(1 / 32.0).addModifier { v -> (v + 1)/2 }.build()

    val footprint: IslandFootprint by lazy {
        calculateFootprint()
    }

    private fun calculateFootprint(): IslandFootprint {
        // 섬의 기본 매개변수 설정
        val centerChunkX = 0
        val centerChunkZ = 0
        val radiusInChunks = 8 // 8 청크 반경 (128블록)

        // 활성 청크 목록 생성
        val activeChunks = mutableListOf<Pair<Int, Int>>()

        // 원형 또는 타원형 기본 모양 결정
        for (dx in -radiusInChunks..radiusInChunks) {
            for (dz in -radiusInChunks..radiusInChunks) {
                val chunkX = centerChunkX + dx
                val chunkZ = centerChunkZ + dz

                // 청크 중심점 좌표 (블록 단위)
                val blockX = (chunkX * 16) + 8.0
                val blockZ = (chunkZ * 16) + 8.0

                // 중심으로부터의 거리 계산
                val distanceSquared = (blockX * blockX) + (blockZ * blockZ)
                val baseRadius = radiusInChunks * 16.0

                // 노이즈를 통한 경계 불규칙화
                val angle = Math.atan2(blockZ, blockX)
                val noiseValue = islandShapeNoise.evaluateNoise(blockX / 16.0, blockZ / 16.0)

                // 최종 반경 결정 (노이즈 적용)
                val finalRadius = baseRadius * (0.8 + 0.4 * noiseValue)

                // 이 청크가 섬의 일부인지 확인
                if (distanceSquared <= finalRadius * finalRadius) {
                    activeChunks.add(Pair(chunkX, chunkZ))
                }
            }
        }

        // 청크 좌표로부터 MultiPolygon 생성
        val multiPolygon = createMultiPolygonFromChunks(activeChunks)

        return IslandFootprint(activeChunks, multiPolygon)

    }

    /**
     * 청크 좌표 목록을 MultiPolygon으로 변환
     */
    private fun createMultiPolygonFromChunks(chunks: List<Pair<Int, Int>>): MultiPolygon {
        val polygons = mutableListOf<Polygon>()

        // 각 청크를 다각형으로 변환하고 통합
        for (chunk in chunks) {
            val (chunkX, chunkZ) = chunk

            // 청크의 경계 좌표 (블록 단위)
            val x1 = chunkX * 16.0
            val z1 = chunkZ * 16.0
            val x2 = x1 + 16.0
            val z2 = z1 + 16.0

            // 청크의 사각형 생성
            val coordinates = arrayOf(
                Coordinate(x1, 0.0, z1),
                Coordinate(x2, 0.0, z1),
                Coordinate(x2, 0.0, z2),
                Coordinate(x1, 0.0, z2),
                Coordinate(x1, 0.0, z1) // 닫힌 폴리곤을 위해 첫 좌표 반복
            )

            val shell = geometryFactory.createLinearRing(coordinates)
            val polygon = geometryFactory.createPolygon(shell)
            polygons.add(polygon)
        }

        // 모든 청크 다각형을 하나의 다각형으로 통합
        val combined = UnaryUnionOp.union(polygons.toMutableSet())

        // 결과가 MultiPolygon이 아니라면 변환
        return combined as? MultiPolygon
            ?: if (combined is Polygon) {
                geometryFactory.createMultiPolygon(arrayOf(combined))
            } else {
                // 비어있거나 다른 형태인 경우 빈 MultiPolygon 반환
                geometryFactory.createMultiPolygon(arrayOf())
            }
    }


    override fun generate(unit: GenerationUnit) {
        val chunkX = unit.absoluteStart().x().toInt() shr 4
        val chunkZ = unit.absoluteStart().z().toInt() shr 4

        // 이 청크가 활성 목록에 있는지 확인
        if (!footprint.activeChunkCoords.contains(Pair(chunkX, chunkZ))) {
            return // 섬 바깥쪽 청크는 생성하지 않음
        }

        // 중심 좌표 설정 (중심에서 가장 높은 지형)
        val centerX = 0
        val centerZ = 0

        // 청크 내 각 블록의 지형 생성
        for (x in 0 until 16) {
            for (z in 0 until 16) {
                val absoluteX = (chunkX * 16) + x
                val absoluteZ = (chunkZ * 16) + z

                // 중심으로부터의 거리 계산
                val dx = absoluteX - centerX
                val dz = absoluteZ - centerZ
                val distanceFromCenter = Math.sqrt((dx * dx + dz * dz).toDouble())

                // 지형 높이 계산
                val baseHeight = 64
                val heightNoise = terrainHeightNoise.evaluateNoise(absoluteX / 16.0, absoluteZ / 16.0)

                // 가장자리로 갈수록 낮아지는 곡선 적용
                val maxDistance = (footprint.activeChunkCoords.size * 16.0) / 2.0 // 대략적인 최대 거리
                val edgeFactor = 1.0 - Math.min(1.0, distanceFromCenter / maxDistance)
                val edgeDropoff = 32 * (1.0 - Math.pow(edgeFactor, 2.0)) // 2차 곡선 적용

                // 최종 높이 결정
                val terrainHeight = baseHeight +
                        (24 * heightNoise) - // 높이 변화
                        edgeDropoff.toInt() // 가장자리 낮춤

                // 지형 생성
                for (y in 0..terrainHeight.toInt()) {
                    val block = when {
                        y == terrainHeight.toInt() -> Block.GRASS_BLOCK
                        y > terrainHeight - 3 -> Block.DIRT
                        else -> Block.STONE
                    }
                    unit.modifier().setBlock(absoluteX, y, absoluteZ, block)
                }
            }
        }

    }

    data class IslandFootprint(
        val activeChunkCoords: List<Pair<Int, Int>>, // (chunkX, chunkZ) 로컬 좌표
        val spatialBounds2D: MultiPolygon
    )
}