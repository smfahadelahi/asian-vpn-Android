package com.asiashield.vpn.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asiashield.vpn.domain.model.VpnServer
import com.asiashield.vpn.presentation.theme.PingFair
import com.asiashield.vpn.presentation.theme.PingGood
import com.asiashield.vpn.presentation.theme.PingPoor
import com.asiashield.vpn.presentation.theme.PingUnknown

/** < 60 ms green, < 150 ms yellow, otherwise red; grey when there is no measurement. */
fun pingColor(latencyMs: Int?): Color = when {
    latencyMs == null -> PingUnknown
    latencyMs < 60 -> PingGood
    latencyMs < 150 -> PingFair
    else -> PingPoor
}

fun pingLabel(server: VpnServer): String = when {
    server.latencyMs != null -> "${server.latencyMs} ms"
    server.latencyChecked -> "Offline"
    else -> "– ms"
}

@Composable
fun PingIndicator(server: VpnServer, modifier: Modifier = Modifier) {
    val color = pingColor(server.latencyMs)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            text = pingLabel(server),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
