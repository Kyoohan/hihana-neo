package com.yhjang.timetable.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
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

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).exists()

    /** 파일이 바뀔 때마다 달라지는 값 — 이걸 키로 비트맵을 다시 읽습니다. */
    fun version(context: Context): Long = file(context).takeIf { it.exists() }?.lastModified() ?: 0L

    /** 사진 선택기에서 받은 [uri] 를 줄여서 저장합니다. 실패하면 false. */
    suspend fun save(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            // 1) 크기만 읽어 축소 비율을 정하고, 2) 그 비율로 디코드합니다 — 수천 px 사진도 메모리를 넘기지 않습니다.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return@runCatching false
            var sample = 1
            while (longest / (sample * 2) >= MAX_EDGE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@runCatching false
            val scale = MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height)
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            } else {
                decoded
            }
            val target = file(context)
            val tmp = File(target.parentFile, "$FILE_NAME.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (bitmap !== decoded) bitmap.recycle()
            decoded.recycle()
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
