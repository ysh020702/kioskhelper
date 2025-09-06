package com.example.kioskhelper.domain.usecase.tts

import javax.inject.Inject
import com.example.kioskhelper.domain.repository.TtsRepository

class SetTtsRateUseCase @Inject constructor(
    private val repo: TtsRepository
) {
    /**
     * TTS의 말하기 속도를 설정합니다. (권장 범위: 0.1f ~ 2.0f)
     * - 문맥/사용자 접근성에 맞게 기본값(예: 1.0f)을 조정할 때 사용합니다.
     */
    operator fun invoke(rate: Float): Unit = repo.setRate(rate)
}