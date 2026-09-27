package io.github.hyh1627723044.shortvideokws.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyh1627723044.shortvideokws.IntentMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlScreen(
    service: ServiceSnapshot,
    settings: SettingsSnapshot,
    onToggle: () -> Unit,
    onAccessibility: () -> Unit,
    onOpenSettings: () -> Unit,
    onPrefix: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("小刷", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                Text("说一句，轻松刷", fontSize = 14.sp, color = Palette.Muted)
            }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "设置", tint = Palette.Text) }
        }
        Spacer(Modifier.height(14.dp))
        AccessibilityBar(service.accessibility, onAccessibility)
        Spacer(Modifier.height(12.dp))
        Hero(service, onToggle)

        SectionLabel("识别与判断")
        AppCard {
            SettingRow(Icons.Outlined.GraphicEq, "语音识别", value = if (settings.cloud) "字节云 ASR" else "本地关键词", onClick = onOpenSettings)
            RowDivider()
            SettingRow(
                Icons.Outlined.AccountTree, "行为判断",
                value = when {
                    !settings.cloud -> "固定口令"
                    settings.intent == IntentMode.JEV -> "JEV 智能判断"
                    else -> "严格口令"
                },
                onClick = onOpenSettings,
            )
        }
        Spacer(Modifier.height(12.dp))
        AppCard {
            SettingRow(
                Icons.Outlined.RecordVoiceOver, "使用「小刷 + 口令」", subtitle = "连续说出，无需停顿",
                trailing = {
                    Switch(
                        settings.prefix, onPrefix, enabled = !service.busy,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Palette.Primary, checkedThumbColor = Color.White,
                            uncheckedTrackColor = Palette.Track, uncheckedBorderColor = Palette.Border, uncheckedThumbColor = Palette.Muted,
                        ),
                    )
                },
            )
        }

        SectionLabel("试着这样说")
        AppCard {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                examplePhrases(settings).forEach { Chip(it) }
            }
            Text("说「停止控制」可随时停止", Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                fontSize = 12.sp, color = Palette.Muted, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AccessibilityBar(connected: Boolean, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (connected) Palette.Surface else Palette.WarningBg,
        border = BorderStroke(1.dp, if (connected) Palette.Border else Palette.WarningBg),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (connected) Icons.Outlined.AccessibilityNew else Icons.Outlined.WarningAmber, null,
                Modifier.size(20.dp), tint = if (connected) Palette.Primary else Palette.Warning)
            Spacer(Modifier.width(10.dp))
            Text(if (connected) "无障碍已连接" else "无障碍服务未开启", Modifier.weight(1f),
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (connected) Palette.Text else Palette.Warning)
            Text(if (connected) "查看" else "去开启", fontSize = 13.sp, color = if (connected) Palette.Muted else Palette.Warning)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = if (connected) Palette.Muted else Palette.Warning)
        }
    }
}

@Composable
private fun Hero(service: ServiceSnapshot, onToggle: () -> Unit) {
    val hero = heroState(service)
    val transition = rememberInfiniteTransition(label = "hero")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "pulse")
    val blink by transition.animateFloat(1f, .35f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "blink")
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Palette.HeroTint) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Pill(hero.badge, hero.tone, dot = true, dotAlpha = if (service.listening) blink else 1f)
            Spacer(Modifier.height(18.dp))
            MicOrb(service.listening, pulse, onToggle)
            Spacer(Modifier.height(16.dp))
            Text(hero.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(hero.subtitle, fontSize = 13.sp, color = if (hero.tone == Tone.DANGER) Palette.Danger else Palette.Muted,
                textAlign = TextAlign.Center, maxLines = 3)
            Spacer(Modifier.height(20.dp))
            if (service.busy) OutlineButton("停止监听", Modifier.fillMaxWidth(), Icons.Filled.Stop, onClick = onToggle)
            else PrimaryButton("开始监听", Modifier.fillMaxWidth(), Icons.Filled.Mic, onClick = onToggle)
        }
    }
}

@Composable
private fun MicOrb(active: Boolean, pulse: Float, onClick: () -> Unit) {
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        val ring = Palette.Primary
        Box(Modifier.size(150.dp).scale(if (active) .9f + pulse * .12f else 1f).clip(CircleShape)
            .background(ring.copy(alpha = if (active) .14f * (1f - pulse) + .03f else .06f)))
        Box(Modifier.size(118.dp).clip(CircleShape).background(ring.copy(alpha = .10f)))
        Box(
            Modifier.size(86.dp).clip(CircleShape).background(Palette.Primary).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(if (active) Icons.Filled.GraphicEq else Icons.Filled.Mic, if (active) "停止监听" else "开始监听", Modifier.size(38.dp), tint = Color.White) }
    }
}
