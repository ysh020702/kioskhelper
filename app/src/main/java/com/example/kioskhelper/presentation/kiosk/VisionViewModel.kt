package com.example.kioskhelper.presentation.kiosk

import androidx.lifecycle.ViewModel
import com.example.kioskhelper.presentation.model.ButtonBox
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class VisionViewModel @Inject constructor() : ViewModel() {
    data class Ui(
        val boxes: List<ButtonBox> = emptyList(),
        val running: Boolean = false
    )
    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    fun onDetections(mapped: List<ButtonBox>) {
        _ui.update { it.copy(boxes = mapped) }
    }
    fun setRunning(r: Boolean) {
        _ui.update { it.copy(running = r) }
    }
}
