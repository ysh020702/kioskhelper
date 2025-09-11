package com.example.kioskhelper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kioskhelper.presentation.model.ButtonBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UiButtonViewModel : ViewModel() {

    private val _buttons = MutableStateFlow<List<ButtonBox>>(emptyList())
    val buttons: StateFlow<List<ButtonBox>> = _buttons

    // 버튼 업데이트
    fun updateButtons(newButtons: List<ButtonBox>) {
        viewModelScope.launch {
            _buttons.value = newButtons
            // 로그 출력
            newButtons.forEach { button ->
                println("ButtonBox id=${button.id}, label=${button.displayLabel}, rect=${button.rect}")
            }
        }
    }
}
