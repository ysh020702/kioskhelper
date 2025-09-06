package com.example.kioskhelper.domain.usecase.stt

import com.example.kioskhelper.domain.repository.SttRepository
import javax.inject.Inject

class CancelSttUseCase @Inject constructor(
    private val repo: SttRepository
) {
    /**
     * 현재 인식을 즉시 취소합니다(결과 콜백 없이 종료될 수 있음).
     * - 빠르게 흐름을 초기화할 때 사용.
     */
    operator fun invoke() = repo.cancel()
}