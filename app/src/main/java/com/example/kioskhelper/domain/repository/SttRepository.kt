package com.example.kioskhelper.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * 음성 인식(STT) 도메인 포트.
 * - 안드로이드 의존 없음
 * - UI는 UseCase를 통해서만 접근
 */
interface SttRepository {

    /** 인식 진행 이벤트(상태 UI 표시/디버깅용) */
    sealed interface Event {
        object Ready : Event
        object BeginningOfSpeech : Event
        data class RmsChanged(val rms: Float) : Event
        object EndOfSpeech : Event
        data class Error(val code: Int, val message: String?) : Event
    }

    /** 부분/최종 텍스트 스트림 */
    fun partialText(): Flow<String>   // 빈 문자열도 올 수 있음(엔진에 따라)
    fun finalText(): Flow<String>     // 한 번의 onResults마다 한 항목

    /** 이벤트 스트림 (선택적 구독) */
    fun events(): Flow<Event>

    /** 인식 제어 */
    fun start(languageTag: String = "ko-KR")
    fun stop()
    fun cancel()

    /** 리소스 정리(필수). Activity/Service onDestroy에서 호출 권장 */
    fun destroy()
}