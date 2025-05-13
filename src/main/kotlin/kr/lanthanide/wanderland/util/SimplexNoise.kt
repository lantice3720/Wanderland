import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random // 코틀린 랜덤 사용

/**
 * 2D Simplex Noise 구현체.
 *
 * Stefan Gustavson의 Java 구현을 바탕으로 Kotlin으로 재작성
 */
class SimplexNoise(seed: Long) {

    // 2D 스큐잉/언스큐잉 상수
    private val F2 = 0.5 * (sqrt(3.0) - 1.0)
    private val G2 = (3.0 - sqrt(3.0)) / 6.0

    // 2D 그래디언트 벡터 (정규화되지 않음)
    private val grad2 = arrayOf(
        intArrayOf(1, 1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(-1, -1),
        intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1)
    )

    // 순열 테이블
    private val p = IntArray(256)

    // 순열 테이블을 두 배로 늘려 인덱싱 편의성 증대 (Wrapping)
    private val perm = IntArray(512)
    private val permGradIndex = IntArray(512) // 그라디언트 인덱스 (perm[i] % grad2.size)

    // 특정 시드 또는 랜덤 시드로 순열 테이블 초기화
    init {
        val random = Random(seed) // 고정 시드 사용 (결과 재현 가능), System.currentTimeMillis() 등으로 변경 가능
        val source = IntArray(256) { it }
        source.shuffle(random) // 배열 섞기

        for (i in 0 until 256) {
            p[i] = source[i]
        }

        // 순열 테이블 확장 및 그라디언트 인덱스 미리 계산
        for (i in 0 until 512) {
            perm[i] = p[i and 255] // i % 256 과 동일
            permGradIndex[i] = perm[i] % grad2.size
        }
    }

    // 내적(Dot Product) 계산 헬퍼 함수
    private fun dot(g: IntArray, x: Double, y: Double): Double {
        return g[0] * x + g[1] * y
    }

    /**
     * 2D Simplex Noise 값을 계산합니다.
     *
     * @param xin 입력 x 좌표
     * @param yin 입력 y 좌표
     * @return 계산된 노이즈 값 (대략 -1.0 ~ 1.0 범위)
     */
    fun noise(xin: Double, yin: Double): Double {
        var n0: Double // 첫 번째 꼭짓점 기여도
        var n1: Double // 두 번째 꼭짓점 기여도
        var n2: Double // 세 번째 꼭짓점 기여도

        // 1단계: 입력 좌표를 스큐잉하여 심플렉스 격자 좌표 찾기
        val s = (xin + yin) * F2
        val i = floor(xin + s).toInt()
        val j = floor(yin + s).toInt()

        // 2단계: 심플렉스 셀 원점을 언스큐잉하여 (x,y) 공간으로 변환
        val t = (i + j) * G2
        val X0 = i - t // 셀 원점 x 좌표
        val Y0 = j - t // 셀 원점 y 좌표

        // 3단계: 셀 내 상대 좌표 계산
        val x0 = xin - X0
        val y0 = yin - Y0

        // 4단계: 입력 점이 속한 심플렉스(삼각형) 결정
        val i1: Int // 두 번째 꼭짓점의 (i,j) 오프셋
        val j1: Int
        if (x0 > y0) { // 아래쪽 삼각형
            i1 = 1; j1 = 0
        } else {      // 위쪽 삼각형
            i1 = 0; j1 = 1
        }

        // 5단계: 나머지 두 꼭짓점의 상대 좌표 계산
        // x1, y1: 두 번째 꼭짓점 상대 좌표
        val x1 = x0 - i1 + G2
        val y1 = y0 - j1 + G2
        // x2, y2: 세 번째 꼭짓점 상대 좌표 (항상 (1,1) 오프셋)
        val x2 = x0 - 1.0 + 2.0 * G2
        val y2 = y0 - 1.0 + 2.0 * G2

        // 6단계: 각 꼭짓점의 해시 값(그라디언트 인덱스) 계산
        val ii = i and 255
        val jj = j and 255
        val gi0 = permGradIndex[ii + perm[jj]]
        val gi1 = permGradIndex[ii + i1 + perm[jj + j1]]
        val gi2 = permGradIndex[ii + 1 + perm[jj + 1]]

        // 7단계: 각 꼭짓점에서의 기여도 계산
        // 기여도 = (r^2 - d^2)^4 * dot(gradient, distance)
        // 여기서 r^2 = 0.5 (2D에서는 0.6을 사용하기도 하나, 원본 구현은 0.5 사용)
        // d^2 = x^2 + y^2

        var t0 = 0.5 - x0 * x0 - y0 * y0
        if (t0 < 0) {
            n0 = 0.0
        } else {
            t0 *= t0
            n0 = t0 * t0 * dot(grad2[gi0], x0, y0)
        }

        var t1 = 0.5 - x1 * x1 - y1 * y1
        if (t1 < 0) {
            n1 = 0.0
        } else {
            t1 *= t1
            n1 = t1 * t1 * dot(grad2[gi1], x1, y1)
        }

        var t2 = 0.5 - x2 * x2 - y2 * y2
        if (t2 < 0) {
            n2 = 0.0
        } else {
            t2 *= t2
            n2 = t2 * t2 * dot(grad2[gi2], x2, y2)
        }

        // 8단계: 세 기여도를 합산하고 스케일링하여 최종 노이즈 값 반환
        // 스케일링 계수(70.0)는 경험적으로 얻어진 값으로, 결과를 대략 [-1, 1] 범위로 만듭니다.
        return 70.0 * (n0 + n1 + n2)
    }
}