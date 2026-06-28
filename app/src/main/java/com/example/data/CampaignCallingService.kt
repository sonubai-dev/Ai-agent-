package com.example.data

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.UUID

class CampaignCallingService : Service() {

    companion object {
        private const val TAG = "CampaignCallingService"
        private const val CHANNEL_ID = "CampaignServiceChannel"
        private const val NOTIFICATION_ID = 888

        const val ACTION_START = "com.example.ACTION_START"
        const val ACTION_PAUSE = "com.example.ACTION_PAUSE"
        const val ACTION_STOP = "com.example.ACTION_STOP"
        const val EXTRA_CAMPAIGN_ID = "EXTRA_CAMPAIGN_ID"

        // Live state available for UI observation
        var isServiceRunning = false
            private set
        var currentCampaignId: String? = null
            private set
        var currentContactName: String? = null
            private set
        var currentStatus: String = "Idle"
            private set
        var currentTranscript: String = ""
            private set
        var currentSentiment: String = "Neutral"
            private set
    }

    private lateinit var repository: AppRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var campaignJob: Job? = null

    private var activeCampaign: CampaignEntity? = null
    private var activeAgent: AgentEntity? = null
    private var pendingContacts = mutableListOf<ContactEntity>()
    private var currentContact: ContactEntity? = null
    private var currentAttemptCount = 0

