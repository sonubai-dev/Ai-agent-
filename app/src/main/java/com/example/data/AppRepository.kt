package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class AppRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val agentDao = database.agentDao()
    private val contactDao = database.contactDao()
    private val campaignDao = database.campaignDao()
    private val callLogDao = database.callLogDao()
    private val firebaseManager = FirebaseManager(context)

    // Agents
    val allAgents: Flow<List<AgentEntity>> = agentDao.getAllAgents()
    suspend fun getAgentById(id: String): AgentEntity? = agentDao.getAgentById(id)
    suspend fun insertAgent(agent: AgentEntity) {
        agentDao.insertAgent(agent)
        firebaseManager.syncAgentToFirebase(agent)
    }
    suspend fun deleteAgent(agent: AgentEntity) = agentDao.deleteAgent(agent)

    // Contacts
    val allContacts: Flow<List<ContactEntity>> = contactDao.getAllContacts()
    suspend fun insertContact(contact: ContactEntity) {
        contactDao.insertContact(contact)
        firebaseManager.syncContactToFirebase(contact)
    }
    suspend fun insertContacts(contacts: List<ContactEntity>) {
        contactDao.insertContacts(contacts)
        contacts.forEach { contact ->
            firebaseManager.syncContactToFirebase(contact)
        }
    }
    suspend fun deleteContact(contact: ContactEntity) = contactDao.deleteContact(contact)
    suspend fun deleteAllContacts() = contactDao.deleteAllContacts()

    // Campaigns
    val allCampaigns: Flow<List<CampaignEntity>> = campaignDao.getAllCampaigns()
    suspend fun getCampaignById(id: String): CampaignEntity? = campaignDao.getCampaignById(id)
    suspend fun insertCampaign(campaign: CampaignEntity) {
        campaignDao.insertCampaign(campaign)
        firebaseManager.syncCampaignToFirebase(campaign)
    }
    suspend fun updateCampaignStatus(id: String, status: String) {
        campaignDao.updateCampaignStatus(id, status)
        getCampaignById(id)?.let { campaign ->
            firebaseManager.syncCampaignToFirebase(campaign)
        }
    }
    suspend fun deleteCampaign(campaign: CampaignEntity) = campaignDao.deleteCampaign(campaign)

    // Campaign Contacts
    suspend fun insertCampaignContact(crossRef: CampaignContactCrossRef) = campaignDao.insertCampaignContact(crossRef)
    suspend fun insertCampaignContacts(crossRefs: List<CampaignContactCrossRef>) = campaignDao.insertCampaignContacts(crossRefs)
    fun getCampaignContactsWithDetails(campaignId: String): Flow<List<CampaignContactWithDetails>> =
        campaignDao.getCampaignContactsWithDetails(campaignId)
    suspend fun getPendingContactsForCampaign(campaignId: String): List<ContactEntity> =
        campaignDao.getPendingContactsForCampaign(campaignId)
    suspend fun updateCampaignContactStatus(campaignId: String, contactId: String, status: String, attempts: Int, lastCallLogId: String?) {
        campaignDao.updateCampaignContactStatus(campaignId, contactId, status, attempts, lastCallLogId)
        // Trigger status sync for the contact
        contactDao.getAllContacts().collect { list ->
            list.find { it.id == contactId }?.let { contact ->
                // Make a copy with the updated status
                val updatedContact = contact.copy(status = status, lastCallTimestamp = System.currentTimeMillis())
                firebaseManager.syncContactToFirebase(updatedContact)
            }
        }
    }
    suspend fun resetCampaignContacts(campaignId: String) = campaignDao.resetCampaignContacts(campaignId)

    // Call Logs
    val allCallLogs: Flow<List<CallLogEntity>> = callLogDao.getAllCallLogs()
    fun getCallLogsForCampaign(campaignId: String): Flow<List<CallLogEntity>> = callLogDao.getCallLogsForCampaign(campaignId)
    suspend fun insertCallLog(log: CallLogEntity) {
        callLogDao.insertCallLog(log)
        firebaseManager.syncCallLogToFirebase(log)
    }
}
