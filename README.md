# 키오스크 도우미 (KioskHelper) 🎤📱

> **AI 기반 키오스크 보조 앱**  
> 카메라로 버튼을 인식하고, 음성 명령으로 원하는 기능을 **탐지 → 강조 → 안내**합니다.  
> 고령자·외국인 등 디지털 취약계층의 **접근성**을 높이는 것이 목표입니다.

---

## 목차
1. [개발 동기](#1-개발-동기)
2. [서비스 설명](#2-서비스-설명)
3. [개발 환경 및 방법](#3-개발-환경-및-방법)
4. [기능 명세서](#4-기능-명세서)
5. [발전 가능성](#5-발전-가능성)
6. [실행 방법](#6-실행-방법)
7. [데모 스크린샷 시연영상](#7-데모-스크린샷-시연영상)
8. [프로젝트 핵심 구조](#8-프로젝트-핵심-구조)
9. [팀 소개](#9-팀-소개)
10. [라이선스](#10-라이선스)

---

## 1. 개발 동기
운영 효율성과 비용 절감으로 무인 키오스크는 빠르게 확산되고 있지만 **모두에게 동일한 편리함**을 제공하지는 않습니다.
특히 디지털 환경에 익숙하지 않은 고령자·디지털 취약계층은 작은 버튼, 복잡한 UI, 새로운 상호 작용 방식 때문에 **이용을 포기**하거나 직원 의존도가 높습니다.

저희는 이 문제를 **디지털 격차** 문제로 보고,  
**실시간 객체 탐지 AI + OCR + 음성 인식**을 결합한 **보조 앱**으로 접근성을 높이고자 했습니다.

---

## 2. 서비스 설명
**KioskHelper**는 키오스크 화면을 스마트폰 카메라로 인식하여,  
사용자의 **음성 명령**에 따라 버튼을 **탐지 → 분류 → 하이라이트 → 음성으로 안내**하는 **AI 보조 앱**입니다.

### 🖇️ 주요 파이프라인 (현재)
On Device AI 기반 객체인식 파이프라인으로 사용자를 보조합니다
1. **YOLOv8 기반 버튼 탐지**  
   → 키오스크 화면에서 버튼 후보 영역(Box) 추출  
2. **OCR + 아이콘 분류 파이프라인**  
   → 추출된 각 Box를 받아 텍스트(OCR)와 아이콘(MobileNet 분류)으로 **역할 식별**  
   → 예: “결제”, “취소”, “홈”, “↑↓” 등으로 의미 파악  
3. **음성 인식(STT)**  
   → 사용자의 자연어 명령 인식 (예: “결제 버튼 강조해줘”)  
4. **명령어-버튼 매칭**  
   → MiniLM 기반 유사도 매칭으로 음성 명령과 가장 가까운 버튼 선택  
5. **시각 강조 + 음성 피드백(TTS)**  
   → 선택된 버튼을 빨간 박스/펄스 애니메이션으로 강조  
   → 동시에 “○○ 버튼을 강조했어요” 음성 안내


### 🛠️ 곧 추가될 확장 기능
현재 파이프라인(YOLO → OCR/아이콘 → 매칭 → 안내)을 확장해, 아래를 준비하고 있습니다:
- **다국어 지원**
   → STT/TTS가 한국어뿐 아니라 영어·중국어·일본어까지 동일 파이프라인에 적용  
- **단계별 안내 자동화**
   → 버튼 선택 후 화면 전환을 감지해, **새 Box 탐지 → OCR/아이콘 분류 → 안내 TTS**로 이어지는 **다음 단계 가이드** 제공

> 말 한마디로 원하는 버튼을 찾고 다음 단계를 알려주는 **온디바이스 AI 키오스크 보조 앱** 입니다.

---

## 3. 개발 환경 및 방법
### 🛠 기술 스택
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![TensorFlow Lite](https://img.shields.io/badge/TensorFlow_Lite-FF6F00?style=for-the-badge&logo=tensorflow&logoColor=white)
![CameraX](https://img.shields.io/badge/CameraX-4285F4?style=for-the-badge&logo=google&logoColor=white)
![Google STT](https://img.shields.io/badge/Google_STT-4285F4?style=for-the-badge&logo=google&logoColor=white)
![Google TTS](https://img.shields.io/badge/Google_TTS-4285F4?style=for-the-badge&logo=google&logoColor=white)

### ⚙️ 구현 방식
- **UI**: Jetpack Compose 기반의 단순·큰 글씨·고대비 디자인  
- **카메라 처리**: CameraX + `DetectionOverlayView`(커스텀 박스 오버레이 뷰)  
- **AI 모델**
  - **YOLOv8 (TFLite)**: 버튼 후보 영역 탐지 (온디바이스)  
  - **OCR (MLkit)**: 버튼 내 텍스트 추출  
  - **MobileNet (TFLite)**: 텍스트가 없는 아이콘형 버튼(↑↓× 등) 분류  
  - **MiniLM 매칭기**: 자연어 명령 ↔ 버튼 라벨/의미 유사도 매칭  
- **음성 처리**
  - **STT**: Google Speech-to-Text  
  - **TTS**: Android TTS  
- **아키텍처**: Android Clean Architecture (Data / Domain / Presentation)
- **DI(의존성 주입)**: Dagger-Hilt

---

## 4. 기능 명세서
| 기능 | 설명 |
| --- | --- |
| **객체 탐지** | 카메라 영상에서 키오스크 버튼을 실시간 탐지 (YOLOv8 TFLite) |
| **버튼 분류** | 탐지된 버튼의 의미(홈·결제·취소 등)를 OCR/모델로 분류 |
| **음성 인식(STT)** | 사용자의 자연어 명령을 인식하여 텍스트로 변환 |
| **명령어 매칭** | 인식된 문장과 버튼 라벨/의미를 MiniLM으로 유사도 매칭 |
| **버튼 하이라이트** | 매칭된 버튼을 빨간 박스/펄스 애니메이션으로 강조 |
| **음성 안내(TTS)** | “○○ 버튼을 강조했어요” 등 결과를 음성으로 피드백 |
| **UX 최적화** | 큰 글씨·단순 인터페이스(한 화면)로 학습 부담 최소화 |


---


## 5. 발전 가능성
- **🤖 종합 AI 키오스크 어시스턴트**  
  1. 화면 인식 + 음성 이해 + 맥락 파악을 결합해, 사용자가 원하는 행동을 판단한 후, 눌러야 하는 버튼을 알려주는 **디지털 점원**으로 발전할 수 있습니다.
  2. 복잡하고 어려운 키오스크 사용 과정에서 **AI가 동행**하는 경험을 목표로 합니다.
- **🏥 다양한 분야 확장**  
  음식점 키오스크에서 **병원 접수기, 공공기관 무인 발급기** 등으로 확대할 수 있습니다.
  고령자·저시력자·외국인의 **디지털 접근성** 을 강화할 수 있습니다.


---


## 6. 실행 방법

```
# 1) 레포지토리 클론
git clone https://github.com/ysh020702/kioskhelper.git

# 2) Android Studio에서 열기 → Gradle Sync

# 3) 실행 전 권한 허용
#    - Camera
#    - Record Audio (STT)

# 4) 디바이스에서 실행 → 키오스크 화면을 비추고 음성 명령 사용
```


---



## 7. 데모 스크린샷 시연영상

```
추가 예정
```

## 8. 프로젝트 핵심 구조


### Android Application
```
kioskhelper/
├─ data/                 # Repository 구현, 로컬/원격/시스템(data source)
│  ├─ platform/                  
│  │  ├─ SimpleStt.kt                # Android SpeechRecognizer를 감싼 경량 STT 래퍼
│  │  └─ SimpleTts.kt                # Android TextToSpeech를 감싼 경량 TTS 래퍼
│  │
│  └─ repositoryImpl/
│     ├─ SttRepositoryImpl.kt        # SimpleStt를 활용해 SttRepository를 구현, 콜백을 Flow로 변환
│     └─ TtsRepositoryImpl.kt        # SimpleTts를 활용해 TtsRepository를 구현, 발화 제어와 이벤트 제공
│
├─ di/                   # Hilt DI 모듈 (Repository/UseCase/Model 제공자)
│  ├─ RepositoryModule.kt            # Repository 구현체들을 Hilt에 바인딩하는 모듈
│  ├─ TtsSttModule.kt                # SimpleTTS / SimpleSTT 객체를 제공하는 모듈
│  └─ VisionModule.kt                # YOLO 감지기와 아이콘 역할 분류기를 Hilt에 제공하는 모듈
│
├─ domain/               # UseCase, 엔터티/모델, 인터페이스(비즈니스 규칙)
│  └─ repository/
│  │  ├─ SttRepository.kt            # STT(음성 인식) 이벤트·텍스트 스트림 및 제어를 정의하는 인터페이스
│  │  └─ TtsRepository.kt            # TTS(음성 합성) 발화 제어·이벤트 스트림을 정의하는 인터페이스
│  └─ usecase/
│     ├─ stt/                        # STT(음성 인식) 관련 유스케이스 모음
│     └─ tts/                        # TTS(음성 합성) 관련 유스케이스 모음 
│
├─ presentation/         # Compose UI, ViewModel, 화면/컴포넌트
│  ├─ kiosk/
│  │  ├─ BaseActivity.kt             # 시스템 바를 숨기고 네비게이션 바 스타일을 설정하는 기본 Activity
│  │  ├─ KioskActivity.kt            # 카메라·오디오 권한 요청 후 KioskScreen을 띄우는 진입 Activity
│  │  ├─ KioskViewModel.kt           # STT/TTS 제어, 버튼 하이라이트 로직, UI 상태를 관리하는 뷰모델
│  │  ├─ VisionViewModel.kt          # YOLO 감지기와 아이콘 역할 분류기를 제공하는 뷰모델
│  │  ├─ setupCamera.kt              # CameraX Preview·ImageAnalysis를 바인딩하고 ObjectDetectAnalyzer 연결
│  │  └─ screen/
│  │     ├─ KioskScreen.kt           # 카메라 뷰, 음성 인식 UI, 상태 표시 등을 통합한 메인 Compose 스크린
│  │     └─ CameraWithOverlay.kt     # CameraX Preview와 DetectionOverlayView를 함께 배치하는 컴포저블
│  │
│  ├─ model/
│  │  └─ ButtonBox.kt                # 버튼 위치(Rect)와 라벨(OCR/아이콘) 정보를 담는 데이터 모델
│  │
│  └─ overlayview/
│     └─ DetectionOverlayView.kt     # YOLO 탐지 박스를 파란/빨간 박스로 그려주는 커스텀 View
│
├─ ui/theme/             # Compose 테마, 컬러
│  ├─ color.kt                       # 공용 color 설정
│  └─ Theme.kt                       # 테마 설정
│ 
├─ utils/                # 공통 도우미(변환 로직)
│  └─ YuvToRgbConverter.kt          # CameraX의 YUV 포맷 ImageProxy를 RGB Bitmap으로 변환
│ 
├─ vision/               # 온디바이스 비전 모델(YOLOv8 TFLite, OCR, 분류기 등)
│   ├─ YoloV8TfliteInterpreter.kt   # 버튼 후보 탐지(YOLOv8 TFLite 추론)
│   └─ IconRoleClassifier.kt        # 버튼 역할 분류(OCR 분류 또는 ↑,↓,✕등의 아이콘 분류)
│ 
└─ KioskApp.kt           # 앱 진입/Compose 세팅
```
### 
```
YOLO_PROJECT/
├── dataset/                  # 데이터셋 최상위 폴더
│   ├── images/               # 이미지 원본 파일 폴더
│   │   ├── train/            # 학습용 이미지
│   │   │   ├── image_001.jpg
│   │   │   ├── ...
│   │   │   └── image_068.jpg
│   │   └── val/              # 검증용 이미지
│   │       ├── image_069.jpg
│   │       ├── ...
│   │       └── image_080.jpg
│   ├── labels/               # YOLO 포맷 라벨(.txt) 폴더
│   │   ├── train/            # 학습용 라벨
│   │   │   ├── image_001.txt
│   │   │   ├── ...
│   │   │   └── image_068.txt
│   │   └── val/              # 검증용 라벨
│   │       ├── image_069.txt
│   │       ├── ...
│   │       └── image_080.txt
│   └── data_path.yaml        # 데이터셋 설정 파일 (경로, 클래스 정의)
├── train.py                  # 모델 학습을 실행하는 메인 스크립트
└── yolov8n.pt                # 학습에 사용할 YOLOv8 모델 가중치 파일
```
---

## 9. 팀 소개
| 이름      | 소속       | 전공/학번           | 역할     | 담당                             |
| ------- | -------- | --------------- | ------ | ------------------------------ |
| **임준범** | **GDGoC KNU** | 모바일공학과 **21학번** | **팀장** | 앱 컨셉 선정, 데이터 수집·가공, **백엔드**    |
| **이승호** | **해달** | 모바일공학과 **21학번** | 팀원 | 데이터 수집·가공, **AI 모델 튜닝·개발**     |
| **배명우** | **해달** | 모바일공학과 **22학번** | 팀원 | 데이터 수집·가공, **백엔드**, 발표자료       |
| **양승환** | **해달** | 모바일공학과 **21학번** | 팀원 | 앱 **프론트엔드·백엔드**, **README** 작성 |

---

## 10. 라이선스

이 프로젝트는 **MIT License** 하에 배포됩니다.  
자세한 내용은 [LICENSE](./LICENSE)를 참고하세요.


