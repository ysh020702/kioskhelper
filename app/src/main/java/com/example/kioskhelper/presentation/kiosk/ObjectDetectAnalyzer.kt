package com.example.kioskhelper.presentation.kiosk

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.example.kioskhelper.presentation.model.ButtonBox
import com.example.kioskhelper.presentation.overlayview.DetectionOverlayView
import com.example.kioskhelper.utils.YuvToRgbConverter
import com.example.kioskhelper.vision.YoloV8TfliteInterpreter
import com.example.kioskhelper.vision.IconRoleClassifier
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@ActivityRetainedScoped
class ObjectDetectAnalyzer @Inject constructor(
    private val previewView: PreviewView,             // 런타임 전달
    private val overlayView: DetectionOverlayView,    // 런타임 전달
    private val detector: YoloV8TfliteInterpreter,    // Hilt 주입
    private val roleClf: IconRoleClassifier,          // Hilt 주입
    private val yuvConverter: YuvToRgbConverter,
    private val kioskViewModel: KioskViewModel,
    private val throttleMs: Long = 0L
) : ImageAnalysis.Analyzer {

    private val lastTs = java.util.concurrent.atomic.AtomicLong(0L)

    // 동시에 1프레임만 처리
    private val inFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private val frameSeq = java.util.concurrent.atomic.AtomicLong(0L)

    // OCR 엔진 (재사용)
    private val textRecognizer = TextRecognition.getClient(
        KoreanTextRecognizerOptions.Builder().build()
    )

    // ==== [PATCH #LOG] 라벨 결정 경로 디버그 스위치/자료형 ====
    private val enableLabelTrace = true
    private data class LabelResult(val text: String, val source: String)

    // ==== [PATCH #6] 키오스크 도메인 사전 ====
    private val LEXICON = listOf(
        // 국문
        "결제","결제완료","신용카드","카드","현금","현장결제",
        "현금영수증","영수증","발행","적립","포인트","쿠폰","회원","회원가입","로그인",
        "주문","주문완료","확인","확정","취소","전체취소","재시도","다음","이전",
        "번호표","대기표","처리중",
        // 영문(간단 예시)
        "CARD","CASH","COUPON","POINT","MEMBER","RECEIPT","ISSUE","ORDER",
        "CANCEL","OK","YES","NO","PAY","NEXT","BACK"
    )

    // ────────────── OCR 보강/유틸 ──────────────
    private fun expandRectToMinSize(
        r: RectF, minW: Float, minH: Float, padFrac: Float, imgW: Int, imgH: Int
    ): RectF {
        val w = r.width(); val h = r.height()
        val padX = max(w * padFrac, 2f)
        val padY = max(h * padFrac, 2f)
        var left = r.left - padX
        var top = r.top - padY
        var right = r.right + padX
        var bottom = r.bottom + padY
        if (right - left < minW) { val add = (minW - (right - left)) / 2f; left -= add; right += add }
        if (bottom - top < minH) { val add = (minH - (bottom - top)) / 2f; top -= add; bottom += add }
        left = left.coerceIn(0f, imgW.toFloat()); top = top.coerceIn(0f, imgH.toFloat())
        right = right.coerceIn(0f, imgW.toFloat()); bottom = bottom.coerceIn(0f, imgH.toFloat())
        if (right <= left) right = min(imgW.toFloat(), left + minW)
        if (bottom <= top) bottom = min(imgH.toFloat(), top + minH)
        return RectF(left, top, right, bottom)
    }

    // ==== PATCH #2: 업스케일 192/1024 유지 ====
    private fun upscaleForOcr(src: Bitmap, targetShort: Int = 192, maxLong: Int = 1024): Bitmap {
        val shortSide = min(src.width, src.height)
        if (shortSide >= targetShort) return src
        val scale = targetShort.toFloat() / shortSide
        val outW = (src.width * scale).toInt().coerceAtMost(maxLong)
        val outH = (src.height * scale).toInt().coerceAtMost(maxLong)
        return Bitmap.createScaledBitmap(src, outW, outH, true)
    }

    // ==== PATCH #3: 전처리(그레이스케일+대비/밝기) ====
    private fun preprocessForOcr(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val cm = android.graphics.ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.45f
        val brightness = 12f
        cm.postConcat(android.graphics.ColorMatrix(floatArrayOf(
            contrast, 0f,       0f,       0f, brightness,
            0f,       contrast, 0f,       0f, brightness,
            0f,       0f,       contrast, 0f, brightness,
            0f,       0f,       0f,       1f, 0f
        )))
        val paint = android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            isFilterBitmap = true
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun normalizeOcr(text: String): String {
        var t = text.replace(Regex("\n+"), " ").trim()
        t = t.replace('O', '0').replace('o', '0')
            .replace('l', '1').replace('I', '1')
            .replace('S', '5')
        return t
    }

    private fun cropBitmap(src: Bitmap, rect: RectF): Bitmap {
        val left = rect.left.toInt().coerceAtLeast(0)
        val top = rect.top.toInt().coerceAtLeast(0)
        val width = rect.width().toInt().coerceAtMost(src.width - left).coerceAtLeast(1)
        val height = rect.height().toInt().coerceAtMost(src.height - top).coerceAtLeast(1)
        return Bitmap.createBitmap(src, left, top, width, height)
    }

    private fun rotate(b: Bitmap, deg: Int): Bitmap {
        if (deg == 0) return b
        val m = android.graphics.Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    // ==== [PATCH #6] 편집거리(레벤슈타인) ====
    private fun editDistance(a: String, b: String): Int {
        val m = a.length; val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) for (j in 1..n) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
        }
        return dp[m][n]
    }

    // ==== [PATCH #6] 토큰 스냅 ====
    private fun snapTokenToLexicon(token: String, maxDist: Int = 2): String {
        if (token.isBlank()) return token
        val hasLetter = token.any { it.isLetter() }
        if (!hasLetter) return token
        val tUpper = token.uppercase()
        var best = token
        var bestD = Int.MAX_VALUE
        for (w in LEXICON) {
            val d = editDistance(tUpper, w.uppercase())
            if (d < bestD) { bestD = d; best = w; if (bestD == 0) break }
        }
        return if (bestD <= maxDist) best else token
    }
    private fun snapPhraseToLexicon(text: String, maxDist: Int = 2): String {
        val tokens = text.split(Regex("[\\s/\\-_,:()]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return text
        val snapped = tokens.map { snapTokenToLexicon(it, maxDist) }
        return snapped.joinToString(" ")
    }

    // ================================
    // ==== [PATCH #CLEANUP] 가격/노이즈 제거 & 라인 선택 ====
    // ================================

    // 한글 많고 숫자 적을수록 좋다(가격 라인 배제 목적)
    private fun hangulScore(s: String): Int {
        val h = Regex("[가-힣]").findAll(s).count()
        val d = Regex("[0-9]").findAll(s).count()
        return h * 2 - d
    }

    // 가격/기호 노이즈 제거 (4,000 / 4000원 / ! 등)
    private fun stripPriceNoise(s: String): String {
        return s
            .replace(Regex("\\b\\d{1,3}(,\\d{3})+\\b"), " ") // 1,234 / 12,345
            .replace(Regex("\\b\\d+\\s*원\\b"), " ")         // 4000원
            .replace(Regex("\\b\\d+[!,.]?\\b"), " ")         // 4000 / 4,000!
            .replace(Regex("[₩$€¥!]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // ML Kit 텍스트에서 "메뉴명일 가능성 높은 한 줄"만 선택
    private fun pickBestLine(vt: com.google.mlkit.vision.text.Text): String {
        val lines = mutableListOf<String>()
        for (b in vt.textBlocks) for (l in b.lines) lines += l.text.trim()
        if (lines.isEmpty()) return vt.text.trim()
        val scored = lines.mapIndexed { idx, s ->
            val score = hangulScore(s) + (lines.size - idx) // 상단 라인 가산점
            score to s
        }
        val best = scored.maxByOrNull { it.first }?.second ?: vt.text.trim()
        return stripPriceNoise(best)
    }

    // ================================
    // ==== [PATCH #MENU] 메뉴 스냅 ====
    // ================================

    private data class MenuEntry(
        val canonical: String,
        val aliases: Set<String> = emptySet(),
        val tags: Set<String> = emptySet()
    )

    // [PATCH #CLEANUP] 숫자 제거 추가(메뉴 매칭 안정화)
    private fun normForMatch(s: String): String {
        val t = s.replace(Regex("\\(.*?\\)"), " ")
            .replace(Regex("[/_,:·]"), " ")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\d"), "")         // ★ 숫자 제거로 가격 노이즈 차단
            .trim()
        return t.replace(" ", "").uppercase()
    }

    private fun buildMenuCatalog(rawLines: List<String>): List<MenuEntry> {
        val catPrefixes = listOf("블록팩", "레디팩")
        val tagWords = mapOf(
            "케이크" to setOf("케이크"), "스틱바" to setOf("스틱바"),
            "모찌" to setOf("모찌"), "모나카" to setOf("모나카"),
            "바움쿠헨" to setOf("바움쿠헨"), "선데" to setOf("선데"),
            "빙수" to setOf("빙수","컵빙수"),
            "블라스트" to setOf("블라스트"), "쉐이크" to setOf("쉐이크"),
            "라떼" to setOf("라떼"), "아메리카노" to setOf("아메리카노"),
            "콜드브루" to setOf("콜드브루")
        )

        fun extractTags(name: String): Set<String> {
            val tags = mutableSetOf<String>()
            for ((k, vs) in tagWords) if (vs.any { name.contains(it) }) tags += k
            if (Regex("\\(.*?Lessly.*?\\)", RegexOption.IGNORE_CASE).containsMatchIn(name)) tags += "Lessly Edition"
            return tags
        }

        fun genAliases(one: String): Pair<Set<String>, Set<String>> {
            var base = one.trim()
            val tags = mutableSetOf<String>()
            for (p in catPrefixes) if (base.startsWith(p)) { tags += p; base = base.removePrefix(p).trim(); break }
            val noParen = base.replace(Regex("\\(.*?\\)"), "").trim()
            fun variants(s: String) = setOf(
                s, s.replace(" ", ""), s.replace("-", " ").replace(Regex("\\s+"), " ").trim()
            )
            val withPrefix = tags.map { "$it $noParen".trim() }.toSet()
            val alias = variants(noParen) + withPrefix.flatMap { variants(it) }
            return alias to (tags + extractTags(one))
        }

        val entries = mutableListOf<MenuEntry>()
        for (line in rawLines.map { it.trim() }.filter { it.isNotEmpty() }) {
            val parts = line.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val first = parts.first()
            val (aliasSet, tags) = genAliases(first)
            val moreAliases = parts.drop(1).flatMap { genAliases(it).first }
            val allAliases = aliasSet + moreAliases
            val canonical = first
                .replace(Regex("^블록팩\\s*|^레디팩\\s*"), "")
                .replace(Regex("\\(.*?\\)"), "")
                .trim()
            entries += MenuEntry(canonical = canonical, aliases = allAliases, tags = tags)
        }
        return entries
    }

    // [PATCH #MENU] 실제 메뉴 라인업 (요약 — 필요시 계속 추가)
    private val RAW_MENU = listOf(
        // 젤라또/팩
        "요거트 젤라또","헤이즐넛 젤라또","애플망고 젤라또",
        "블록팩 레인보우샤베트","블록팩 베리베리스트로베리","블록팩 초콜릿","블록팩 바람과함께사라지다",
        "블록팩 이상한나라의솜사탕","블록팩 민트초코봉봉","블록팩 뉴욕치즈케이크","블록팩 쿠키앤크림",
        "블록팩 엄마는외계인","블록팩 아몬드봉봉","블록팩 슈팅스타","블록팩 체리쥬빌레",
        "레디팩 초코나무 숲","레디팩 31 요거트","레디팩 레인보우 샤베트","레디팩 민트 초콜릿 칩",
        "레디팩 베리베리 스트로베리","레디팩 소금 우유","레디팩 아몬드 봉봉","레디팩 엄마는 외계인",
        "레디팩 오레오 쿠키 앤 크림","레디팩 체리쥬빌레",

        // 매장 주요 아이스크림/콘
        "위대한 비쵸비","블루 바나나 브륄레","(Lessly Edition) 아몬드 봉봉","(Lessly Edition) 엄마는 외계인",
        "트로피컬 썸머 플레이","북극곰 폴라베어","블루 서퍼 비치","아이스 맥심 모카골드","사랑에 빠진 딸기",
        "피치 요거트","피치 Pang", "망고 Pang","수박 Hero","메롱 멜론","애플 민트","엄마는 외계인",
        "민트 초콜릿 칩","뉴욕 치즈케이크","레인보우 샤베트","체리쥬빌레","슈팅스타","오레오 쿠키 앤 크림",
        "베리베리 스트로베리","31요거트","바람과 함께 사라지다","피스타치오 아몬드","초콜릿 무스","그린티",
        "초콜릿","자모카 아몬드 훠지","아몬드 봉봉","바닐라",

        // 케이크/와츄원 (일부)
        "(Lessly Edition) 민트 초콜릿 칩 미니 케이크","(Lessly Edition) 엄마는 외계인 미니 케이크",
        "개구쟁이 스머프 하우스","더 듬뿍 프루티 케이크","더 듬뿍 복숭아 케이크","더 듬뿍 딸기 케이크",
        "진정한 초콜릿 케이크","진정한 치즈 케이크","진정한 티라미수 케이크","반짝이는 잔망루피",
        "나눠먹는 와츄원","나눠먹는 큐브 와츄원","골라먹는 27 큐브","해피 버스데이","우주에서 온 엄마는 외계인",
        "스노우 볼 와츄원","리얼 초코 27 큐브","미니 골라먹는 와츄원","미니 해피 버스데이 케이크",

        // 스낵/빙수/마카롱/모찌/모나카/스틱바 (일부)
        "블루 바나나 스틱바","(Lessly Edition) 저당 피치요거트 스틱바","(Lessly Edition) 저당 망고코코넛 스틱바",
        "아이스 바움쿠헨 아몬드봉봉","아이스 바움쿠헨 카페오레","쿠키 크런치 선데","카페 크런치 선데",
        "더 듬뿍 설향딸기 컵빙수","더 듬뿍 설향딸기 빙수","더 듬뿍 칸탈로프 멜론 컵빙수","더 듬뿍 칸탈로프 멜론 빙수",
        "더 듬뿍 팥빙수","더 듬뿍 팥 컵빙수","버터 쿠키 샌드 스트로베리","버터 쿠키 샌드 바닐라 카라멜",
        "그린티 킷캣 선데","아이스 마카롱 크림브륄레","아이스 꿀떡","홀리데이 미니 아이스 마카롱","DIY 모나카 세트",
        "아이스 모찌 밤 티라미수","아몬드봉봉모찌","아이스 모찌 슈크림","맥심 스틱바 슈프림골드","아이스 쿠키 샌드 바닐라",
        "맥심 스틱바 모카골드 마일드","미니 아이스 스틱바 바닐라","아이스 마카롱 체리쥬빌레","아이스 마카롱 초콜릿 무스",
        "아이스 마카롱 쿠키앤크림","아이스 모나카 쫀떡 인절미","아이스 모나카 우유","아이스 모찌 소금우유",
        "아이스 모찌 그린티","아이스 모찌 스트로베리","아이스 모찌 초코바닐라","아이스 모찌 크림치즈",

        // 블라스트/음료 (일부)
        "아이스 믹스커피 블라스트","짐빔 하이볼 레몬 블라스트","(Lessly Edition) 쉐이크",
        "모구모구 블라스트","칸탈로프 멜론 블라스트","수박 블라스트","딸기 찹쌀떡 쉐이크","저당 과일티",
        "설향딸기 블라스트","요거트 블라스트","카푸치노 블라스트","아이스크림 블라스트","와츄원 쉐이크",
        "밀크 쉐이크","오레오 쉐이크","납작복숭아 아이스티","딸기 연유 라떼",

        // 커피
        "아메리카노","카페라떼","바닐라빈 라떼","카라멜 마끼아또","엄마는 외계인 카페모카","연유라떼",
        "카페31","아포가토 라떼","콜드브루 아메리카노","콜드브루 라떼","콜드브루 오트","슈크림 아포가토 블라스트",
        "슈가밤 커피","슈가밤 블라스트","아포가토"
    )

    private val MENU_CATALOG: List<MenuEntry> by lazy { buildMenuCatalog(RAW_MENU) }

    private fun bestMenuMatch(text: String): String? {
        val q = normForMatch(text)
        if (q.isBlank()) return null
        var bestCanon: String? = null
        var bestDist = Int.MAX_VALUE
        var bestAliasLen = 0
        for (e in MENU_CATALOG) for (alias in e.aliases) {
            val a = normForMatch(alias); if (a.isBlank()) continue
            val d = editDistance(q, a)
            if (d < bestDist || (d == bestDist && a.length > bestAliasLen)) {
                bestDist = d; bestCanon = e.canonical; bestAliasLen = a.length
            }
        }
        val maxAllowed = kotlin.math.max(1, (kotlin.math.ceil(q.length * 0.2)).toInt()) // 길이 20%
        return if (bestCanon != null && bestDist <= maxAllowed) bestCanon else null
    }

    // ================================

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (throttleMs > 0 && now - lastTs.get() < throttleMs) { image.close(); return }
            lastTs.set(now)

            if (!inFlight.compareAndSet(false, true)) { image.close(); return }

            // 카메라 프레임 → 비트맵(+회전 보정)
            val src = yuvConverter.toBitmap(image)
            val rot = image.imageInfo.rotationDegrees
            val rotated = if (rot != 0) rotate(src, rot) else src
            if (rot != 0) src.recycle()

            // YOLO 탐지
            var dets = detector.detect(rotated)
            dets = dets.sortedByDescending { it.score }.take(20)

            val frameId = frameSeq.incrementAndGet()

            // ❶ 프라임 드로우
            val primeList = dets.mapIndexed { i, d -> ButtonBox(id = i, rect = d.rect, ocrLabel = null, iconLabel = null) }

            overlayView.post {
                try { overlayView.bringToFront() } catch (_: Throwable) {}
                overlayView.setSourceSize(rotated.width, rotated.height)
                overlayView.submitBoxes(primeList)
                overlayView.invalidate()
                android.util.Log.i("ANALYZER_BOXES","Prime draw ${primeList.size} boxes (labels pending)")
            }

            if (dets.isEmpty()) { inFlight.set(false); return }

            val working = java.util.Collections.synchronizedList(primeList.toMutableList())
            val pending = java.util.concurrent.atomic.AtomicInteger(dets.size)

            fun pushUpdate(reason: String) {
                if (frameSeq.get() != frameId) return
                overlayView.post {
                    overlayView.setSourceSize(rotated.width, rotated.height)
                    overlayView.submitBoxes(working.toList())
                    overlayView.invalidate()
                    android.util.Log.d("ANALYZER_BOXES","Update($reason) -> ${working.count { it.ocrLabel != null || it.iconLabel != null }}/${working.size}")
                }
            }

            dets.forEachIndexed { idx, det ->
                // PATCH #1: 크롭 확장 (작은 박스 보호)
                val expanded = expandRectToMinSize(
                    det.rect, minW = 64f, minH = 40f,
                    padFrac = if (det.rect.width() < 80f) 0.25f else 0.15f,
                    imgW = rotated.width, imgH = rotated.height
                )

                val rawCrop = cropBitmap(rotated, expanded)

                // PATCH #2 + #3: 업스케일 + 전처리
                val up = upscaleForOcr(rawCrop, targetShort = 192, maxLong = 1024)
                val ocrCrop = preprocessForOcr(up)

                val inputImg = InputImage.fromBitmap(ocrCrop, 0)

                fun setIcon(reason: String) {
                    val icon = roleClf.predictRole(rawCrop)
                    android.util.Log.d("LABEL","[$idx] ICON label='$icon' reason=$reason rect=$expanded raw=${rawCrop.width}x${rawCrop.height} ocr=${ocrCrop.width}x${ocrCrop.height}")
                    val old = working[idx]
                    working[idx] = old.copy(iconLabel = icon)
                    pushUpdate("icon")
                }

                textRecognizer.process(inputImg)
                    .addOnSuccessListener { vt ->
                        // ==== [PATCH #CLEANUP] 메뉴 라인만 선택 + 가격/기호 제거 ====
                        val bestLineRaw = pickBestLine(vt)          // 후보 라인 중 최선
                        val bestLine = stripPriceNoise(bestLineRaw) // 가격/기호 제거

                        if (bestLine.isNotEmpty()) {
                            // ==== [PATCH #LOG] 라벨 결정 경로 추적 ====
                            var res = LabelResult(bestLine, "raw-line")

                            // 1) 원문으로 1차 메뉴 스냅 (라틴/띄어쓰기 보존)
                            val preMenu = bestMenuMatch(bestLine)
                            if (preMenu != null) res = LabelResult(preMenu, "menu-pre")

                            // 2) normalize + 도메인 사전 스냅(결제/포인트 등 일반 버튼)
                            val base = res.text.ifBlank { bestLine }
                            val lex = snapPhraseToLexicon(base.take(30), maxDist = 2)
                            if (lex != base) res = LabelResult(lex, "lexicon")

                            // 3) 최종 메뉴 스냅(정식 메뉴명으로 교정)
                            val postMenu = bestMenuMatch(res.text)
                            if (postMenu != null) res = LabelResult(postMenu, "menu-post")

                            val finalLabel = res.text

                            if (enableLabelTrace) {
                                android.util.Log.i(
                                    "LABEL",
                                    "[$idx] OCR label='$finalLabel' src=${res.source} (raw='${vt.text.trim()}', line='${bestLineRaw}'->'${bestLine}') rect=$expanded raw=${rawCrop.width}x${rawCrop.height} ocr=${ocrCrop.width}x${ocrCrop.height}"
                                )
                            } else {
                                android.util.Log.i(
                                    "LABEL",
                                    "[$idx] OCR label='$finalLabel' rect=$expanded raw=${rawCrop.width}x${rawCrop.height} ocr=${ocrCrop.width}x${ocrCrop.height}"
                                )
                            }

                            val old = working[idx]
                            working[idx] = old.copy(ocrLabel = finalLabel)
                            pushUpdate("ocr+clean+menu+trace")
                        } else {
                            setIcon("ocr-empty")
                        }

                        // 메모리 정리
                        if (ocrCrop !== up && !ocrCrop.isRecycled) ocrCrop.recycle()
                        if (up !== rawCrop && !up.isRecycled) up.recycle()
                        if (!rawCrop.isRecycled) rawCrop.recycle()

                        if (pending.decrementAndGet() == 0) {
                            android.util.Log.i(
                                "ANALYZER_BOXES",
                                buildString {
                                    append("Final list for frame ").append(frameId).append('\n')
                                    working.forEach { b ->
                                        append("id=").append(b.id)
                                            .append(", rect=").append(b.rect)
                                            .append(", ocr=").append(b.ocrLabel)
                                            .append(", icon=").append(b.iconLabel).append('\n')
                                    }
                                }
                            )
                            kioskViewModel.setDetectedButtons(working.toList())
                            inFlight.set(false)
                        }
                    }
                    .addOnFailureListener { e ->
                        setIcon("ocr-failed: ${e.message}")
                        if (ocrCrop !== up && !ocrCrop.isRecycled) ocrCrop.recycle()
                        if (up !== rawCrop && !up.isRecycled) up.recycle()
                        if (!rawCrop.isRecycled) rawCrop.recycle()

                        if (pending.decrementAndGet() == 0) {
                            android.util.Log.i(
                                "ANALYZER_BOXES",
                                buildString {
                                    append("Final list for frame ").append(frameId).append('\n')
                                    working.forEach { b ->
                                        append("id=").append(b.id)
                                            .append(", rect=").append(b.rect)
                                            .append(", ocr=").append(b.ocrLabel)
                                            .append(", icon=").append(b.iconLabel).append('\n')
                                    }
                                }
                            )
                            kioskViewModel.setDetectedButtons(working.toList())
                            inFlight.set(false)
                        }
                    }
            }

        } catch (e: Throwable) {
            android.util.Log.e("ANALYZER", "analyze error", e)
            overlayView.post { overlayView.submitBoxes(emptyList()); overlayView.invalidate() }
            inFlight.set(false)
        } finally { image.close() }
    }
}

/*
@ActivityRetainedScoped
class ObjectDetectAnalyzer @Inject constructor(
    private val previewView: PreviewView,             // 런타임 전달
    private val overlayView: DetectionOverlayView,    // 런타임 전달
    private val detector: YoloV8TfliteInterpreter,    // Hilt 주입
    private val roleClf: IconRoleClassifier,          // Hilt 주입
    private val yuvConverter: YuvToRgbConverter,
    private val kioskViewModel: KioskViewModel,
    private val throttleMs: Long = 0L
) : ImageAnalysis.Analyzer {

    private val lastTs = java.util.concurrent.atomic.AtomicLong(0L)

    // 동시에 1프레임만 처리
    private val inFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private val frameSeq = java.util.concurrent.atomic.AtomicLong(0L)

    // OCR 엔진 (재사용)
    private val textRecognizer = TextRecognition.getClient(
        KoreanTextRecognizerOptions.Builder().build()
    )

    // ==== [PATCH #LOG] 라벨 결정 경로 디버그 스위치/자료형 ====
    private val enableLabelTrace = true
    private data class LabelResult(val text: String, val source: String)

    // ==== [PATCH #6] 키오스크 도메인 사전 ====
    private val LEXICON = listOf(
        // 국문
        "결제","결제완료","신용카드","카드","현금","현장결제",
        "현금영수증","영수증","발행","적립","포인트","쿠폰","회원","회원가입","로그인",
        "주문","주문완료","확인","확정","취소","전체취소","재시도","다음","이전",
        "번호표","대기표","처리중",
        // 영문(간단 예시)
        "CARD","CASH","COUPON","POINT","MEMBER","RECEIPT","ISSUE","ORDER",
        "CANCEL","OK","YES","NO","PAY","NEXT","BACK"
    )

    // ────────────── OCR 보강/유틸 ──────────────
    private fun expandRectToMinSize(
        r: RectF, minW: Float, minH: Float, padFrac: Float, imgW: Int, imgH: Int
    ): RectF {
        val w = r.width(); val h = r.height()
        val padX = max(w * padFrac, 2f)
        val padY = max(h * padFrac, 2f)
        var left = r.left - padX
        var top = r.top - padY
        var right = r.right + padX
        var bottom = r.bottom + padY
        if (right - left < minW) { val add = (minW - (right - left)) / 2f; left -= add; right += add }
        if (bottom - top < minH) { val add = (minH - (bottom - top)) / 2f; top -= add; bottom += add }
        left = left.coerceIn(0f, imgW.toFloat()); top = top.coerceIn(0f, imgH.toFloat())
        right = right.coerceIn(0f, imgW.toFloat()); bottom = bottom.coerceIn(0f, imgH.toFloat())
        if (right <= left) right = min(imgW.toFloat(), left + minW)
        if (bottom <= top) bottom = min(imgH.toFloat(), top + minH)
        return RectF(left, top, right, bottom)
    }

    // ==== PATCH #2: 업스케일 192/1024 유지 ====
    private fun upscaleForOcr(src: Bitmap, targetShort: Int = 192, maxLong: Int = 1024): Bitmap {
        val shortSide = min(src.width, src.height)
        if (shortSide >= targetShort) return src
        val scale = targetShort.toFloat() / shortSide
        val outW = (src.width * scale).toInt().coerceAtMost(maxLong)
        val outH = (src.height * scale).toInt().coerceAtMost(maxLong)
        return Bitmap.createScaledBitmap(src, outW, outH, true)
    }

    // ==== PATCH #3: 전처리(그레이스케일+대비/밝기) ====
    private fun preprocessForOcr(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val cm = android.graphics.ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.45f
        val brightness = 12f
        cm.postConcat(android.graphics.ColorMatrix(floatArrayOf(
            contrast, 0f,       0f,       0f, brightness,
            0f,       contrast, 0f,       0f, brightness,
            0f,       0f,       contrast, 0f, brightness,
            0f,       0f,       0f,       1f, 0f
        )))
        val paint = android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            isFilterBitmap = true
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun normalizeOcr(text: String): String {
        var t = text.replace(Regex("\n+"), " ").trim()
        t = t.replace('O', '0').replace('o', '0')
            .replace('l', '1').replace('I', '1')
            .replace('S', '5')
        return t
    }

    private fun cropBitmap(src: Bitmap, rect: RectF): Bitmap {
        val left = rect.left.toInt().coerceAtLeast(0)
        val top = rect.top.toInt().coerceAtLeast(0)
        val width = rect.width().toInt().coerceAtMost(src.width - left).coerceAtLeast(1)
        val height = rect.height().toInt().coerceAtMost(src.height - top).coerceAtLeast(1)
        return Bitmap.createBitmap(src, left, top, width, height)
    }

    private fun rotate(b: Bitmap, deg: Int): Bitmap {
        if (deg == 0) return b
        val m = android.graphics.Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    // ==== [PATCH #6] 편집거리(레벤슈타인) ====
    private fun editDistance(a: String, b: String): Int {
        val m = a.length; val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) for (j in 1..n) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
        }
        return dp[m][n]
    }

    // ==== [PATCH #6] 토큰 스냅 ====
    private fun snapTokenToLexicon(token: String, maxDist: Int = 2): String {
        if (token.isBlank()) return token
        val hasLetter = token.any { it.isLetter() }
        if (!hasLetter) return token
        val tUpper = token.uppercase()
        var best = token
        var bestD = Int.MAX_VALUE
        for (w in LEXICON) {
            val d = editDistance(tUpper, w.uppercase())
            if (d < bestD) { bestD = d; best = w; if (bestD == 0) break }
        }
        return if (bestD <= maxDist) best else token
    }
    private fun snapPhraseToLexicon(text: String, maxDist: Int = 2): String {
        val tokens = text.split(Regex("[\\s/\\-_,:()]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return text
        val snapped = tokens.map { snapTokenToLexicon(it, maxDist) }
        return snapped.joinToString(" ")
    }

    // ================================
    // ==== [PATCH #CLEANUP] 가격/노이즈 제거 & 라인 선택 ====
    // ================================
    private fun hangulScore(s: String): Int {
        val h = Regex("[가-힣]").findAll(s).count()
        val d = Regex("[0-9]").findAll(s).count()
        return h * 2 - d
    }

    private fun stripPriceNoise(s: String): String {
        return s
            .replace(Regex("\\b\\d{1,3}(,\\d{3})+\\b"), " ")
            .replace(Regex("\\b\\d+\\s*원\\b"), " ")
            .replace(Regex("\\b\\d+[!,.]?\\b"), " ")
            .replace(Regex("[₩$€¥!]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun pickBestLine(vt: com.google.mlkit.vision.text.Text): String {
        val lines = mutableListOf<String>()
        for (b in vt.textBlocks) for (l in b.lines) lines += l.text.trim()
        if (lines.isEmpty()) return vt.text.trim()
        val scored = lines.mapIndexed { idx, s ->
            val score = hangulScore(s) + (lines.size - idx) // 상단 라인 가산점
            score to s
        }
        val best = scored.maxByOrNull { it.first }?.second ?: vt.text.trim()
        return stripPriceNoise(best)
    }

    // ================================
    // ==== [PATCH #MENU] 메뉴 스냅 ====
    // ================================
    private data class MenuEntry(
        val canonical: String,
        val aliases: Set<String> = emptySet(),
        val tags: Set<String> = emptySet()
    )

    private fun normForMatch(s: String): String {
        val t = s.replace(Regex("\\(.*?\\)"), " ")
            .replace(Regex("[/_,:·]"), " ")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\d"), "")         // ★ 숫자 제거로 가격 노이즈 차단
            .trim()
        return t.replace(" ", "").uppercase()
    }

    private fun buildMenuCatalog(rawLines: List<String>): List<MenuEntry> {
        val catPrefixes = listOf("블록팩", "레디팩")
        val tagWords = mapOf(
            "케이크" to setOf("케이크"), "스틱바" to setOf("스틱바"),
            "모찌" to setOf("모찌"), "모나카" to setOf("모나카"),
            "바움쿠헨" to setOf("바움쿠헨"), "선데" to setOf("선데"),
            "빙수" to setOf("빙수","컵빙수"),
            "블라스트" to setOf("블라스트"), "쉐이크" to setOf("쉐이크"),
            "라떼" to setOf("라떼"), "아메리카노" to setOf("아메리카노"),
            "콜드브루" to setOf("콜드브루")
        )

        fun extractTags(name: String): Set<String> {
            val tags = mutableSetOf<String>()
            for ((k, vs) in tagWords) if (vs.any { name.contains(it) }) tags += k
            if (Regex("\\(.*?Lessly.*?\\)", RegexOption.IGNORE_CASE).containsMatchIn(name)) tags += "Lessly Edition"
            return tags
        }

        fun genAliases(one: String): Pair<Set<String>, Set<String>> {
            var base = one.trim()
            val tags = mutableSetOf<String>()
            for (p in catPrefixes) if (base.startsWith(p)) { tags += p; base = base.removePrefix(p).trim(); break }
            val noParen = base.replace(Regex("\\(.*?\\)"), "").trim()
            fun variants(s: String) = setOf(
                s, s.replace(" ", ""), s.replace("-", " ").replace(Regex("\\s+"), " ").trim()
            )
            val withPrefix = tags.map { "$it $noParen".trim() }.toSet()
            val alias = variants(noParen) + withPrefix.flatMap { variants(it) }
            return alias to (tags + extractTags(one))
        }

        val entries = mutableListOf<MenuEntry>()
        for (line in rawLines.map { it.trim() }.filter { it.isNotEmpty() }) {
            val parts = line.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val first = parts.first()
            val (aliasSet, tags) = genAliases(first)
            val moreAliases = parts.drop(1).flatMap { genAliases(it).first }
            val allAliases = aliasSet + moreAliases
            val canonical = first
                .replace(Regex("^블록팩\\s*|^레디팩\\s*"), "")
                .replace(Regex("\\(.*?\\)"), "")
                .trim()
            entries += MenuEntry(canonical = canonical, aliases = allAliases, tags = tags)
        }
        return entries
    }

    // [PATCH #MENU] 실제 메뉴 라인업 (요약 — 필요시 계속 추가)
    private val RAW_MENU = listOf(
        // 젤라또/팩
        "요거트 젤라또","헤이즐넛 젤라또","애플망고 젤라또",
        "블록팩 레인보우샤베트","블록팩 베리베리스트로베리","블록팩 초콜릿","블록팩 바람과함께사라지다",
        "블록팩 이상한나라의솜사탕","블록팩 민트초코봉봉","블록팩 뉴욕치즈케이크","블록팩 쿠키앤크림",
        "블록팩 엄마는외계인","블록팩 아몬드봉봉","블록팩 슈팅스타","블록팩 체리쥬빌레",
        "레디팩 초코나무 숲","레디팩 31 요거트","레디팩 레인보우 샤베트","레디팩 민트 초콜릿 칩",
        "레디팩 베리베리 스트로베리","레디팩 소금 우유","레디팩 아몬드 봉봉","레디팩 엄마는 외계인",
        "레디팩 오레오 쿠키 앤 크림","레디팩 체리쥬빌레",

        // 매장 주요 아이스크림/콘
        "위대한 비쵸비","블루 바나나 브륄레","(Lessly Edition) 아몬드 봉봉","(Lessly Edition) 엄마는 외계인",
        "트로피컬 썸머 플레이","북극곰 폴라베어","블루 서퍼 비치","아이스 맥심 모카골드","사랑에 빠진 딸기",
        "피치 요거트","피치 Pang", "망고 Pang","수박 Hero","메롱 멜론","애플 민트","엄마는 외계인",
        "민트 초콜릿 칩","뉴욕 치즈케이크","레인보우 샤베트","체리쥬빌레","슈팅스타","오레오 쿠키 앤 크림",
        "베리베리 스트로베리","31요거트","바람과 함께 사라지다","피스타치오 아몬드","초콜릿 무스","그린티",
        "초콜릿","자모카 아몬드 훠지","아몬드 봉봉","바닐라",

        // 케이크/와츄원 (일부)
        "(Lessly Edition) 민트 초콜릿 칩 미니 케이크","(Lessly Edition) 엄마는 외계인 미니 케이크",
        "개구쟁이 스머프 하우스","더 듬뿍 프루티 케이크","더 듬뿍 복숭아 케이크","더 듬뿍 딸기 케이크",
        "진정한 초콜릿 케이크","진정한 치즈 케이크","진정한 티라미수 케이크","반짝이는 잔망루피",
        "나눠먹는 와츄원","나눠먹는 큐브 와츄원","골라먹는 27 큐브","해피 버스데이","우주에서 온 엄마는 외계인",
        "스노우 볼 와츄원","리얼 초코 27 큐브","미니 골라먹는 와츄원","미니 해피 버스데이 케이크",

        // 스낵/빙수/마카롱/모찌/모나카/스틱바 (일부)
        "블루 바나나 스틱바","(Lessly Edition) 저당 피치요거트 스틱바","(Lessly Edition) 저당 망고코코넛 스틱바",
        "아이스 바움쿠헨 아몬드봉봉","아이스 바움쿠헨 카페오레","쿠키 크런치 선데","카페 크런치 선데",
        "더 듬뿍 설향딸기 컵빙수","더 듬뿍 설향딸기 빙수","더 듬뿍 칸탈로프 멜론 컵빙수","더 듬뿍 칸탈로프 멜론 빙수",
        "더 듬뿍 팥빙수","더 듬뿍 팥 컵빙수","버터 쿠키 샌드 스트로베리","버터 쿠키 샌드 바닐라 카라멜",
        "그린티 킷캣 선데","아이스 마카롱 크림브륄레","아이스 꿀떡","홀리데이 미니 아이스 마카롱","DIY 모나카 세트",
        "아이스 모찌 밤 티라미수","아몬드봉봉모찌","아이스 모찌 슈크림","맥심 스틱바 슈프림골드","아이스 쿠키 샌드 바닐라",
        "맥심 스틱바 모카골드 마일드","미니 아이스 스틱바 바닐라","아이스 마카롱 체리쥬빌레","아이스 마카롱 초콜릿 무스",
        "아이스 마카롱 쿠키앤크림","아이스 모나카 쫀떡 인절미","아이스 모나카 우유","아이스 모찌 소금우유",
        "아이스 모찌 그린티","아이스 모찌 스트로베리","아이스 모찌 초코바닐라","아이스 모찌 크림치즈",

        // 블라스트/음료 (일부)
        "아이스 믹스커피 블라스트","짐빔 하이볼 레몬 블라스트","(Lessly Edition) 쉐이크",
        "모구모구 블라스트","칸탈로프 멜론 블라스트","수박 블라스트","딸기 찹쌀떡 쉐이크","저당 과일티",
        "설향딸기 블라스트","요거트 블라스트","카푸치노 블라스트","아이스크림 블라스트","와츄원 쉐이크",
        "밀크 쉐이크","오레오 쉐이크","납작복숭아 아이스티","딸기 연유 라떼",

        // 커피
        "아메리카노","카페라떼","바닐라빈 라떼","카라멜 마끼아또","엄마는 외계인 카페모카","연유라떼",
        "카페31","아포가토 라떼","콜드브루 아메리카노","콜드브루 라떼","콜드브루 오트","슈크림 아포가토 블라스트",
        "슈가밤 커피","슈가밤 블라스트","아포가토"
    )

    private val MENU_CATALOG: List<MenuEntry> by lazy { buildMenuCatalog(RAW_MENU) }

    // ==== [PATCH #MENU-RELAX] 느슨 매칭 파라미터 ====
    private val MENU_STRICT_RATIO = 0.20f
    private val MENU_RELAX_RATIO  = 0.28f
    private val MENU_RELAX_MIN    = 2
    private val MENU_RELAX_MAX    = 5

    // ==== [PATCH #MENU-RELAX] 엄격/느슨 허용치 동시 지원 ====
    private fun bestMenuMatch(text: String, relaxed: Boolean = false): String? {
        val q = normForMatch(text)
        if (q.isBlank()) return null

        var bestCanon: String? = null
        var bestDist = Int.MAX_VALUE
        var bestAliasLen = 0

        for (e in MENU_CATALOG) for (alias in e.aliases) {
            val a = normForMatch(alias); if (a.isBlank()) continue
            val d = editDistance(q, a)
            if (d < bestDist || (d == bestDist && a.length > bestAliasLen)) {
                bestDist = d; bestCanon = e.canonical; bestAliasLen = a.length
            }
        }

        val strictAllowed = kotlin.math.max(1, (kotlin.math.ceil(q.length * MENU_STRICT_RATIO)).toInt())
        val relaxAllowed = kotlin.math.max(
            strictAllowed,
            (q.length * MENU_RELAX_RATIO).toInt().coerceIn(MENU_RELAX_MIN, MENU_RELAX_MAX)
        )

        val allowed = if (relaxed) relaxAllowed else strictAllowed
        return if (bestCanon != null && bestDist <= allowed) bestCanon else null
    }

    // ================================

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (throttleMs > 0 && now - lastTs.get() < throttleMs) { image.close(); return }
            lastTs.set(now)

            if (!inFlight.compareAndSet(false, true)) { image.close(); return }

            // 카메라 프레임 → 비트맵(+회전 보정)
            val src = yuvConverter.toBitmap(image)
            val rot = image.imageInfo.rotationDegrees
            val rotated = if (rot != 0) rotate(src, rot) else src
            if (rot != 0) src.recycle()

            // YOLO 탐지
            var dets = detector.detect(rotated)
            dets = dets.sortedByDescending { it.score }.take(20)

            val frameId = frameSeq.incrementAndGet()

            // ❶ 프라임 드로우
            val primeList = dets.mapIndexed { i, d -> ButtonBox(id = i, rect = d.rect, ocrLabel = null, iconLabel = null) }

            overlayView.post {
                try { overlayView.bringToFront() } catch (_: Throwable) {}
                overlayView.setSourceSize(rotated.width, rotated.height)
                overlayView.submitBoxes(primeList)
                overlayView.invalidate()
                android.util.Log.i("ANALYZER_BOXES","Prime draw ${primeList.size} boxes (labels pending)")
            }

            if (dets.isEmpty()) { inFlight.set(false); return }

            val working = java.util.Collections.synchronizedList(primeList.toMutableList())
            val pending = java.util.concurrent.atomic.AtomicInteger(dets.size)

            fun pushUpdate(reason: String) {
                if (frameSeq.get() != frameId) return
                overlayView.post {
                    overlayView.setSourceSize(rotated.width, rotated.height)
                    overlayView.submitBoxes(working.toList())
                    overlayView.invalidate()
                    android.util.Log.d("ANALYZER_BOXES","Update($reason) -> ${working.count { it.ocrLabel != null || it.iconLabel != null }}/${working.size}")
                }
            }

            dets.forEachIndexed { idx, det ->
                // PATCH #1: 크롭 확장 (작은 박스 보호)
                val expanded = expandRectToMinSize(
                    det.rect, minW = 64f, minH = 40f,
                    padFrac = if (det.rect.width() < 80f) 0.25f else 0.15f,
                    imgW = rotated.width, imgH = rotated.height
                )

                val rawCrop = cropBitmap(rotated, expanded)

                // PATCH #2 + #3: 업스케일 + 전처리
                val up = upscaleForOcr(rawCrop, targetShort = 192, maxLong = 1024)
                val ocrCrop = preprocessForOcr(up)

                val inputImg = InputImage.fromBitmap(ocrCrop, 0)

                fun setIcon(reason: String) {
                    val icon = roleClf.predictRole(rawCrop)
                    android.util.Log.d("LABEL","[$idx] ICON label='$icon' reason=$reason rect=$expanded raw=${rawCrop.width}x${rawCrop.height} ocr=${ocrCrop.width}x${ocrCrop.height}")
                    val old = working[idx]
                    working[idx] = old.copy(iconLabel = icon)
                    pushUpdate("icon")
                }

                textRecognizer.process(inputImg)
                    .addOnSuccessListener { vt ->
                        // ==== [PATCH #CLEANUP] 메뉴 라인만 선택 + 가격/기호 제거 ====
                        val bestLineRaw = pickBestLine(vt)
                        val bestLine = stripPriceNoise(bestLineRaw)

                        if (bestLine.isNotEmpty()) {
                            // ==== [PATCH #LOG] 라벨 결정 경로 추적 ====
                            var res = LabelResult(bestLine, "raw-line")

                            // 1) 원문으로 1차 메뉴 스냅 (엄격)
                            val preMenu = bestMenuMatch(bestLine) // strict
                            if (preMenu != null) res = LabelResult(preMenu, "menu-pre")

                            // 2) normalize + 도메인 사전 스냅(결제/포인트 등 일반 버튼)
                            val base = res.text.ifBlank { bestLine }
                            val lex = snapPhraseToLexicon(base.take(30), maxDist = 2)
                            if (lex != base) res = LabelResult(lex, "lexicon")

                            // 3) 최종 메뉴 스냅(정식 메뉴명) — 느슨 허용
                            val postMenu = bestMenuMatch(res.text, relaxed = true) // ★ relaxed 적용
                            if (postMenu != null) res = LabelResult(postMenu, "menu-post")

                            val finalLabel = res.text

                            if (enableLabelTrace) {
                                android.util.Log.i(
                                    "LABEL",
                                    "[$idx] OCR label='$finalLabel' src=${res.source} (raw='${vt.text.trim()}', line='${bestLineRaw}'->'${bestLine}') rect=$expanded raw=${rawCrop.width}x${rawCrop.height} ocr=${ocrCrop.width}x${ocrCrop.height}"
                                )
                            } else {
                                android.util.Log.i(
                                    "LABEL",
                                    "[$idx] OCR label='$finalLabel' rect=$expanded raw=${rawCrop.width}x${rawCrop.height} ocr=${ocrCrop.width}x${ocrCrop.height}"
                                )
                            }

                            val old = working[idx]
                            working[idx] = old.copy(ocrLabel = finalLabel)
                            pushUpdate("ocr+clean+menu+trace")
                        } else {
                            setIcon("ocr-empty")
                        }

                        // 메모리 정리
                        if (ocrCrop !== up && !ocrCrop.isRecycled) ocrCrop.recycle()
                        if (up !== rawCrop && !up.isRecycled) up.recycle()
                        if (!rawCrop.isRecycled) rawCrop.recycle()

                        if (pending.decrementAndGet() == 0) {
                            android.util.Log.i(
                                "ANALYZER_BOXES",
                                buildString {
                                    append("Final list for frame ").append(frameId).append('\n')
                                    working.forEach { b ->
                                        append("id=").append(b.id)
                                            .append(", rect=").append(b.rect)
                                            .append(", ocr=").append(b.ocrLabel)
                                            .append(", icon=").append(b.iconLabel).append('\n')
                                    }
                                }
                            )
                            kioskViewModel.setDetectedButtons(working.toList())
                            inFlight.set(false)
                        }
                    }
                    .addOnFailureListener { e ->
                        setIcon("ocr-failed: ${e.message}")
                        if (ocrCrop !== up && !ocrCrop.isRecycled) ocrCrop.recycle()
                        if (up !== rawCrop && !up.isRecycled) up.recycle()
                        if (!rawCrop.isRecycled) rawCrop.recycle()

                        if (pending.decrementAndGet() == 0) {
                            android.util.Log.i(
                                "ANALYZER_BOXES",
                                buildString {
                                    append("Final list for frame ").append(frameId).append('\n')
                                    working.forEach { b ->
                                        append("id=").append(b.id)
                                            .append(", rect=").append(b.rect)
                                            .append(", ocr=").append(b.ocrLabel)
                                            .append(", icon=").append(b.iconLabel).append('\n')
                                    }
                                }
                            )
                            kioskViewModel.setDetectedButtons(working.toList())
                            inFlight.set(false)
                        }
                    }
            }

        } catch (e: Throwable) {
            android.util.Log.e("ANALYZER", "analyze error", e)
            overlayView.post { overlayView.submitBoxes(emptyList()); overlayView.invalidate() }
            inFlight.set(false)
        } finally { image.close() }
    }
}
*/