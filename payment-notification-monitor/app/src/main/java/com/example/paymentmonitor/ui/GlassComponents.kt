package com.example.paymentmonitor.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.paymentmonitor.ui.theme.ThemeMode
import kotlinx.coroutines.delay

@Immutable
data class GlassColors(
    val background: Brush,
    val card: Color,
    val elevatedCard: Color,
    val border: Color,
    val strongBorder: Color,
    val shadow: Color,
    val glowPrimary: Color,
    val glowSecondary: Color,
    val navSelected: Color,
    val navUnselected: Color,
    val positive: Color,
    val warning: Color,
    val negative: Color,
)

private val LocalGlassColors = staticCompositionLocalOf {
    GlassColors(
        background = Brush.linearGradient(listOf(Color.Black, Color.DarkGray)),
        card = Color.White.copy(alpha = 0.1f),
        elevatedCard = Color.White.copy(alpha = 0.16f),
        border = Color.White.copy(alpha = 0.15f),
        strongBorder = Color.White.copy(alpha = 0.24f),
        shadow = Color.Black.copy(alpha = 0.25f),
        glowPrimary = Color(0xFF6674FF),
        glowSecondary = Color(0xFF20D8E6),
        navSelected = Color.White.copy(alpha = 0.18f),
        navUnselected = Color.White.copy(alpha = 0.68f),
        positive = Color(0xFF58E8B2),
        warning = Color(0xFFFFCA69),
        negative = Color(0xFFFF8A98),
    )
}

val glassColors: GlassColors
    @Composable get() = LocalGlassColors.current

@Composable
fun GlassStyleProvider(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) {
        GlassColors(
            background = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF070916),
                    Color(0xFF111538),
                    Color(0xFF082B38),
                ),
                start = androidx.compose.ui.geometry.Offset.Zero,
                end = androidx.compose.ui.geometry.Offset.Infinite,
            ),
            card = Color(0xFFEEF1FF).copy(alpha = 0.09f),
            elevatedCard = Color(0xFFF7F8FF).copy(alpha = 0.14f),
            border = Color.White.copy(alpha = 0.14f),
            strongBorder = Color.White.copy(alpha = 0.26f),
            shadow = Color.Black.copy(alpha = 0.32f),
            glowPrimary = Color(0xFF7180FF),
            glowSecondary = Color(0xFF23DDE8),
            navSelected = Color.White.copy(alpha = 0.14f),
            navUnselected = Color(0xFFC7C9DF),
            positive = Color(0xFF69EBC0),
            warning = Color(0xFFFFCB74),
            negative = Color(0xFFFF91A0),
        )
    } else {
        GlassColors(
            background = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFF6F7FF),
                    Color(0xFFE9EBFF),
                    Color(0xFFDDF9FA),
                ),
                start = androidx.compose.ui.geometry.Offset.Zero,
                end = androidx.compose.ui.geometry.Offset.Infinite,
            ),
            card = Color.White.copy(alpha = 0.55f),
            elevatedCard = Color.White.copy(alpha = 0.72f),
            border = Color.White.copy(alpha = 0.72f),
            strongBorder = Color.White.copy(alpha = 0.94f),
            shadow = Color(0xFF3B478F).copy(alpha = 0.16f),
            glowPrimary = Color(0xFF7C73FF),
            glowSecondary = Color(0xFF28C7D3),
            navSelected = Color.White.copy(alpha = 0.72f),
            navUnselected = Color(0xFF555A75),
            positive = Color(0xFF007F65),
            warning = Color(0xFF946200),
            negative = Color(0xFFB3263D),
        )
    }
    CompositionLocalProvider(LocalGlassColors provides colors, content = content)
}

@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(glassColors.background),
    ) {
        BackgroundOrb(
            color = glassColors.glowPrimary.copy(alpha = 0.28f),
            size = 260.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-70).dp),
        )
        BackgroundOrb(
            color = glassColors.glowSecondary.copy(alpha = 0.22f),
            size = 230.dp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-110).dp, y = 40.dp),
        )
        BackgroundOrb(
            color = glassColors.glowPrimary.copy(alpha = 0.13f),
            size = 190.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 70.dp, y = 70.dp),
        )
        content()
    }
}

