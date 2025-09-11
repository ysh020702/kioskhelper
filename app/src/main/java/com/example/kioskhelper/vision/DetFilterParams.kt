package com.example.kioskhelper.vision

data class DetFilterParams(
    val scoreThresh: Float = 0.40f,      // 1차 점수 컷
    val minRelArea: Float = 0.0012f,     // 프레임 대비 최소 면적(0.12%)
    val maxRelArea: Float = 0.60f,       // 너무 큰 것도 컷
    val minAspect: Float = 0.4f,         // w/h 하한 (버튼은 극단적 세로/가로 막대 제외)
    val maxAspect: Float = 2.5f,         // w/h 상한
    val borderPx: Int = 8,               // 프레임 경계 근처 작은 박스는 컷
    val nmsIou: Float = 0.50f,           // NMS IoU
    val keepTopK: Int = 30,              // 최종 유지 개수
    val allowClasses: Set<Int>? = null,  // 허용 클래스만 통과(모델에 버튼/아이콘 클래스가 있다면)
    val classThresh: Map<Int, Float> = emptyMap() // 클래스별 임계값
)