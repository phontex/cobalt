package com.cobalt.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs

object SoftShape {
    val page = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 22.dp, bottomEnd = 22.dp)
    val card = RoundedCornerShape(16.dp)
    val inner = RoundedCornerShape(12.dp)
    val control = RoundedCornerShape(11.dp)
    val pill = RoundedCornerShape(percent = 50)
}

val LocalBarInset = staticCompositionLocalOf { 96.dp }

fun breakableId(value: String): String = value.replace(".", ".\u200B")

enum class CardTone { RAISED, FLAT, ACCENT }

@Composable
private fun press(pressed: Boolean, to: Float = .985f): Float =
    animateFloatAsState(if (pressed) to else 1f, tween(120), label = "press").value

@Composable
private fun ownInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }

private fun blend(base: Color, overlay: Color, fraction: Float): Color = Color(
    red = base.red + (overlay.red - base.red) * fraction,
    green = base.green + (overlay.green - base.green) * fraction,
    blue = base.blue + (overlay.blue - base.blue) * fraction,
    alpha = base.alpha,
)

private fun Color.faded(): Color = copy(alpha = 0f)

fun Modifier.bookPageTurn(pageOffset: Float): Modifier = this
    .zIndex(if (pageOffset >= 0f) 1f else 0f)
    .graphicsLayer {
        transformOrigin = TransformOrigin(0f, 0.5f)
        cameraDistance = 18f * density
        val clamped = pageOffset.coerceIn(-1f, 1f)
        translationX = clamped * size.width
        if (clamped > 0f) {
            rotationY = -clamped * 94f
            alpha = if (clamped > 0.78f) ((1f - clamped) / 0.22f).coerceIn(0f, 1f) else 1f
        } else if (clamped < 0f) {
            val reveal = 1f - abs(clamped)
            val s = 0.965f + reveal * 0.035f
            scaleX = s
            scaleY = s
        }
    }
    .drawWithContent {
        drawContent()
        val clamped = pageOffset.coerceIn(-1f, 1f)
        if (clamped > 0f) {
            val foldAlpha = (clamped * 0.28f).coerceIn(0f, 0.28f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = foldAlpha * 0.45f),
                        Color.Transparent,
                        Color.Black.copy(alpha = foldAlpha),
                    ),
                ),
            )
        } else if (clamped < 0f) {
            val castAlpha = (abs(clamped) * 0.36f).coerceIn(0f, 0.36f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = castAlpha),
                        Color.Black.copy(alpha = castAlpha * 0.35f),
                        Color.Transparent,
                    ),
                ),
            )
        }
    }

@Composable
fun BookSheet(
    modifier: Modifier = Modifier,
    pageOffset: Float = 0f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalDesign.current
    val spineShade = if (c.dark) Color.Black.copy(alpha = 0.42f) else Color(0xFF1A1712).copy(alpha = 0.08f)
    val stackEdge = blend(c.surface, c.line, 0.45f)

    Box(
        modifier
            .fillMaxSize()
            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)
            .bookPageTurn(pageOffset),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 6.dp, top = 5.dp, bottom = 5.dp)
                .offset(x = 5.dp)
                .clip(SoftShape.page)
                .background(stackEdge)
                .border(1.dp, c.line, SoftShape.page),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 3.dp, top = 2.dp, bottom = 2.dp)
                .offset(x = 2.5.dp)
                .clip(SoftShape.page)
                .background(blend(c.surface, c.line, 0.22f))
                .border(1.dp, c.line, SoftShape.page),
        )
        Box(
            Modifier
                .fillMaxSize()
                .shadow(
                    elevation = if (c.dark) 0.dp else 10.dp,
                    shape = SoftShape.page,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.12f),
                )
                .clip(SoftShape.page)
                .background(c.surface)
                .border(1.dp, c.line, SoftShape.page)
                .drawWithContent {
                    drawContent()
                    val spineWidth = 18.dp.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(spineShade, Color.Transparent),
                            startX = 0f,
                            endX = spineWidth,
                        ),
                        size = Size(spineWidth, size.height),
                    )
                    val stitchX = 9.dp.toPx()
                    drawLine(
                        color = c.line.copy(alpha = if (c.dark) 0.65f else 0.9f),
                        start = Offset(stitchX, 0f),
                        end = Offset(stitchX, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                content = content,
            )
        }
    }
}

