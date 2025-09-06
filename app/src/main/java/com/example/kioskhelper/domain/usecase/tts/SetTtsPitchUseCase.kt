package com.example.kioskhelper.domain.usecase.tts

import javax.inject.Inject
import com.example.kioskhelper.domain.repository.TtsRepository

class SetTtsPitchUseCase @Inject constructor(
    private val repo: TtsRepository
) {
    /**
     * TTS의 피치(음높이)를 설정합니다. (권장 범위: 0.5f ~ 2.0f)
     * - 더 또렷하거나 부드러운 톤을 내도록 조정할 때 사용합니다.
     */
    operator fun invoke(pitch: Float): Unit = repo.setPitch(pitch)
}