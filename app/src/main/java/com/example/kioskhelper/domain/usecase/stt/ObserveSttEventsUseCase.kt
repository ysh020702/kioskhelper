package com.example.kioskhelper.domain.usecase.stt

import com.example.kioskhelper.domain.repository.SttRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSttEventsUseCase @Inject constructor(
    private val repo: SttRepository
) {
    /**
     * STT 상태 이벤트(Ready/Begin/RMS/End/Error)를 구독합니다.
     * - 마이크 레벨 표시, 버튼 활성화, 로깅에 활용.
     */
    operator fun invoke(): Flow<SttRepository.Event> = repo.events()
}