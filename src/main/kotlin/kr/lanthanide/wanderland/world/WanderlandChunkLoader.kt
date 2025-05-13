package kr.lanthanide.wanderland.world

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kr.lanthanide.wanderland.DBManager
import kr.lanthanide.wanderland.util.BiomeUtils
import kr.lanthanide.wanderland.util.RLEUtils
import net.kyori.adventure.nbt.TagStringIO
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.DynamicChunk
import net.minestom.server.instance.IChunkLoader
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.world.biome.Biome
import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.*


// TODO adapt to changed island-chunk schema
class WanderlandChunkLoader: IChunkLoader {
    private val dbDispatcher = Dispatchers.IO
    private val scope = CoroutineScope(dbDispatcher + SupervisorJob())
    private val gson = Gson()

    override fun loadChunk(instance: Instance, chunkX: Int, chunkZ: Int): Chunk? {
        println("Loading chunk at $chunkX, $chunkZ for instance ${instance.uuid}")

        return runBlocking(dbDispatcher) { // Minestom의 I/O 스레드에서 이 runBlocking 블록이 완료될 때까지 대기
            var connection: Connection? = null
            var chunkMetaPs: PreparedStatement?
            var sectionPs: PreparedStatement?
            var chunkMetaRs: ResultSet?
            var sectionRs: ResultSet?
            try {
                connection = DBManager.getConnection()
                val worldUuid = instance.uuid
                val dimensionType = MinecraftServer.getDimensionTypeRegistry().get(instance.dimensionType)

                // 1. 청크 메타데이터 조회
                val chunkSql = "SELECT chunk_id, fk_island_id FROM chunk WHERE fk_world_id = ? AND chunk_x = ? AND chunk_z = ?"
                chunkMetaPs = connection.prepareStatement(chunkSql)
                chunkMetaPs.setObject(1, worldUuid)
                chunkMetaPs.setInt(2, chunkX)
                chunkMetaPs.setInt(3, chunkZ)
                chunkMetaRs = chunkMetaPs.executeQuery()

                val dbChunkMeta: DbChunkMeta? = if (chunkMetaRs.next()) {
                    DbChunkMeta(
                        chunkMetaRs.getObject("chunk_id") as UUID,
                        chunkMetaRs.getObject("fk_island_id") as? UUID // Nullable
                    )
                } else {
                    null
                }

                if (dbChunkMeta == null) {
                    println("Chunk not found in DB: $chunkX, $chunkZ for world $worldUuid")
                    return@runBlocking null // 청크 메타데이터 없으면 null 반환
                }

                // 2. Minestom 청크 객체 생성
                val minestomChunk = DynamicChunk(instance, chunkX, chunkZ)

                // Chunk의 tag에 fk_island_id 저장
                minestomChunk.setTag(Tag.UUID("island_id"), dbChunkMeta.islandId)

                // 3. 청크 섹션 데이터 조회
                val sectionSql = "SELECT section_y, block_palette, block_indices, block_nbt_data, biome_data, block_data_version FROM chunk_section WHERE fk_chunk_id = ? ORDER BY section_y"
                sectionPs = connection.prepareStatement(sectionSql)
                sectionPs.setObject(1, dbChunkMeta.chunkId)
                sectionRs = sectionPs.executeQuery()

                val sectionsData = mutableListOf<DbChunkSectionData>()
                while (sectionRs.next()) {
                    val pgNbtObject = sectionRs.getObject("block_nbt_data") as? PGobject
                    val nbtJsonString = pgNbtObject?.value

                    sectionsData.add(
                        DbChunkSectionData(
                            sectionRs.getInt("section_y"),
                            (sectionRs.getArray("block_palette")?.array as? Array<Int>)?.toIntArray(),
                            sectionRs.getBytes("block_indices"),
                            nbtJsonString,
                            sectionRs.getBytes("biome_data"),
                            sectionRs.getInt("block_data_version")
                        )
                    )
                }
                if (sectionsData.isEmpty() && chunkMetaRs.isBeforeFirst) { // 메타는 있는데 섹션이 없는 경우 (완전 빈 청크일 수 있음)
                    println("Chunk meta found but no sections for chunk ${dbChunkMeta.chunkId}. Assuming empty chunk or error.")
                }


                // 4. 섹션 데이터를 Minestom 청크에 적용
                for (sectionData in sectionsData) {
                    // 버전 관리 (지금은 v1만 가정)
                    if (sectionData.blockDataVersion != 1) {
                        System.err.println("Unsupported block_data_version ${sectionData.blockDataVersion} for chunk ${dbChunkMeta.chunkId}, section ${sectionData.sectionY}")
                        continue
                    }

                    val palette = sectionData.blockPalette
                    val rleIndices = sectionData.blockIndicesRLE

                    if (dimensionType == null) {
                        System.err.println("Dimension type not found for instance ${instance.uuid}")
                        continue
                    }
                    if (palette != null && rleIndices != null && rleIndices.isNotEmpty()) {
                        // 16*16*16 = 4096
                        val decodedIndices = RLEUtils.rleDecode(rleIndices, 4096)
                        if (decodedIndices.size != 4096) {
                            System.err.println("Decoded indices size mismatch for chunk ${dbChunkMeta.chunkId}, section ${sectionData.sectionY}. Expected 4096, got ${decodedIndices.size}")
                            // 이 경우 해당 섹션 블록 설정을 건너뛰거나, 오류 처리를 할 수 있습니다.
                            // 여기서는 일단 진행하지만, 실제로는 더 강력한 오류 처리가 필요합니다.
                        }

                        var nbtMap: Map<String, String>? = null
                        if (sectionData.blockNbtDataJson != null) {
                            try {
                                val type = object : TypeToken<Map<String, String>>() {}.type
                                nbtMap = gson.fromJson(sectionData.blockNbtDataJson, type)
                            } catch (e: Exception) {
                                System.err.println("Error parsing NBT JSON for chunk ${dbChunkMeta.chunkId}, section ${sectionData.sectionY}: ${e.message}")
                            }
                        }

                        for (yInSection in 0..15) {
                            val actualY = sectionData.sectionY * 16 + yInSection
                            // Minestom은 월드 Y 범위 제한이 있을 수 있으므로, instance.dimensionType.minY/maxY 확인 필요
                            if (actualY < dimensionType.minY() || actualY >= dimensionType.maxY() + 16 /* 청크 섹션의 끝 고려 */ ) {
                                System.err.println("Block is out of height bounds for instance ${instance.uuid}, chunk ${dbChunkMeta.chunkId}, section ${sectionData.sectionY}")
                            }

                            for (zInSection in 0..15) {
                                for (xInSection in 0..15) {
                                    val index1D = (yInSection * 16 + zInSection) * 16 + xInSection // Y Z X order
                                    if (index1D < decodedIndices.size) { // 디코딩된 인덱스 배열 범위 확인
                                        val paletteIndex = decodedIndices[index1D]
                                        if (paletteIndex < palette.size) { // 팔레트 범위 확인
                                            val stateId = palette[paletteIndex]
                                            var block = Block.fromStateId(stateId)

                                            // NBT 적용
                                            val nbtKey = "$xInSection,$yInSection,$zInSection"
                                            nbtMap?.get(nbtKey)?.let { snbtString ->
                                                try {
                                                    val compoundTag = TagStringIO.get().asCompound(snbtString)
                                                    block = block?.withNbt(compoundTag)
                                                } catch (e: Exception) {
                                                    System.err.println("Error parsing SNBT string '$snbtString' for block $xInSection,$yInSection,$zInSection in chunk ${dbChunkMeta.chunkId}, section ${sectionData.sectionY}: ${e.message}")
                                                }
                                            }

                                            if (block != null) {
                                                minestomChunk.setBlock(xInSection, actualY, zInSection, block)
                                            } else {
                                                System.err.println("Block from stateId $stateId is null for chunk ${dbChunkMeta.chunkId}, section ${sectionData.sectionY}. Skipping.")
                                            }
                                        } else {
                                            System.err.println("Palette index out of bounds for chunk ${dbChunkMeta.chunkId}, section ${sectionData.sectionY}")
                                        }
                                    } else {
                                        // 이미 위에서 decodedIndices.size != 4096 일때 경고 했으므로, 여기서는 무시하거나 로깅
                                    }
                                }
                            }
                        }
                    } else if (palette == null && rleIndices == null) {
                        // 모든 블록이 기본 블록(예: 공기, stateId 0)으로 채워진 섹션일 수 있습니다.
                        // 또는 데이터가 없는 경우일 수 있습니다. 이 경우 명시적으로 공기로 채우거나,
                        // 생성기가 이후에 채우도록 두거나 (보고서에 따르면 로더가 모든 블록을 설정해야 함)
                        // 현재는 아무 작업도 하지 않으므로 Minestom 청크는 기본값(공기)으로 유지됩니다.
                        println("Section ${sectionData.sectionY} for chunk ${dbChunkMeta.chunkId} has no palette/indices, assuming air.")
                    }


                    // 생물군계 적용
                    sectionData.biomeDataBytes?.let { biomeBytes ->
                        if (biomeBytes.isNotEmpty()) {
                            val biomeNetworkIds = BiomeUtils.deserializeSectionBiomeData(biomeBytes) // 64개의 ID 반환
                            var biomeIdx = 0
                            for (bz in 0..3) { // 4x4x4 셀 Z
                                for (by in 0..3) { // 4x4x4 셀 Y
                                    for (bx in 0..3) { // 4x4x4 셀 X
                                        val biomeId = biomeNetworkIds.getOrNull(biomeIdx++) ?: MinecraftServer.getBiomeRegistry().getId(Biome.PLAINS) // 기본값 또는 오류 처리
                                        val biome = MinecraftServer.getBiomeRegistry().getKey(biomeId) ?: Biome.PLAINS // ID로 Biome 객체 찾기

                                        // 4x4x4 셀 내의 각 블록에 생물군계 설정
                                        for (dz in 0..3) {
                                            for (dy in 0..3) {
                                                for (dx in 0..3) {
                                                    val worldX = chunkX * 16 + bx * 4 + dx // 청크 내 x 좌표 (0-15)
                                                    val worldY = sectionData.sectionY * 16 + by * 4 + dy // 실제 월드 y 좌표
                                                    val worldZ = chunkZ * 16 + bz * 4 + dz // 청크 내 z 좌표 (0-15)
                                                    // 위 worldX, worldZ 계산은 bx, bz 기준이므로 0-15 범위로 수정해야 합니다.
                                                    // 각 4x4x4 셀의 시작 좌표 (청크 내부 기준)
                                                    val cellBaseX = bx * 4
                                                    val cellBaseY = by * 4 // section y 기준
                                                    val cellBaseZ = bz * 4

                                                    val blockXInChunk = cellBaseX + dx
                                                    val blockYInChunkSection = cellBaseY + dy // 섹션 내부 Y (0-15)
                                                    val blockZInChunk = cellBaseZ + dz

                                                    val actualBlockYInWorld = sectionData.sectionY * 16 + blockYInChunkSection

                                                    if (biome != null &&
                                                        actualBlockYInWorld >= dimensionType.minY() &&
                                                        actualBlockYInWorld < dimensionType.maxY() + 16 /* 청크 섹션의 끝 고려 */) {
                                                        minestomChunk.setBiome(blockXInChunk, actualBlockYInWorld, blockZInChunk, biome)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                println("Successfully loaded chunk $chunkX, $chunkZ for world $worldUuid")
                return@runBlocking minestomChunk
            } catch (e: Exception) {
                // 오류 로깅
                System.err.println("Error loading chunk $chunkX, $chunkZ: ${e.message}")
                e.printStackTrace()
                null // 오류 발생 시 null 반환
            } finally {
                connection?.close()
            }
        }
    }

    override fun saveChunk(chunk: Chunk) {
        val chunkX = chunk.chunkX
        val chunkZ = chunk.chunkZ
        val worldUuid = chunk.instance.uuid // Instance.uuid 사용
        val dimensionType = MinecraftServer.getDimensionTypeRegistry().get(chunk.instance.dimensionType) ?: error("Dimension type not found! This shouldn't happen...")

        println("Saving chunk at $chunkX, $chunkZ for world $worldUuid")

        runBlocking(dbDispatcher) {
            var connection: Connection? = null
            try {
                connection = DBManager.getConnection()
                connection.autoCommit = false // 트랜잭션 시작

                // 1. fk_island_id 결정
                val fkIslandId: UUID? = chunk.getTag(Tag.UUID("island_id"))

                // 2. chunk 테이블에 메타데이터 UPSERT (INSERT ... ON CONFLICT DO UPDATE)
                // 먼저 chunk_id가 있는지 확인하거나, (fk_world_id, chunk_x, chunk_z)로 기존 chunk_id를 가져옴
                var chunkIdFromDb: UUID? = null
                val selectChunkIdSql =
                    "SELECT chunk_id FROM chunk WHERE fk_world_id = ? AND chunk_x = ? AND chunk_z = ?"
                connection.prepareStatement(selectChunkIdSql).use { ps ->
                    ps.setObject(1, worldUuid)
                    ps.setInt(2, chunkX)
                    ps.setInt(3, chunkZ)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            chunkIdFromDb = rs.getObject("chunk_id") as UUID
                        }
                    }
                }

                if (chunkIdFromDb == null) { // 새 청크
                    chunkIdFromDb = UUID.randomUUID()
                    val insertChunkSql = """
                    INSERT INTO chunk (chunk_id, fk_island_id, chunk_x, chunk_z, fk_world_id, last_modified_at)
                    VALUES (?, ?, ?, ?, ?, NOW())
                """.trimIndent()
                    connection.prepareStatement(insertChunkSql).use { ps ->
                        ps.setObject(1, chunkIdFromDb)
                        if (fkIslandId != null) ps.setObject(2, fkIslandId) else ps.setNull(2, Types.NULL)
                        ps.setInt(3, chunkX)
                        ps.setInt(4, chunkZ)
                        ps.setObject(5, worldUuid)
                        ps.executeUpdate()
                    }
                } else { // 기존 청크 업데이트
                    val updateChunkSql = """
                    UPDATE chunk SET fk_island_id = ?, last_modified_at = NOW()
                    WHERE chunk_id = ?
                """.trimIndent()
                    connection.prepareStatement(updateChunkSql).use { ps ->
                        if (fkIslandId != null) ps.setObject(1, fkIslandId) else ps.setNull(1, Types.NULL)
                        ps.setObject(2, chunkIdFromDb)
                        ps.executeUpdate()
                    }
                }

                // 3. 각 섹션 데이터 저장/업데이트
                // Minestom 청크의 섹션은 Y 인덱스로 관리됨 (worldY / 16)
                val minSectionY = chunk.minSection
                val maxSectionY = chunk.maxSection
                val dimensionMinY = dimensionType.minY()
                val dimensionMaxY = dimensionType.maxY() // 블록 Y 좌표의 최대값 (포함)

                // chunk_section 테이블 UPSERT SQL
                val upsertSectionSql = """
                INSERT INTO chunk_section (section_id, fk_chunk_id, section_y, block_palette, block_indices, block_nbt_data, biome_data, block_data_version)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                ON CONFLICT (fk_chunk_id, section_y) DO UPDATE SET
                    block_palette = EXCLUDED.block_palette,
                    block_indices = EXCLUDED.block_indices,
                    block_nbt_data = EXCLUDED.block_nbt_data,
                    biome_data = EXCLUDED.biome_data,
                    block_data_version = EXCLUDED.block_data_version
            """.trimIndent() // section_id는 ON CONFLICT 시 어떻게 할지? 여기선 새 UUID를 쓰지만, 고유 제약조건 때문에 section_id도 조회해서 써야 함.

                connection.prepareStatement(upsertSectionSql).use { sectionPs ->
                    for (sectionYDbIdx in minSectionY..maxSectionY) {
                        // Minestom 1.20.6+ `chunk.getSection(sectionY)` 는 `net.minestom.server.instance.Section`을 반환.
                        // 하지만 블록 정보 추출은 여전히 chunk.getBlock()을 통해 하는 것이 일반적.
                        // 또는 minestomSection.blockPalette().getAll { x,y,z, value -> ... } 등을 활용.

                        val currentSectionPaletteMap = mutableMapOf<Int, Int>() // stateId to paletteIndex
                        val reversePaletteList = mutableListOf<Int>() // paletteIndex to stateId
                        val sectionIndicesArray = IntArray(4096)
                        val sectionNbtMap = mutableMapOf<String, String>() // "x,y_in_s,z" -> SNBT
                        var paletteIdxCounter = 0
                        var hasNonDefaultBlock = false // 공기 외 블록 또는 NBT가 있는지 여부

                        for (yS in 0..15) { // 섹션 내 Y (0-15)
                            val blockY = sectionYDbIdx * 16 + yS // 실제 월드 Y 좌표
                            if (blockY < dimensionMinY || blockY > dimensionMaxY) continue // 월드 범위 밖이면 스킵

                            for (zS in 0..15) {
                                for (xS in 0..15) {
                                    val block = chunk.getBlock(xS, blockY, zS)
                                    val stateId = block.stateId() // 이제 Int 타입

                                    if (stateId != Block.AIR.stateId()) { // 공기가 아닌 블록이 하나라도 있으면
                                        hasNonDefaultBlock = true
                                    }

                                    val paletteAssignedIdx = currentSectionPaletteMap.computeIfAbsent(stateId) {
                                        reversePaletteList.add(stateId)
                                        paletteIdxCounter++
                                        paletteIdxCounter - 1
                                    }
                                    sectionIndicesArray[(yS * 16 + zS) * 16 + xS] = paletteAssignedIdx

                                    if (block.hasNbt()) {
                                        block.nbt()?.let { nbt ->
                                            sectionNbtMap["$xS,$yS,$zS"] =
                                                TagStringIO.get().asString(nbt) // toSNBT() 사용
                                            hasNonDefaultBlock = true // NBT가 있어도 저장 가치가 있음
                                        }
                                    }
                                }
                            }
                        }

                        // 생물군계 데이터 추출 (4x4x4 셀 단위, 총 64개)
                        val sectionBiomeIds = IntArray(64)
                        var biomeCellIdx = 0
                        for (bzC in 0..3) { // 4x4x4 셀 Z 인덱스
                            for (byC in 0..3) { // 4x4x4 셀 Y 인덱스
                                for (bxC in 0..3) { // 4x4x4 셀 X 인덱스
                                    // 각 4x4x4 셀의 중앙 또는 대표 지점에서 생물군계 샘플링
                                    val sampleX = bxC * 4 + 2 // 셀 내 x (0-15)
                                    val sampleYInSection = byC * 4 + 2 // 셀 내 y (0-15)
                                    val sampleZ = bzC * 4 + 2 // 셀 내 z (0-15)
                                    val sampleWorldY = sectionYDbIdx * 16 + sampleYInSection

                                    val biomeId = if (sampleWorldY >= dimensionMinY && sampleWorldY <= dimensionMaxY) {
                                        MinecraftServer.getBiomeRegistry()
                                            .getId(chunk.getBiome(sampleX, sampleWorldY, sampleZ))
                                    } else {
                                        MinecraftServer.getBiomeRegistry().getId(Biome.PLAINS) // 월드 범위 밖이면 기본값
                                    }
                                    sectionBiomeIds[biomeCellIdx++] = biomeId // null일 경우 기본값
                                }
                            }
                        }

                        // 데이터 직렬화
                        val dbPaletteArray: IntArray?
                        val dbIndicesRLE: ByteArray?

                        if (!hasNonDefaultBlock && sectionNbtMap.isEmpty()) {
                            // 모든 블록이 공기이고 NBT도 없는 경우, 팔레트와 인덱스를 NULL로 저장하거나 최소화
                            // 예: 팔레트에 [0(공기 stateId)], 인덱스는 모든 값이 0인 RLE 데이터
                            // 여기서는 간단히 null로 처리 (loadChunk에서 null 처리 로직 필요)
                            dbPaletteArray = null // 또는 intArrayOf(Block.AIR.stateId())
                            dbIndicesRLE = null // 또는 RLEUtils.rleEncode(IntArray(4096) { 0 }) -> palette 인덱스 0
                        } else {
                            dbPaletteArray = reversePaletteList.toIntArray()
                            dbIndicesRLE = RLEUtils.rleEncode(sectionIndicesArray)
                        }

                        val dbNbtJsonString = if (sectionNbtMap.isNotEmpty()) gson.toJson(sectionNbtMap) else null
                        val dbBiomeBytes = BiomeUtils.serializeSectionBiomeData(sectionBiomeIds)
                        val currentBlockDataVersion = 1 // 현재 버전

                        // section_id 조회 또는 생성
                        var sectionIdFromDb: UUID? = null
                        val selectSectionIdSql =
                            "SELECT section_id FROM chunk_section WHERE fk_chunk_id = ? AND section_y = ?"
                        connection.prepareStatement(selectSectionIdSql).use { psSelId ->
                            psSelId.setObject(1, chunkIdFromDb)
                            psSelId.setInt(2, sectionYDbIdx)
                            psSelId.executeQuery().use { rsSelId ->
                                if (rsSelId.next()) {
                                    sectionIdFromDb = rsSelId.getObject("section_id") as UUID
                                }
                            }
                        }
                        val sectionUuidToUse = sectionIdFromDb ?: UUID.randomUUID()


                        // UPSERT 실행
                        sectionPs.setObject(1, sectionUuidToUse)
                        sectionPs.setObject(2, chunkIdFromDb)
                        sectionPs.setInt(3, sectionYDbIdx)

                        if (dbPaletteArray != null) sectionPs.setArray(
                            4,
                            connection.createArrayOf("INTEGER", dbPaletteArray.toTypedArray())
                        ) else sectionPs.setNull(4, Types.ARRAY)
                        if (dbIndicesRLE != null) sectionPs.setBytes(5, dbIndicesRLE) else sectionPs.setNull(
                            5,
                            Types.BINARY
                        )
                        if (dbNbtJsonString != null) sectionPs.setString(6, dbNbtJsonString) else sectionPs.setNull(
                            6,
                            Types.VARCHAR
                        ) // jsonb로 캐스팅은 SQL에서
                        if (dbBiomeBytes.isNotEmpty()) sectionPs.setBytes(7, dbBiomeBytes) else sectionPs.setNull(
                            7,
                            Types.BINARY
                        )
                        sectionPs.setInt(8, currentBlockDataVersion)

                        sectionPs.executeUpdate()
                    }
                }

                connection.commit() // 트랜잭션 커밋
                println("Successfully saved chunk $chunkX, $chunkZ for world $worldUuid")

            } catch (e: Exception) {
                System.err.println("Critical error saving chunk $chunkX, $chunkZ for world $worldUuid: ${e.message}")
                e.printStackTrace()
                try {
                    connection?.rollback() // 오류 발생 시 롤백
                } catch (re: Exception) {
                    System.err.println("Error during rollback: ${re.message}")
                }
            } finally {
                try {
                    connection?.autoCommit = true // 원래 상태로 복구
                    connection?.close()
                } catch (ce: Exception) {
                    System.err.println("Error closing connection: ${ce.message}")
                }
            }
        }
    }
}

data class DbChunkMeta(val chunkId: UUID, val islandId: UUID?) // fk_island_id는 nullable

data class DbChunkSectionData(
    val sectionY: Int,
    val blockPalette: IntArray?, // Nullable if all air
    val blockIndicesRLE: ByteArray?, // Nullable if all air
    val blockNbtDataJson: String?, // JSON 문자열 (SNBT 맵)
    val biomeDataBytes: ByteArray?,
    val blockDataVersion: Int
) {
    // IntArray, ByteArray에 대한 equals/hashCode 자동 생성 주의 (참조 비교가 될 수 있음)
    // 필요시 커스텀 구현
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DbChunkSectionData

        if (sectionY != other.sectionY) return false
        if (blockPalette != null) {
            if (other.blockPalette == null) return false
            if (!blockPalette.contentEquals(other.blockPalette)) return false
        } else if (other.blockPalette != null) return false
        if (blockIndicesRLE != null) {
            if (other.blockIndicesRLE == null) return false
            if (!blockIndicesRLE.contentEquals(other.blockIndicesRLE)) return false
        } else if (other.blockIndicesRLE != null) return false
        if (blockNbtDataJson != other.blockNbtDataJson) return false
        if (biomeDataBytes != null) {
            if (other.biomeDataBytes == null) return false
            if (!biomeDataBytes.contentEquals(other.biomeDataBytes)) return false
        } else if (other.biomeDataBytes != null) return false
        if (blockDataVersion != other.blockDataVersion) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sectionY
        result = 31 * result + (blockPalette?.contentHashCode() ?: 0)
        result = 31 * result + (blockIndicesRLE?.contentHashCode() ?: 0)
        result = 31 * result + (blockNbtDataJson?.hashCode() ?: 0)
        result = 31 * result + (biomeDataBytes?.contentHashCode() ?: 0)
        result = 31 * result + blockDataVersion
        return result
    }
}