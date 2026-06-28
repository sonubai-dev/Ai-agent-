package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val voiceId: String,
    val language: String,
    val systemPrompt: String,
    val greetingText: String,
    val knowledgeBase: String,
    val webhookUrl: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String, // UUID or Phone number
    val name: String,
    val phone: String,
    val email: String,
    val company: String,
    val notes: String,
    val tags: String, // Comma-separated
    val status: String, // PENDING, CALLED, CONNECTED, FAILED, DNC
    val leadSource: String,
    val lastCallTimestamp: Long = 0L
)

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey val id: String,
    val name: String,
    val agentId: String,
    val status: String, // DRAFT, SCHEDULED, RUNNING, PAUSED, COMPLETED, CANCELLED
    val scheduledTime: Long = 0L,
    val retryRules: String, // e.g., "Max 3 retries, 5m delay"
    val delayBetweenCallsSeconds: Int = 10,
    val maxAttempts: Int = 3,
    val businessHoursStart: String = "09:00",
    val businessHoursEnd: String = "17:00",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "campaign_contacts", primaryKeys = ["campaignId", "contactId"])
data class CampaignContactCrossRef(
    val campaignId: String,
    val contactId: String,
    val status: String, // PENDING, DIALING, CONNECTED, FAILED, COMPLETED, SKIPPED
    val attempts: Int = 0,
    val lastCallLogId: String? = null
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey val id: String,
    val campaignId: String?,
    val contactId: String,
    val contactName: String,
    val contactPhone: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int,
    val transcript: String,
    val summary: String,
    val leadScore: Int, // 0-100
    val outcome: String, // CONNECTED, BUSY, NO_ANSWER, VOICEMAIL, FAILED
    val retryCount: Int = 0
)