@Composable
private fun BackgroundOrb(
    color: Color,
    size: Dp,
    modifier: Modifier,
) {
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(
            radius = 72.dp,
            edgeTreatment = BlurredEdgeTreatment.Unbounded,
        )
    } else {
        Modifier.graphicsLayer { alpha = 0.62f }
    }
    Box(
        modifier = modifier
            .then(blurModifier)
            .size(size)
            .background(color, CircleShape),
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    shape: Shape = RoundedCornerShape(28.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "glass-card-scale",
    )
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (elevated) 22.dp else 12.dp,
                shape = shape,
                ambientColor = glassColors.shadow,
                spotColor = glassColors.shadow,
            )
            .clip(shape)
            .background(if (elevated) glassColors.elevatedCard else glassColors.card)
            .border(
                BorderStroke(
                    1.dp,
                    if (elevated) glassColors.strongBorder else glassColors.border,
                ),
                shape,
            )
            .then(clickModifier),
        content = content,
    )
}

enum class GlassButtonStyle {
    PRIMARY,
    SECONDARY,
    DANGER,
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    style: GlassButtonStyle = GlassButtonStyle.PRIMARY,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "glass-button-scale",
    )
    val targetColor = when (style) {
        GlassButtonStyle.PRIMARY -> MaterialTheme.colorScheme.primary
        GlassButtonStyle.SECONDARY -> glassColors.elevatedCard
        GlassButtonStyle.DANGER -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
    }
    val background by animateColorAsState(
        targetValue = if (enabled) targetColor else targetColor.copy(alpha = 0.35f),
        label = "glass-button-background",
    )
    val foreground = when (style) {
        GlassButtonStyle.PRIMARY -> MaterialTheme.colorScheme.onPrimary
        GlassButtonStyle.SECONDARY -> MaterialTheme.colorScheme.onSurface
        GlassButtonStyle.DANGER -> MaterialTheme.colorScheme.onErrorContainer
    }
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(
                BorderStroke(
                    1.dp,
                    if (style == GlassButtonStyle.PRIMARY) {
                        Color.White.copy(alpha = 0.24f)
                    } else {
                        glassColors.border
                    },
                ),
                RoundedCornerShape(18.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(20.dp),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = foreground.copy(alpha = if (enabled) 1f else 0.72f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun AnimatedStatusPill(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (active) {
            glassColors.positive.copy(alpha = 0.16f)
        } else {
            glassColors.warning.copy(alpha = 0.13f)
        },
        label = "status-background",
    )
    val border by animateColorAsState(
        targetValue = if (active) glassColors.positive.copy(alpha = 0.45f)
        else glassColors.warning.copy(alpha = 0.42f),
        label = "status-border",
    )
    val dotSize by animateDpAsState(
        targetValue = if (active) 9.dp else 7.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "status-dot",
    )
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .size(dotSize)
                .background(if (active) glassColors.positive else glassColors.warning, CircleShape),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = if (active) "正常" else "待处理",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

data class GlassNavigationItem(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
)

@Composable
fun FloatingGlassNavigation(
    items: List<GlassNavigationItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp),
        elevated = true,
        shape = RoundedCornerShape(27.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(7.dp)) {
            val itemWidth = maxWidth / items.size
            val highlightOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "nav-highlight",
            )
            Box(
                Modifier
                    .offset(x = highlightOffset)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 3.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(glassColors.navSelected)
                    .border(1.dp, glassColors.border, RoundedCornerShape(21.dp)),
            )
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { index, item ->
                    NavigationItem(
                        item = item,
                        selected = selectedIndex == index,
                        onClick = { onSelected(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavigationItem(
    item: GlassNavigationItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "nav-icon-scale",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else glassColors.navUnselected,
        label = "nav-color",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .testTag(item.testTag)
            .semantics { contentDescription = item.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp).graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
fun ThemeModeButton(
    mode: ThemeMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = when (mode) {
        ThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
        ThemeMode.LIGHT -> Icons.Outlined.LightMode
        ThemeMode.DARK -> Icons.Outlined.DarkMode
    }
    val label = when (mode) {
        ThemeMode.SYSTEM -> "主题：跟随系统"
        ThemeMode.LIGHT -> "主题：浅色"
        ThemeMode.DARK -> "主题：深色"
    }
    GlassCard(
        modifier = modifier.size(48.dp).semantics { contentDescription = label },
        shape = CircleShape,
        elevated = true,
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp).align(Alignment.Center),
        )
    }
}

@Composable
fun AnimatedSection(
    delayMillis: Long,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 5 }),
    ) {
        content()
    }
}

@Composable
fun SectionHeading(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun GlassMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    GlassCard(
        modifier = modifier.widthIn(min = 96.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
