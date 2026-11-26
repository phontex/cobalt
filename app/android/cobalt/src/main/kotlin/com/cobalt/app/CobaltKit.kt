package com.cobalt.app

import androidx.compose.ui.unit.round

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Geometry. Every surface is built from these four radii; the radius stays smaller than
 * half the element's smallest side, so nothing turns into a pill by accident.
 */
object SoftShape {
    val card = RoundedCornerShape(22.dp)
    val inner = RoundedCornerShape(16.dp)
    val control = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/**
 * Height the floating navigation bar occupies, including the system navigation inset.
 * Screens pad their scrollable content by this amount, so nothing hides behind the bar.
 */
val LocalBarInset = staticCompositionLocalOf { 104.dp }

/** Long identifiers wrap at dots instead of being clipped at the edge. */
fun breakableId(value: String): String = value.replace(".", ".\u200B")

enum class CardTone { RAISED, FLAT, ACCENT }

@Composable
private fun press(pressed: Boolean, to: Float = .985f): Float =
    animateFloatAsState(if (pressed) to else 1f, tween(120), label = "press").value

/**
 * Shared interaction source for components that draw their own feedback.
 *
 * Every control passes `indication = null`: the theme's default indication is a spotlight
 * highlight that paints outside the element bounds, which made neighbouring rows and tabs
 * flash grey. Feedback here is explicit — a scale, a tint or a thumb shift.
 */
@Composable
private fun ownInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }

/**
 * A card is a tonal surface with a hairline outline; depth comes from lightness, not from
 * a shadow, so the same card reads correctly on ivory, graphite and true black.
 */
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
            CardTone.RAISED -> if (pressed && onClick != null) blend(c.surface, c.accent, if (c.dark) .06f else .03f) else c.surface
            CardTone.FLAT -> c.well
            CardTone.ACCENT -> c.accentSurface
        },
        tween(140),
        label = "cardBg",
    )
    val edge = if (tone == CardTone.ACCENT) c.accent.copy(alpha = if (c.dark) .26f else .16f) else c.line
    Column(
        modifier.fillMaxWidth()
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

/** Mixes [overlay] into [base] by [fraction]; used for press tints. */
private fun blend(base: Color, overlay: Color, fraction: Float): Color = Color(
    red = base.red + (overlay.red - base.red) * fraction,
    green = base.green + (overlay.green - base.green) * fraction,
    blue = base.blue + (overlay.blue - base.blue) * fraction,
    alpha = base.alpha,
)

/** A list block: one surface, rows separated by hairlines. */
@Composable
fun SoftGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalDesign.current
    Column(
        modifier.fillMaxWidth().clip(SoftShape.card).background(c.surface)
            .border(1.dp, c.line, SoftShape.card),
        content = content,
    )
}