@Composable
fun BookRunningHeader(
    chapter: String,
    pageNumber: Int,
    totalPages: Int,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalDesign.current
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                SoftIconButton(onClick = onBack)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                chapter.uppercase(),
                style = CobaltType.label,
                color = c.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                trailing()
                Spacer(Modifier.width(10.dp))
            }
            Text(
                "Стр. %02d / %02d".format(pageNumber, totalPages),
                style = CobaltType.monoSmall,
                color = c.muted,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(0.6.dp).background(c.line.copy(alpha = 0.55f)))
    }
}

@Composable
fun BookRibbonBar(
    chapters: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val c = LocalDesign.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(if (c.dark) 0.dp else 8.dp, shape, ambientColor = Color.Black.copy(alpha = .05f), spotColor = Color.Black.copy(alpha = .08f))
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BookFlipArrow(
            text = "‹",
            enabled = selected > 0,
            onClick = { if (selected > 0) onSelect(selected - 1) },
        )
        chapters.forEachIndexed { index, label ->
            val active = index == selected
            val interaction = ownInteraction()
            val pressed by interaction.collectIsPressedAsState()
            val tone by animateColorAsState(
                when {
                    active -> c.accentSurface
                    pressed -> c.well
                    else -> c.accentSurface.faded()
                },
                tween(150),
                label = "ribbonTone",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(SoftShape.control)
                    .background(tone)
                    .then(if (active) Modifier.border(1.dp, c.accent.copy(alpha = if (c.dark) .4f else .22f), SoftShape.control) else Modifier)
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(index) },
                    )
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                val parts = label.split(" · ", limit = 2)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (parts.size == 2) {
                        Text(
                            parts[0],
                            style = CobaltType.label.copy(fontSize = 10.sp, lineHeight = 12.sp),
                            color = if (active) c.accent else c.faint,
                            maxLines = 1,
                        )
                        Text(
                            parts[1],
                            style = CobaltType.micro.copy(
                                fontSize = 10.5.sp,
                                lineHeight = 13.sp,
                                fontWeight = if (active) FontWeight.W600 else FontWeight.W500,
                            ),
                            color = if (active) c.accent else c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            label,
                            style = CobaltType.micro.copy(fontWeight = if (active) FontWeight.W600 else FontWeight.W500),
                            color = if (active) c.accent else c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        BookFlipArrow(
            text = "›",
            enabled = selected < chapters.lastIndex,
            onClick = { if (selected < chapters.lastIndex) onSelect(selected + 1) },
        )
    }
}

@Composable
private fun BookFlipArrow(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalDesign.current
    val interaction = ownInteraction()
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(34.dp, 38.dp)
            .clip(SoftShape.control)
            .background(if (pressed && enabled) c.well else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = CobaltType.title.copy(fontSize = 18.sp),
            color = if (enabled) c.text else c.line,
        )
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.RAISED,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(SoftSpace.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalDesign.current
    val interaction = ownInteraction()
    val pressed by interaction.collectIsPressedAsState()
    val scale = press(pressed, .992f)
    val background by animateColorAsState(
        when (tone) {
            CardTone.RAISED -> if (pressed && onClick != null) blend(c.well, c.accent, if (c.dark) .08f else .04f) else c.well
            CardTone.FLAT -> c.well
            CardTone.ACCENT -> c.accentSurface
        },
        tween(140),
        label = "cardBg",
    )
    val edge = if (tone == CardTone.ACCENT) c.accent.copy(alpha = if (c.dark) .28f else .18f) else c.line
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(SoftShape.card)
            .background(background)
            .border(1.dp, edge, SoftShape.card)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick,
                ) else Modifier,
            )
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun SoftGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalDesign.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(SoftShape.card)
            .background(c.well)
            .border(1.dp, c.line, SoftShape.card),
        content = content,
    )
}

