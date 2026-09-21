package com.maxrave.simpmusic.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A [Surface] with an infinite border animation.
 *
 * @param content The content to be displayed inside the [Surface]
 * Should using [CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified)] to remove default padding.
 */
@Composable
fun InfiniteBorderAnimationView(
    isAnimated: Boolean = false,
    brush: Brush = Brush.sweepGradient(listOf(Color.Gray, Color.White)),
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    contentPadding: Dp = 0.dp,
    borderWidth: Dp = 1.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    oneCircleDurationMillis: Int = 3000,
    content: @Composable () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Infinite Color Animation")
    val degrees by infiniteTransition.animateFloat(
        initialValue = 90f,
        targetValue = 450f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = oneCircleDurationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "Infinite Colors",
    )
    val scaleAnimationValue by animateFloatAsState(
        if (isAnimated) 1f else 0f,
        tween(800),
    )
    Surface(
        modifier =
            Modifier
                .clip(
                    shape,
                ).padding(borderWidth)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }.drawBehind {
                    scale(scale = scaleAnimationValue) {
                        rotate(degrees = degrees) {
                            drawCircle(
                                brush = brush,
                                radius = size.width,
                                blendMode = BlendMode.SrcIn,
                            )
                        }
                    }
                }.animateContentSize(),
        color = backgroundColor,
        shape = shape,
    ) {
        Box(
            modifier =
                Modifier
                    .background(
                        color = if (isAnimated) Color.Black else backgroundColor,
                    ).padding(
                        contentPadding,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
fun LimitedBorderAnimationView(
    isAnimated: Boolean = false,
    brush: Brush = Brush.sweepGradient(listOf(Color.Gray, Color.White)),
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    contentPadding: Dp = 0.dp,
    borderWidth: Dp = 1.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    oneCircleDurationMillis: Int = 3000,
    interactionNumber: Int = 1,
    content: @Composable () -> Unit,
) {
    var shouldAnimate by rememberSaveable {
        mutableStateOf(false)
    }
    val scaleAnimationValue by animateFloatAsState(
        if (shouldAnimate) 1f else 0f,
        tween(800),
    )

    LaunchedEffect(true) {
        if (isAnimated) {
            shouldAnimate = true
            delay(interactionNumber * oneCircleDurationMillis.toLong())
            shouldAnimate = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "Infinite Color Animation")
    val degrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = oneCircleDurationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "Infinite Colors",
    )
    Surface(
        modifier =
            Modifier
                .clip(
                    shape,
                ).padding(borderWidth)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }.drawBehind {
                    if (isAnimated) {
                        scale(
                            scale = scaleAnimationValue,
                        ) {
                            rotate(degrees = degrees) {
                                drawCircle(
                                    brush = brush,
                                    radius = size.width,
                                    blendMode = BlendMode.SrcIn,
                                )
                            }
                        }
                    }
                },
        shape = shape,
    ) {
        Box(
            modifier =
                Modifier
                    .background(
                        color = backgroundColor,
                    ).padding(
                        contentPadding,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
/**
 * 播放指示 Lottie(audio_playing_animation.json,fr=100/op=144 帧≈1.44s 循环)的限帧进度驱动。
 * compottie 自动播放以屏幕刷新率(高刷机 120Hz)逐帧失效重绘——当前曲指示条是实测主热源
 * (真机静止歌单页仍 ~120fps、主线程 35-55% CPU→发热,2026-09-21 真机归因)。这里以
 * [fps]=12 手写进度:状态写入频率=重绘频率,频谱条视觉依旧流畅;调用点都在"正在播放"
 * 分支内,组合离开分支协程自动取消。
 */
@Composable
fun rememberThrottledLottieProgress(
    fps: Float = 12f,
    durationMs: Float = 1440f,
): () -> Float {
    val progress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last > 0) {
                    val dtMs = (now - last) / 1_000_000f
                    if (dtMs >= 1000f / fps) {
                        progress.floatValue = (progress.floatValue + dtMs / durationMs) % 1f
                    }
                }
                last = now
            }
        }
    }
    return { progress.floatValue }
}
