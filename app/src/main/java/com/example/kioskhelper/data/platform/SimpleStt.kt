package com.example.kioskhelper.data.platform

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SimpleStt(
    private val ctx: Context,
    private val onPartial: (String?) -> Unit,
    private val onFinal: (String?) -> Unit,
    private val onEvent: (event: Int, rms: Float?) -> Unit = { _, _ -> } // 상태 이벤트 전달용(선택)
) : RecognitionListener {

    private val recog = SpeechRecognizer.createSpeechRecognizer(ctx)
    private val baseIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
    }

    init { recog.setRecognitionListener(this) }

    /** 언어 태그(예: ko-KR, en-US)를 매번 전달받아 인식 시작 */
    fun start(languageTag: String = "ko-KR") {
        val intent = Intent(baseIntent).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        }
        recog.startListening(intent)
    }

    fun stop() = recog.stopListening()
    fun cancel() = recog.cancel()
    fun destroy() = recog.destroy()

    // RecognitionListener
    override fun onPartialResults(bundle: Bundle) {
        val text = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        onPartial(text)
    }
    override fun onResults(bundle: Bundle) {
        val text = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        onFinal(text)
    }
    override fun onError(error: Int) { onFinal(""); onEvent(EVENT_ERROR, null) }
    override fun onReadyForSpeech(params: Bundle?) { onEvent(EVENT_READY, null) }
    override fun onBeginningOfSpeech() { onEvent(EVENT_BEGIN, null) }
    override fun onRmsChanged(rmsdB: Float) { onEvent(EVENT_RMS, rmsdB) }
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { onEvent(EVENT_END, null) }
    override fun onEvent(eventType: Int, params: Bundle?) {}

    companion object {
        const val EVENT_READY = 1
        const val EVENT_BEGIN = 2
        const val EVENT_END = 3
        const val EVENT_RMS = 4
        const val EVENT_ERROR = 5
    }
}