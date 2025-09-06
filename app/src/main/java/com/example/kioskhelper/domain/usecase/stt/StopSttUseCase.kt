package com.example.kioskhelper.domain.usecase.stt

import com.example.kioskhelper.domain.repository.SttRepository
import javax.inject.Inject

class StopSttUseCase @Inject constructor(
    private val repo: SttRepository
) {
    /**
     * 현재 진행 중인 인식을 중지합니다(onEndOfSpeech 유도).
     * - 결과가 바로 나오지 않을 수 있어, finalText 구독은 유지하세요.
     */
    operator fun invoke() = repo.stop()
}