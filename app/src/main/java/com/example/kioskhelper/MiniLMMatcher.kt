package com.example.kioskhelper

import android.content.Context
import android.graphics.RectF
import android.util.Log
import com.example.kioskhelper.presentation.model.ButtonBox
import org.json.JSONObject
import java.nio.charset.Charset
import kotlin.math.max
import kotlin.math.min

class MiniLMMatcher(context: Context) {

    private val similarGroups: List<Set<String>>

    init {
        // JSON 파일 로드
        val jsonStr = context.assets.open("similar_words.json").readBytes().toString(Charset.defaultCharset())
        val json = JSONObject(jsonStr)
        val groupsArray = json.getJSONArray("groups")

        val tempList = mutableListOf<Set<String>>()
        for (i in 0 until groupsArray.length()) {
            val arr = groupsArray.getJSONArray(i)
            val set = mutableSetOf<String>()
            for (j in 0 until arr.length()) {
                set.add(arr.getString(j))
            }
            tempList.add(set)
        }
        similarGroups = tempList
    }


    private val threshold = 0.6 // 문자열 유사도 임계값

    /** 버튼과 query 유사도 계산 후 id 반환 */
    fun matchAndHighlight(query: String, buttons: List<ButtonBox>): List<Int> {
        if (buttons.isEmpty() || query.isBlank()) return emptyList()

        val results = mutableListOf<Pair<Int, Float>>()

        for (b in buttons) {
            val text = b.displayLabel.orEmpty()
            if (text.isBlank()) continue

            val simScore = if (areSimilar(query, text)) 1f else 0f

            Log.d("SimilarityCheck", "Query: \"$query\" | Button: \"$text\" | Similarity: $simScore")

            if (simScore >= 1f) {
                results.add(b.id to simScore)
            }
        }

        return results.sortedByDescending { it.second }.map { it.first }
    }

    /** 유사도 검사: 그룹 + 문자열 유사도 */
    private fun areSimilar(a: String, b: String): Boolean {

        val normA = a.replace(" ", "")
        val normB = b.replace(" ", "")

        // 동일 문자열 또는 부분 문자열
        if (normA == normB || normA in normB || normB in normA) return true

        // JSON 그룹 기반 유사도
        for (group in similarGroups) {
            if (normA in group && normB in group) return true
        }

        // 문자열 유사도 계산 (자모 단위 변환 후)
        val sim = similarity(HangulUtils.decompose(normA), HangulUtils.decompose(normB))
        return sim >= threshold
    }

    /** Levenshtein 기반 문자열 유사도 0~1 */
    private fun similarity(s1: String, s2: String): Double {
        val distance = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        return if (maxLen == 0) 1.0 else 1.0 - distance.toDouble() / maxLen
    }

    /** Levenshtein Distance 계산 */
    private fun levenshteinDistance(s: String, t: String): Int {
        val m = s.length
        val n = t.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // 삭제
                    dp[i][j - 1] + 1,       // 삽입
                    dp[i - 1][j - 1] + cost // 교체
                )
            }
        }
        return dp[m][n]
    }

    /** 한글 자모 분해 유틸 */
    object HangulUtils {
        private val CHO = listOf(
            'ㄱ','ㄲ','ㄴ','ㄷ','ㄸ','ㄹ','ㅁ','ㅂ','ㅃ','ㅅ','ㅆ',
            'ㅇ','ㅈ','ㅉ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'
        )
        private val JUNG = listOf(
            'ㅏ','ㅐ','ㅑ','ㅒ','ㅓ','ㅔ','ㅕ','ㅖ','ㅗ','ㅘ','ㅙ','ㅚ',
            'ㅛ','ㅜ','ㅝ','ㅞ','ㅟ','ㅠ','ㅡ','ㅢ','ㅣ'
        )
        private val JONG = listOf(
            ' ','ㄱ','ㄲ','ㄳ','ㄴ','ㄵ','ㄶ','ㄷ','ㄹ','ㄺ','ㄻ','ㄼ','ㄽ',
            'ㄾ','ㄿ','ㅀ','ㅁ','ㅂ','ㅄ','ㅅ','ㅆ','ㅇ','ㅈ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'
        )

        /** 한글 한 글자를 자모 단위로 분리 */
        fun decomposeChar(c: Char): String {
            if (c !in '가'..'힣') return c.toString()

            val base = c.code - 0xAC00
            val cho = base / (21 * 28)
            val jung = (base % (21 * 28)) / 28
            val jong = base % 28

            val sb = StringBuilder()
            sb.append(CHO[cho])
            sb.append(JUNG[jung])
            if (jong != 0) sb.append(JONG[jong])
            return sb.toString()
        }

        /** 문자열 전체를 자모 단위로 변환 */
        fun decompose(str: String): String {
            return str.flatMap { decomposeChar(it).toList() }.joinToString("")
        }
    }

}

