package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignManagementScreen(
    viewModel: CallAgentViewModel,
    modifier: Modifier = Modifier
) {
    val campaigns by viewModel.campaigns.collectAsState()
    val agents by viewModel.agents.collectAsState()
    val contacts by viewModel.contacts.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }

    // State for create form
    var name by remember { mutableStateOf("") }
    var selectedAgentId by remember { mutableStateOf("") }
    var retryRules by remember { mutableStateOf("Max 3 retries, 5m delay") }
    var delayBetweenCallsSeconds by remember { mutableStateOf("10") }
    var maxAttempts by remember { mutableStateOf("3") }
    var businessHoursStart by remember { mutableStateOf("09:00") }
    var businessHoursEnd by remember { mutableStateOf("17:00") }

    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (campaigns.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Call Campaigns",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Launch automated calling tasks. Bind contacts, schedule delays, and set active calling periods.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("assemble_campaign_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Assemble Campaign")
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize().padding(bottom = 72.dp)
            ) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Campaign Panel (${campaigns.size})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FilledTonalButton(
                            onClick = { showCreateDialog = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Create")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Campaign")
                        }
                    }
                }

                items(campaigns) { campaign ->
                    val agent = agents.find { it.id == campaign.agentId }
                    CampaignCard(
                        campaign = campaign,
                        agentName = agent?.name ?: "Unknown Agent",
                        viewModel = viewModel,
                        onDelete = { viewModel.deleteCampaign(campaign) }
                    )
                }
            }
        }

        // Add/Create Campaign Dialog
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Assemble Outbound Campaign") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 450.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Campaign Name") },
                            placeholder = { Text("e.g. Summer Outbound Leads") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Agent Selection Dropdown
                        var agentExpanded by remember { mutableStateOf(false) }
                        val activeAgent = agents.find { it.id == selectedAgentId }
                        Box {
                            OutlinedButton(
                                onClick = { agentExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (activeAgent != null) "Agent: ${activeAgent.name}"
                                    else "Select AI Voice Agent"
                                )
                            }
                            DropdownMenu(
                                expanded = agentExpanded,
                                onDismissRequest = { agentExpanded = false }
                            ) {
                                agents.forEach { agent ->
                                    DropdownMenuItem(
                                        text = { Text(agent.name) },
                                        onClick = {
                                            selectedAgentId = agent.id
                                            agentExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = delayBetweenCallsSeconds,
                            onValueChange = { delayBetweenCallsSeconds = it },
                            label = { Text("Delay Between Calls (Seconds)") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = maxAttempts,
                            onValueChange = { maxAttempts = it },
                            label = { Text("Max Call Attempts per Contact") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = retryRules,
                            onValueChange = { retryRules = it },
                            label = { Text("Retry Policy Description") },
                            placeholder = { Text("e.g., Retry 3 times, wait 10m") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = businessHoursStart,
                                onValueChange = { businessHoursStart = it },
                                label = { Text("Allowed From") },
                                placeholder = { Text("09:00") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = businessHoursEnd,
                                onValueChange = { businessHoursEnd = it },
                                label = { Text("Allowed Until") },
                                placeholder = { Text("17:00") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = "All active contacts in the database (${contacts.size}) will be automatically bound to this campaign upon assembly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (name.isNotEmpty() && selectedAgentId.isNotEmpty()) {
                                viewModel.createCampaign(
                                    name = name,
                                    agentId = selectedAgentId,
                                    selectedContactIds = contacts.map { it.id },
                                    retryRules = retryRules,
                                    delayBetweenCallsSeconds = delayBetweenCallsSeconds.toIntOrNull() ?: 10,
                                    maxAttempts = maxAttempts.toIntOrNull() ?: 3,
                                    businessHoursStart = businessHoursStart,
                                    businessHoursEnd = businessHoursEnd
                                )
                                showCreateDialog = false
                                // Reset fields
                                name = ""
                                selectedAgentId = ""
                            }
                        }
                    ) {
                        Text("Assemble")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun CampaignCard(
    campaign: CampaignEntity,
    agentName: String,
    viewModel: CallAgentViewModel,
    onDelete: () -> Unit
) {
    val campaignContactsFlow = remember(campaign.id) { viewModel.getCampaignContacts(campaign.id) }
    val campaignContacts by campaignContactsFlow.collectAsState(initial = emptyList())

    val isRunning by viewModel.isCampaignRunning.collectAsState()
    val activeCampaignId by viewModel.activeCampaignId.collectAsState()

    val isThisCampaignRunning = isRunning && activeCampaignId == campaign.id

    // Calculate details
    val totalContacts = campaignContacts.size
    val completed = campaignContacts.count { it.status == "COMPLETED" }
    val pending = campaignContacts.count { it.status == "PENDING" }
    val failed = campaignContacts.count { it.status == "FAILED" }

    val progress = if (totalContacts > 0) completed.toFloat() / totalContacts.toFloat() else 0f

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = campaign.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Bound Agent: $agentName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Badge status
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (campaign.status) {
                                "RUNNING" -> Color(0xFFE8F5E9)
                                "PAUSED" -> Color(0xFFFFF3E0)
                                "COMPLETED" -> Color(0xFFE3F2FD)
                                else -> Color(0xFFECEFF1)
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = campaign.status,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = when (campaign.status) {
                            "RUNNING" -> Color(0xFF2E7D32)
                            "PAUSED" -> Color(0xFFEF6C00)
                            "COMPLETED" -> Color(0xFF1565C0)
                            else -> Color(0xFF455A64)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Metrics
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Campaign Progress: ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$completed / $totalContacts Completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Detailed breakdowns
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Pending: $pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = "Bouncing / Failed: $failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC62828)
                )
                Text(
                    text = "Dial Delay: ${campaign.delayBetweenCallsSeconds}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isThisCampaignRunning) {
                        Button(
                            onClick = { viewModel.pauseCampaignCalling() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pause")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startCampaignCalling(campaign.id) },
                            enabled = pending > 0 && (!isRunning || activeCampaignId == campaign.id),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dial SIM Campaign")
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.resetCampaignContactsProgress(campaign.id) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Queue")
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Campaign",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
