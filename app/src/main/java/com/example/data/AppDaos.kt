package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class CampaignContactWithDetails(
    val contactId: String,
    val name: String,
    val phone: String,
    val company: String,
    val status: String, // PENDING, DIALING, CONNECTED, FAILED, COMPLETED, SKIPPED
    val attempts: Int
)

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents ORDER BY createdAt DESC")
    fun getAllAgents(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents WHERE id = :id LIMIT 1")
    suspend fun getAgentById(id: String): AgentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgent(agent: AgentEntity)

    @Delete
    suspend fun deleteAgent(agent: AgentEntity)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()
}

@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaigns ORDER BY createdAt DESC")
    fun getAllCampaigns(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns WHERE id = :id LIMIT 1")
    suspend fun getCampaignById(id: String): CampaignEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaign(campaign: CampaignEntity)

    @Query("UPDATE campaigns SET status = :status WHERE id = :id")
    suspend fun updateCampaignStatus(id: String, status: String)

    @Delete
    suspend fun deleteCampaign(campaign: CampaignEntity)

    // Campaign Contacts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaignContact(crossRef: CampaignContactCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaignContacts(crossRefs: List<CampaignContactCrossRef>)

    @Query("""
        SELECT c.id as contactId, c.name, c.phone, c.company, cc.status, cc.attempts 
        FROM campaign_contacts cc 
        INNER JOIN contacts c ON cc.contactId = c.id 
        WHERE cc.campaignId = :campaignId
    """)
    fun getCampaignContactsWithDetails(campaignId: String): Flow<List<CampaignContactWithDetails>>

    @Query("""
        SELECT c.* FROM contacts c 
        INNER JOIN campaign_contacts cc ON c.id = cc.contactId 
        WHERE cc.campaignId = :campaignId AND cc.status = 'PENDING'
    """)
    suspend fun getPendingContactsForCampaign(campaignId: String): List<ContactEntity>

    @Query("UPDATE campaign_contacts SET status = :status, attempts = :attempts, lastCallLogId = :lastCallLogId WHERE campaignId = :campaignId AND contactId = :contactId")
    suspend fun updateCampaignContactStatus(campaignId: String, contactId: String, status: String, attempts: Int, lastCallLogId: String?)

    @Query("UPDATE campaign_contacts SET status = 'PENDING', attempts = 0, lastCallLogId = NULL WHERE campaignId = :campaignId")
    suspend fun resetCampaignContacts(campaignId: String)
}

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE campaignId = :campaignId ORDER BY timestamp DESC")
    fun getCallLogsForCampaign(campaignId: String): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(log: CallLogEntity)
}
