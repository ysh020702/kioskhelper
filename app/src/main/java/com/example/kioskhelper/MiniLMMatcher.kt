package com.example.kioskhelper

import android.content.Context
import ai.onnxruntime.*
import android.util.Log
import com.example.kioskhelper.presentation.model.ButtonBox
import kotlin.math.sqrt

class MiniLMMatcher(context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val vocab: Map<String, Int>
    private val clsId: Int
    private val sepId: Int
    private val padId: Int
    private val unkId: Int

    init {
        // ONNX 모델 로드
        val modelBytes = context.assets.open("minilm.onnx").readBytes()
        session = env.createSession(modelBytes)

        // vocab.txt 로드
        vocab = context.assets.open("tokenizer/vocab.txt").bufferedReader().useLines { lines ->
            lines.mapIndexed { idx, token -> token to idx }.toMap()
        }

        // special token id 저장
        clsId = vocab["[CLS]"] ?: 0
        sepId = vocab["[SEP]"] ?: 0
        padId = vocab["[PAD]"] ?: 0
        unkId = vocab["[UNK]"] ?: 0

        // 모델 출력 정보 로그
        for (entry in session.outputInfo.entries) {
            Log.d("ONNX", "Output name=${entry.key}, info=${entry.value}")
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

            Log.d("SimilarityCheck", "Button: \"$query\" |Button: \"$text\" | sim=$sim")

            if (sim >= 0.8f) results.add(b.id to sim)
        }

        return results.sortedByDescending { it.second }.map { it.first }
    }

    /** 텍스트를 벡터로 변환 (pooler_output 사용) */
    private fun embed(text: String, maxLength: Int = 16): FloatArray {
        val inputIds = tokenize(text, maxLength)
        val attentionMask = LongArray(inputIds.size) { if (inputIds[it] != padId.toLong()) 1L else 0L }

        val inputIdsTensor = OnnxTensor.createTensor(env, arrayOf(inputIds))
        val attentionMaskTensor = OnnxTensor.createTensor(env, arrayOf(attentionMask))

        val result = session.run(
            mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor
            )
        )

        // pooler_output 사용
        @Suppress("UNCHECKED_CAST")
        val output = result[1].value as Array<FloatArray> // shape [batch, hidden]
        return l2Normalize(output[0]) // 첫 번째 배치 벡터
    }

    /** WordPiece 기반 토크나이즈 */
    private fun tokenize(text: String, maxLength: Int): LongArray {
        val tokens = mutableListOf<Int>()
        tokens.add(clsId)

        for (word in text.split(" ")) {
            if (word.isBlank()) continue
            val subTokens = wordPieceTokenize(word)
            tokens.addAll(subTokens)
        }

        tokens.add(sepId)

        // maxLength 맞추기
        val padded = tokens.take(maxLength).toMutableList()
        while (padded.size < maxLength) {
            padded.add(padId)
        }

        return padded.map { it.toLong() }.toLongArray()
    }

    /** WordPiece longest-match-first */
    private fun wordPieceTokenize(word: String): List<Int> {
        val subTokens = mutableListOf<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var curSub: String? = null

            while (start < end) {
                val substr = if (start == 0) word.substring(start, end)
                else "##" + word.substring(start, end)
                if (vocab.containsKey(substr)) {
                    curSub = substr
                    break
                }
                end -= 1
            }

            if (curSub == null) {
                subTokens.add(unkId)
                break
            }

            subTokens.add(vocab[curSub] ?: unkId)
            start = end
        }
        return subTokens
    }

    /** Cosine similarity */
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

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) {
            norm += v * v
        }
        norm = sqrt(norm)
        return vector.map { it / norm }.toFloatArray()
    }

}
