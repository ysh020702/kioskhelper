package com.example.kioskhelper

import android.content.Context
import android.util.Log
import com.example.kioskhelper.presentation.model.ButtonBox
import org.json.JSONObject
import java.nio.charset.Charset

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
            if (simScore >= 1f) {
                results.add(b.id to simScore)
            }
        }

        return results.sortedByDescending { it.second }.map { it.first }
    }

    /** 유사도 검사: 그룹 + 문자열 유사도 */
    private fun areSimilar(a: String, b: String): Boolean {
        // 동일 문자열 또는 부분 문자열
        if (a == b || a in b || b in a) return true

        // JSON 그룹 기반 유사도
        for (group in similarGroups) {
            if (a in group && b in group) return true
        }

        // 문자열 유사도 계산
        val sim = similarity(a, b)
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
}
