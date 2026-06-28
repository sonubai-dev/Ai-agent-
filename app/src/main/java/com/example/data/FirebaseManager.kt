package com.example.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Robust, production-grade Firebase Manager that handles JWT-like Authentication
 * (Email/Password and Google Sign-In) and Database sync with Firebase Firestore or Realtime Database.
 * Includes automatic and graceful local fallback when Google services / google-services.json are missing.
 */
class FirebaseManager(private val context: Context) {
    private val TAG = "FirebaseManager"

    // Dynamic initializers to prevent app crashes if Firebase is not configured/initialized
    val isInitialized: Boolean by lazy {
        try {
            val app = FirebaseApp.getInstance()
            app != null
        } catch (e: IllegalStateException) {
            try {
                val app = FirebaseApp.initializeApp(context)
                app != null
            } catch (ex: Exception) {
                Log.w(TAG, "Firebase could not be initialized. Operating in clean Local Offline Sandbox mode.")
                false
            }
        }
    }

    val auth: FirebaseAuth? by lazy {
        if (isInitialized) FirebaseAuth.getInstance() else null
    }

    val firestore: FirebaseFirestore? by lazy {
        if (isInitialized) FirebaseFirestore.getInstance() else null
    }

    val rtdb: FirebaseDatabase? by lazy {
        if (isInitialized) FirebaseDatabase.getInstance() else null
    }

    /**
     * Authenticate via Email/Password (Sign In)
     */
    suspend fun signInWithEmail(email: String, password: String): AuthResultWrapper = withContext(Dispatchers.IO) {
        val authInstance = auth
        if (authInstance == null) {
            return@withContext AuthResultWrapper.FallbackSuccess(
                email = email,
                token = "local_sandbox_jwt_token_email",
                message = "Firebase not initialized. Authenticated via Local Offline Sandbox."
            )
        }

        try {
            val result = authInstance.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                val tokenResult = user.getIdToken(false).await()
                AuthResultWrapper.Success(
                    email = user.email ?: email,
                    token = tokenResult.token ?: "",
                    userId = user.uid
                )
            } else {
                AuthResultWrapper.Error("Authentication returned empty user record.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Sign In Error", e)
            AuthResultWrapper.Error(e.localizedMessage ?: "Unknown Authentication Error")
        }
    }

    /**
     * Authenticate via Email/Password (Sign Up / Register)
     */
    suspend fun signUpWithEmail(email: String, password: String): AuthResultWrapper = withContext(Dispatchers.IO) {
        val authInstance = auth
        if (authInstance == null) {
            return@withContext AuthResultWrapper.FallbackSuccess(
                email = email,
                token = "local_sandbox_jwt_token_signup",
                message = "Firebase not initialized. Admin account registered in Local Sandbox."
            )
        }

        try {
            val result = authInstance.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                val tokenResult = user.getIdToken(true).await()
                AuthResultWrapper.Success(
                    email = user.email ?: email,
                    token = tokenResult.token ?: "",
                    userId = user.uid
                )
            } else {
                AuthResultWrapper.Error("User registration succeeded but user record is null.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Sign Up Error", e)
            AuthResultWrapper.Error(e.localizedMessage ?: "Unknown Registration Error")
        }
    }

    /**
     * Authenticate via Google ID Token (Gmail Auth)
     */
    suspend fun signInWithGoogleToken(idToken: String): AuthResultWrapper = withContext(Dispatchers.IO) {
        val authInstance = auth
        if (authInstance == null) {
            return@withContext AuthResultWrapper.FallbackSuccess(
                email = "demo.agent@google.com",
                token = "local_sandbox_jwt_token_google",
                message = "Firebase not initialized. Authenticated Google Identity via Sandbox."
            )
        }

        try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            val result = authInstance.signInWithCredential(credential).await()
            val user = result.user
            if (user != null) {
                val tokenResult = user.getIdToken(false).await()
                AuthResultWrapper.Success(
                    email = user.email ?: "google.user@gmail.com",
                    token = tokenResult.token ?: "",
                    userId = user.uid
                )
            } else {
                AuthResultWrapper.Error("Google Credential Authentication returned empty user.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Google Auth Error", e)
            AuthResultWrapper.Error(e.localizedMessage ?: "Unknown Google Authentication Error")
        }
    }

