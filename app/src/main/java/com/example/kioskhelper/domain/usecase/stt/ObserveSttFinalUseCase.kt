package com.example.kioskhelper.domain.usecase.stt

import com.example.kioskhelper.domain.repository.SttRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSttFinalUseCase @Inject constructor(
    private val repo: SttRepository
) {
    /**
     * STT 최종 인식 텍스트 스트림을 구독합니다.
     * - 명령 처리/전송 등 결정 로직에 사용.
     */
    operator fun invoke(): Flow<String> = repo.finalText()
}