package com.example.kioskhelper.domain.usecase.tts

import com.example.kioskhelper.domain.repository.TtsRepository
import javax.inject.Inject

class SpeakNowUseCase @Inject constructor(
    private val repo: TtsRepository
) {
    /**
     * 현재 재생 중인 발화를 즉시 중단(QUEUE_FLUSH)하고 `text`를 바로 읽기 시작합니다.
     * - 긴급 안내/취소/경고 메시지 등 기존 말하기를 끊고 바로 말해야 할 때 사용합니다.
     */
    suspend operator fun invoke(text: String): Unit = repo.speakNow(text)
}