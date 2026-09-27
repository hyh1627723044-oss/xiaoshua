package io.github.hyh1627723044.shortvideokws.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tone(val fg: Color, val bg: Color) {
    SUCCESS(Palette.Success, Palette.SuccessBg),
    NEUTRAL(Palette.Muted, Palette.Track),
    WARNING(Palette.Warning, Palette.WarningBg),
    DANGER(Palette.Danger, Palette.DangerBg),
    INFO(Palette.Info, Palette.InfoBg),
}

data class Notice(val text: String, val tone: Tone)

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Palette.Surface,
        border = BorderStroke(1.dp, Palette.Border),
    ) { Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), content = content) }
}

@Composable
fun SectionLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
        if (trailing != null) Text(trailing, fontSize = 12.sp, color = Palette.Muted)
    }
}

@Composable
fun Hint(text: String) {
    Text(text, Modifier.padding(start = 4.dp, top = 8.dp, bottom = 10.dp), fontSize = 12.sp, color = Palette.Muted)
}

@Composable
fun Pill(text: String, tone: Tone, dot: Boolean = false, dotAlpha: Float = 1f) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(tone.bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(7.dp).alpha(dotAlpha).clip(CircleShape).background(tone.fg))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = tone.fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ConfiguredPill(configured: Boolean) =
    if (configured) Pill("已配置", Tone.SUCCESS) else Pill("未配置", Tone.NEUTRAL)

@Composable
fun Segmented(options: List<String>, selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(14.dp)).padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .background(if (active) Palette.Primary.copy(alpha = if (enabled) 1f else .45f) else Color.Transparent)
                    .clickable(enabled = enabled && !active) { onSelect(index) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = when { active -> Color.White; enabled -> Palette.Text; else -> Palette.Muted.copy(alpha = .6f) },
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    badge: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else .45f
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(vertical = 12.dp).alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = Palette.Primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
                if (badge != null) { Spacer(Modifier.width(8.dp)); badge() }
            }
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(top = 2.dp))
        }
        if (value != null) Text(value, fontSize = 14.sp, color = Palette.Text, modifier = Modifier.padding(start = 8.dp))
        if (trailing != null) trailing()
        else if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.Muted)
    }
}

@Composable
fun RowDivider() = HorizontalDivider(color = Palette.Border, thickness = 1.dp)

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick, modifier.height(54.dp), enabled = enabled, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Primary, contentColor = Color.White,
            disabledContainerColor = Palette.Primary.copy(alpha = .35f), disabledContentColor = Color.White,
        ),
    ) { ButtonContent(text, icon) }
}

@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick, modifier.height(54.dp), enabled = enabled, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, if (enabled) Palette.Primary else Palette.Border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Palette.Surface, contentColor = Palette.Primary),
    ) { ButtonContent(text, icon) }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun NoticeBanner(notice: Notice, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(notice.tone.bg).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (notice.tone) {
            Tone.SUCCESS -> Icons.Outlined.CheckCircle
            Tone.DANGER -> Icons.Outlined.ErrorOutline
            else -> Icons.Outlined.Info
        }
        Icon(icon, null, Modifier.size(18.dp), tint = notice.tone.fg)
        Spacer(Modifier.width(8.dp))
        Text(notice.text, color = notice.tone.fg, fontSize = 13.sp)
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Palette.Text)
                }
            }
            Text(title, Modifier.weight(1f).offset(x = if (onBack != null) (-8).dp else 0.dp),
                fontSize = if (onBack != null) 22.sp else 28.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            if (action != null) action()
        }
        if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = Palette.Muted,
            modifier = Modifier.padding(start = if (onBack != null) 36.dp else 0.dp))
    }
}

@Composable
fun SecureFooter(text: String = "密钥加密保存在本机") {
    Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Shield, null, Modifier.size(14.dp), tint = Palette.Muted)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = Palette.Muted)
    }
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    enabled: Boolean = true,
    secret: Boolean = false,
    placeholder: String? = null,
    supporting: String? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = Palette.Muted, fontWeight = FontWeight.Medium)
            if (badge != null) { Spacer(Modifier.width(8.dp)); badge() }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value, onValue, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = Palette.Text),
            placeholder = placeholder?.let { { Text(it, fontSize = 14.sp, color = Palette.Muted) } },
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri),
            trailingIcon = if (secret) { {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "隐藏" else "显示", tint = Palette.Muted)
                }
            } } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.Primary, unfocusedBorderColor = Palette.Border,
                cursorColor = Palette.Primary, focusedContainerColor = Palette.Surface, unfocusedContainerColor = Palette.Surface,
            ),
        )
        if (supporting != null) Text(supporting, fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
    }
}

// Compact right-aligned numeric box with an optional unit, as in the advanced settings mockup.
@Composable
fun ValueRow(label: String, value: String, onValue: (String) -> Unit, unit: String? = null, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp, color = Palette.Text)
        ValueBox(value, onValue, unit, enabled)
    }
}

@Composable
fun ValueBox(value: String, onValue: (String) -> Unit, unit: String? = null, enabled: Boolean = true) {
    BasicTextField(
        value, onValue, enabled = enabled, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = TextStyle(fontSize = 15.sp, color = Palette.Text, textAlign = TextAlign.End),
        decorationBox = { inner ->
            Row(
                Modifier.width(if (unit != null) 112.dp else 88.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Surface)
                    .border(1.dp, Palette.Border, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { inner() }
                if (unit != null) { Spacer(Modifier.width(4.dp)); Text(unit, fontSize = 13.sp, color = Palette.Muted) }
            }
        },
    )
}

@Composable
fun Chip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
    ) { Text(text, fontSize = 14.sp, color = Palette.Text) }
}