    private var callStartTime: Long = 0L
    private var isCallActive = false
    private var phoneStateReceiver: BroadcastReceiver? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        repository = AppRepository(applicationContext)
        createNotificationChannel()
        registerPhoneStateReceiver()
        acquireWakeLocks()
        isServiceRunning = true
    }

    private fun acquireWakeLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CampaignCallingService::WakeLock")
            wakeLock?.acquire()

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CampaignCallingService::WifiLock")
            wifiLock?.acquire()
            Log.d(TAG, "WakeLocks acquired successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLocks", e)
        }
    }

    private fun releaseWakeLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
            Log.d(TAG, "WakeLocks released successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLocks", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val campaignId = intent?.getStringExtra(EXTRA_CAMPAIGN_ID)

        Log.d(TAG, "onStartCommand Action: $action, CampaignId: $campaignId")

        when (action) {
            ACTION_START -> {
                if (campaignId != null) {
                    startCampaign(campaignId)
                } else {
                    stopSelf()
                }
            }
            ACTION_PAUSE -> {
                pauseCampaign()
            }
            ACTION_STOP -> {
                stopCampaign()
            }
        }

        return START_NOT_STICKY
    }

    private fun startCampaign(campaignId: String) {
        currentCampaignId = campaignId
        currentStatus = "Initializing"
        updateNotification("Starting Campaign...", "Loading list and configurations")

        campaignJob?.cancel()
        campaignJob = serviceScope.launch {
            try {
                val campaign = repository.getCampaignById(campaignId)
                if (campaign == null) {
                    Log.e(TAG, "Campaign not found: $campaignId")
                    stopSelf()
                    return@launch
                }

                activeCampaign = campaign
                repository.updateCampaignStatus(campaignId, "RUNNING")

                val agent = repository.getAgentById(campaign.agentId)
                activeAgent = agent

                // Load all pending contacts for this campaign
                val contacts = repository.getPendingContactsForCampaign(campaignId)
                pendingContacts.clear()
                pendingContacts.addAll(contacts)

                Log.d(TAG, "Loaded ${pendingContacts.size} pending contacts for campaign ${campaign.name}")

                if (pendingContacts.isEmpty()) {
                    completeCampaign()
                } else {
                    dialNext()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting campaign", e)
                currentStatus = "Error: ${e.localizedMessage}"
                updateNotification("Campaign Error", "Failed to start calling sequence")
            }
        }
    }

    private suspend fun dialNext() {
        if (pendingContacts.isEmpty()) {
            completeCampaign()
            return
        }

        val contact = pendingContacts.first()
        currentContact = contact
        currentContactName = contact.name
        currentStatus = "Dialing"
        currentAttemptCount++

        updateNotification(
            "Calling ${contact.name}",
            "Campaign: ${activeCampaign?.name ?: ""} | SIM Call Active"
        )

        Log.d(TAG, "Dialing contact: ${contact.name} (${contact.phone})")

        // Mark cross-ref status as DIALING
        repository.updateCampaignContactStatus(
            campaignId = activeCampaign!!.id,
            contactId = contact.id,
            status = "DIALING",
            attempts = currentAttemptCount,
            lastCallLogId = null
        )

        // Trigger real outbound SIM Call
        callStartTime = System.currentTimeMillis()
        isCallActive = true
        currentTranscript = "Connecting to ${contact.name}...\n"
        currentSentiment = "Analyzing..."

        // Simulate real-time streaming updates
        serviceScope.launch {
            delay(2000L)
            if (!isCallActive) return@launch
            currentTranscript += "[Agent]: Hello, is this ${contact.name}?\n"
            currentSentiment = "Neutral"
            
            delay(3000L)
            if (!isCallActive) return@launch
            currentTranscript += "[${contact.name}]: Yes, speaking.\n"
            currentSentiment = "Positive"
            
            delay(3000L)
            if (!isCallActive) return@launch
            currentTranscript += "[Agent]: I'm calling from ${activeAgent?.name ?: "our company"}. Do you have a moment?\n"
            
            delay(3000L)
            if (!isCallActive) return@launch
            currentTranscript += "[${contact.name}]: Actually I'm a bit busy.\n"
            currentSentiment = "Hesitant"
            
            delay(3000L)
            if (!isCallActive) return@launch
            currentTranscript += "[Agent]: I understand. I will be brief.\n"
            currentSentiment = "Neutral"
        }

        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${contact.phone}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(callIntent)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: CALL_PHONE permission missing!", e)
            currentStatus = "Error: Permission Missing"
            updateNotification("Permission Missing", "Please grant CALL_PHONE permission")
            
            // Revert status to PENDING
            repository.updateCampaignContactStatus(
                campaignId = activeCampaign!!.id,
                contactId = contact.id,
                status = "PENDING",
                attempts = 0,
                lastCallLogId = null
            )
            stopSelf()
        }
    }

    private fun handleCallEnded() {
        if (!isCallActive || currentContact == null || activeCampaign == null) return
        isCallActive = false

        val durationMs = System.currentTimeMillis() - callStartTime
        val durationSeconds = (durationMs / 1000).toInt()
        val contact = currentContact!!
        val campaign = activeCampaign!!

        Log.d(TAG, "Call ended. Contact: ${contact.name}, Duration: $durationSeconds seconds")

        currentStatus = "Generating AI Analysis"
        updateNotification("Processing AI Logs", "Analyzing call transcript & summaries...")

        campaignJob = serviceScope.launch {
            try {
                // Classify outcome based on duration
                val outcome = when {
                    durationSeconds < 3 -> "NO_ANSWER"
                    durationSeconds < 8 -> "BUSY"
                    else -> "CONNECTED"
                }

                // Generate AI Transcript, Summary, and Lead score using Gemini!
                val analysis = ApiService.generateCallAnalysis(
                    agentName = activeAgent?.name ?: "Voice Agent",
                    systemPrompt = activeAgent?.systemPrompt ?: "Outbound Campaign Assistant",
                    greetingText = activeAgent?.greetingText ?: "Hello, how can I help you today?",
                    contactName = contact.name,
                    contactCompany = contact.company,
                    contactNotes = contact.notes,
                    durationSeconds = durationSeconds,
                    simulatedOutcome = outcome
                )

                // Save Call Log
                val logId = UUID.randomUUID().toString()
                val log = CallLogEntity(
                    id = logId,
                    campaignId = campaign.id,
                    contactId = contact.id,
                    contactName = contact.name,
                    contactPhone = contact.phone,
                    durationSeconds = durationSeconds,
                    transcript = analysis.transcript,
                    summary = analysis.summary,
                    leadScore = analysis.leadScore,
                    outcome = outcome,
                    retryCount = currentAttemptCount - 1
                )
                repository.insertCallLog(log)

                // Update cross-reference state to COMPLETED or FAILED
                val nextStatus = if (outcome == "CONNECTED") "COMPLETED" else "FAILED"
                repository.updateCampaignContactStatus(
                    campaignId = campaign.id,
                    contactId = contact.id,
                    status = nextStatus,
                    attempts = currentAttemptCount,
                    lastCallLogId = logId
                )

                // Remove from local pending queue
                pendingContacts.removeFirstOrNull()
                currentAttemptCount = 0

                // Wait for the configured campaign delay between calls (e.g. 10s)
                val delaySeconds = campaign.delayBetweenCallsSeconds
                currentStatus = "Waiting ($delaySeconds s)"
                for (i in delaySeconds downTo 1) {
                    if (!isServiceRunning) return@launch
                    updateNotification(
                        "Next call in $i seconds...",
                        "Completed ${contact.name}. Outcome: $outcome"
                    )
                    delay(1000L)
                }

                // Dial Next contact
                dialNext()

            } catch (e: Exception) {
                Log.e(TAG, "Error handling call completion", e)
                // Proceed to next contact anyway to prevent locking
                pendingContacts.removeFirstOrNull()
                currentAttemptCount = 0
                dialNext()
            }
        }
    }

    private fun pauseCampaign() {
        Log.d(TAG, "Pausing Campaign")
        currentStatus = "Paused"
        campaignJob?.cancel()
        serviceScope.launch {
            currentCampaignId?.let {
                repository.updateCampaignStatus(it, "PAUSED")
            }
            updateNotification("Campaign Paused", "Tap Resume in the app to continue")
        }
    }

    private fun stopCampaign() {
        Log.d(TAG, "Stopping Campaign")
        campaignJob?.cancel()
        serviceScope.launch {
            currentCampaignId?.let {
                repository.updateCampaignStatus(it, "CANCELLED")
            }
            stopSelf()
        }
    }

    private fun completeCampaign() {
        Log.d(TAG, "Campaign Completed")
        currentStatus = "Completed"
        campaignJob?.cancel()
        serviceScope.launch {
            currentCampaignId?.let {
                repository.updateCampaignStatus(it, "COMPLETED")
            }
            updateNotification("Campaign Completed", "All calls placed successfully!")
            delay(3000L)
            stopSelf()
        }
    }

    private fun registerPhoneStateReceiver() {
        phoneStateReceiver = object : BroadcastReceiver() {
            private var lastState = TelephonyManager.CALL_STATE_IDLE

            override fun onReceive(context: Context, intent: Intent) {
                val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                Log.d(TAG, "Phone State Broadcast Received: $stateStr")

                val state = when (stateStr) {
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                    TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                    TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
                    else -> TelephonyManager.CALL_STATE_IDLE
                }

                if (state == lastState) return

                if (lastState == TelephonyManager.CALL_STATE_OFFHOOK && state == TelephonyManager.CALL_STATE_IDLE) {
                    Log.d(TAG, "Call finished (OFFHOOK -> IDLE) detected via BroadcastReceiver!")
                    handleCallEnded()
                }

                lastState = state
            }
        }

        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(phoneStateReceiver, filter)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Outbound Campaign Dialer Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun updateNotification(title: String, text: String) {
        val notificationIntent = Intent(this, Class.forName("com.example.MainActivity"))
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service Destroyed")
        isServiceRunning = false
        currentCampaignId = null
        currentContactName = null
        currentStatus = "Idle"
        
        campaignJob?.cancel()
        serviceScope.cancel()

        phoneStateReceiver?.let {
            unregisterReceiver(it)
        }
        
        releaseWakeLocks()
        super.onDestroy()
    }
}
