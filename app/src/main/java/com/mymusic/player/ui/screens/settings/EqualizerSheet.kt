package com.mymusic.player.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mymusic.player.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val eqState by viewModel.equalizerState.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audio Equalizer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = eqState.enabled,
                        onCheckedChange = { viewModel.setEqualizerEnabled(it) }
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Presets Horizontal Scroll
            Text(
                text = "Presets",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                eqState.availablePresets.forEachIndexed { index, presetName ->
                    FilterChip(
                        selected = eqState.currentPreset == presetName,
                        onClick = { viewModel.setEqualizerPreset(index.toShort()) },
                        label = { Text(presetName) },
                        enabled = eqState.enabled,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Frequency Bands
            Text(
                text = "Frequency Bands",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (eqState.bands.isEmpty()) {
                Text(
                    text = "Built-in hardware equalizer is initializing or not supported on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                eqState.bands.forEach { band ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = band.displayFrequency,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(0.25f)
                        )

                        Slider(
                            value = band.levelMillibels.toFloat(),
                            onValueChange = {
                                viewModel.setEqualizerBandLevel(band.index.toShort(), it.toInt().toShort())
                            },
                            valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
                            enabled = eqState.enabled,
                            modifier = Modifier.weight(0.75f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bass Boost
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bass Boost",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(0.3f)
                )

                Slider(
                    value = eqState.bassBoost.toFloat(),
                    onValueChange = { viewModel.setBassBoost(it.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    enabled = eqState.enabled,
                    modifier = Modifier.weight(0.7f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
