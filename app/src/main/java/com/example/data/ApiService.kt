package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object ApiService {
    private const val TAG = "ApiService"
    private const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Generates a realistic phone call transcript, summary, and lead score
     * using Gemini API, based on the Agent system prompt, greeting, and Contact context.
     */
    suspend fun generateCallAnalysis(
        agentName: String,
        systemPrompt: String,
        greetingText: String,
        contactName: String,
        contactCompany: String,
        contactNotes: String,
        durationSeconds: Int,
        simulatedOutcome: String // CONNECTED, BUSY, NO_ANSWER, VOICEMAIL
    ): CallAnalysisResult = withContext(Dispatchers.IO) {
        
        // If the outcome is not connected, return a simple non-connected analysis
        if (simulatedOutcome != "CONNECTED") {
            return@withContext CallAnalysisResult(
                transcript = "[Call ended with outcome: $simulatedOutcome]",
                summary = "The outbound SIM call was placed but not connected. Status: $simulatedOutcome.",
                leadScore = 0
            )
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is not configured, returning local simulated data.")
            return@withContext getMockAnalysis(contactName, contactCompany, agentName)
        }

        try {
            val prompt = """
                You are simulating an outbound calling system called CallAgent AI.
                An AI voice agent named "$agentName" just placed a real outbound call to a contact.
                
                Agent Details:
                - System Prompt / Business Rules: "$systemPrompt"
                - Initial Greeting Text: "$greetingText"
                
                Recipient (Contact) Details:
                - Name: "$contactName"
                - Company: "$contactCompany"
                - History/Notes: "$contactNotes"
                - Call Duration: $durationSeconds seconds
                
                Please generate a realistic, professional, double-sided turn-by-turn dialogue transcript (10-15 conversational lines) of this phone call. 
                Then generate a concise executive summary of the conversation (2-3 sentences), the customer's response/objections, and any next steps.
                Finally, assign an integer Lead Score from 0 to 100 representing how qualified this lead is (high interest, appointment booked, or next action scheduled should score 70-100; neutral/follow-up score 35-69; rejected/not interested/wrong number score 0-34).
                
                You MUST return a JSON object with EXACTLY the following structure (do not include any markdown formatting or surrounding ```json blocks, just raw JSON):
                {
                  "transcript": "Agent: [Greeting...]\nCustomer: ...\nAgent: ...",
                  "summary": "...",
                  "leadScore": 85
                }
            """.trimIndent()

            // Build request JSON manually to avoid issues with Kotlin Serialization setup
            val requestBodyJson = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            val partObj = JSONObject()
            
            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            requestBodyJson.put("contents", contentsArray)

            // Add response mime type format configuration to force JSON response
            val generationConfig = JSONObject()
            generationConfig.put("responseMimeType", "application/json")
            requestBodyJson.put("generationConfig", generationConfig)

            val request = Request.Builder()
                .url("$GEMINI_URL?key=$apiKey")
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "API call failed with code ${response.code}: ${response.message}")
                    return@withContext getMockAnalysis(contactName, contactCompany, agentName)
                }

                val responseBodyStr = response.body?.string() ?: ""
                Log.d(TAG, "Raw Response: $responseBodyStr")

                val responseJson = JSONObject(responseBodyStr)
                val candidates = responseJson.getJSONArray("candidates")
                val firstCandidate = candidates.getJSONObject(0)
                val textResponse = firstCandidate.getJSONObject("content")
                    .getJSONArray("parts").getJSONObject(0).getString("text")

                Log.d(TAG, "Parsed Text Content: $textResponse")

                val resultJson = JSONObject(textResponse.trim())
                CallAnalysisResult(
                    transcript = resultJson.optString("transcript", "Transcript unavailable"),
                    summary = resultJson.optString("summary", "Summary unavailable"),
                    leadScore = resultJson.optInt("leadScore", 50)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating AI call analysis, falling back to mock", e)
            getMockAnalysis(contactName, contactCompany, agentName)
        }
    }

    private fun getMockAnalysis(contactName: String, company: String, agentName: String): CallAnalysisResult {
        val randomScore = (40..95).random()
        val transcript = """
            Agent ($agentName): Hello $contactName, this is $agentName calling from Emergent.sh on behalf of our team. How are you today?
            Customer ($contactName): Hello! I'm doing well, thank you. What is this regarding?
            Agent ($agentName): I noticed your company, $company, was interested in automating outbound phone campaigns to scale lead acquisition.
            Customer ($contactName): Yes, actually. We are currently looking for a solution that links directly with our SIM calling plan to keep VoIP costs low.
            Agent ($agentName): That's exactly what we specialize in! Our Android CallAgent AI integrates directly with your local device's SIM card and runs sequential high-converting call campaigns in the background.
            Customer ($contactName): That sounds fantastic. Can you send me over some pricing details and scheduling options?
            Agent ($agentName): Absolutely, Sarah. I will send a summary documentation pack to your email, and we can schedule a quick 15-minute onboarding call tomorrow.
            Customer ($contactName): Great, thank you! Speak tomorrow.
        """.trimIndent()

        val summary = "Contacted $contactName from $company to discuss outbound SIM campaign automation. The recipient expressed strong interest in keeping VoIP carrier costs low and requested a pricing/documentation pack. Scheduled a follow-up call."
        
        return CallAnalysisResult(
            transcript = transcript,
            summary = summary,
            leadScore = randomScore
        )
    }
}

data class CallAnalysisResult(
    val transcript: String,
    val summary: String,
    val leadScore: Int
)