/** Page header: eyebrow, large title, one line of support. Left aligned. */
@Composable
fun SoftPageTitle(title: String, subtitle: String? = null, eyebrow: String? = null) {
    val c = LocalDesign.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        if (eyebrow != null) {
            Text(eyebrow.uppercase(), style = CobaltType.label, color = c.faint, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(title, style = CobaltType.display, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(subtitle, style = CobaltType.caption, color = c.muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/**
 * Fading a tint towards [Color.Transparent] is not the same as fading it out: `Transparent`
 * is `Color(0x00000000)`, so a plain sRGB interpolation walks the colour through black and
 * the element flashes dark for the length of the animation. Every animated tint below fades
 * towards the same hue with zero alpha instead, which interpolates cleanly.
 */
private fun Color.faded(): Color = copy(alpha = 0f)

@Composable
fun SoftIconButton(onClick: () -> Unit, icon: Int = R.drawable.ic_cobalt_back) {
    val c = LocalDesign.current
    val interaction = ownInteraction()
    val pressed by interaction.collectIsPressedAsState()
    val scale = press(pressed, .92f)
    val fill by animateColorAsState(if (pressed) c.well else c.well.faded(), tween(120), label = "iconBg")
    Box(
        Modifier.size(42.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(SoftShape.control).background(fill).border(1.dp, c.line, SoftShape.control)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ImageVector.vectorResource(icon), null, tint = c.text, modifier = Modifier.size(17.dp))
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
        modifier.height(44.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(SoftShape.control).background(background)
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
            modifier = Modifier.padding(horizontal = 18.dp),
        )
    }
}

/** Section eyebrow followed by a hairline that runs to the edge of the column. */
@Composable
fun SoftSectionTitle(text: String) {
    val c = LocalDesign.current
    Row(
        Modifier.fillMaxWidth().padding(top = SoftSpace.section, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = CobaltType.label, color = c.faint, maxLines = 1)
        Box(Modifier.weight(1f).padding(start = 12.dp).height(1.dp).background(c.line))
    }
}

@Composable
fun SoftDivider(start: Dp = SoftSpace.card, end: Dp = SoftSpace.card) {
    if (LocalHideDividers.current) return
    Box(
        Modifier.padding(start = start, end = end).fillMaxWidth().height(1.dp)
            .background(LocalDesign.current.line),
    )
}

/**
 * List row. Press feedback is a tint confined to the row itself, so a tap never flashes
 * the rows around it.
 */
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
    val rowTint = blend(c.surface, c.accent, if (c.dark) .08f else .04f)
    val fill by animateColorAsState(
        if (pressed && onClick != null) rowTint else rowTint.faded(),
        tween(120),
        label = "rowBg",
    )
    Row(
        Modifier.fillMaxWidth()
            .background(fill)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick,
                ) else Modifier,
            )
            .heightIn(min = if (summary == null) 54.dp else 66.dp)
            .padding(horizontal = SoftSpace.card, vertical = 12.dp),
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

/**
 * Cobalt switch. Two variants share one geometry so the setting keeps its meaning while the
 * control stays native to Cobalt:
 *
 * - ColorOS: filled track with a hairline outline and a compact thumb.
 * - iOS: solid track without an outline and a larger thumb that sits closer to the edge.
 *
 * The thumb travels with a single eased animation, the track colour crossfades, and the
 * thumb dips slightly under the finger. The touch target is 52 × 48 dp in both variants.
 */
@Composable
fun CobaltSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val c = LocalDesign.current
    val ios = LocalIosStyle.current
    val trackWidth = 50.dp
    val trackHeight = 30.dp
    val thumbSize = if (ios) 26.dp else 24.dp
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
        when {
            checked -> c.accent
            ios -> c.line
            else -> c.well
        },
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
            Modifier.size(trackWidth, trackHeight).clip(CircleShape).background(trackColor)
                .then(
                    if (!ios && !checked) Modifier.border(1.dp, c.line, CircleShape)
                    else if (!ios && checked) Modifier.border(1.dp, Color.Transparent, CircleShape)
                    else Modifier,
                ),
        ) {
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(offset.roundToPx(), inset.roundToPx()) }
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
    val rowTint = blend(c.surface, c.accent, if (c.dark) .08f else .04f)
    val fill by animateColorAsState(
        if (pressed) rowTint else rowTint.faded(),
        tween(120),
        label = "toggleRowBg",
    )
    Row(
        Modifier.fillMaxWidth().background(fill)
            .toggleable(
                value = checked,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null,
                onValueChange = onChange,
            )
            .heightIn(min = if (summary == null) 54.dp else 66.dp)
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
        // The row owns the gesture, so the switch itself only paints state.
        CobaltSwitch(checked = checked, onChange = onChange, interactive = false)
    }
}

/** Chip used inside a group of mutually exclusive values. */
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
            pressed -> c.well
            else -> c.accentSurface.faded()
        },
        tween(150),
        label = "chipBg",
    )
    val edge by animateColorAsState(
        if (selected) c.accent.copy(alpha = if (c.dark) .5f else .32f) else c.line,
        tween(150),
        label = "chipEdge",
    )
    Box(
        modifier.height(40.dp).clip(SoftShape.control).background(bg).border(1.dp, edge, SoftShape.control)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = CobaltType.caption.copy(fontWeight = if (selected) FontWeight.W500 else FontWeight.W400),
            color = if (selected) c.accent else c.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Wrapping selector: labels never truncate, so long Russian words stay readable. */
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
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { index, label ->
                SoftChip(label, index == selectedIndex) { onChange(index) }
            }
        }
    }
}

