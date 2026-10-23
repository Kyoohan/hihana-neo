package com.skh.hihanaevo.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 사용자가 고른 배경 사진 — 앱 내부 저장소에 한 장만 둡니다. 원본이 아니라 긴 변 1600px 로 줄여
 * JPEG 로 저장해, 매 실행마다 큰 사진을 디코드하지 않게 합니다. 없으면 기본(초록 그레인) 배경입니다.
 */
object PageBackgroundStore {

    private const val FILE_NAME = "page_background.jpg"
    private const val MAX_EDGE = 1600
    private const val CROP_EDGE = 2400

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).exists()

    /** 파일이 바뀔 때마다 달라지는 값 — 이걸 키로 비트맵을 다시 읽습니다. */
    fun version(context: Context): Long = file(context).takeIf { it.exists() }?.lastModified() ?: 0L

    /** 자르기 화면용으로 [uri] 를 읽습니다 — EXIF 회전을 적용하고 긴 변 [CROP_EDGE]px 이하로 줄입니다. */
    suspend fun load(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            // 1) 크기만 읽어 축소 비율을 정하고, 2) 그 비율로 디코드합니다 — 수천 px 사진도 메모리를 넘기지 않습니다.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return@runCatching null
            var sample = 1
            while (longest / (sample * 2) >= CROP_EDGE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@runCatching null
            // 세로로 찍은 사진은 픽셀은 가로인데 EXIF 에 회전이 적혀 있습니다 — 안 돌리면 눕혀진 채 저장됐습니다.
            val orientation = runCatching {
                resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
            }.getOrNull() ?: 1
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            }
            if (matrix.isIdentity) decoded
            else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }.getOrNull()
    }

    /** 자른 [bitmap] 을 긴 변 [MAX_EDGE]px 로 줄여 저장합니다. 실패하면 false. */
    suspend fun saveBitmap(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val scale = MAX_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }
            val target = file(context)
            val tmp = File(target.parentFile, "$FILE_NAME.tmp")
            tmp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (scaled !== bitmap) scaled.recycle()
            tmp.renameTo(target)
        }.getOrDefault(false)
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun stashFile(context: Context): File = File(context.filesDir, "$FILE_NAME.kept")

    /** 사진을 지우지 않고 옆에 치워 둡니다 — 업데이트 투어에서 '새 기본 배경'을 골랐다가 마음을 바꿔도 되돌릴 수 있게. */
    fun stash(context: Context) {
        val f = file(context)
        if (f.exists()) f.renameTo(stashFile(context))
    }

    /** [stash] 로 치워 둔 사진을 다시 배경으로. 치운 게 없으면 false. */
    fun restore(context: Context): Boolean {
        val kept = stashFile(context)
        return kept.exists() && kept.renameTo(file(context))
    }
}

/** 현재 배경 사진 — null 이면 기본 배경을 그립니다. 앱 루트에서 제공합니다. */
val LocalPageBackground = compositionLocalOf<ImageBitmap?> { null }

/** [version] 이 바뀔 때마다 저장된 배경 사진을 다시 읽습니다 (없으면 null). */
@Composable
fun rememberPageBackground(version: Long): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = version) {
        value = withContext(Dispatchers.IO) {
            val file = PageBackgroundStore.file(context)
            if (!file.exists()) null
            else runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
        }
    }
    return bitmap
}

/**
 * 배경 사진 자르기 — 화면 비율 그대로의 틀 안에서 사진을 끌고(이동) 두 손가락으로 벌려(확대) 맞춥니다.
 * 틀은 화면 전체이므로 보이는 그대로가 배경이 됩니다. '적용'을 누르면 틀 안의 영역만 잘라 돌려줍니다.
 */
