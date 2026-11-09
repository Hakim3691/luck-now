package com.lucknow.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PayloadMergerTest {

    private fun page(vararg draws: List<Int>): String =
        draws.joinToString(prefix = """{"draw_history":[""", postfix = "]}") { numbers ->
            """{"numbers":[${numbers.joinToString(",")}]}"""
        }

    @Test
    fun `concatenates paginated payloads into one sequence`() {
        val pageOne = page(listOf(4, 17, 23, 31, 38, 44), listOf(7, 11, 19, 25, 33, 41))
        val pageTwo = page(listOf(2, 13, 21, 29, 35, 49), listOf(3, 9, 16, 27, 36, 45))

        val merged = PayloadMerger.merged(listOf(pageOne, pageTwo))

        assertNotNull(merged)
        assertEquals(24, merged!!.values.size)
        assertEquals(4, merged.draws)
        assertEquals(4, merged.values.first())
        assertEquals(45, merged.values.last())
    }

    @Test
    fun `drops duplicate draws across payload copies`() {
        val pageOne = page(listOf(4, 17, 23, 31, 38, 44))
        val merged = PayloadMerger.merged(listOf(pageOne, pageOne, pageOne))

        assertEquals(6, merged!!.values.size)
    }

    @Test
    fun `does not merge unrelated shapes`() {
        val draws = page(listOf(4, 17, 23, 31, 38, 44))
        val other = """{"metadata":[{"code":1},{"code":2},{"code":3},{"code":4},{"code":5},{"code":6},{"code":7},{"code":8},{"code":9},{"code":10},{"code":11}]}"""

        val merged = PayloadMerger.merged(listOf(draws, other))
        assertEquals(6, merged!!.values.size)
    }
}
