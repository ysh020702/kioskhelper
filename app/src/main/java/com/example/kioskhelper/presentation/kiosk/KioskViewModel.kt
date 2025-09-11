package com.example.kioskhelper.presentation.kiosk

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kioskhelper.MiniLMMatcher
import com.example.kioskhelper.domain.repository.SttRepository
import com.example.kioskhelper.domain.repository.TtsRepository
import com.example.kioskhelper.domain.usecase.stt.*
import com.example.kioskhelper.domain.usecase.tts.*
import com.example.kioskhelper.presentation.model.ButtonBox
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch



@HiltViewModel
class KioskViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    // STT
    private val cancelStt: CancelSttUseCase,
    private val destroyStt: DestroySttUseCase,
    private val observeSttEvents: ObserveSttEventsUseCase,
    private val observeSttFinal: ObserveSttFinalUseCase,
    private val observeSttPartial: ObserveSttPartialUseCase,
    private val startStt: StartSttUseCase,
    private val stopStt: StopSttUseCase,

    // TTS
    private val enqueueSpeak: EnqueueSpeakUseCase,
    private val observeTtsEvents: ObserveTtsEventsUseCase,
    private val setTtsPitch: SetTtsPitchUseCase,
    private val setTtsRate: SetTtsRateUseCase,
    private val speakNow: SpeakNowUseCase,
    private val stopTts: StopTtsUseCase
) : ViewModel() {

    // ── UI 모델 ────────────────────────────────────────────────────────────
    data class UiState(
        val listening: Boolean = false,           // 듣는 중(토글 ON)
        val statusDotOn: Boolean = false,         // 우상단 초록 점
        val tip: String? = "키오스크 화면을 비춰주세요.",
        val partialText: String = "",
        val finalText: String = "",               // ✅ 최종 누적
        val sttError: String? = null,
        val ttsSpeaking: Boolean = false,
        val highlightedIds: List<Int> = emptyList(),
        val buttons: List<ButtonBox> = emptyList()
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    // ── 내부 상태 ──────────────────────────────────────────────────────────
    private var isListening = false
    private var currentLangTag: String = "ko-KR"
    private var lastPartial: String? = null

    // ── 외부(카메라/탐지)에서 버튼 세트 주입 ─────────────────────────────
    fun setDetectedButtons(buttons: List<ButtonBox>) {
        _ui.update { it.copy(buttons = buttons) }
        /*buttons.forEach { button ->
            Log.d("MatchAndHighlight", "Button name: ${button.displayLabel}")
        }*/
        // 버튼이 바뀌면 현재 텍스트로 매칭 재시도
        val query = ui.value.partialText.ifBlank { ui.value.finalText }
        if (query.isNotBlank()) {
            val ids = matchAndHighlight(query, buttons)
            _ui.update { it.copy(highlightedIds = ids) }
        }
    }

    // ── 토글: 한 번 누르면 시작/종료 ──────────────────────────────────────
    fun onMicToggle() {
        if (isListening) stopListeningFlow() else startListeningFlow("ko-KR")
    }

    // ── 취소: STT 즉시 취소 + 하이라이트/텍스트 초기화 ────────────────────
    fun onCancel() = viewModelScope.launch {
        isListening = false
        cancelStt()
        stopTts()
        lastPartial = null
        _ui.update {
            it.copy(
                listening = false,
                statusDotOn = false,
                partialText = "",
                finalText = "",
                highlightedIds = emptyList(),
                tip = "취소했어요.",
                sttError = null
            )
        }
    }

    // ── TTS 설정(앱 시작 시 1회 호출 권장) ────────────────────────────────
    fun initTts(rate: Float = 1.0f, pitch: Float = 1.0f) = viewModelScope.launch {
        setTtsRate(rate)
        setTtsPitch(pitch)
    }

    // ── 듣기 시작/종료 ────────────────────────────────────────────────────
    private fun startListeningFlow(languageTag: String = "ko-KR") {
        if (isListening) return
        isListening = true
        currentLangTag = languageTag
        lastPartial = null

        _ui.update {
            it.copy(
                listening = true,
                statusDotOn = true,
                sttError = null,
                tip = null,
                partialText = "" // 새 세션 진입 시 클리어
            )
        }

        viewModelScope.launch {
            // 마이크 충돌 방지: TTS가 재생 중이면 먼저 멈춤
            stopTts()
            startStt(currentLangTag)
        }
    }

    private fun stopListeningFlow() {
        if (!isListening) return
        isListening = false

        viewModelScope.launch {
            stopStt() // 필요 시 cancelStt() 병행 고려
            _ui.update { it.copy(listening = false, statusDotOn = false) }
        }
    }

    // ── 최종 처리: final(or 마지막 partial)로 매칭/안내 → 종료 ─────────────
    private fun finishUtterance(finalOrNull: String?) {
        val text = finalOrNull?.takeIf { it.isNotBlank() }
            ?: lastPartial?.takeIf { it.isNotBlank() }
            ?: ""

        if (text.isNotBlank()) {
            // 최종 누적 + 진행중 partial 지우기
            _ui.update {
                it.copy(
                    finalText = (text).trim(),
                    partialText = ""
                )
            }

            val ids = matchAndHighlight(text, ui.value.buttons)
            if (ids.isNotEmpty()) {
                val topId = ids.first()
                val topLabel = ui.value.buttons.firstOrNull { it.id == topId }?.displayLabel ?: "해당 버튼"
                _ui.update { it.copy(highlightedIds = listOf(topId)) }

                // 종료 후 안내(마이크와 충돌 없음)
                viewModelScope.launch {
                    enqueueSpeak("‘$topLabel’ 버튼을 강조했어요.")
                }
            } else {
                _ui.update { it.copy(highlightedIds = emptyList()) }
                viewModelScope.launch {
                    enqueueSpeak("해당하는 버튼이 없어요. 다시 말씀해 주세요.")
                }
            }
        } else {
            // 아무 텍스트도 없으면 조용히 종료
            _ui.update { it.copy(partialText = "") }
        }

        // 세션 종료
        stopListeningFlow()
        lastPartial = null
    }

    // ── 수집: STT Partial / Final / Events, TTS Events ────────────────────
    init {
        // Partial: 실시간 하이라이트
        observeSttPartial()
            .onEach { text ->
                if (text.isNullOrBlank()) return@onEach
                lastPartial = text
                _ui.update { it.copy(partialText = text, sttError = null) }
                val ids = matchAndHighlight(text, ui.value.buttons)
                _ui.update { it.copy(highlightedIds = ids) }
            }
            .launchIn(viewModelScope)

        // Final: 결과 오면 즉시 최종 처리(= 종료 및 탐지/안내)
        observeSttFinal()
            .onEach { text ->
                val final = text.orEmpty()
                finishUtterance(final)
            }
            .launchIn(viewModelScope)

        // STT 이벤트: 침묵 감지/에러 시 즉시 마무리 (자동 재시작 없음)
        observeSttEvents()
            .onEach { ev ->
                when (ev) {
                    is SttRepository.Event.Ready -> _ui.update { it.copy(sttError = null) }
                    is SttRepository.Event.Error -> {
                        _ui.update { it.copy(sttError = ev.message) }
                        finishUtterance(null) // 에러 시점까지의 말로 마무리
                    }
                    is SttRepository.Event.EndOfSpeech -> {
                        finishUtterance(null) // 잠깐 침묵 → 종료 & 탐지
                    }
                    else -> Unit
                }
            }
            .launchIn(viewModelScope)

        // TTS 이벤트(말하는 중 표시)
        observeTtsEvents()
            .onEach { tev ->
                when (tev) {
                    is TtsRepository.Event.Started -> _ui.update { it.copy(ttsSpeaking = true) }
                    is TtsRepository.Event.Done -> _ui.update { it.copy(ttsSpeaking = false) }
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        destroyStt() // 리소스 정리
    }


    // ── KioskViewModel 내부 ────────────────────────────────────────────────

    private val miniLmMatcher = MiniLMMatcher(appContext)

    private fun matchAndHighlight(query: String, buttons: List<ButtonBox>): List<Int> {
        buttons.forEach { button ->
            Log.d("MatchAndHighlight", "Button name: ${button.displayLabel}")
        }
        return miniLmMatcher.matchAndHighlight(query, buttons)

    }


    // 작은 확장 헬퍼
    private inline fun <T> MutableStateFlow<T>.update(block: (T) -> T) { value = block(value) }
}
