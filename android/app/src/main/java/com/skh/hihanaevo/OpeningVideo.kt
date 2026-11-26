package com.skh.hihanaevo

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlin.math.max

/**
 * 첫 실행 오프닝 영상(캠퍼스 위를 나는 리본이 로고가 되는 장면) — 앱 용량을 늘리지 않으려고 앱에 넣지 않고, 요약 서버에서
 * 스트리밍으로 재생합니다. 기기에는 저장하지 않습니다. 개인 정보는 보내지 않습니다. iOS OpeningVideo.swift 와 같은 동작입니다.
 *
 * - 나오는 때: 첫 실행 투어 맨 앞(새로 설치·Evo 개편 투어), 정보 화면 로고 다섯 번(이스터에그).
 * - 시작될 때까지 로딩 표시를 띄우고, [START_MS] 안에 시작되지 않으면(인터넷 없음·느림) 예전 코드 애니메이션으로 대신합니다
 *   (2026-11-25 사용자 요청). 데이터 절약 모드면 받지 않고 바로 코드 애니메이션.
 * - 재생 중 [STALL_MS] 넘게 멈추면 영상을 끊고 앱이 그리는 로고로 바로 넘어갑니다.
 */
internal object OpeningVideo {
    /** 영상 파일 이름 — 영상을 바꾸면 이름(버전)도 바꾸고, 서버(summary-worker/assets/opening)에도 같은 이름으로 올립니다. */
    const val NAME = "opening-v1.mp4"
    const val URL = "https://hihana-summary.kyoohan0711ultra.workers.dev/opening/$NAME"

    /** 영상 마지막 프레임의 로고 테두리(영상 화면 비율 0..1, 위가 0) — Blender 카메라로 잰 값(2026-11-24, 마지막 프레임). */
    const val LOGO_CX = 0.4992f
    const val LOGO_CY = 0.4978f
    const val LOGO_W = 0.3795f
    const val LOGO_H = 0.2294f

    /** 재생이 이 안에 시작되지 않으면 예전 코드 애니메이션(정한 값 — 기다리는 동안은 오프닝 바탕 + 로딩 표시). */
    const val START_MS = 3000L
    /** 재생 중 이만큼 넘게 멈추면 영상을 끊고 로고로 넘어감(정한 값). */
    const val STALL_MS = 1500L

    /** 이번에 스트리밍할 주소 — 데이터 절약 모드(종량제 망에서 켜 둔 경우)면 null 로 코드 애니메이션만. */
    fun url(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return URL
        val saver = cm.isActiveNetworkMetered && cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        return if (saver) null else URL
    }

    /** 예전 시험 빌드가 받아 둔 영상 파일을 지웁니다(지금은 저장하지 않음). */
    fun removeOldDownloads(context: Context) {
        val dir = File(context.applicationContext.filesDir, "opening")
        if (dir.exists()) dir.deleteRecursively()
    }
}

/**
 * 영상을 화면에 꽉 차게(가운데 기준, 남는 쪽은 잘림) 스트리밍 재생합니다. 소리는 없습니다.
 * 끝나면 마지막 프레임이 그대로 남아, 그 위에서 앱이 그리는 로고로 바꿔 끼웁니다.
 * [onStart] 는 첫 프레임이 실제로 그려진 때, [onEnd] 는 끝까지 재생했거나 재생 중 [OpeningVideo.STALL_MS] 넘게 멈춘 때.
 */
@Composable
internal fun OpeningVideoLayer(
    url: String,
    alpha: () -> Float,
    onPrepared: (Int, Int) -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onError: () -> Unit,
) {
    val player = remember { MediaPlayer() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(Unit) { onDispose { handler.removeCallbacksAndMessages(null); runCatching { player.release() } } }
    AndroidView(
        factory = { c ->
            TextureView(c).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        try {
                            var started = false
                            player.setSurface(Surface(st))
                            player.setDataSource(url)
                            player.setVolume(0f, 0f)
                            player.setOnPreparedListener { mp ->
                                fitCover(this@apply, mp.videoWidth, mp.videoHeight)
                                onPrepared(mp.videoWidth, mp.videoHeight)
                                mp.start()
                            }
                            player.setOnInfoListener { _, what, _ ->
                                when (what) {
                                    MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> if (!started) { started = true; onStart() }
                                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> if (started) {
                                        handler.removeCallbacksAndMessages(null)
                                        handler.postDelayed({ onEnd() }, OpeningVideo.STALL_MS)
                                    }
                                    MediaPlayer.MEDIA_INFO_BUFFERING_END -> handler.removeCallbacksAndMessages(null)
                                }
                                false
                            }
                            player.setOnCompletionListener { handler.removeCallbacksAndMessages(null); onEnd() }
                            player.setOnErrorListener { _, _, _ -> handler.removeCallbacksAndMessages(null); if (started) onEnd() else onError(); true }
                            player.prepareAsync()
                        } catch (_: Exception) {
                            onError()
                        }
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        runCatching { fitCover(this@apply, player.videoWidth, player.videoHeight) }
                    }
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        },
        modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha() },
    )
}

/** TextureView 는 영상을 뷰 크기로 늘려 그리므로, 비율을 되돌린 뒤 화면을 꽉 채우게 키웁니다(가운데 기준). */
private fun fitCover(v: TextureView, vw: Int, vh: Int) {
    val w = v.width.toFloat(); val h = v.height.toFloat()
    if (w <= 0f || h <= 0f || vw <= 0 || vh <= 0) return
    val s = max(w / vw, h / vh)
    v.setTransform(Matrix().apply { setScale(vw * s / w, vh * s / h, w / 2f, h / 2f) })
}

/** 화면(w×h)에 꽉 차게 그린 영상 속 로고의 자리 — (테두리 가운데 x, y, 로고 1단위의 픽셀) 를 [out] 에. */
internal fun openingVideoLogoPlace(w: Float, h: Float, vw: Int, vh: Int, boundsW: Float, boundsH: Float, out: FloatArray) {
    val s = max(w / vw, h / vh)
    val ox = (w - vw * s) / 2f; val oy = (h - vh * s) / 2f
    out[0] = ox + OpeningVideo.LOGO_CX * vw * s
    out[1] = oy + OpeningVideo.LOGO_CY * vh * s
    out[2] = minOf(OpeningVideo.LOGO_W * vw * s / boundsW, OpeningVideo.LOGO_H * vh * s / boundsH)
}
