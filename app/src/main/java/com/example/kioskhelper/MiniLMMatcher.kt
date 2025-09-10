package com.example.kioskhelper

import android.content.Context
import ai.onnxruntime.*
import com.example.kioskhelper.presentation.kiosk.KioskViewModel
import org.json.JSONObject
import java.io.BufferedReader
import java.nio.LongBuffer
import kotlin.math.sqrt

class MiniLMMatcher(context: Context) {

    private val session: OrtSession
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val vocab: Map<String, Int>

    init {
        // ONNX 모델 로드
        val modelBytes = context.assets.open("minilm.onnx").readBytes()
        session = env.createSession(modelBytes)

        // vocab.json 로드 (assets/tokenizer/vocab.json)
        val vocabJson = context.assets.open("tokenizer/vocab.json").bufferedReader().use(BufferedReader::readText)
        val jsonObj = JSONObject(vocabJson)
        val tempVocab = mutableMapOf<String, Int>()
        jsonObj.keys().forEach { key -> tempVocab[key] = jsonObj.getInt(key) }
        vocab = tempVocab
    }

    fun matchAndHighlight(query: String, buttons: List<KioskViewModel.UiButton>): List<Int> {
        if (buttons.isEmpty() || query.isBlank()) return emptyList()

        val queryEmb = embed(query)
        val results = mutableListOf<Pair<Int, Float>>()

        for (b in buttons) {
            val text = b.text.orEmpty()
            if (text.isBlank()) continue
            val btnEmb = embed(text)
            val sim = cosineSimilarity(queryEmb, btnEmb)
            if (sim >= 0.7f) results.add(b.id to sim)
        }

        return results.sortedByDescending { it.second }.map { it.first }
    }

    private fun embed(text: String): FloatArray {
        val tokens = simpleTokenize(text) // LongArray 반환
        val inputIds = arrayOf(tokens)    // 2D 배열로 변환
        val attentionMask = Array(tokens.size) { LongArray(1) { 1L } } // 2D 배열, batch=1

        val inputIdsTensor = OnnxTensor.createTensor(env, inputIds)
        val attentionMaskTensor = OnnxTensor.createTensor(env, arrayOf(LongArray(tokens.size) { 1L })) // batch=1

        val result = session.run(mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor
        ))

        val output = result[0].value as Array<FloatArray>
        return output[0]
    }



    private fun simpleTokenize(text: String, maxLength: Int = 16): LongArray {
        val words = text.lowercase().split(" ", ".", ",")
        val ids = words.map { vocab[it] ?: vocab["[UNK]"] ?: 0 }
            .take(maxLength)
            .map { it.toLong() }   // 여기서 Int → Long 변환
            .toMutableList()
        while (ids.size < maxLength) ids.add(vocab["[PAD]"]?.toLong() ?: 0L)
        return ids.toLongArray()
    }


    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return dot / (sqrt(normA) * sqrt(normB))
    }
}
