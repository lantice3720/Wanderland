package kr.lanthanide.wanderland.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object RLEUtils {
    /**
     * RLE 디코딩을 수행하여 팔레트 인덱스 배열을 반환합니다.
     * 저장된 형식: [값(Int), 반복횟수(Int), 값(Int), 반복횟수(Int), ...]
     * 실제로는 바이트 단위 또는 더 효율적인 가변 길이 정수 인코딩을 사용할 수 있습니다.
     * 여기서는 간단한 Int 쌍으로 가정합니다. 실제 프로덕션에서는 더 정교한 RLE 구현이 필요합니다.
     */
    fun rleDecode(compressedData: ByteArray, expectedSize: Int): IntArray {
        val decodedList = ArrayList<Int>(expectedSize)
        val bais = ByteArrayInputStream(compressedData)
        val buffer = ByteBuffer.allocate(8) // Int(4) + Int(4)
        buffer.order(ByteOrder.BIG_ENDIAN) // 또는 LISLE_ENDIAN, 저장 시와 일치해야 함

        while (bais.available() >= 8) {
            bais.read(buffer.array(), 0, 8)
            buffer.rewind()
            val value = buffer.int
            val count = buffer.int
            for (i in 0 until count) {
                if (decodedList.size < expectedSize) {
                    decodedList.add(value)
                } else {
                    // 예상 크기 초과, 오류 또는 경고 처리
                    System.err.println("RLE decode error: Decoded data exceeds expected size $expectedSize")
                    return decodedList.toIntArray()
                }
            }
        }
        if (decodedList.size != expectedSize && expectedSize > 0) { // expectedSize가 0인 경우는 모두 공기인 섹션일 수 있음
            System.err.println("RLE decode warning: Decoded size ${decodedList.size} does not match expected size $expectedSize")
        }
        return decodedList.toIntArray()
    }

    /**
     * 팔레트 인덱스 배열을 RLE 인코딩하여 ByteArray로 반환합니다.
     * 저장된 형식: [값(Int), 반복횟수(Int), 값(Int), 반복횟수(Int), ...]
     */
    fun rleEncode(indices: IntArray): ByteArray {
        if (indices.isEmpty()) return ByteArray(0)

        val baos = ByteArrayOutputStream()
        val buffer = ByteBuffer.allocate(8)
        buffer.order(ByteOrder.BIG_ENDIAN)

        var currentVal = indices[0]
        var count = 1
        for (i in 1 until indices.size) {
            if (indices[i] == currentVal) {
                count++
            } else {
                buffer.clear()
                buffer.putInt(currentVal)
                buffer.putInt(count)
                baos.write(buffer.array())
                currentVal = indices[i]
                count = 1
            }
        }
        // 마지막 시퀀스 저장
        buffer.clear()
        buffer.putInt(currentVal)
        buffer.putInt(count)
        baos.write(buffer.array())
        return baos.toByteArray()
    }
}

object BiomeUtils {
    private const val BIOMES_PER_SECTION = 64 // 4x4x4 cells per 16x16x16 section

    /**
     * 직렬화된 생물군계 데이터(네트워크 ID 배열)를 IntArray로 역직렬화합니다.
     * 각 ID는 Int로 저장되었다고 가정합니다.
     */
    fun deserializeSectionBiomeData(bytes: ByteArray): IntArray {
        if (bytes.isEmpty()) return IntArray(BIOMES_PER_SECTION) { 0 } // 기본값 (예: plains) 또는 오류

        val biomeIds = IntArray(BIOMES_PER_SECTION)
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.BIG_ENDIAN) // 저장 시와 일치

        if (bytes.size != BIOMES_PER_SECTION * Int.SIZE_BYTES) {
            System.err.println("Biome data size mismatch. Expected ${BIOMES_PER_SECTION * Int.SIZE_BYTES}, got ${bytes.size}. Returning default biomes.")
            return IntArray(BIOMES_PER_SECTION) { 0 } // 예시: plains ID
        }

        for (i in 0 until BIOMES_PER_SECTION) {
            biomeIds[i] = buffer.int
        }
        return biomeIds
    }

    /**
     * 생물군계 ID 배열(IntArray)을 직렬화하여 ByteArray로 반환합니다.
     */
    fun serializeSectionBiomeData(biomeIds: IntArray): ByteArray {
        if (biomeIds.size != BIOMES_PER_SECTION) {
            throw IllegalArgumentException("Biome ID array must contain $BIOMES_PER_SECTION elements.")
        }
        val buffer = ByteBuffer.allocate(BIOMES_PER_SECTION * Int.SIZE_BYTES)
        buffer.order(ByteOrder.BIG_ENDIAN)
        for (id in biomeIds) {
            buffer.putInt(id)
        }
        return buffer.array()
    }
}