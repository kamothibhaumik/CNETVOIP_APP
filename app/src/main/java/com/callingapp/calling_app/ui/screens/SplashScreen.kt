package com.callingapp.calling_app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callingapp.calling_app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LogoNavy = Color(0xFF001170)
private val LogoElectric = Color(0xFF2563EB)

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val logoReveal = remember { Animatable(0f) }
    val glowAlpha  = remember { Animatable(0f) }
    val textReveal = remember { Animatable(0f) }
    val blurValue  = remember { Animatable(18f) }

    val infiniteTransition = rememberInfiniteTransition(label = "ambient")

    // Slow, deep pulse for the electric glow
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f, targetValue = 1.22f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        // Step 1: Reveal logo with a smooth deceleration curve
        launch {
            logoReveal.animateTo(1f, tween(1100, easing = EaseOutQuart))
        }
        // Step 2: Transition out blur as the logo sharpens
        launch {
            blurValue.animateTo(0f, tween(1300))
        }

        delay(700)

        // Step 3: Expand the blue glow behind the circuit card
        launch {
            glowAlpha.animateTo(0.7f, tween(900))
        }

        // Step 4: Text reveal for title and tagline
        launch {
            textReveal.animateTo(1f, tween(1100, easing = EaseInOutCubic))
        }

        delay(2800)

        // Final Outro Fade
        launch { logoReveal.animateTo(0f, tween(700)) }
        launch { textReveal.animateTo(0f, tween(700)) }
        delay(700)

        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF050B1C), Color(0xFF02040A)),
                    radius = 2200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // ── Electric Ambient Radial Glow ──
        Box(
            modifier = Modifier
                .size(380.dp)
                .scale(pulseScale)
                .alpha(glowAlpha.value * 0.28f)
                .background(
                    Brush.sweepGradient(
                        colors = listOf(Color.Transparent, LogoElectric, Color.Transparent)
                    ),
                    CircleShape
                )
                .blur(60.dp)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // ── Circuit Logo Container ──
            Box(contentAlignment = Alignment.Center) {
                // Secondary Diffused Glow
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(logoReveal.value * 1.15f)
                        .alpha(glowAlpha.value * 0.45f)
                        .background(LogoElectric, CircleShape)
                        .blur(26.dp)
                )

                // High-fidelity Logo Card
                Surface(
                    modifier = Modifier
                        .size(104.dp)
                        .scale(logoReveal.value)
                        .blur(blurValue.value.dp)
                        .clip(RoundedCornerShape(26.dp)),
                    color = Color.White,
                    shadowElevation = 10.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.cnetgeek_logo),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(logoReveal.value)
                        )
                    }
                }
            }

            Spacer(Modifier.height(42.dp))

            // ── Typography Section ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(textReveal.value)
                    .offset(y = (18 * (1 - textReveal.value)).dp)
            ) {
                Text(
                    text = "CNETVOIP",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 8.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                // Modern accent separator
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(2.5.dp)
                        .background(LogoElectric.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "COMMUNICATION REDEFINED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF93C5FD),
                    letterSpacing = 3.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}