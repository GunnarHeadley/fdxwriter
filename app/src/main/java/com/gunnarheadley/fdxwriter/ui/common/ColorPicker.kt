package com.gunnarheadley.fdxwriter.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gunnarheadley.fdxwriter.data.fdx.FdxColor
import kotlin.math.roundToInt

/** Final Draft's built-in card/note colors. Custom colors are also available via the picker. */
val FD_COLORS = listOf(
    "#FFFFFFFFFFFF", // White
    "#EBEB62627B7B", // Red
    "#EFEFA4A46262", // Orange
    "#E5E5CBCB6C6C", // Yellow
    "#929290900000", // Olive
    "#8F8FC3C36A6A", // Green
    "#6363A7A7EFEF", // Blue
    "#9A9AAEAEDBDB", // Steel
    "#AFAF9393E8E8", // Purple
    "#E2E29898DDDD", // Magenta
    "#B2B27C7C7373", // Brown
    "#C0C0C0C0C0C0", // Gray
)

/** A palette of Final Draft colors plus a custom RGB picker. [selected]/[onSelect] are FDX colors. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: String, onSelect: (String) -> Unit) {
    var showCustom by remember {
        mutableStateOf(FD_COLORS.none { it.equals(selected, ignoreCase = true) })
    }
    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (fdx in FD_COLORS) {
                val swatch = FdxColor.toArgb(fdx)?.let { Color(it) } ?: Color.White
                ColorDot(
                    fill = SolidColor(swatch),
                    selected = !showCustom && fdx.equals(selected, ignoreCase = true),
                    onClick = { showCustom = false; onSelect(fdx) },
                )
            }
            ColorDot(
                fill = Brush.sweepGradient(
                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                ),
                selected = showCustom,
                onClick = { showCustom = true },
                label = "+",
            )
        }
        if (showCustom) {
            Spacer(Modifier.height(12.dp))
            CustomColorSliders(
                argb = FdxColor.toArgb(selected) ?: 0xFFCCCCCC.toInt(),
                onChange = { onSelect(FdxColor.fromArgb(it)) },
            )
        }
    }
}

@Composable
private fun ColorDot(fill: Brush, selected: Boolean, onClick: () -> Unit, label: String? = null) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color(0x55000000),
                shape = CircleShape,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CustomColorSliders(argb: Int, onChange: (Int) -> Unit) {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(argb)),
        )
        ChannelSlider("R", r) { onChange(packArgb(it, g, b)) }
        ChannelSlider("G", g) { onChange(packArgb(r, it, b)) }
        ChannelSlider("B", b) { onChange(packArgb(r, g, it)) }
    }
}

@Composable
private fun ChannelSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(18.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun packArgb(r: Int, g: Int, b: Int): Int =
    (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
