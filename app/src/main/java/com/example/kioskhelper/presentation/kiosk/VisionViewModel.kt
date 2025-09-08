package com.example.kioskhelper.presentation.kiosk

import androidx.lifecycle.ViewModel
import com.example.kioskhelper.vision.IconRoleClassifier
import com.example.kioskhelper.vision.YoloV8TfliteInterpreter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VisionViewModel @Inject constructor(
    private val _yolo: YoloV8TfliteInterpreter,
    private val _roleClf: IconRoleClassifier
) : ViewModel() {
    // 경고!! 뷰모델 구조 비권장 방식임
    val detector: YoloV8TfliteInterpreter get() =_yolo
    val roleClf: IconRoleClassifier get() = _roleClf

}
