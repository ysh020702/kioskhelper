package com.example.kioskhelper

import android.content.Context
import ai.onnxruntime.*
import com.example.kioskhelper.presentation.model.ButtonBox
import kotlin.math.sqrt

class MiniLMMatcher(context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val vocab: Map<String, Int>

    init {
        // ONNX 모델 로드
        val modelBytes = context.assets.open("minilm.onnx").readBytes()
        session = env.createSession(modelBytes)

        // vocab.txt 로드
        vocab = context.assets.open("tokenizer/vocab.txt").bufferedReader().useLines { lines ->
            lines.mapIndexed { idx, token -> token to idx }.toMap()
        }
    }

    fun matchAndHighlight(query: String, buttons: List<ButtonBox>): List<Int> {
        if (buttons.isEmpty() || query.isBlank()) return emptyList()

        val queryEmb = embed(query)
        val results = mutableListOf<Pair<Int, Float>>()

        for (b in buttons) {
            val text = b.displayLabel.orEmpty()
            if (text.isBlank()) continue
            val btnEmb = embed(text)
            val sim = cosineSimilarity(queryEmb, btnEmb)
            if (sim >= 0.6f) results.add(b.id to sim)
        }

        return results.sortedByDescending { it.second }.map { it.first }
    }

    private fun embed(text: String, maxLength: Int = 16): FloatArray {
        val inputIds = simpleTokenize(text, maxLength)
        val attentionMask = LongArray(inputIds.size) { 1L }

        val inputIdsTensor = OnnxTensor.createTensor(env, arrayOf(inputIds))
        val attentionMaskTensor = OnnxTensor.createTensor(env, arrayOf(attentionMask))

        val result = session.run(
            mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor
            )
        )

        @Suppress("UNCHECKED_CAST")
        val output = result[0].value as Array<Array<FloatArray>>
        return output[0][0]
    }


    private fun simpleTokenize(text: String, maxLength: Int): LongArray {
        val tokens: List<Long> = text
            .split(" ")
            .map { vocab[it] ?: vocab["[UNK]"] ?: 0 } // Int
            .map { it.toLong() } // Int -> Long
        return tokens.take(maxLength).toLongArray() // 이제 LongArray 가능
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