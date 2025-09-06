package com.example.kioskhelper.domain.usecase.stt

import com.example.kioskhelper.domain.repository.SttRepository
import javax.inject.Inject

class StartSttUseCase @Inject constructor(
    private val repo: SttRepository
) {
    /**
     * STT 인식을 시작합니다.
     * @param languageTag 언어 태그(예: "ko-KR", "en-US")
     * - 마이크 권한(RECORD_AUDIO) 필요
     * - 여러 번 호출 시 엔진 동작에 따라 재시작/무시될 수 있음
     */
    operator fun invoke(languageTag: String = "ko-KR") = repo.start(languageTag)
}