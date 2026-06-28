package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class CallAgentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)
    private val TAG = "CallAgentViewModel"

    // Exposed DB State Flows
    val agents: StateFlow<List<AgentEntity>> = repository.allAgents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts: StateFlow<List<ContactEntity>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val campaigns: StateFlow<List<CampaignEntity>> = repository.allCampaigns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callLogs: StateFlow<List<CallLogEntity>> = repository.allCallLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Service State tracking
    private val _isCampaignRunning = MutableStateFlow(false)
    val isCampaignRunning: StateFlow<Boolean> = _isCampaignRunning.asStateFlow()

    private val _activeCampaignId = MutableStateFlow<String?>(null)
    val activeCampaignId: StateFlow<String?> = _activeCampaignId.asStateFlow()

    private val _currentDialingContact = MutableStateFlow<String?>(null)
    val currentDialingContact: StateFlow<String?> = _currentDialingContact.asStateFlow()

    private val _campaignEngineStatus = MutableStateFlow("Idle")
    val campaignEngineStatus: StateFlow<String> = _campaignEngineStatus.asStateFlow()

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    private val _currentSentiment = MutableStateFlow("Neutral")
    val currentSentiment: StateFlow<String> = _currentSentiment.asStateFlow()

    // API Key State (Persisted in SharedPreferences)
    private val prefs = application.getSharedPreferences("call_agent_prefs", Context.MODE_PRIVATE)
    private val _omnidimensionApiKey = MutableStateFlow(prefs.getString("omni_api_key", "") ?: "")
    val omnidimensionApiKey: StateFlow<String> = _omnidimensionApiKey.asStateFlow()

    private val _jwtToken = MutableStateFlow(prefs.getString("jwt_token", "") ?: "")
    val jwtToken: StateFlow<String> = _jwtToken.asStateFlow()

    private val _currentUserEmail = MutableStateFlow(prefs.getString("user_email", "") ?: "")
    val currentUserEmail: StateFlow<String> = _currentUserEmail.asStateFlow()

    // Firebase Auth Error State Tracking
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val firebaseManager = FirebaseManager(application)

    init {
        // Monitor active campaign status periodically from service static state
        viewModelScope.launch {
            while (true) {
                _isCampaignRunning.value = CampaignCallingService.isServiceRunning
                _activeCampaignId.value = CampaignCallingService.currentCampaignId
                _currentDialingContact.value = CampaignCallingService.currentContactName
                _campaignEngineStatus.value = CampaignCallingService.currentStatus
                _currentTranscript.value = CampaignCallingService.currentTranscript
                _currentSentiment.value = CampaignCallingService.currentSentiment
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    // --- Authentication Actions ---
    fun login(email: String, passwordOrProvider: String, isGoogle: Boolean = false) {
        viewModelScope.launch {
            _authError.value = null
            val result = if (isGoogle) {
                firebaseManager.signInWithGoogleToken(passwordOrProvider)
            } else {
                firebaseManager.signInWithEmail(email, passwordOrProvider)
            }

            when (result) {
                is AuthResultWrapper.Success -> {
                    prefs.edit()
                        .putString("user_email", result.email)
                        .putString("jwt_token", result.token)
                        .apply()
                    _currentUserEmail.value = result.email
                    _jwtToken.value = result.token
                }
                is AuthResultWrapper.FallbackSuccess -> {
                    prefs.edit()
                        .putString("user_email", result.email)
                        .putString("jwt_token", result.token)
                        .apply()
                    _currentUserEmail.value = result.email
                    _jwtToken.value = result.token
                    Log.i(TAG, "Fallback Success: ${result.message}")
                }
                is AuthResultWrapper.Error -> {
                    _authError.value = result.errorMessage
                    Log.e(TAG, "Auth Error: ${result.errorMessage}")
                }
            }
        }
    }

    fun signUp(email: String, passwordOrProvider: String) {
        viewModelScope.launch {
            _authError.value = null
            val result = firebaseManager.signUpWithEmail(email, passwordOrProvider)
            when (result) {
                is AuthResultWrapper.Success -> {
                    prefs.edit()
                        .putString("user_email", result.email)
                        .putString("jwt_token", result.token)
                        .apply()
                    _currentUserEmail.value = result.email
                    _jwtToken.value = result.token
                }
                is AuthResultWrapper.FallbackSuccess -> {
                    prefs.edit()
                        .putString("user_email", result.email)
                        .putString("jwt_token", result.token)
                        .apply()
                    _currentUserEmail.value = result.email
                    _jwtToken.value = result.token
                    Log.i(TAG, "Fallback Success: ${result.message}")
                }
                is AuthResultWrapper.Error -> {
                    _authError.value = result.errorMessage
                    Log.e(TAG, "Registration Error: ${result.errorMessage}")
                }
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun logout() {
        firebaseManager.signOut()
        prefs.edit()
            .remove("user_email")
            .remove("jwt_token")
            .apply()
        _currentUserEmail.value = ""
        _jwtToken.value = ""
        _authError.value = null
    }

    // --- Settings Actions ---
    fun saveApiKey(key: String) {
        prefs.edit().putString("omni_api_key", key).apply()
        _omnidimensionApiKey.value = key
    }

    // --- Agent Actions ---
    fun addAgent(name: String, voiceId: String, language: String, systemPrompt: String, greetingText: String, knowledgeBase: String, webhookUrl: String) {
        viewModelScope.launch {
            val agent = AgentEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                voiceId = voiceId,
                language = language,
                systemPrompt = systemPrompt,
                greetingText = greetingText,
                knowledgeBase = knowledgeBase,
                webhookUrl = webhookUrl
            )
            repository.insertAgent(agent)
        }
    }

    fun updateAgent(agent: AgentEntity) {
        viewModelScope.launch {
            repository.insertAgent(agent)
        }
    }

    fun deleteAgent(agent: AgentEntity) {
        viewModelScope.launch {
            repository.deleteAgent(agent)
        }
    }

    // --- Contact Actions ---
    fun addContact(name: String, phone: String, email: String, company: String, notes: String, tags: String, leadSource: String) {
        viewModelScope.launch {
            val contact = ContactEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                phone = phone,
                email = email,
                company = company,
                notes = notes,
                tags = tags,
                status = "PENDING",
                leadSource = leadSource
            )
            repository.insertContact(contact)
        }
    }

    fun updateContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.insertContact(contact)
        }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }

    // --- Campaign Actions ---
    fun createCampaign(name: String, agentId: String, selectedContactIds: List<String>, retryRules: String, delayBetweenCallsSeconds: Int, maxAttempts: Int, businessHoursStart: String, businessHoursEnd: String) {
        viewModelScope.launch {
            val campaignId = UUID.randomUUID().toString()
            val campaign = CampaignEntity(
                id = campaignId,
                name = name,
                agentId = agentId,
                status = "DRAFT",
                retryRules = retryRules,
                delayBetweenCallsSeconds = delayBetweenCallsSeconds,
                maxAttempts = maxAttempts,
                businessHoursStart = businessHoursStart,
                businessHoursEnd = businessHoursEnd
            )
            repository.insertCampaign(campaign)

            // Link contacts
            val crossRefs = selectedContactIds.map { contactId ->
                CampaignContactCrossRef(
                    campaignId = campaignId,
                    contactId = contactId,
                    status = "PENDING"
                )
            }
            repository.insertCampaignContacts(crossRefs)
        }
    }

    fun updateCampaign(campaign: CampaignEntity) {
        viewModelScope.launch {
            repository.insertCampaign(campaign)
        }
    }

    fun deleteCampaign(campaign: CampaignEntity) {
        viewModelScope.launch {
            repository.deleteCampaign(campaign)
        }
    }

    fun getCampaignContacts(campaignId: String): Flow<List<CampaignContactWithDetails>> {
        return repository.getCampaignContactsWithDetails(campaignId)
    }

    // --- Campaign Control Center (Foreground Service Integrations) ---
    fun startCampaignCalling(campaignId: String) {
        Log.d(TAG, "Triggering Start Calling Service for: $campaignId")
        val context = getApplication<Application>().applicationContext
        val serviceIntent = Intent(context, CampaignCallingService::class.java).apply {
            action = CampaignCallingService.ACTION_START
            putExtra(CampaignCallingService.EXTRA_CAMPAIGN_ID, campaignId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun pauseCampaignCalling() {
        Log.d(TAG, "Triggering Pause Calling Service")
        val context = getApplication<Application>().applicationContext
        val serviceIntent = Intent(context, CampaignCallingService::class.java).apply {
            action = CampaignCallingService.ACTION_PAUSE
        }
        context.startService(serviceIntent)
    }

    fun stopCampaignCalling() {
        Log.d(TAG, "Triggering Stop Calling Service")
        val context = getApplication<Application>().applicationContext
        val serviceIntent = Intent(context, CampaignCallingService::class.java).apply {
            action = CampaignCallingService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }

    fun resetCampaignContactsProgress(campaignId: String) {
        viewModelScope.launch {
            repository.resetCampaignContacts(campaignId)
            repository.updateCampaignStatus(campaignId, "DRAFT")
        }
    }

    // --- Seed CRM Demonstration Data (First-turn onboarding accelerator) ---
    fun seedSampleData() {
        viewModelScope.launch {
            // Seed 2 Agents
            val agent1Id = UUID.randomUUID().toString()
            val agent1 = AgentEntity(
                id = agent1Id,
                name = "Aria - Senior Sales Closer",
                voiceId = "omni-voice-aria-female-smooth",
                language = "en-US",
                systemPrompt = "You are a warm, persuasive corporate sales closer. Your goal is to schedule a 15-minute product demonstration. Listen actively, overcome budget objections by emphasizing ROI, and remain extremely professional.",
                greetingText = "Hi there! This is Aria calling from Emergent.sh. I hope I'm catching you at a good time?",
                knowledgeBase = "Emergent.sh specializes in automated SIM card cold calling algorithms. Average campaign ROI is 400% with immediate setup.",
                webhookUrl = "https://api.emergent.sh/webhooks/aria"
            )

            val agent2Id = UUID.randomUUID().toString()
            val agent2 = AgentEntity(
                id = agent2Id,
                name = "Marcus - Client Support",
                voiceId = "omni-voice-marcus-male-deep",
                language = "en-US",
                systemPrompt = "You are Marcus, a patient client experience manager. You are calling current users to collect structured qualitative feedback on their experience, address any interface friction, and thank them.",
                greetingText = "Hello! Marcus here from CallAgent support. I wanted to check in on how your campaign dashboard is working today?",
                knowledgeBase = "Release v2.4 supports dual SIM configurations, multi-language prompt scripts, and full custom background synthesis.",
                webhookUrl = "https://api.emergent.sh/webhooks/marcus"
            )

            repository.insertAgent(agent1)
            repository.insertAgent(agent2)

            // Seed 5 Contacts
            val contact1 = ContactEntity(
                id = UUID.randomUUID().toString(),
                name = "Sarah Jenkins",
                phone = "555-0192",
                email = "sarah.j@acmetech.io",
                company = "Acme Technologies",
                notes = "Expressed interest in cold calling solutions for SaaS sales. Prefers afternoon follow-ups.",
                tags = "SaaS, Warm Lead",
                status = "PENDING",
                leadSource = "Google Ads"
            )
            val contact2 = ContactEntity(
                id = UUID.randomUUID().toString(),
                name = "Daniel Kovac",
                phone = "555-0144",
                email = "daniel@kovacgrowth.com",
                company = "Kovac Growth Partners",
                notes = "Managing partner of high-growth marketing firm. Looking to deploy voice agents to 50 active client campaigns.",
                tags = "Agency, Enterprise",
                status = "PENDING",
                leadSource = "Referral"
            )
            val contact3 = ContactEntity(
                id = UUID.randomUUID().toString(),
                name = "Elena Rostova",
                phone = "555-0177",
                email = "elena@rostova-design.co",
                company = "Rostova Creative Studio",
                notes = "Boutique creative design director. Very design-centric, focused on premium custom integration aesthetics.",
                tags = "Boutique, Cold Lead",
                status = "PENDING",
                leadSource = "Cold Outreach"
            )
            val contact4 = ContactEntity(
                id = UUID.randomUUID().toString(),
                name = "Michael Vance",
                phone = "555-0121",
                email = "m.vance@vancerealty.com",
                company = "Vance Luxury Real Estate",
                notes = "Requires direct SIM integration with property listing databases to auto-dial high-net-worth sellers.",
                tags = "Real Estate, Hot Lead",
                status = "PENDING",
                leadSource = "Organic Search"
            )
            val contact5 = ContactEntity(
                id = UUID.randomUUID().toString(),
                name = "Aisha Nkosi",
                phone = "555-0185",
                email = "nkosi.a@finventures.za",
                company = "Nkosi Financial Ventures",
                notes = "Inquired about data compliance and offline encryption policies. Strict local regulatory standards.",
                tags = "Finance, High Intent",
                status = "PENDING",
                leadSource = "Linkedin"
            )

            val contactsList = listOf(contact1, contact2, contact3, contact4, contact5)
            repository.insertContacts(contactsList)

            // Seed 1 active Campaign with Agent 1
            val campaignId = UUID.randomUUID().toString()
            val campaign = CampaignEntity(
                id = campaignId,
                name = "Summer SaaS Accelerator Campaign",
                agentId = agent1Id,
                status = "DRAFT",
                retryRules = "Max 3 attempts, 5 minute intervals",
                delayBetweenCallsSeconds = 8,
                maxAttempts = 3,
                businessHoursStart = "09:00",
                businessHoursEnd = "17:00"
            )
            repository.insertCampaign(campaign)

            // Link seed contacts to this campaign
            val crossRefs = contactsList.map { contact ->
                CampaignContactCrossRef(
                    campaignId = campaignId,
                    contactId = contact.id,
                    status = "PENDING"
                )
            }
            repository.insertCampaignContacts(crossRefs)
        }
    }
}
