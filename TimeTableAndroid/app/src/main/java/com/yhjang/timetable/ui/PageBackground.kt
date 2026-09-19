package com.yhjang.timetable.ui

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
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .fillMaxWidth(),
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
                        onApply(Bitmap.createBitmap(bitmap, x, y, w, h))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