/** Segmented control for two or three short, mutually exclusive options. */
@Composable
fun SoftSegmented(
    labels: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    val c = LocalDesign.current
    Row(
        modifier.fillMaxWidth().clip(SoftShape.control).background(c.well)
            .border(1.dp, c.line, SoftShape.control).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selectedIndex
            val interaction = ownInteraction()
            val pressed by interaction.collectIsPressedAsState()
            val bg by animateColorAsState(
                when {
                    active -> c.surface
                    pressed -> blend(c.well, c.surface, .55f)
                    else -> c.surface.faded()
                },
                tween(150),
                label = "segBg",
            )
            Box(
                Modifier.weight(1f).height(38.dp).clip(SoftShape.control).background(bg)
                    .then(if (active) Modifier.border(1.dp, c.line, SoftShape.control) else Modifier)
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
                    style = CobaltType.caption.copy(fontWeight = if (active) FontWeight.W500 else FontWeight.W400),
                    color = if (active) c.text else c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun SoftWell(contentPadding: PaddingValues = PaddingValues(14.dp), content: @Composable ColumnScope.() -> Unit) {
    val c = LocalDesign.current
    Column(
        Modifier.fillMaxWidth().clip(SoftShape.inner).background(c.well).padding(contentPadding),
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
        Modifier.clip(SoftShape.pill).background(tint.copy(alpha = if (c.dark) .20f else .10f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(tint))
        Text(text, style = CobaltType.micro, color = tint, modifier = Modifier.padding(start = 5.dp), maxLines = 1)
    }
}

/** One status row: label on the left, indicator and value on the right. */
@Composable
fun SoftStatusLine(label: String, value: String, tone: SoftTone) {
    val c = LocalDesign.current
    val tint = tone.color(c)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(vertical = 3.dp),
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

/** Search field with a clear button that appears only when there is text. */
@Composable
fun SoftSearchField(state: TextFieldState, placeholder: String = "Поиск") {
    val c = LocalDesign.current
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(SoftShape.control)
            .background(c.surface).border(1.dp, c.line, SoftShape.control)
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
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (pressed) c.well else Color.Transparent)
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

/**
 * Floating bottom bar: one raised surface with a single tinted marker for the current tab.
 * Each item tints only itself on press, so tapping one tab never flashes its neighbours.
 */
@Composable
fun SoftTabBar(
    labels: List<String>,
    icons: List<Int>,
    selected: Int,
    hideLabels: Boolean = false,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val c = LocalDesign.current
    val glass = LocalGlassBar.current
    val shape = SoftShape.card
    // The live shader was visually attractive but too expensive on long Compose pages: it
    // re-recorded the whole backdrop during every scroll and route transition. Keep the same
    // frosted language with a stable tonal surface and hairline; it remains visible on detail
    // screens and does not steal frames from the page.
    val fill = if (glass) {
        if (c.dark) c.surface.copy(alpha = .94f) else c.surface.copy(alpha = .92f)
    } else c.surface
    Row(
        modifier.fillMaxWidth()
            .shadow(if (c.dark) 0.dp else 14.dp, shape, ambientColor = Color.Black.copy(alpha = .05f), spotColor = Color.Black.copy(alpha = .07f))
            .clip(shape)
            .background(fill)
            .border(1.dp, if (glass) c.line.copy(alpha = .82f) else c.line, shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            val interaction = ownInteraction()
            val pressed by interaction.collectIsPressedAsState()
            val tone by animateColorAsState(
                when {
                    active -> c.accentSurface
                    pressed -> c.well
                    else -> c.accentSurface.faded()
                },
                tween(160),
                label = "tabTone",
            )
            val itemScale by animateFloatAsState(if (pressed) .96f else 1f, tween(110), label = "tabScale")
            Row(
                Modifier.weight(1f).height(46.dp).graphicsLayer { scaleX = itemScale; scaleY = itemScale }
                    .clip(SoftShape.inner).background(tone)
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(index) },
                    )
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    ImageVector.vectorResource(icons[index]), null,
                    tint = if (active) c.accent else c.faint, modifier = Modifier.size(19.dp),
                )
                if (!hideLabels) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        style = CobaltType.micro.copy(letterSpacing = 0.sp),
                        color = if (active) c.accent else c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun SoftField(value: String, onValueChange: (String) -> Unit, label: String, minLines: Int = 1, limit: Int = 4096) {
    val c = LocalDesign.current
    Column(
        Modifier.fillMaxWidth().clip(SoftShape.inner).background(c.well)
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
        Modifier.heightIn(min = 36.dp).clip(SoftShape.control)
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
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            SoftIconButton(onClick = onBack)
            Spacer(Modifier.width(14.dp))
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

/** Small supporting paragraph under a block. */
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
        Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(vertical = 9.dp),
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
