package kr.lanthanide.wanderland.world

import net.minestom.server.instance.block.Block
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 하늘섬의 다양한 특성을 정의
 * 별도 표기가 없다면 Float 값은 0.0~1.0 범위
 */
data class IslandProperty(
    // 기본 생성 속성
    val seed: Long,
    val name: String = "Unknown Island",

    // 크기 및 형태
    val standardSize: Int = 8, // 표준적인 섬의 청크 단위 직경; 실제 생성 크기는 다를 수 있음
    val shapeComplexity: Float = 0.5f, // 크기가 노이즈 함수로 결정되는 비율
    val islandBaseShape: IslandBasehape = IslandBasehape.CIRCULAR,

    // 지형 관련 속성
    val baseHeight: Int = 64,
    val heightVariation: Int = 16,
    val steepness: Float = 0.5f,
    val flatness: Float = 0.3f,

    // 생태 관련 속성
    val temperature: Float = 0.5f, // 0은 섭씨 -30도 수준, 1은 섭씨 40도 수준
    val humidity: Float = 0.5f,

    // 재질 관련 속성
    val surfaceBlock: Block = Block.GRASS_BLOCK,
    val subSurfaceBlock: Block = Block.DIRT,
    val deepBlock: Block = Block.STONE,

    // 구조물 및 특수 기능
    val hasWaterFeatures: Boolean = false,         // 수공간(호수, 연못) 포함 여부
    val hasForest: Boolean = true,                 // 숲 포함 여부
    val hasStructures: Boolean = false,            // 구조물 포함 여부
    val hasOres: Boolean = true,                   // 광물 포함 여부
    val hasCaves: Boolean = false,                 // 동굴 시스템 포함 여부

    // 고급 속성
    val customFeatures: Map<String, Any> = mapOf() // 사용자 정의 추가 속성
) {
    /**
     * 섬의 높이 프로필 계산
     */
    fun getHeightProfile(distanceFromCenter: Double, maxDistance: Double): Int {
        val normalizedDistance = distanceFromCenter / maxDistance
        val edgeFactor = 1.0 - normalizedDistance

        // 경사도를 고려한 높이 감소 계산
        val steepnessFactor = edgeFactor.pow(1.0 + steepness * 2.0)

        return (baseHeight + heightVariation * steepnessFactor).roundToInt()
    }

    companion object {
        /**
         * 기본 속성값으로 새 인스턴스 생성
         */
        fun createDefault(seed: Long): IslandProperty {
            return IslandProperty(seed = seed)
        }

        /**
         * 특정 테마에 맞는 사전 설정 인스턴스 생성
         */
        fun createThemed(seed: Long, theme: IslandTheme): IslandProperty {
            return when (theme) {
                IslandTheme.TROPICAL -> IslandProperty(
                    seed = seed,
                    name = "Tropical Paradise",
                    temperature = 0.8f,
                    humidity = 0.7f,
                    hasWaterFeatures = true,
                    hasForest = true,
                    surfaceBlock = Block.GRASS_BLOCK,
                )

                IslandTheme.VOLCANIC -> IslandProperty(
                    seed = seed,
                    name = "Volcanic Isle",
                    temperature = 0.9f,
                    humidity = 0.3f,
                    steepness = 0.8f,
                    heightVariation = 24,
                    surfaceBlock = Block.BASALT,
                    subSurfaceBlock = Block.BLACKSTONE,
                    deepBlock = Block.MAGMA_BLOCK,
                    hasForest = false,
                )

                IslandTheme.MESA -> IslandProperty(
                    seed = seed,
                    name = "Mesa Plateau",
                    temperature = 0.7f,
                    humidity = 0.1f,
                    flatness = 0.7f,
                    steepness = 0.6f,
                    surfaceBlock = Block.TERRACOTTA,
                    subSurfaceBlock = Block.RED_TERRACOTTA,
                    deepBlock = Block.STONE,
                    hasForest = false,
                )

                IslandTheme.MUSHROOM -> IslandProperty(
                    seed = seed,
                    name = "Fungal Isle",
                    temperature = 0.5f,
                    humidity = 0.9f,
                    surfaceBlock = Block.MYCELIUM,
                    subSurfaceBlock = Block.DIRT,
                    hasStructures = true,
                    customFeatures = mapOf("hasMushrooms" to true)
                )

                IslandTheme.WINTER -> IslandProperty(
                    seed = seed,
                    name = "Frozen Peak",
                    temperature = 0.1f,
                    humidity = 0.4f,
                    heightVariation = 20,
                    surfaceBlock = Block.SNOW_BLOCK,
                    subSurfaceBlock = Block.POWDER_SNOW,
                    deepBlock = Block.PACKED_ICE,
                    hasForest = true,
                )
            }
        }
    }
}

/**
 * 섬의 기본 형태
 */
enum class IslandBasehape {
    CIRCULAR,    // 원형
    ELLIPTICAL,  // 타원형
    IRREGULAR,   // 불규칙한 형태
    CRESCENT,    // 초승달 형태
}

/**
 * 사전 정의된 섬 테마
 */
enum class IslandTheme {
    TROPICAL,    // 열대 섬
    VOLCANIC,    // 화산 섬
    MESA,        // 메사 고원
    MUSHROOM,    // 버섯 섬
    WINTER       // 겨울 테마
}