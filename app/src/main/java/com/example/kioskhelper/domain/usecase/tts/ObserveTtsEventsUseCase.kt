package com.example.kioskhelper.domain.usecase.tts

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import com.example.kioskhelper.domain.repository.TtsRepository

class ObserveTtsEventsUseCase @Inject constructor(
    private val repo: TtsRepository
) {
    /**
     * TTS 발화 이벤트 스트림을 관찰합니다.
     * - UI에서 Started/Done 등을 구독하여 재생 상태 표시/버튼 활성화 제어 등에 활용합니다.
     */
    operator fun invoke(): Flow<TtsRepository.Event> = repo.events()
}