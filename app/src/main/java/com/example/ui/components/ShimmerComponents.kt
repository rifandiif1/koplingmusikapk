package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun rememberShimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)
    val transition = rememberInfiniteTransition(label = "koplingfy_shimmer")
    val translate by transition.animateFloat(
        initialValue = -320f,
        targetValue = 1100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate, 0f),
        end = Offset(translate + 360f, 180f)
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    brush: Brush = rememberShimmerBrush()
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun HomeFeedSkeletonShimmer() {
    val brush = rememberShimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Section header placeholder
        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(modifier = Modifier.width(150.dp).height(20.dp), brush = brush)
            ShimmerBox(modifier = Modifier.width(210.dp).height(13.dp), brush = brush)
        }

        // Track list shimmer rows
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBox(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(12.dp), brush = brush)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShimmerBox(modifier = Modifier.fillMaxWidth(0.72f).height(15.dp), brush = brush)
                        ShimmerBox(modifier = Modifier.fillMaxWidth(0.46f).height(12.dp), brush = brush)
                    }
                    ShimmerBox(modifier = Modifier.size(28.dp), shape = CircleShape, brush = brush)
                }
            }
        }

        // Horizontal carousel shimmer cards
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .width(180.dp)
                    .height(20.dp),
                brush = brush
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                userScrollEnabled = false
            ) {
                items(4) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShimmerBox(
                            modifier = Modifier.size(144.dp),
                            shape = RoundedCornerShape(18.dp),
                            brush = brush
                        )
                        ShimmerBox(modifier = Modifier.width(120.dp).height(14.dp), brush = brush)
                        ShimmerBox(modifier = Modifier.width(86.dp).height(11.dp), brush = brush)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultsSkeletonShimmer() {
    val brush = rememberShimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(6) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(modifier = Modifier.size(54.dp), shape = RoundedCornerShape(14.dp), brush = brush)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.76f).height(15.dp), brush = brush)
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.50f).height(12.dp), brush = brush)
                }
                ShimmerBox(modifier = Modifier.size(30.dp), shape = CircleShape, brush = brush)
            }
        }
    }
}