@Composable
fun BackgroundCropDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onApply: (Bitmap) -> Unit,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val context = LocalContext.current
    // 밝기·채도는 사진을 고르는 이 화면에서부터 — 새 사진은 기본값(-10%·-10%)에서 시작하고 미리보기에 바로 반영됩니다.
    var brightness by remember { mutableFloatStateOf(PageBackgroundAdjust.DEFAULT_BRIGHTNESS) }
    var saturation by remember { mutableFloatStateOf(PageBackgroundAdjust.DEFAULT_SATURATION) }
    val previewFilter = PageBackgroundAdjust.filterFor(brightness, saturation)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val frameW = constraints.maxWidth.toFloat()
            val frameH = constraints.maxHeight.toFloat()
            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            // 처음엔 화면을 꽉 채우는(cover) 배율로 가운데에.
            val minScale = maxOf(frameW / bw, frameH / bh)
            var scale by remember(minScale) { mutableFloatStateOf(minScale) }
            var tx by remember(minScale) { mutableFloatStateOf((frameW - bw * minScale) / 2f) }
            var ty by remember(minScale) { mutableFloatStateOf((frameH - bh * minScale) / 2f) }
            fun clamp() {
                // 틀 밖으로 빈 곳이 생기지 않도록 이동 범위를 제한합니다.
                tx = tx.coerceIn(frameW - bw * scale, 0f)
                ty = ty.coerceIn(frameH - bh * scale, 0f)
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(minScale) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(minScale, minScale * 6f)
                            // 손가락 사이 중심을 기준으로 확대되도록 이동값을 보정합니다.
                            val k = newScale / scale
                            tx = centroid.x - (centroid.x - tx) * k + pan.x
                            ty = centroid.y - (centroid.y - ty) * k + pan.y
                            scale = newScale
                            clamp()
                        }
                    },
            ) {
                drawImage(
                    image,
                    dstOffset = IntOffset(tx.roundToInt(), ty.roundToInt()),
                    dstSize = IntSize((bw * scale).roundToInt(), (bh * scale).roundToInt()),
                    colorFilter = previewFilter,
                )
            }
            Text(
                "끌어서 위치를, 두 손가락으로 크기를 맞추세요",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 20.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .fillMaxWidth(),
            ) {
            // 밝기·채도 — 사진 위에 어두운 판을 깔아 어떤 사진에서도 읽히게.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                @Composable
                fun Adjust(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: Float, onChange: (Float) -> Unit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(44.dp))
                        OneUiSlider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
                        val pct = (shown * 100).roundToInt()
                        Text(
                            if (pct > 0) "+$pct%" else "$pct%",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.width(52.dp).padding(start = 8.dp),
                        )
                    }
                }
                Adjust("밝기", brightness, -0.4f..0.4f, brightness) { brightness = (it * 50).roundToInt() / 50f }
                Adjust("채도", saturation, 0f..1.6f, saturation - 1f) { saturation = (it * 50).roundToInt() / 50f }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OneUiButton("취소", onClick = onDismiss, modifier = Modifier.weight(1f), style = OneUiButtonStyle.Neutral)
                OneUiButton(
                    "적용",
                    onClick = {
                        val x = ((-tx) / scale).roundToInt().coerceIn(0, bitmap.width - 1)
                        val y = ((-ty) / scale).roundToInt().coerceIn(0, bitmap.height - 1)
                        val w = (frameW / scale).roundToInt().coerceIn(1, bitmap.width - x)
                        val h = (frameH / scale).roundToInt().coerceIn(1, bitmap.height - y)
                        PageBackgroundAdjust.set(context, brightness, saturation)
                        onApply(Bitmap.createBitmap(bitmap, x, y, w, h))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            }
        }
    }
}

/** 카드 서리 유리용으로 미리 흐린 배경 사진 — 루트에서 사진이 바뀔 때 한 번만 만듭니다(null = 사진 없음). */
val LocalBlurredPageBackground = compositionLocalOf<ImageBitmap?> { null }

/**
 * 창 좌표의 사각형 아래에 보이는 흐린 배경의 평균 밝기(0~1) — 서리 카드 안 글자색을 정할 때 씁니다.
 * [drawPageBackground] 와 같은 cover 배치로 좌표를 옮기고, 설정의 밝기 보정([brightness], 색 행렬 오프셋)을 더합니다.
 * 흐린 사본은 긴 변 96px 라 12점만 읽어도 충분하고 비용이 거의 없습니다.
 */
