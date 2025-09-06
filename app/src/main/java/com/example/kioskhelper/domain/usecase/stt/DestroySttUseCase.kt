package com.example.kioskhelper.domain.usecase.stt

import com.example.kioskhelper.domain.repository.SttRepository
import javax.inject.Inject

class DestroySttUseCase @Inject constructor(
    private val repo: SttRepository
) {
    /**
     * STT 리소스를 해제합니다.
     * - Activity/Service onDestroy 등에서 반드시 호출 권장.
     */
    operator fun invoke() = repo.destroy()
}