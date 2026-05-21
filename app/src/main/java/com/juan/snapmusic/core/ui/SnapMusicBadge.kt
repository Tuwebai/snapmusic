package com.juan.snapmusic.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.TextPrimary

@Composable
fun SnapMusicBadge(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = TextPrimary,
        modifier = modifier
            .background(SurfaceElevated, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
