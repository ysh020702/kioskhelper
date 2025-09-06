package com.example.kioskhelper.domain.usecase.tts

import javax.inject.Inject
import com.example.kioskhelper.domain.repository.TtsRepository

class StopTtsUseCase @Inject constructor(
    private val repo: TtsRepository
) {
    /**
     * 현재 진행 중인 발화를 즉시 중지합니다.
     * - 화면 전환/사용자 상호작용으로 더 이상 말하기가 불필요할 때 사용합니다.
     */
    operator fun invoke(): Unit = repo.stop()
}