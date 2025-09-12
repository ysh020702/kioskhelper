# 키오스크 도우미 (KioskHelper) 🎤📱

[![Contributors](https://img.shields.io/github/contributors/ysh020702/kioskhelper.svg?style=for-the-badge)](https://github.com/ysh020702/kioskhelper/graphs/contributors)
[![Forks](https://img.shields.io/github/forks/ysh020702/kioskhelper.svg?style=for-the-badge)](https://github.com/ysh020702/kioskhelper/network/members)
[![Stargazers](https://img.shields.io/github/stars/ysh020702/kioskhelper.svg?style=for-the-badge)](https://github.com/ysh020702/kioskhelper/stargazers)
[![Issues](https://img.shields.io/github/issues/ysh020702/kioskhelper.svg?style=for-the-badge)](https://github.com/ysh020702/kioskhelper/issues)
![License](https://img.shields.io/github/license/ysh020702/kioskhelper?style=for-the-badge)
![Last Commit](https://img.shields.io/github/last-commit/ysh020702/kioskhelper?style=for-the-badge)
![License](https://img.shields.io/github/license/ysh020702/kioskhelper?style=for-the-badge)

> **AI 기반 키오스크 보조 앱**  
> 카메라로 버튼을 인식하고, 음성 명령으로 원하는 기능을 **탐지 → 강조 → 안내**합니다.  
> 고령자·외국인 등 디지털 취약계층의 **접근성**을 높이는 것이 목표입니다.

---

## 목차
1. [개발 동기](#1-개발-동기)
2. [서비스 설명](#2-서비스-설명)
3. [개발 환경 및 방법](#3-개발-환경-및-방법)
4. [기능 명세서](#4-기능-명세서)
5. [개발 예정 기능](#5-개발-예정-기능)
6. [발전 가능성](#6-발전-가능성)
7. [실행 방법](#7-실행-방법)
8. [데모 스크린샷 시연영상](#8-데모-스크린샷-시연영상)
9. [프로젝트 구조](#9-프로젝트-구조)
10. [팀 소개](#10-팀-소개)
11. [라이선스](#11-라이선스)

---

## 1. 개발 동기
운영 효율성과 비용 절감으로 무인 키오스크는 빠르게 확산되고 있지만 **모두에게 동일한 편리함**을 제공하지는 않습니다.  
특히 디지털 환경에 익숙하지 않은 고령자·취약계층은 작은 버튼, 복잡한 UI, 낯선 인터랙션 때문에 **이용을 포기**하거나 직원 의존도가 높습니다.

저희는 이 문제를 **디지털 격차** 문제로 보고,  
**실시간 객체 탐지 AI + OCR + 음성 인식**을 결합한 **스마트 보조 앱**으로 접근성을 높이고자 했습니다.

---

## 2. 서비스 설명
**KioskHelper**는 키오스크 화면을 스마트폰 카메라로 인식하여,  
사용자의 **음성 명령**에 따라 버튼을 **탐지 → 분류 → 하이라이트 → 음성으로 안내**하는 **AI 보조 앱**입니다.

### 🖇️주요 파이프라인
1. **YOLOv8 기반 버튼 탐지** → 실시간으로 UI 버튼 영역 추출  
2. **아이콘/텍스트 기반 역할 분류** → 홈·결제·취소 등 의미 파악 (OCR + MobileNet 보조)  
3. **음성 인식(STT)** → 자연어 명령 인식 (예: “결제 버튼 강조해줘”)  
4. **명령어-버튼 매칭** → MiniLM 기반 유사도 매칭으로 대상 버튼 선택  
5. **시각 강조 + 음성 피드백(TTS)** → 빨간 박스/펄스 애니메이션 + “○○ 버튼을 강조했어요”

> 말 한마디로 원하는 버튼을 찾아주는 **AI 키오스크 보조 앱** 입니다

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
  - **OCR (TFLite)**: 버튼 내 텍스트 추출 → 라벨링  
  - **MobileNet (TFLite)**: 텍스트가 없는 아이콘형 버튼(↑↓× 등) 분류  
  - **MiniLM 매칭기**: “자연어 명령 ↔ 버튼 라벨/의미” 유사도 매칭  
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

## 5. 개발 예정 기능
- **🌐 다국어 지원**  
  한국어 중심에서 **영어/중국어/일본어** 등 다국어 STT·TTS 기능을 추가하여 
  외국인·다문화 사용자도 언어 장벽 없이 이용 가능하게 합니다.
- **🧭 단계별 안내 자동화**  
  버튼 선택 이후 **화면 전환을 자동 감지**하여 **점원처럼 다음 단계를 안내**합니다.  
  예: “결제하기” 선택 → 다음 화면에서 “카드/간편결제/현금 중 선택하세요” 가이드.

---

## 6. 발전 가능성
- **🤖 종합 AI 키오스크 어시스턴트**  
  1. 화면 인식 + 음성 이해 + 맥락 파악을 결합해 사용자가 원하는 행동을 판단해 눌러야 하는 버튼을 알려주는 **디지털 점원**으로 발전할 수 있습니다.
  2. 
  3. 복잡하고 어려운 키오스크 사용 과정에서 **AI가 동행**하는 경험을 목표로 합니다.
- **🏥 다양한 분야 확장**  
  음식점 키오스크에서 **병원 접수기, 공공기관 무인 발급기** 등으로 확대할 수 있습니다.
  고령자·저시력자·외국인의 **디지털 접근성** 을 강화할 수 있습니다.

---

## 7. 실행 방법

```
# 1) 레포지토리 클론
git clone https://github.com/ysh020702/kioskhelper.git

# 2) Android Studio에서 열기 → Gradle Sync

# 3) 실행 전 권한 허용
#    - Camera
#    - Record Audio (STT)


# 4) 디바이스에서 실행 → 키오스크 화면을 비추고 음성 명령 사용
```

## 8. 데모 스크린샷 시연영상

```
추가 예정
```

## 9. 프로젝트 구조
```
kioskhelper/
├─ core/                 # 코어 유틸/상수/확장/베이스(공용 레이어)
├─ data/                 # Repository 구현, 로컬/원격/시스템(data source)
│  ├─ platform
│  └─ repositoryImpl
├─ di/                   # Hilt DI 모듈(Repository/UseCase/Model 제공자)
├─ domain/               # UseCase, 엔터티/모델, 인터페이스(비즈니스 규칙)
│  ├─
│  ├─
│  ├─
│  ├─
│  └─
│
├─ presentation/         # Compose UI, ViewModel, 화면/컴포넌트
├─ ui/
│  └─ theme/             # Compose 테마, 컬러/타이포/셰이프
├─ utils/                # 공통 도우미(변환, 로깅, 좌표 매핑 등)
├─ vision/               # 온디바이스 비전(YOLOv8 TFLite, OCR, 분류기 등)
│   ├─ YoloV8TfliteInterpreter.kt   # 버튼 후보 탐지(TFLite 추론)
│   ├─ IconRoleClassifier.kt        # 아이콘형 버튼 역할 분류(예: ↑,↓,✕)
│   └─ (ex) OcrTflite*.kt           # 버튼 텍스트 추출(OCR)
└─ KioskApp.kt           # 앱 진입/Compose 세팅
```

## 10. 팀 소개
| 이름      | 소속       | 전공/학번           | 역할     | 담당                             |
| ------- | -------- | --------------- | ------ | ------------------------------ |
| **임준범** | **GDSC** | 모바일공학과 **21학번** | **팀장** | 앱 컨셉 선정, 데이터 수집·가공, **백엔드**    |
| **이승호** | **해달**   | 모바일공학과 **21학번** | 팀원     | 데이터 수집·가공, **AI 모델 튜닝·개발**     |
| **배명우** | **해달**   | 모바일공학과 **22학번** | 팀원     | 데이터 수집·가공, **백엔드**, 발표자료       |
| **양승환** | **해달**   | 모바일공학과 **21학번** | 팀원     | 앱 **프론트엔드·백엔드**, **README** 작성 |


## 11. 라이선스

이 프로젝트는 **MIT License** 하에 배포됩니다.  
자세한 내용은 [LICENSE](./LICENSE)를 참고하세요.


