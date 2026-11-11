package com.asiashield.vpn.presentation.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asiashield.vpn.domain.model.VpnServer
import com.asiashield.vpn.domain.model.VpnState
import com.asiashield.vpn.presentation.VpnUiState
import com.asiashield.vpn.presentation.components.PingIndicator
import com.asiashield.vpn.presentation.components.formatBytes
import com.asiashield.vpn.presentation.components.formatDuration
import com.asiashield.vpn.presentation.components.formatRate
import com.asiashield.vpn.presentation.theme.Amber
import com.asiashield.vpn.presentation.theme.Navy600
import com.asiashield.vpn.presentation.theme.Navy700
import com.asiashield.vpn.presentation.theme.Navy800
import com.asiashield.vpn.presentation.theme.Red
import com.asiashield.vpn.presentation.theme.Teal
import com.asiashield.vpn.presentation.theme.TextSecondary

@Composable
fun HomeScreen(
    uiState: VpnUiState,
    onPowerClick: () -> Unit,
    onSmartConnect: () -> Unit,
    onOpenServers: () -> Unit,
) {
    val state = uiState.state
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "AsiaShield VPN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Secure nodes across Asia",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            Spacer(Modifier.height(36.dp))
            PowerButton(state = state, onClick = onPowerClick)
            Spacer(Modifier.height(24.dp))

            Text(
                text = statusTitle(state),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = stateColor(state),
            )
            Text(
                text = statusSubtitle(uiState),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state == VpnState.FAILED) Red else TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(28.dp))
            SessionMetrics(uiState)
            Spacer(Modifier.height(16.dp))
            SelectedServerCard(server = uiState.selectedServer, onClick = onOpenServers)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSmartConnect,
                enabled = state != VpnState.DISCONNECTING,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("⚡ Smart Connect · fastest server")
            }
        }
    }
}

private fun stateColor(state: VpnState): Color = when (state) {
    VpnState.CONNECTED -> Teal
    VpnState.CONNECTING, VpnState.DISCONNECTING -> Amber
    VpnState.FAILED -> Red
    VpnState.IDLE -> Navy600
}

private fun statusTitle(state: VpnState): String = when (state) {
    VpnState.IDLE -> "Not connected"
    VpnState.CONNECTING -> "Connecting…"
    VpnState.CONNECTED -> "Protected"
    VpnState.DISCONNECTING -> "Disconnecting…"
    VpnState.FAILED -> "Connection failed"
}

private fun statusSubtitle(uiState: VpnUiState): String = when (uiState.state) {
    VpnState.IDLE -> "Tap the button to connect"
    VpnState.CONNECTING -> "Establishing WireGuard tunnel"
    VpnState.CONNECTED -> "Your traffic is routed via ${uiState.selectedServer?.city ?: "Asia"}"
    VpnState.DISCONNECTING -> "Closing tunnel"
    VpnState.FAILED -> uiState.status.errorMessage ?: "Tap the button to retry"
}

// ---------------------------------------------------------------------- power button

@Composable
private fun PowerButton(state: VpnState, onClick: () -> Unit) {
    val accent by animateColorAsState(stateColor(state), tween(400), label = "accent")
    val description = if (state.isActive) "Disconnect VPN" else "Connect VPN"

    Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        // Pulse: fast while a transition is in flight, slow "breathing" once protected.
        val pulseMillis = when (state) {
            VpnState.CONNECTING, VpnState.DISCONNECTING -> 1100
            VpnState.CONNECTED -> 2600
            VpnState.IDLE, VpnState.FAILED -> 0
        }
        if (pulseMillis > 0) {
            key(pulseMillis) {
                PulseRing(color = accent, durationMillis = pulseMillis, delayMillis = 0)
                PulseRing(color = accent, durationMillis = pulseMillis, delayMillis = pulseMillis / 2)
            }
        }

        Box(
            modifier = Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Navy700, Navy800)))
                .border(3.dp, accent, CircleShape)
                .clickable(
                    enabled = state != VpnState.DISCONNECTING,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            PowerGlyph(color = if (state == VpnState.IDLE) Color.White else accent)
        }
    }
}

@Composable
private fun PulseRing(color: Color, durationMillis: Int, delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMillis),
        ),
        label = "pulseProgress",
    )
    Box(
        Modifier
            .size(168.dp)
            .graphicsLayer {
                val scale = 1f + 0.42f * progress
                scaleX = scale
                scaleY = scale
                alpha = 0.45f * (1f - progress)
            }
            .background(color, CircleShape),
    )
}

/** IEC "power" symbol: an open ring with a vertical bar through the gap. */
@Composable
private fun PowerGlyph(color: Color) {
    Canvas(Modifier.size(64.dp)) {
        val stroke = size.minDimension * 0.11f
        val inset = stroke / 2 + size.minDimension * 0.08f
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - 2 * inset, size.height - 2 * inset),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = color,
            start = Offset(center.x, stroke / 2),
            end = Offset(center.x, center.y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

// ---------------------------------------------------------------------- metrics

@Composable
private fun SessionMetrics(uiState: VpnUiState) {
    val status = uiState.status
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SESSION", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                text = formatDuration(uiState.sessionSeconds),
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThroughputTile(
                    icon = Icons.Filled.KeyboardArrowDown,
                    label = "Download",
                    rate = formatRate(status.rxBytesPerSec),
                    total = formatBytes(status.rxBytes),
                    tint = Teal,
                    modifier = Modifier.weight(1f),
                )
                ThroughputTile(
                    icon = Icons.Filled.KeyboardArrowUp,
                    label = "Upload",
                    rate = formatRate(status.txBytesPerSec),
                    total = formatBytes(status.txBytes),
                    tint = Amber,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ThroughputTile(
    icon: ImageVector,
    label: String,
    rate: String,
    total: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(rate, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Total $total", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

// ---------------------------------------------------------------------- server card

@Composable
private fun SelectedServerCard(server: VpnServer?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = server?.flagEmoji ?: "🌏", fontSize = 34.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = server?.city ?: "Choose a server",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (server != null) {
                    Text(
                        text = "${server.country} · ${server.region.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
            if (server != null) PingIndicator(server)
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = "Change server",
                tint = TextSecondary,
            )
        }
    }
}
