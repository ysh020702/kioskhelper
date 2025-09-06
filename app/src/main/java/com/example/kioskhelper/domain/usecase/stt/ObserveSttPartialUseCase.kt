package com.example.kioskhelper.domain.usecase.stt

import com.example.kioskhelper.domain.repository.SttRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSttPartialUseCase @Inject constructor(
    private val repo: SttRepository
) {
    /**
     * STT 부분 인식 텍스트 스트림을 구독합니다.
     * - 실시간 입력 UI 표시 등에 사용.
     */
    operator fun invoke(): Flow<String> = repo.partialText()
}