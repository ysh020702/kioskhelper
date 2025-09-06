package com.example.kioskhelper.data.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.os.Build
import android.speech.tts.UtteranceProgressListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 가벼운 TTS 래퍼
 * - 한국어 우선, 실패 시 시스템 기본 언어로 폴백
 * - 말하기/대기열/중지/속도·피치/완료 콜백 지원
 * - Activity/Service 에서 onDestroy 때 shutdown() 꼭 호출
 */
class SimpleTts(
    context: Context,
    private val defaultLocale: Locale = Locale.KOREAN,
    private val defaultRate: Float = 1.0f,   // 0.1 ~ 2.0
    private val defaultPitch: Float = 1.0f   // 0.5 ~ 2.0
) : TextToSpeech.OnInitListener {

    private val ready = AtomicBoolean(false)
    private val tts: TextToSpeech = TextToSpeech(context, this)

    private var onSayDone: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // 언어 설정: ko-KR → 안 되면 시스템 기본 → 마지막으로 US
            val ok = setLanguage(defaultLocale)
                    || setLanguage(Locale.getDefault())
                    || setLanguage(Locale.US)
            if (!ok) {
                // 언어 데이터가 전혀 없을 때도 있으니 rate/pitch만이라도 세팅
            }
            setRate(defaultRate)
            setPitch(defaultPitch)
            // 완료 콜백
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { onSayDone?.invoke() }
                override fun onError(utteranceId: String?) { onSayDone?.invoke() }
                override fun onError(utteranceId: String?, errorCode: Int) { onSayDone?.invoke() }
            })
            ready.set(true)
        }
    }

    /** 언어 설정. 성공시 true */
    fun setLanguage(locale: Locale): Boolean {
        val r = tts.setLanguage(locale)
        return r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    /** 말하기(기존 발화 중단 후 재생). 완료시 callback 호출 */
    fun say(text: String, onDone: (() -> Unit)? = null) {
        if (!ready.get() || text.isBlank()) return
        onSayDone = onDone
        if (Build.VERSION.SDK_INT >= 21) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utter-now")
        } else {
            @Suppress("DEPRECATION")
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utter-now")
        }
    }

    /** 대기열에 추가(이전 발화 끝난 뒤 재생) */
    fun sayQueued(text: String, onDone: (() -> Unit)? = null) {
        if (!ready.get() || text.isBlank()) return
        onSayDone = onDone
        if (Build.VERSION.SDK_INT >= 21) {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, "utter-queued")
        } else {
            @Suppress("DEPRECATION")
            tts.speak(text, TextToSpeech.QUEUE_ADD,null, "utter-queued")
        }
    }

    /** 속도(0.1~2.0) */
    fun setRate(rate: Float) { if (ready.get()) tts.setSpeechRate(rate.coerceIn(0.1f, 2.0f)) }

    /** 피치(0.5~2.0) */
    fun setPitch(pitch: Float) { if (ready.get()) tts.setPitch(pitch.coerceIn(0.5f, 2.0f)) }

    /** 현재 발화 중지 */
    fun stop() { if (ready.get()) tts.stop() }

    /** 리소스 해제 – Activity/Service onDestroy에서 호출 필수 */
    fun shutdown() { try { tts.stop(); tts.shutdown() } catch (_: Throwable) {} }
}