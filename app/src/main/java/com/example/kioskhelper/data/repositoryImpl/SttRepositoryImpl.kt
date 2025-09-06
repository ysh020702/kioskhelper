package com.example.kioskhelper.data.repositoryImpl

import android.content.Context
import com.example.kioskhelper.data.platform.SimpleStt
import com.example.kioskhelper.domain.repository.SttRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

/**
 * SimpleStt를 감싸 도메인 인터페이스를 구현.
 * - 안드로이드 의존은 여기서 끝.
 * - 콜백 → Flow로 변환하여 UI에서 관찰 가능
 */

class SttRepositoryImpl @Inject constructor(
    @ApplicationContext private val ctx: Context
) : SttRepository {

    private val _partial = MutableSharedFlow<String>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val _final = MutableSharedFlow<String>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val _events = MutableSharedFlow<SttRepository.Event>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val stt: SimpleStt = SimpleStt(
        ctx = ctx,
        onPartial = { txt -> _partial.tryEmit(txt.orEmpty()) },
        onFinal = { txt -> _final.tryEmit(txt.orEmpty()) },
        onEvent = { type, rms ->
            when (type) {
                SimpleStt.EVENT_READY -> _events.tryEmit(SttRepository.Event.Ready)
                SimpleStt.EVENT_BEGIN -> _events.tryEmit(SttRepository.Event.BeginningOfSpeech)
                SimpleStt.EVENT_END -> _events.tryEmit(SttRepository.Event.EndOfSpeech)
                SimpleStt.EVENT_RMS -> _events.tryEmit(SttRepository.Event.RmsChanged(rms ?: 0f))
                SimpleStt.EVENT_ERROR -> _events.tryEmit(SttRepository.Event.Error(-1, "STT error"))
            }
        }
    )

    override fun partialText(): Flow<String> = _partial.asSharedFlow()
    override fun finalText(): Flow<String> = _final.asSharedFlow()
    override fun events(): Flow<SttRepository.Event> = _events.asSharedFlow()

    override fun start(languageTag: String) = stt.start(languageTag)
    override fun stop() = stt.stop()
    override fun cancel() = stt.cancel()
    override fun destroy() = stt.destroy()
}