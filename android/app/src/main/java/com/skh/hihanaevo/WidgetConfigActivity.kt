package com.skh.hihanaevo

import kotlin.math.roundToInt
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.skh.hihanaevo.ui.OneUi
import com.skh.hihanaevo.ui.OneUiButton
import com.skh.hihanaevo.ui.OneUiCard
import com.skh.hihanaevo.ui.OneUiChip
import com.skh.hihanaevo.ui.OneUiSlider
import com.skh.hihanaevo.ui.HiHanaEvoTheme
import com.skh.hihanaevo.ui.oneUiPageBackground
import com.skh.hihanaevo.widget.HiHanaEvoWidget
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            val scope = rememberCoroutineScope()
            HiHanaEvoTheme {
                WidgetConfigScreen(
                    onConfirm = { opacity, theme, accent ->
                        scope.launch {
                            PlanStore.setHomeWidgetOpacity(applicationContext, opacity)
                            PlanStore.setHomeWidgetTheme(applicationContext, theme)
                            PlanStore.setHomeWidgetAccent(applicationContext, accent)
                            HiHanaEvoWidget().updateAll(applicationContext)

                            val resultValue = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(RESULT_OK, resultValue)
                            finish()
                        }
                    }
                )
            }
        }
    }
}

/** 슬라이더 값을 5% 단위로 — 반올림해야 80 이 79.99… 에서 79 로 잘리지 않습니다. */
internal fun widgetOpacityStep(value: Float): Int =
    (value / PlanStore.WIDGET_OPACITY_STEP).roundToInt() * PlanStore.WIDGET_OPACITY_STEP

/** 10~100% 를 5% 간격으로 — 사이 칸 수(끝 둘 제외). */
internal const val WIDGET_OPACITY_SLIDER_STEPS = (100 - 10) / PlanStore.WIDGET_OPACITY_STEP - 1

@Composable
fun WidgetConfigScreen(onConfirm: (Int, String, String) -> Unit) {
    val context = LocalContext.current
    var opacity by remember { mutableFloatStateOf(PlanStore.DEFAULT_WIDGET_OPACITY.toFloat()) }
    var theme by remember { mutableStateOf(PlanStore.THEME_SYSTEM) }
    var accent by remember { mutableStateOf(PlanStore.WIDGET_ACCENT_KIND) }

    LaunchedEffect(Unit) {
        opacity = PlanStore.homeWidgetOpacity(context).toFloat()
        theme = PlanStore.homeWidgetTheme(context)
        accent = PlanStore.homeWidgetAccent(context)
    }

    val darkPreview = when (theme) {
        PlanStore.THEME_DARK -> true
        PlanStore.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    // One UI "Enlarged header" 처럼 가운데 큰 제목 + 회색 설명, 그 아래 카드 두 장(미리보기 / 설정)입니다.
    Scaffold(modifier = Modifier.oneUiPageBackground(), containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = OneUi.PagePadding, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "위젯 설정",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "위젯의 테마·강조 색·배경 불투명도를 조절할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))

            // 실제 배경 위에 얹 모습을 내낸 미리보기
            val alphaVal = opacity / 100f
            val wallpaperColor = if (darkPreview) Color(0xFF202737) else Color(0xFFB9C7DE)
            val cardBase = if (darkPreview) Color(0xFF0F172A) else Color(0xFFFFFFFF)
            val previewTitle = if (darkPreview) Color(0xFFFFFFFF) else Color(0xFF0F172A)
            val previewSub = if (darkPreview) Color(0xFFE2E8F0) else Color(0xFF475569)
            // 미리보기의 강조 글자 — 종류별(수업 색) / 앱 강조 색 / 글자색과 같게
            val previewAccent = when (accent) {
                PlanStore.WIDGET_ACCENT_APP -> MaterialTheme.colorScheme.primary
                PlanStore.WIDGET_ACCENT_TEXT -> previewTitle
                else -> if (darkPreview) Color(0xFF4C9EF5) else Color(0xFF0072DE)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(OneUi.CornerLarge))
                    .background(wallpaperColor),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBase.copy(alpha = alphaVal))
                        .padding(14.dp),
                ) {
                    Text("1교시 · 수업 중", fontSize = 12.sp, color = previewAccent)
                    Spacer(Modifier.height(8.dp))
                    Text("수학 I", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = previewTitle)
                    Text("3층 302호", fontSize = 13.sp, color = previewSub)
                    Spacer(Modifier.weight(1f))
                    Text("09:00 ~ 09:50", fontSize = 11.sp, color = previewSub)
                }
            }

            Spacer(Modifier.height(16.dp))

            OneUiCard(modifier = Modifier.fillMaxWidth()) {
                Text("테마", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanStore.themes.forEach { option ->
                        OneUiChip(
                            selected = theme == option,
                            onClick = { theme = option },
                            label = PlanStore.themeLabel(option),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text("강조 색", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanStore.widgetAccents.forEach { option ->
                        OneUiChip(
                            selected = accent == option,
                            onClick = { accent = option },
                            label = PlanStore.widgetAccentLabel(option),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("배경 불투명도", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Text("${widgetOpacityStep(opacity)}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(4.dp))
                OneUiSlider(
                    value = opacity,
                    onValueChange = { opacity = widgetOpacityStep(it).toFloat() },
                    valueRange = 10f..100f,
                    steps = WIDGET_OPACITY_SLIDER_STEPS,
                )
            }

            Spacer(Modifier.weight(1f))

            OneUiButton(
                text = "설정 완료",
                onClick = { onConfirm(widgetOpacityStep(opacity), theme, accent) },
                modifier = Modifier.fillMaxWidth(),
                leading = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.width(18.dp)) },
            )
        }
    }
}
