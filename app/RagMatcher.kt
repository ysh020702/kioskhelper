package com.example.kioskhelper.core

import com.example.kioskhelper.core.RealtimeKioskPipeline.ButtonDet
import kotlin.collections.map


class RagMatcher {
    private val synonyms = mapOf(
        "next" to listOf("다음","다음으로","넘어가","계속","next","continue"),
        "confirm" to listOf("확인","선택","완료","ok","확정"),
        "cancel" to listOf("취소","그만","cancel","종료"),
        "pay" to listOf("결제","지불","계산","카드 결제","checkout"),
        "back" to listOf("뒤로","이전","back","돌아가기"),
        "home" to listOf("처음","홈","메인","home"),
        "language" to listOf("언어","한국어","English","language")
    )


    data class Match(val top1: ButtonDet, val top2: ButtonDet, val ambiguous: Boolean)


    fun match(stt: String, buttons: List<ButtonDet>): Match {
        val s = stt.trim().lowercase()
        val ranked = buttons.map { b -> b to score(s, buildDoc(b)) }
            .sortedByDescending { it.second }
        val t1 = ranked[0]
        val t2 = ranked.getOrElse(1) { ranked[0] }
        val ambiguous = !(t1.second >= 0.60 && (t1.second - t2.second) >= 0.08)
        return Match(t1.first, t2.first, ambiguous)
    }


    private fun buildDoc(b: ButtonDet): String {
        val parts = mutableListOf<String>()
        b.text?.let { parts += it }
        b.role?.let { r -> parts += r; parts += (synonyms[r] ?: emptyList()) }
        return parts.joinToString(" ")
    }


    private fun score(q: String, d: String): Double {
        if (d.isBlank()) return 0.0
        val qt = tokenize(q); val dt = tokenize(d)
        val inter = qt.intersect(dt.toSet()).size.toDouble()
        val union = (qt + dt).toSet().size.toDouble()
        val jaccard = if (union>0) inter/union else 0.0
        val contains = if (d.contains(q, ignoreCase = true)) 0.3 else 0.0
        return (0.7*jaccard + contains).coerceIn(0.0, 1.0)
    }
    private fun tokenize(s:String) = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter{it.isNotBlank()}
}