fun blurredLuminanceUnder(blurred: ImageBitmap, winW: Float, winH: Float, left: Float, top: Float, width: Float, height: Float, brightness: Float): Float {
    val bmp = blurred.asAndroidBitmap()
    if (winW <= 0f || winH <= 0f || width <= 0f || height <= 0f) return 0.5f
    val scale = maxOf(winW / bmp.width, winH / bmp.height)
    val offX = (winW - bmp.width * scale) / 2f
    val offY = (winH - bmp.height * scale) / 2f
    var sum = 0f
    var n = 0
    for (iy in 0 until 3) for (ix in 0 until 4) {
        val x = ((left + width * (ix + 0.5f) / 4f - offX) / scale).toInt().coerceIn(0, bmp.width - 1)
        val y = ((top + height * (iy + 0.5f) / 3f - offY) / scale).toInt().coerceIn(0, bmp.height - 1)
        val c = bmp.getPixel(x, y)
        sum += (0.2126f * android.graphics.Color.red(c) + 0.7152f * android.graphics.Color.green(c) + 0.0722f * android.graphics.Color.blue(c)) / 255f
        n++
    }
    return (sum / n + brightness).coerceIn(0f, 1f)
}

/**
 * 서리 판 위 글자가 밝아야 하는지(판이 어두운지) — 판 아래 배경 밝기와 틴트를 섞은 실제 밝기로 판단합니다.
 * [previous] 가 있으면 0.45~0.55 사이에선 바꾸지 않아(이력) 스크롤 중 글자색이 깜빡이지 않습니다.
 */
fun frostOnDark(backdropLum: Float, tint: androidx.compose.ui.graphics.Color, previous: Boolean?): Boolean {
    val effective = backdropLum * (1f - tint.alpha) + tint.luminance() * tint.alpha
    return when (previous) {
        null -> effective < 0.5f
        true -> effective < 0.55f
        false -> effective < 0.45f
    }
}

/**
 * 사진을 크게 흐린 작은 사본 — 긴 변 96px 로 줄인 뒤 상자 흐림을 세 번(가우시안에 가깝게) 걸어, 그릴 때 화면 크기로
 * 늘리면(이중 선형 보간) 반경 수십 dp 의 흐림처럼 보입니다. 카드가 스크롤 프레임마다 실시간 흐림을 계산하지 않게 합니다.
 */
