package com.example.kioskhelper.domain.repository

import kotlinx.coroutines.flow.Flow

interface TtsRepository {
    sealed interface Event {
        /** 발화 명령이 들어갔을 때(UI에서 재생 아이콘 등 표시용) */
        data class Started(val text: String) : Event
        /** SimpleTts는 onError도 onDone으로 합쳐 부르므로, 우선 Done으로 통일 */
        data class Done(val text: String) : Event
    }

    /** 기존 발화 중단 후 즉시 말하기 */
    suspend fun speakNow(text: String)

    /** 대기열 추가(이전 발화 끝난 뒤 재생) */
    suspend fun enqueue(text: String)

    /** 속도/피치 제어 */
    fun setRate(value: Float)
    fun setPitch(value: Float)

    /** 현재 발화 중지 */
    fun stop()

    /** 발화 이벤트 스트림 (Started/Done) */
    fun events(): Flow<Event>
}
