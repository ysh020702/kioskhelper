// com/example/kioskhelper/core/RagMatcher.kt

package com.example.kioskhelper.core

import android.content.Context
import com.example.kioskhelper.core.RealtimeKioskPipeline.ButtonDet
import org.json.JSONObject
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.math.max

// 최종 매칭 결과를 표현하기 위한 Sealed Class. 결과의 종류를 명확하게 구분할 수 있습니다.
sealed class MatchResult
data class SingleMatch(val button: ButtonDet) : MatchResult()
data class AmbiguousMatch(val button1: ButtonDet, val button2: ButtonDet) : MatchResult()
data class ScrollMatch(val scrollButton: ButtonDet) : MatchResult()
object NoMatch : MatchResult()


class RagMatcher(private val context: Context) {

    // JSON 파일의 데이터를 담을 데이터 클래스
    private data class SynonymsData(val synonyms: List<String>, val tags: List<String>)
    private data class ScoredButton(val button: ButtonDet, var score: Int)

    private val synonymsDB: Map<String, SynonymsData>

    init {
        // 앱이 시작될 때 assets 폴더의 JSON 파일을 읽어 메모리에 저장합니다.
        synonymsDB = parseSynonymsJson("kiosk_synonyms.json")
    }

    /**
     * 사용자의 발화와 현재 화면의 버튼들을 기반으로 최적의 버튼을 찾습니다.
     */
    fun findBestMatch(userInput: String, buttonsOnScreen: List<ButtonDet>): MatchResult {
        if (userInput.isBlank() || buttonsOnScreen.isEmpty()) {
            return NoMatch
        }

        // 1. '검색(Retrieval)' 단계: 각 버튼의 점수를 계산합니다.
        val scoredButtons = calculateScores(userInput.lowercase(), buttonsOnScreen)

        if (scoredButtons.isEmpty() || scoredButtons[0].score < 50) {
            return NoMatch
        }

        // 2. '생성(Generation)' 단계: 점수와 상황을 바탕으로 최종 행동을 결정합니다.
        return decideNextAction(scoredButtons, buttonsOnScreen)
    }

    private fun calculateScores(userInput: String, buttons: List<ButtonDet>): List<ScoredButton> {
        val scoredButtons = mutableListOf<ScoredButton>()
        val userInputWords = userInput.split(" ").filter { it.isNotEmpty() }

        for (button in buttons) {
            val buttonLabel = (button.text ?: button.role ?: "").lowercase()
            if (buttonLabel.isBlank()) continue

            var currentScore = 0

            // 규칙 1: 완벽 일치 (200점) - 버튼 텍스트가 사용자 발화에 그대로 포함
            if (userInput.contains(buttonLabel)) {
                currentScore = max(currentScore, 200)
            }

            // 규칙 2: 동의어 일치 (180점)
            // Case A: 버튼 텍스트가 DB의 키일 때
            synonymsDB[buttonLabel]?.synonyms?.forEach { synonym ->
                if (userInput.contains(synonym.lowercase())) {
                    currentScore = max(currentScore, 180)
                }
            }
            // Case B: 사용자 발화의 단어가 DB의 키일 때
            userInputWords.forEach { word ->
                synonymsDB[word]?.synonyms?.forEach { synonym ->
                    if (buttonLabel.contains(synonym.lowercase())) {
                        currentScore = max(currentScore, 180)
                    }
                }
            }

            // 규칙 3: 상위어 포함 (100점, 계층 구조 탐색용) - 예: 발화 "치즈버거", 버튼 "버거"
            if (userInput.contains(buttonLabel) && userInput.length > buttonLabel.length) {
                currentScore = max(currentScore, 100)
            }

            // 규칙 4: 하위어 포함 (70점) - 예: 발화 "치즈버거", 버튼 "더블치즈버거"
            if (buttonLabel.contains(userInput)) {
                currentScore = max(currentScore, 70)
            }


            if (currentScore > 0) {
                scoredButtons.add(ScoredButton(button, currentScore))
            }
        }

        return scoredButtons.sortedByDescending { it.score }
    }

    private fun decideNextAction(scoredButtons: List<ScoredButton>, allButtonsOnScreen: List<ButtonDet>): MatchResult {
        val topButton = scoredButtons[0]
        val secondButton = scoredButtons.getOrNull(1)

        // 화면에 스크롤 관련 UI가 있는지 확인 (IconRoleClassifier의 role 활용)
        val scrollButton = allButtonsOnScreen.firstOrNull {
            it.role in listOf("arrow_down", "arrow_up", "scroll_bar") ||
                    (it.text != null && synonymsDB["스크롤"]?.synonyms?.contains(it.text) == true)
        }
        val hasScrollUI = scrollButton != null

        // [판단 로직 시작]
        val isSufficient = topButton.score >= 180
        val hasBigMargin = secondButton == null || topButton.score > secondButton.score * 1.5
        if (isSufficient && hasBigMargin) {
            return SingleMatch(topButton.button)
        }

        if (topButton.score > 70 && hasScrollUI) {
            return ScrollMatch(scrollButton!!)
        }

        if (topButton.score > 70 && secondButton != null && secondButton.score > 60) {
            return AmbiguousMatch(topButton.button, secondButton.button)
        }

        if (topButton.score >= 50) {
            return SingleMatch(topButton.button)
        }

        return NoMatch
    }

    private fun parseSynonymsJson(fileName: String): Map<String, SynonymsData> {
        val db = mutableMapOf<String, SynonymsData>()
        try {
            val inputStream: InputStream = context.assets.open(fileName)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)

            for (key in jsonObject.keys()) {
                if (key.startsWith("__comment__")) continue

                val item = jsonObject.getJSONObject(key)
                val synonyms = (0 until item.getJSONArray("synonyms").length()).map {
                    item.getJSONArray("synonyms").getString(it)
                }
                val tags = (0 until item.getJSONArray("tags").length()).map {
                    item.getJSONArray("tags").getString(it)
                }
                db[key] = SynonymsData(synonyms, tags)
            }
        } catch (e: Exception) {
            e.printStackTrace() // 실제 앱에서는 Crashlytics 등으로 리포트하는 것이 좋습니다.
        }
        return db
    }
}