package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private val SplashTop = Color(0xFFFFFFFF)
private val SplashBottom = Color(0xFFF3EEFC)
private val NameStart = Color(0xFF9B7BF2) // the logo's violet …
private val NameEnd = Color(0xFFDD8FE6)   // … to its pink

private val BIG_ICON = 112.dp
private val SMALL_ICON = 34.dp

/**
 * Launch intro (≈ 2.7 s, tap to skip):
 * 1. the app icon fades in, 2. the name writes itself letter by letter,
 * 3. the icon shrinks, hops and drops onto the "x" of the name, bounces twice (the x squashes),
 * 4. everything fades out and the app opens.
 */
@Composable
fun ChatboxSplash(title: String, tagline: String, onFinished: () -> Unit) {
    val name = title.ifBlank { "Chatbox" }
    val landIndex = name.lastIndexOf('x', ignoreCase = true).let { if (it < 0) name.lastIndex else it }

    val iconIn = remember { Animatable(0f) }
    val letters = remember(name) { List(name.length) { Animatable(0f) } }
    val taglineIn = remember { Animatable(0f) }
    val flight = remember { Animatable(0f) }
    val landing = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }

    var origin by remember { mutableStateOf(Offset.Zero) }
    var boxW by remember { mutableStateOf(0f) }
    var nameBounds by remember { mutableStateOf<Rect?>(null) }
    var landBounds by remember { mutableStateOf<Rect?>(null) }
    var done by remember { mutableStateOf(false) }
    val finish: () -> Unit = {
        if (!done) {
            done = true
            onFinished()
        }
    }

    LaunchedEffect(name) {
        launch { iconIn.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        delay(250)
        letters.forEachIndexed { i, a ->
            launch {
                delay(i * 75L)
                a.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f))
            }
        }
        launch {
            delay(letters.size * 75L + 150)
            taglineIn.animateTo(1f, tween(400))
        }
        delay(letters.size * 75L + 350)
        flight.animateTo(1f, tween(600, easing = LinearEasing))
        landing.animateTo(1f, tween(480, easing = LinearEasing))
        delay(250)
        exit.animateTo(0f, tween(280))
        finish()
    }

    val density = LocalDensity.current
    val bigPx = with(density) { BIG_ICON.toPx() }
    val smallPx = with(density) { SMALL_ICON.toPx() }
    val gapPx = with(density) { 28.dp.toPx() }
    val hopPx = with(density) { 70.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SplashTop, SplashBottom)))
            .alpha(exit.value)
            .onGloballyPositioned {
                origin = it.positionInRoot()
                boxW = it.size.width.toFloat()
            }
            .pointerInput(Unit) { detectTapGestures { finish() } }
            .testTag("launch_splash_screen")
    ) {
        // ---- the name, letter by letter (plain material Text: single letters must never be "translated")
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 60.dp)
                .padding(horizontal = 24.dp)
        ) {
            Row(modifier = Modifier.onGloballyPositioned { nameBounds = it.boundsInRoot() }) {
                name.forEachIndexed { i, ch ->
                    val p = letters[i].value
                    // the landing letter squashes each time the icon touches it
                    val squash = if (i == landIndex) squashAt(landing.value, flight.value) else 1f
                    androidx.compose.material3.Text(
                        text = ch.toString(),
                        color = lerp(NameStart, NameEnd, if (name.length > 1) i / (name.length - 1f) else 0f),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .then(if (i == landIndex) Modifier.onGloballyPositioned { landBounds = it.boundsInRoot() } else Modifier)
                            .graphicsLayer {
                                alpha = p.coerceIn(0f, 1f)
                                translationY = (1f - p) * 36.dp.toPx()
                                scaleX = (0.6f + 0.4f * p) * (2f - squash)
                                scaleY = (0.6f + 0.4f * p) * squash
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.Text(
                text = tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8A84A3),
                modifier = Modifier.graphicsLayer {
                    alpha = taglineIn.value
                    translationY = (1f - taglineIn.value) * 10.dp.toPx()
                }
            )
        }

        // ---- the icon: above the name, then hops and drops onto the "x"
        val nb = nameBounds
        val lb = landBounds
        if (boxW > 0f && nb != null) {
            val startX = boxW / 2f
            val startY = (nb.top - origin.y) - gapPx - bigPx / 2f
            // land on the top of the lowercase letter (≈ 40 % down its line box)
            val endX = if (lb != null) lb.center.x - origin.x else startX
            val endY = if (lb != null) (lb.top - origin.y) + lb.height * 0.40f - smallPx / 2f else startY

            val f = flight.value
            val ease = f * f * (3f - 2f * f)
            val size = bigPx + (smallPx - bigPx) * ease
            val cx = startX + (endX - startX) * ease
            // a little hop up first, then gravity pulls it down onto the letter
            val cy = startY + (endY - startY) * (f * f) - hopPx * 4f * f * (1f - f) * 0.55f +
                bounceOffset(landing.value, density.density)
            val sizeDp = with(density) { size.toDp() }
            Box(
                modifier = Modifier
                    .offset { IntOffset((cx - size / 2f).roundToInt(), (cy - size / 2f).roundToInt()) }
                    .size(sizeDp)
                    .graphicsLayer {
                        val inP = iconIn.value
                        alpha = inP
                        scaleX = 0.7f + 0.3f * inP
                        scaleY = 0.7f + 0.3f * inP
                        rotationZ = -24f * sin(PI.toFloat() * f) + 8f * sin(PI.toFloat() * landing.value * 2f) * (1f - landing.value)
                    }
                    .shadow(elevation = (10f * (1f - ease) + 3f).dp, shape = RoundedCornerShape(26)),
            ) {
                Image(
                    painter = painterResource(R.drawable.saira_asset_5733705484_1),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(26))
                )
            }
        }
    }
}

/** Two shrinking bounces after the icon lands (pixels, negative = up). */
private fun bounceOffset(t: Float, density: Float): Float {
    val dp = when {
        t <= 0f || t >= 1f -> 0f
        t < 0.5f -> -24f * sin(PI.toFloat() * t / 0.5f)
        t < 0.85f -> -8f * sin(PI.toFloat() * (t - 0.5f) / 0.35f)
        else -> 0f
    }
    return dp * density
}

/** Squash of the landing letter at every touch-down (1 = normal). */
private fun squashAt(landing: Float, flight: Float): Float {
    if (flight < 1f) return 1f
    fun bump(center: Float, width: Float, depth: Float) = depth * (1f - abs(landing - center) / width).coerceAtLeast(0f)
    return 1f - bump(0f, 0.09f, 0.26f) - bump(0.5f, 0.07f, 0.12f) - bump(0.85f, 0.06f, 0.05f)
}
