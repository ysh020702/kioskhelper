package com.example.kioskhelper.data.repositoryImpl

import android.content.Context
import com.example.kioskhelper.data.platform.SimpleTts
import com.example.kioskhelper.domain.repository.TtsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

@OptIn(ExperimentalCoroutinesApi::class)
class TtsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context : Context
) : TtsRepository{

    // SimpleTts는 내부에서 TTS 엔진 초기화 & 콜백 제공
    private val tts by lazy { SimpleTts(context) }

    private val _events = MutableSharedFlow<TtsRepository.Event>(extraBufferCapacity = 16)

    override fun events(): Flow<TtsRepository.Event> = _events

    override suspend fun speakNow(text: String) {
        if (text.isBlank()) return
        _events.tryEmit(TtsRepository.Event.Started(text))
        // SimpleTts의 콜백은 완료/오류 시 한 번 호출됨
        suspendCancellableCoroutine { cont ->
            tts.say(text) {
                _events.tryEmit(TtsRepository.Event.Done(text))
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    override suspend fun enqueue(text: String) {
        if (text.isBlank()) return
        _events.tryEmit(TtsRepository.Event.Started(text))
        suspendCancellableCoroutine { cont ->
            tts.sayQueued(text) {
                _events.tryEmit(TtsRepository.Event.Done(text))
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    override fun setRate(value: Float) = tts.setRate(value)
    override fun setPitch(value: Float) = tts.setPitch(value)
    override fun stop() = tts.stop()

    /** 필요 시 앱 종료 시점에서 불러줄 수 있도록 노출해도 됨 */
    fun shutdown() = tts.shutdown()
}