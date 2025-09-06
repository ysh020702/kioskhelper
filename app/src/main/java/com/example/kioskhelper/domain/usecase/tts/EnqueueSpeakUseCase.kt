package com.example.kioskhelper.domain.usecase.tts

import com.example.kioskhelper.domain.repository.TtsRepository
import javax.inject.Inject

class EnqueueSpeakUseCase @Inject constructor(
    private val repo: TtsRepository
) {
    /**
     * 현재 발화가 끝난 뒤 대기열(QUEUE_ADD)에 추가하여 `text`를 순차적으로 읽습니다.
     * - 여러 문장을 자연스럽게 이어서 말해야 할 때 사용합니다.
     */
    suspend operator fun invoke(text: String): Unit = repo.enqueue(text)
}