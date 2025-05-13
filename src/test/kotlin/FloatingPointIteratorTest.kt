import kr.lanthanide.wanderland.util.step
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertSame

class FloatingPointIteratorTest {
    @Test
    fun `it works!`() {
        val result = ArrayList<Double>()
        for (x in (0.0..5.5) step 0.1) {
            result.add(x)
        }
        assertSame(result.size, 56, "Result Size should be 56")

        result.clear()

        for (x in (-10.1..1.0) step 1.0) {
            result.add(x)
        }
        assertSame(result.size, 12, "Result Size should be 12")
    }
}