@Composable
fun SoftPageTitle(title: String, subtitle: String? = null, eyebrow: String? = null) {
    val c = LocalDesign.current
    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)) {
        if (eyebrow != null) {
            Text(eyebrow.uppercase(), style = CobaltType.label, color = c.faint, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(title, style = CobaltType.display, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(subtitle, style = CobaltType.caption, color = c.muted, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
fun SoftIconButton(onClick: () -> Unit, icon: Int = R.drawable.ic_cobalt_back) {
    val c = LocalDesign.current
    val interaction = ownInteraction()
    val pressed by interaction.collectIsPressedAsState()
    val scale = press(pressed, .92f)
    val fill by animateColorAsState(if (pressed) c.well else c.well.faded(), tween(120), label = "iconBg")
    Box(
        Modifier
            .size(38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(SoftShape.control)
            .background(fill)
            .border(1.dp, c.line, SoftShape.control)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ImageVector.vectorResource(icon), null, tint = c.text, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun SoftAction(
    text: String,
    primary: Boolean = true,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = LocalDesign.current
    val interaction = ownInteraction()
    val pressed by interaction.collectIsPressedAsState()
    val scale = press(pressed, .975f)
    val background by animateColorAsState(
        when {
            !enabled -> if (c.dark) Color(0x0FFFFFFF) else c.well
            primary -> if (pressed) blend(c.accent, Color.Black, .12f) else c.accent
            else -> if (pressed) c.accentSurface else c.accentSurface.faded()
        },
        tween(140),
        label = "actionBg",
    )
    Box(
        modifier
            .height(42.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(SoftShape.control)
            .background(background)
            .then(if (!primary && enabled) Modifier.border(1.dp, c.line, SoftShape.control) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = CobaltType.button.copy(fontSize = 14.sp),
            color = when {
                !enabled -> c.faint
                primary -> c.onAccent
                else -> c.accent
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
fun SoftSectionTitle(text: String) {
    val c = LocalDesign.current
    Row(
        Modifier.fillMaxWidth().padding(top = SoftSpace.section, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("§", style = CobaltType.label, color = c.accent, modifier = Modifier.padding(end = 6.dp))
        Text(text.uppercase(), style = CobaltType.label, color = c.faint, maxLines = 1)
        Box(Modifier.weight(1f).padding(start = 10.dp).height(1.dp).background(c.line))
    }
}

@Composable
fun SoftDivider(start: Dp = SoftSpace.card, end: Dp = SoftSpace.card) {
    Box(
        Modifier
            .padding(start = start, end = end)
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalDesign.current.line),
    )
}

@Composable
fun SoftRow(
    title: String,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: String? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalDesign.current
    val interaction = ownInteraction()
    val pressed by interaction.collectIsPressedAsState()
    val rowTint = blend(c.well, c.accent, if (c.dark) .08f else .04f)
    val fill by animateColorAsState(
        if (pressed && onClick != null) rowTint else rowTint.faded(),
        tween(120),
        label = "rowBg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(fill)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick,
                ) else Modifier,
            )
            .heightIn(min = if (summary == null) 52.dp else 64.dp)
            .padding(horizontal = SoftSpace.card, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = CobaltType.headline, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (summary != null) {
                Text(
                    summary,
                    style = CobaltType.caption,
                    color = c.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (trailing != null) {
            Text(trailing, style = CobaltType.micro, color = c.faint, modifier = Modifier.padding(start = 12.dp), maxLines = 1)
        }
        if (showChevron) {
            Icon(
                ImageVector.vectorResource(R.drawable.ic_phosphor_chevron), null,
                tint = c.faint, modifier = Modifier.padding(start = 10.dp).size(14.dp),
            )
        }
    }
}

@Composable
fun CobaltSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val c = LocalDesign.current
    val trackWidth = 50.dp
    val trackHeight = 30.dp
    val thumbSize = 24.dp
    val inset = (trackHeight - thumbSize) / 2

    val interaction = ownInteraction()
    val pressed by interaction.collectIsPressedAsState()
    val ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val offset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - inset else inset,
        animationSpec = tween(if (pressed) 120 else 210, easing = ease),
        label = "thumbOffset",
    )
    val thumbScale by animateFloatAsState(if (pressed) .9f else 1f, tween(110), label = "thumbScale")
    val trackColor by animateColorAsState(
        if (checked) c.accent else c.surface,
        tween(180),
        label = "trackColor",
    )
    val thumbColor = if (c.dark) Color(0xFFF5F2EC) else Color.White

    Box(
        modifier
            .size(width = 52.dp, height = 48.dp)
            .then(
                if (interactive) Modifier.toggleable(
                    value = checked,
                    role = Role.Switch,
                    interactionSource = interaction,
                    indication = null,
                    onValueChange = onChange,
                ) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(trackWidth, trackHeight)
                .clip(CircleShape)
                .background(trackColor)
                .border(1.dp, if (checked) Color.Transparent else c.line, CircleShape),
        ) {
            Box(
                Modifier
                    .offset { IntOffset(offset.roundToPx(), inset.roundToPx()) }
                    .size(thumbSize)
                    .graphicsLayer { scaleX = thumbScale; scaleY = thumbScale }
                    .shadow(if (c.dark) 0.dp else 2.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        }
    }
}

@Composable
fun SoftToggleRow(title: String, summary: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalDesign.current
    val interaction = ownInteraction()
    val pressed by interaction.collectIsPressedAsState()
    val rowTint = blend(c.well, c.accent, if (c.dark) .08f else .04f)
    val fill by animateColorAsState(
        if (pressed) rowTint else rowTint.faded(),
        tween(120),
        label = "toggleRowBg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(fill)
            .toggleable(
                value = checked,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null,
                onValueChange = onChange,
            )
            .heightIn(min = if (summary == null) 52.dp else 64.dp)
            .padding(start = SoftSpace.card, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(title, style = CobaltType.headline, color = c.text)
            if (summary != null) {
                Text(summary, style = CobaltType.caption, color = c.muted, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        CobaltSwitch(checked = checked, onChange = onChange, interactive = false)
    }
}

@Composable
fun SoftChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalDesign.current
    val interaction = ownInteraction()
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        when {
            selected -> if (pressed) blend(c.accentSurface, c.accent, .10f) else c.accentSurface
            pressed -> c.surface
            else -> c.surface
        },
        tween(150),
        label = "chipBg",
    )
    val edge by animateColorAsState(
        if (selected) c.accent.copy(alpha = if (c.dark) .5f else .35f) else c.line,
        tween(150),
        label = "chipEdge",
    )
    Box(
        modifier
            .height(38.dp)
            .clip(SoftShape.control)
            .background(bg)
            .border(1.dp, edge, SoftShape.control)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = CobaltType.caption.copy(fontWeight = if (selected) FontWeight.W600 else FontWeight.W400),
            color = if (selected) c.accent else c.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoftChoiceGroup(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onChange: (Int) -> Unit,
) {
    val c = LocalDesign.current
    Column {
        Text(title, style = CobaltType.bodyStrong, color = c.text)
        FlowRow(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { index, label ->
                SoftChip(label, index == selectedIndex) { onChange(index) }
            }
        }
    }
}

@Composable
fun SoftSegmented(
    labels: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    val c = LocalDesign.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(SoftShape.control)
            .background(c.surface)
            .border(1.dp, c.line, SoftShape.control)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selectedIndex
            val interaction = ownInteraction()
            val pressed by interaction.collectIsPressedAsState()
            val bg by animateColorAsState(
                when {
                    active -> c.accentSurface
                    pressed -> c.well
                    else -> c.accentSurface.faded()
                },
                tween(150),
                label = "segBg",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(SoftShape.control)
                    .background(bg)
                    .then(if (active) Modifier.border(1.dp, c.accent.copy(alpha = if (c.dark) .4f else .24f), SoftShape.control) else Modifier)
                    .selectable(
                        selected = active,
                        role = Role.RadioButton,
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onChange(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = CobaltType.caption.copy(fontWeight = if (active) FontWeight.W600 else FontWeight.W400),
                    color = if (active) c.accent else c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun SoftWell(contentPadding: PaddingValues = PaddingValues(13.dp), content: @Composable ColumnScope.() -> Unit) {
    val c = LocalDesign.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SoftShape.inner)
            .background(c.surface)
            .border(1.dp, c.line, SoftShape.inner)
            .padding(contentPadding),
        content = content,
    )
}

enum class SoftTone { POSITIVE, NEUTRAL, WARNING, NEGATIVE }

@Composable
fun SoftTone.color(c: DesignColors): Color = when (this) {
    SoftTone.POSITIVE -> c.success
    SoftTone.NEUTRAL -> c.muted
    SoftTone.WARNING -> c.warning
    SoftTone.NEGATIVE -> c.danger
}

@Composable
fun SoftBadge(tone: SoftTone, text: String) {
    val c = LocalDesign.current
    val tint = tone.color(c)
    Row(
        Modifier
            .clip(SoftShape.pill)
            .background(tint.copy(alpha = if (c.dark) .20f else .10f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(tint))
        Text(text, style = CobaltType.micro, color = tint, modifier = Modifier.padding(start = 5.dp), maxLines = 1)
    }
}

@Composable
fun SoftStatusLine(label: String, value: String, tone: SoftTone) {
    val c = LocalDesign.current
    val tint = tone.color(c)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = CobaltType.body, color = c.text, modifier = Modifier.weight(1f))
        Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
        Text(
            value,
            style = CobaltType.micro,
            color = if (tone == SoftTone.NEUTRAL) c.muted else tint,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
fun SoftSearchField(state: TextFieldState, placeholder: String = "Поиск") {
    val c = LocalDesign.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(SoftShape.control)
            .background(c.well)
            .border(1.dp, c.line, SoftShape.control)
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ImageVector.vectorResource(R.drawable.ic_phosphor_search), null, tint = c.faint, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (state.text.isEmpty()) Text(placeholder, style = CobaltType.body, color = c.faint, maxLines = 1)
            BasicTextField(
                state = state,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
                textStyle = CobaltType.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        if (state.text.isNotEmpty()) {
            val interaction = ownInteraction()
            val pressed by interaction.collectIsPressedAsState()
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (pressed) c.surface else Color.Transparent)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                        state.clearText()
                    }
                    .semantics { contentDescription = "Очистить" },
                contentAlignment = Alignment.Center,
            ) {
                Text("\u00D7", style = CobaltType.title.copy(fontWeight = FontWeight.W400), color = c.muted)
            }
        }
    }
}

@Composable
fun SoftField(value: String, onValueChange: (String) -> Unit, label: String, minLines: Int = 1, limit: Int = 4096) {
    val c = LocalDesign.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SoftShape.inner)
            .background(c.well)
            .border(1.dp, c.line, SoftShape.inner)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label.uppercase(), style = CobaltType.label, color = c.faint)
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= limit) onValueChange(it) },
            textStyle = CobaltType.body.copy(color = c.text),
            cursorBrush = SolidColor(c.accent),
            minLines = minLines,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

@Composable
fun SoftTextAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalDesign.current
    val interaction = ownInteraction()
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(SoftShape.control)
            .background(if (pressed && enabled) c.well else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = CobaltType.micro, color = if (enabled) c.accent else c.faint, maxLines = 1)
    }
}

@Composable
fun SoftHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    val c = LocalDesign.current
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            SoftIconButton(onClick = onBack)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = CobaltType.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = CobaltType.caption, color = c.muted, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
fun SoftStatusDot(tone: SoftTone, size: Dp = 8.dp) {
    val c = LocalDesign.current
    Box(Modifier.size(size).clip(CircleShape).background(tone.color(c)))
}

@Composable
fun SoftNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = CobaltType.caption,
        color = LocalDesign.current.faint,
        modifier = modifier.fillMaxWidth().padding(top = 10.dp),
    )
}

@Composable
fun SoftKeyValue(label: String, value: String, mono: Boolean = false) {
    val c = LocalDesign.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = CobaltType.caption, color = c.muted, modifier = Modifier.weight(1f))
        Text(
            value,
            style = if (mono) CobaltType.mono else CobaltType.caption.copy(fontWeight = FontWeight.W500),
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SoftEmptyState(title: String, text: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    val c = LocalDesign.current
    SoftCard {
        Text(title, style = CobaltType.headline, color = c.text)
        Text(text, style = CobaltType.caption, color = c.muted, modifier = Modifier.padding(top = 6.dp))
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                SoftTextAction(actionText) { onAction() }
            }
        }
    }
}
