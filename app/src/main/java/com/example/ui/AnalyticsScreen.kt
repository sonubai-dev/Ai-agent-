package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CallLogEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: CallAgentViewModel,
    modifier: Modifier = Modifier
) {
    val callLogs by viewModel.callLogs.collectAsState()

    val totalCalls = callLogs.size
    val connected = callLogs.count { it.outcome == "CONNECTED" }
    val busy = callLogs.count { it.outcome == "BUSY" }
    val noAnswer = callLogs.count { it.outcome == "NO_ANSWER" }
    val failed = callLogs.count { it.outcome == "FAILED" }

    // Average duration
    val avgDuration = if (totalCalls > 0) callLogs.map { it.durationSeconds }.average().toInt() else 0

    // Conversions (Lead score >= 75)
    val converted = callLogs.count { it.outcome == "CONNECTED" && it.leadScore >= 75 }
    val conversionRate = if (totalCalls > 0) (converted * 100) / totalCalls else 0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Performance Analytics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Summary cards
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    border = CardDefaults.outlinedCardBorder(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Conversion Rate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$conversionRate%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
                        Text("Leads scored >= 75", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    border = CardDefaults.outlinedCardBorder(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Avg Call Duration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${avgDuration}s", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Text("Active conversation seconds", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }
        }

        // Custom Donut Chart (Outcome Breakdowns)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "SIM Call outcome distribution",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (totalCalls == 0) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No call events logged. Launch dialer campaign to see breakdown charts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            // Canvas Drawing
                            Canvas(modifier = Modifier.size(120.dp)) {
                                val total = totalCalls.toFloat()
                                val angleConnected = (connected / total) * 360f
                                val angleBusy = (busy / total) * 360f
                                val angleNoAnswer = (noAnswer / total) * 360f
                                val angleFailed = (failed / total) * 360f

                                var currentAngle = 0f

                                // Connected slice (Green)
                                drawArc(
                                    color = Color(0xFF4CAF50),
                                    startAngle = currentAngle,
                                    sweepAngle = angleConnected,
                                    useCenter = false,
                                    style = Stroke(width = 24f)
                                )
                                currentAngle += angleConnected

                                // Busy slice (Orange)
                                drawArc(
                                    color = Color(0xFFFF9800),
                                    startAngle = currentAngle,
                                    sweepAngle = angleBusy,
                                    useCenter = false,
                                    style = Stroke(width = 24f)
                                )
                                currentAngle += angleBusy

                                // No Answer slice (Blue)
                                drawArc(
                                    color = Color(0xFF2196F3),
                                    startAngle = currentAngle,
                                    sweepAngle = angleNoAnswer,
                                    useCenter = false,
                                    style = Stroke(width = 24f)
                                )
                                currentAngle += angleNoAnswer

                                // Failed slice (Red)
                                drawArc(
                                    color = Color(0xFFF44336),
                                    startAngle = currentAngle,
                                    sweepAngle = angleFailed,
                                    useCenter = false,
                                    style = Stroke(width = 24f)
                                )
                            }

                            // Legend Indicators
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LegendItem(color = Color(0xFF4CAF50), label = "Connected ($connected)")
                                LegendItem(color = Color(0xFFFF9800), label = "Busy ($busy)")
                                LegendItem(color = Color(0xFF2196F3), label = "No Answer ($noAnswer)")
                                LegendItem(color = Color(0xFFF44336), label = "Failed ($failed)")
                            }
                        }
                    }
                }
            }
        }

        // Lead Scoring Trend Chart (Canvas Bar Chart)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Lead qualification scores",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val connectedLogs = callLogs.filter { it.outcome == "CONNECTED" }

                    if (connectedLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No answered calls to map. Lead scoring is active only on connected calls.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        // Drawing bars
                        val scoreGroups = remember(connectedLogs) {
                            val high = connectedLogs.count { it.leadScore >= 75 }
                            val mid = connectedLogs.count { it.leadScore in 40..74 }
                            val low = connectedLogs.count { it.leadScore < 40 }
                            listOf(low, mid, high)
                        }

                        val maxCount = scoreGroups.maxOrNull()?.coerceAtLeast(1) ?: 1

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            scoreGroups.forEachIndexed { index, count ->
                                val label = when (index) {
                                    0 -> "Low Intent (<40)"
                                    1 -> "Neutral Intent (40-74)"
                                    else -> "High Intent (>=75)"
                                }
                                val color = when (index) {
                                    0 -> Color(0xFFF44336)
                                    1 -> Color(0xFF2196F3)
                                    else -> Color(0xFF4CAF50)
                                }

                                val fillRatio = count.toFloat() / maxCount.toFloat()

                                Column {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("$count leads", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(12.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(fillRatio)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(color)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
