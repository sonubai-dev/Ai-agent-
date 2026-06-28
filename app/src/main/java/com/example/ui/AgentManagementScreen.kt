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
import com.example.data.AgentEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentManagementScreen(
    viewModel: CallAgentViewModel,
    modifier: Modifier = Modifier
) {
    val agents by viewModel.agents.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    // State for temporary form
    var name by remember { mutableStateOf("") }
    var voiceId by remember { mutableStateOf("omni-voice-aria-female") }
    var language by remember { mutableStateOf("en-US") }
    var systemPrompt by remember { mutableStateOf("") }
    var greetingText by remember { mutableStateOf("") }
    var knowledgeBase by remember { mutableStateOf("") }
    var webhookUrl by remember { mutableStateOf("") }

    val languages = listOf("en-US", "en-GB", "es-ES", "es-MX", "fr-FR", "de-DE", "ja-JP")
    val voices = listOf(
        "omni-voice-aria-female-smooth" to "Aria (Female Smooth)",
        "omni-voice-marcus-male-deep" to "Marcus (Male Deep)",
        "omni-voice-clara-female-professional" to "Clara (Female Business)",
        "omni-voice-jonathan-male-cheerful" to "Jonathan (Male Energetic)"
    )

    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (agents.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No AI Voice Agents",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Configure a custom agent with prompts, greeting messages, and voice options to start your call pipeline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Assemble AI Agent")
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
                            text = "AI Voice Agents (${agents.size})",
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
                            Text("New Agent")
                        }
                    }
                }

                items(agents) { agent ->
                    AgentCard(
                        agent = agent,
                        onDelete = { viewModel.deleteAgent(agent) },
                        onClone = {
                            viewModel.addAgent(
                                name = agent.name + " (Copy)",
                                voiceId = agent.voiceId,
                                language = agent.language,
                                systemPrompt = agent.systemPrompt,
                                greetingText = agent.greetingText,
                                knowledgeBase = agent.knowledgeBase,
                                webhookUrl = agent.webhookUrl
                            )
                        }
                    )
                }
            }
        }

        // Add/Create Agent Dialog
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Assemble New Omnidimension AI Agent") },
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
                            label = { Text("Agent Name") },
                            placeholder = { Text("e.g. Sales Outbound Closer") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Voice Picker
                        var voiceExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { voiceExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Voice: " + (voices.find { it.first == voiceId }?.second ?: voiceId))
                            }
                            DropdownMenu(
                                expanded = voiceExpanded,
                                onDismissRequest = { voiceExpanded = false }
                            ) {
                                voices.forEach { voice ->
                                    DropdownMenuItem(
                                        text = { Text(voice.second) },
                                        onClick = {
                                            voiceId = voice.first
                                            voiceExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Language Picker
                        var langExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { langExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Language: $language")
                            }
                            DropdownMenu(
                                expanded = langExpanded,
                                onDismissRequest = { langExpanded = false }
                            ) {
                                languages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang) },
                                        onClick = {
                                            language = lang
                                            langExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = greetingText,
                            onValueChange = { greetingText = it },
                            label = { Text("Greeting Phrase (Starts conversation)") },
                            placeholder = { Text("Hello! This is Aria calling about...") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            label = { Text("System Instruction Script") },
                            placeholder = { Text("You are a helpful closer... Overcome budget objections...") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = knowledgeBase,
                            onValueChange = { knowledgeBase = it },
                            label = { Text("Supplementary Knowledge Base") },
                            placeholder = { Text("Product specifications, prices, or answers to common FAQs.") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = webhookUrl,
                            onValueChange = { webhookUrl = it },
                            label = { Text("Outbound Webhook Sync Endpoint") },
                            placeholder = { Text("https://company.crm/webhooks/calls") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (name.isNotEmpty()) {
                                viewModel.addAgent(
                                    name = name,
                                    voiceId = voiceId,
                                    language = language,
                                    systemPrompt = systemPrompt,
                                    greetingText = greetingText,
                                    knowledgeBase = knowledgeBase,
                                    webhookUrl = webhookUrl
                                )
                                showCreateDialog = false
                                // Reset fields
                                name = ""
                                systemPrompt = ""
                                greetingText = ""
                                knowledgeBase = ""
                                webhookUrl = ""
                            }
                        }
                    ) {
                        Text("Create")
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
fun AgentCard(
    agent: AgentEntity,
    onDelete: () -> Unit,
    onClone: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = agent.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Voice: ${agent.voiceId} • Lang: ${agent.language}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onClone) {
                        Icon(
                            imageVector = Icons.Default.CopyAll,
                            contentDescription = "Clone",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Greeting Greeting Phrase:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "\"${agent.greetingText}\"",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "System Prompt Script Rules:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = agent.systemPrompt,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            if (agent.webhookUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Webhook",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sync: ${agent.webhookUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
