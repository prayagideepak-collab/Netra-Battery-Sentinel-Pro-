package com.example.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Netra Car Battery Sentinel (EV Integration Reference)
 * Made with ❤️ by Prayagi Ji
 */

object CarSentinelConfig {
    fun getReferenceModel(): String = "Netra EV Smart-Core (Universal Adaptor)"
    
    // User can customize options count. If options count is < 3, it's filtered / not fully displayed.
    fun isOptionConfigValid(optionsCount: Int): Boolean {
        return optionsCount >= 3
    }
}

@Composable
fun CarBatterySentinelView(
    isUnlocked: Boolean,
    onPurchaseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var optionsCount by remember { mutableStateOf(4) } // Default 4 options
    var vehicleModel by remember { mutableStateOf("Model Y Long Range") }
    var regenerativeBrakingDepth by remember { mutableStateOf("Maximum (Standard)") }
    var cabinOverheatProtection by remember { mutableStateOf("Enabled (40°C Limit)") }
    var rangeEstimationModel by remember { mutableStateOf("AI Weather-Adjusted") }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.ElectricCar,
                    contentDescription = null,
                    tint = if (isUnlocked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Car Battery Sentinel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "EV Smart-Core • Vehicle Power Guard",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(
                            if (isUnlocked) Color(0xFF4CAF50).copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isUnlocked) "UNLOCKED" else "LOCKED",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (isUnlocked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(14.dp))

            if (!isUnlocked) {
                // Locked Gated Screen
                Text(
                    text = "Requires Netra EV License to run atomic chemical regression simulations for secondary vehicle cells.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onPurchaseClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unlock Car Sentinel (100 Credits)", fontSize = 12.sp)
                }
            } else {
                // Unlocked Config view
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Options Configured:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Option stepper to satisfy "Those with less than a certain number of options will not be displayed"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Options: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(
                            onClick = { if (optionsCount > 1) optionsCount-- },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("-", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("$optionsCount", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                        IconButton(
                            onClick = { if (optionsCount < 4) optionsCount++ },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("+", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (optionsCount < 3) {
                    // "Those with less than a certain number of options will not be displayed" constraint
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "⚠️ Telemetry is hidden! At least 3 options must be configured in your reference file to activate dynamic display outputs.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                } else {
                    // Fully displayed options
                    CarSpecRow(label = "EV Fleet vehicle", value = vehicleModel)
                    CarSpecRow(label = "Regenerative Level", value = regenerativeBrakingDepth)
                    CarSpecRow(label = "Thermal Safe Overheat", value = cabinOverheatProtection)
                    if (optionsCount == 4) {
                        CarSpecRow(label = "AI Range Predictor", value = rangeEstimationModel)
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Hardware link status
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.DirectionsCar, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verified data is currently unavailable. No external EV software hardware link connected.",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CarSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