fun blurredBackdrop(photo: ImageBitmap): ImageBitmap {
    val src = photo.asAndroidBitmap()
    val scale = 96f / maxOf(src.width, src.height)
    val w = (src.width * scale).toInt().coerceAtLeast(1)
    val h = (src.height * scale).toInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(src.copy(Bitmap.Config.ARGB_8888, false), w, h, true)
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    val tmp = IntArray(w * h)
    val r = 3
    repeat(3) {
        boxBlur(px, tmp, w, h, r, horizontal = true)
        boxBlur(tmp, px, w, h, r, horizontal = false)
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out.asImageBitmap()
}

private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    for (line in 0 until lines) {
        for (i in 0 until len) {
            var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
            for (k in -r..r) {
                val j = (i + k).coerceIn(0, len - 1)
                val c = if (horizontal) src[line * w + j] else src[j * w + line]
                a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
            }
            val idx = if (horizontal) line * w + i else i * w + line
            dst[idx] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
    }
}

/**
 * 배경 사진 위에 바로 놓이는 글자·아이콘 색(헤더 제목·날짜·오른쪽 위 버튼) — 사진 위쪽이 어두우면 흰색, 밝으면 검정.
 * 사진이 없으면 null(테마 색 그대로). 헤더가 접혀 유리 섬 안으로 들어가면 테마 색으로 돌아갑니다.
 */
val LocalOnPageColor = compositionLocalOf<Color?> { null }

/**
 * 화면 위쪽(헤더가 놓이는 높이의 22%)에 보이는 사진 영역의 평균 밝기로 [LocalOnPageColor] 를 정합니다. 사진은 화면을 꽉
 * 채우도록 가운데를 잘라 그리므로([drawPageBackground]) 같은 잘림으로 보이는 영역을 계산합니다. 흐린 사본(작음)으로 잽니다.
 */
fun onPageColorFor(blurred: ImageBitmap, screenW: Float, screenH: Float, brightness: Float = 0f): Color {
    val bmp = blurred.asAndroidBitmap()
    val scale = maxOf(screenW / bmp.width, screenH / bmp.height)
    val visW = screenW / scale
    val visH = screenH / scale
    val left = ((bmp.width - visW) / 2f).toInt().coerceAtLeast(0)
    val top = ((bmp.height - visH) / 2f).toInt().coerceAtLeast(0)
    val right = (left + visW).toInt().coerceAtMost(bmp.width)
    val bottom = (top + visH * 0.22f).toInt().coerceIn(top + 1, bmp.height)
    var sum = 0.0
    var n = 0
    for (y in top until bottom) for (x in left until right) {
        val c = bmp.getPixel(x, y)
        // 상대 휘도(sRGB 근사).
        sum += 0.2126 * ((c shr 16) and 0xFF) + 0.7152 * ((c shr 8) and 0xFF) + 0.0722 * (c and 0xFF)
        n++
    }
    val luminance = (if (n == 0) 128.0 else sum / n) + brightness * 255
    return if (luminance < 150) Color.White else Color(0xFF111114)
}

/** 사진 배경 위에 바로 놓이는 보조 글자 색(빈 목록 안내·섹션 제목 등) — 사진이 없으면 테마의 보조 글자 색. */
@Composable
fun onPageVariantColor(): Color =
    LocalOnPageColor.current?.copy(alpha = 0.9f) ?: androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant

/**
 * 배경 사진 밝기·채도 조절 — 설정 → 화면의 배경 이미지 아래 슬라이더. 사진 파일은 그대로 두고 그릴 때 색 행렬로 적용해,
 * 언제든 원래대로 되돌릴 수 있습니다. 카드 서리 유리가 비추는 흐린 사본에도 같은 보정이 들어갑니다.
 */
object PageBackgroundAdjust {
    private const val PREFS = "page_background_adjust"
    /** 기본값 — 밝기 -10%, 채도 -10%: 사진을 살짝 가라앉혀 카드·글자가 덜 묻히게(사용자가 정한 값). */
    const val DEFAULT_BRIGHTNESS = -0.1f
    const val DEFAULT_SATURATION = 0.9f
    // 기본값을 바꾸면서 저장 이름도 바꿨습니다(_v2) — 예전에 저장된 0%·100% 가 새 기본값을 덮지 않게.
    private const val KEY_BRIGHTNESS = "brightness_v2"
    private const val KEY_SATURATION = "saturation_v2"

    /** -0.4 ~ +0.4 (화면 밝기에 더하는 양). */
    var brightness by mutableFloatStateOf(DEFAULT_BRIGHTNESS)
        private set
    /** 0 (흑백) ~ 1.6, 1 이 원래 색. */
    var saturation by mutableFloatStateOf(DEFAULT_SATURATION)
        private set

    /** 기본값 그대로인지 — 설정의 '원래대로' 버튼을 보일지. */
    val isDefault: Boolean get() = brightness == DEFAULT_BRIGHTNESS && saturation == DEFAULT_SATURATION

    fun load(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        brightness = p.getFloat(KEY_BRIGHTNESS, DEFAULT_BRIGHTNESS)
        saturation = p.getFloat(KEY_SATURATION, DEFAULT_SATURATION)
    }

    fun set(context: Context, brightness: Float = this.brightness, saturation: Float = this.saturation) {
        this.brightness = brightness
        this.saturation = saturation
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_BRIGHTNESS, brightness).putFloat(KEY_SATURATION, saturation).apply()
    }

    fun reset(context: Context) = set(context, DEFAULT_BRIGHTNESS, DEFAULT_SATURATION)

    /** 지금 값의 색 필터 — 보정이 없으면(0%·100%) null(필터 없이 원래 색). */
    fun colorFilter(): androidx.compose.ui.graphics.ColorFilter? = filterFor(brightness, saturation)

    /** [brightness]·[saturation] 의 색 필터 — 자르기 화면의 미리보기도 같은 식을 씁니다. */
    fun filterFor(brightness: Float, saturation: Float): androidx.compose.ui.graphics.ColorFilter? {
        if (brightness == 0f && saturation == 1f) return null
        val m = androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(saturation) }
        val offset = brightness * 255f
        m.values[4] += offset
        m.values[9] += offset
        m.values[14] += offset
        return androidx.compose.ui.graphics.ColorFilter.colorMatrix(m)
    }
}