    /**
     * Log Out user from session
     */
    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Error logging out from Firebase Auth", e)
        }
    }

    // --- Firebase Database / Firestore Synchronization Interfaces ---

    /**
     * Synchronize a Call Log to the Remote Firebase Backend (Firestore & RTDB redundantly for real-time CRM updates)
     */
    suspend fun syncCallLogToFirebase(log: CallLogEntity) = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext

        // 1. Sync to Firestore (Excellent for dashboard analysis, indexes, querying)
        try {
            val db = firestore
            if (db != null) {
                val logMap = hashMapOf(
                    "id" to log.id,
                    "campaignId" to log.campaignId,
                    "contactId" to log.contactId,
                    "contactName" to log.contactName,
                    "contactPhone" to log.contactPhone,
                    "timestamp" to log.timestamp,
                    "durationSeconds" to log.durationSeconds,
                    "transcript" to log.transcript,
                    "summary" to log.summary,
                    "leadScore" to log.leadScore,
                    "outcome" to log.outcome,
                    "retryCount" to log.retryCount
                )
                db.collection("call_logs").document(log.id).set(logMap).await()
                Log.d(TAG, "Successfully synchronized Call Log ${log.id} to Firestore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncing Call Log to Firestore", e)
        }

        // 2. Sync to Realtime Database (Excellent for real-time live calls feeds on browser dashboards)
        try {
            val rtdbInstance = rtdb
            if (rtdbInstance != null) {
                val ref = rtdbInstance.getReference("live_call_activity").child(log.id)
                val logMap = hashMapOf(
                    "id" to log.id,
                    "contactName" to log.contactName,
                    "contactPhone" to log.contactPhone,
                    "timestamp" to log.timestamp,
                    "outcome" to log.outcome,
                    "leadScore" to log.leadScore,
                    "duration" to log.durationSeconds
                )
                ref.setValue(logMap).await()
                Log.d(TAG, "Successfully synchronized Call Log ${log.id} to Realtime Database")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncing Call Log to Realtime Database", e)
        }
    }

    /**
     * Synchronize a Contact status update to Firebase
     */
    suspend fun syncContactToFirebase(contact: ContactEntity) = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext
        try {
            val db = firestore
            if (db != null) {
                val contactMap = hashMapOf(
                    "id" to contact.id,
                    "name" to contact.name,
                    "phone" to contact.phone,
                    "email" to contact.email,
                    "company" to contact.company,
                    "notes" to contact.notes,
                    "tags" to contact.tags,
                    "status" to contact.status,
                    "leadSource" to contact.leadSource,
                    "lastCallTimestamp" to contact.lastCallTimestamp
                )
                db.collection("contacts").document(contact.id).set(contactMap).await()
                Log.d(TAG, "Successfully synchronized Contact ${contact.id} to Firestore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncing Contact to Firestore", e)
        }
    }

    /**
     * Synchronize Campaign state to Firebase
     */
    suspend fun syncCampaignToFirebase(campaign: CampaignEntity) = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext
        try {
            val db = firestore
            if (db != null) {
                val campaignMap = hashMapOf(
                    "id" to campaign.id,
                    "name" to campaign.name,
                    "agentId" to campaign.agentId,
                    "status" to campaign.status,
                    "scheduledTime" to campaign.scheduledTime,
                    "retryRules" to campaign.retryRules,
                    "delayBetweenCallsSeconds" to campaign.delayBetweenCallsSeconds,
                    "maxAttempts" to campaign.maxAttempts,
                    "businessHoursStart" to campaign.businessHoursStart,
                    "businessHoursEnd" to campaign.businessHoursEnd,
                    "createdAt" to campaign.createdAt
                )
                db.collection("campaigns").document(campaign.id).set(campaignMap).await()
                Log.d(TAG, "Successfully synchronized Campaign ${campaign.id} to Firestore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncing Campaign to Firestore", e)
        }
    }

    /**
     * Synchronize Agent to Firebase
     */
    suspend fun syncAgentToFirebase(agent: AgentEntity) = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext
        try {
            val db = firestore
            if (db != null) {
                val agentMap = hashMapOf(
                    "id" to agent.id,
                    "name" to agent.name,
                    "voiceId" to agent.voiceId,
                    "language" to agent.language,
                    "systemPrompt" to agent.systemPrompt,
                    "greetingText" to agent.greetingText,
                    "knowledgeBase" to agent.knowledgeBase,
                    "webhookUrl" to agent.webhookUrl,
                    "createdAt" to agent.createdAt
                )
                db.collection("agents").document(agent.id).set(agentMap).await()
                Log.d(TAG, "Successfully synchronized Agent ${agent.id} to Firestore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncing Agent to Firestore", e)
        }
    }
}

/**
 * Standardized Authentication Results wrapper to handle exceptions, success tokens,
 * and elegant sandbox fallback states safely.
 */
sealed class AuthResultWrapper {
    data class Success(val email: String, val token: String, val userId: String) : AuthResultWrapper()
    data class FallbackSuccess(val email: String, val token: String, val message: String) : AuthResultWrapper()
    data class Error(val errorMessage: String) : AuthResultWrapper()
}
