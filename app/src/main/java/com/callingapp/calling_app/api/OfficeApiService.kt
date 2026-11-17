package com.callingapp.calling_app.api

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// ─────────────────────────────────────────────────────────────────────────────
// Existing Models
// ─────────────────────────────────────────────────────────────────────────────

data class ExtensionContact(
    @SerializedName("number")        val extension: String  = "",
    @SerializedName("name")          val name: String       = "",
    @SerializedName("email")         val email: String      = "",
    @SerializedName("device_status") val deviceStatus: String = "offline",  // "online"/"offline"
    @SerializedName("dnd_status")    val dndStatus: Boolean = false          // ✅ actual DND field
)
data class LoginResponse(
    val status: Boolean,
    val message: String? = null,
    val extension: String? = null,
    val name: String? = null,
    val tenant: String? = null,
    @SerializedName("tenant_code") val tenantCode: String? = null
)

data class ContactsResponse(
    @SerializedName("status") val status: Boolean              = false,
    @SerializedName("data")   val data: List<ExtensionContact> = emptyList()
)
// ─────────────────────────────────────────────────────────────────────────────
// Emergency Call Models
// ─────────────────────────────────────────────────────────────────────────────

data class EmergencyCallResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("data")   val data: List<EmergencyCall> = emptyList()
)

data class EmergencyCall(
    @SerializedName("id")          val id: Int,
    @SerializedName("src")         val extension: String,
    @SerializedName("dst")         val number: String,
    @SerializedName("clid")        val callerId: String,
    @SerializedName("start")       val date: String,
    @SerializedName("duration")    val duration: Int,
    @SerializedName("disposition") val status: String,
    @SerializedName("dcontext") val dcontext: String
)

// ─────────────────────────────────────────────────────────────────────────────
// Fax Models
// ─────────────────────────────────────────────────────────────────────────────

data class FaxRecord(
    @SerializedName("id")           val id: String         = "",
    @SerializedName("to_number")    val toNumber: String   = "",
    @SerializedName("from_number")  val fromNumber: String = "",
    @SerializedName("date")         val date: String       = "",
    @SerializedName("status")       val status: String     = "",
    @SerializedName("pages")        val pages: Int         = 0,
    @SerializedName("filename")     val filename: String   = ""
)

data class FaxSendResponse(
    @SerializedName("status")  val status: Boolean = false,
    @SerializedName("message") val message: String = "",
    @SerializedName("fax_id")  val faxId: String   = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// Voicemail Models
// ─────────────────────────────────────────────────────────────────────────────

data class VoicemailResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("data")   val data: List<VoicemailRecord> = emptyList()
)

data class VoicemailRecord(
    @SerializedName("msg_id")    val id: String = "",
    @SerializedName("caller_id") val fromNumber: String = "",
    @SerializedName("date")      val date: String = "",
    @SerializedName("duration")  val durationStr: String = "",
    @SerializedName("audio_url") val audioUrl: String = "",
    val fromName: String = "",
    val isRead: Boolean = false,
    val transcript: String = ""
)

data class RingGroupResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("data")   val data: List<RingGroup> = emptyList()
)

data class RingGroup(
    @SerializedName("id")        val id: Int,
    @SerializedName("name")      val name: String = "",
    @SerializedName("extension") val number: String = "",
    @SerializedName("strategy")  val strategy: String = "",
    @SerializedName("timeout")   val timeout: Int = 0,
    @SerializedName("members")   val membersRaw: String = ""
)

data class IvrListResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("data")   val data: List<IvrMenu> = emptyList()
)

data class IvrMenu(
    @SerializedName("id")        val id: Int,
    @SerializedName("name")      val name: String = "",
    @SerializedName("extension") val number: String = "",
    @SerializedName("prompts")   val prompts: IvrPrompts? = null,
    @SerializedName("actions")   val actions: Map<String, IvrAction> = emptyMap(),
    @SerializedName("settings")  val settings: IvrSettings? = null
)

data class IvrPrompts(
    @SerializedName("welcome") val welcome: VoicePrompt? = null,
    @SerializedName("repeat")  val repeat: VoicePrompt?  = null,
    @SerializedName("timeout") val timeout: VoicePrompt? = null,
    @SerializedName("invalid") val invalid: VoicePrompt? = null
)

data class VoicePrompt(
    @SerializedName("id")        val id: Any? = null,
    @SerializedName("name")      val name: String = "",
    @SerializedName("audio_url") val audioUrl: String = ""
)

data class IvrAction(
    @SerializedName("type")         val type: String? = null,
    @SerializedName("target_id")    val targetId: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("details")      val details: IvrActionDetails? = null
)

data class IvrActionDetails(
    @SerializedName("extension") val extension: String? = null,
    @SerializedName("members")   val members: List<String>? = null
)

data class IvrSettings(
    @SerializedName("digit_timeout") val digitTimeout: String? = null,
    @SerializedName("direct_extension_call") val directDial: Boolean = false
)

data class WakeUpCall(
    @SerializedName("extension")      val extension: String = "",
    @SerializedName("room_number")    val roomNumber: String = "",
    @SerializedName("guest_name")     val guestName: String = "",
    @SerializedName("timestamp")      val timestamp: Long = 0L,
    @SerializedName("formatted_date") val formattedDate: String = ""
)

data class WakeUpResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("data")   val data: List<WakeUpCall> = emptyList()
)

data class WakeUpActionResponse(
    @SerializedName("status")  val status: Boolean = false,
    @SerializedName("message") val message: String = ""
)

data class ChatConversation(
    val id: String,
    val name: String,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int = 0
)

data class ChatMessage(
    val id: String,
    val text: String,
    val isFromMe: Boolean,
    val time: String
)

// ─────────────────────────────────────────────────────────────────────────────
// ✅ NEW MODELS: DND and Number Block
// ─────────────────────────────────────────────────────────────────────────────

data class DndStatusResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("is_dnd_enabled") val isDndEnabled: Boolean,
    @SerializedName("message") val message: String = ""
)

data class BlockedNumber(
    @SerializedName("id")         val id: Int,
    @SerializedName("number")     val number: String,
    @SerializedName("block_type") val blockType: String = "ALL", // "ALL", "INCOMING"
    @SerializedName("created_at") val createdAt: String = ""
)

data class BlockedNumbersResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("data")   val data: List<BlockedNumber> = emptyList()
)

data class GeneralActionResponse(
    @SerializedName("status")  val status: Boolean = false,
    @SerializedName("message") val message: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// Tenant Call History / CDR Models
// ─────────────────────────────────────────────────────────────────────────────

data class CallStats(
    @SerializedName("total_calls") val totalCalls: Int = 0,
    @SerializedName("answered")    val answered: Int = 0,
    @SerializedName("missed")      val missed: Int = 0,
    @SerializedName("busy")        val busy: Int = 0,
    @SerializedName("avg_talk")    val avgTalk: Int = 0
)

data class CallRecordItem(
    @SerializedName("id")          val id: Long = 0L,
    @SerializedName("start")       val start: String = "",
    @SerializedName("src")         val source: String = "",
    @SerializedName("dst")         val destination: String = "",
    @SerializedName("clid")        val callerId: String = "",
    @SerializedName("duration")    val duration: Int = 0,
    @SerializedName("billsec")     val billableSeconds: Int = 0,
    @SerializedName("disposition") val disposition: String = "" // "ANSWERED", "NO ANSWER", "BUSY", "FAILED"
)

data class CallHistoryResponse(
    @SerializedName("status")              val status: Boolean = false,
    @SerializedName("reference_extension") val referenceExtension: String = "",
    @SerializedName("stats")               val stats: CallStats? = null,
    @SerializedName("calls")               val calls: List<CallRecordItem> = emptyList(),
    @SerializedName("pagination")          val pagination: PaginationMeta? = null
)

data class PaginationMeta(
    @SerializedName("current_page") val currentPage: Int = 1,
    @SerializedName("last_page")    val lastPage: Int = 1,
    @SerializedName("per_page")     val perPage: Int = 30,
    @SerializedName("total")        val total: Int = 0
)


// ─────────────────────────────────────────────────────────────────────────────
// API Interface
// ─────────────────────────────────────────────────────────────────────────────

interface OfficeApiService {

    @POST("api/mobile/login")
    suspend fun login(@Body body: Map<String, String>): Response<LoginResponse>

    @GET("api/mobile/extensions")
    suspend fun getExtensions(
        @Query("tenant_code") tenantCode: String,
        @Query("extension")   myExtension: String,
        @Query("password")    password: String
    ): Response<List<ExtensionContact>>

    @GET("api/911-calls/{extension}")
    suspend fun getEmergencyCalls(@Path("extension") extension: String): Response<EmergencyCallResponse>

    @GET("api/mobile/emergency-calls")
    suspend fun getEmergencyCalls(
        @Query("tenant_code") tenantCode: String,
        @Query("extension")   extension: String,
        @Query("password")    password: String
    ): Response<List<EmergencyCall>>

    @GET("api/mobile/fax")
    suspend fun getFaxList(
        @Query("tenant_code") tenantCode: String,
        @Query("extension")   extension: String,
        @Query("password")    password: String
    ): Response<List<FaxRecord>>

    @Multipart
    @POST("api/mobile/fax/send")
    suspend fun sendFax(
        @Part("tenant_code") tenantCode: RequestBody,
        @Part("extension")   extension: RequestBody,
        @Part("password")    password: RequestBody,
        @Part("to_number")   toNumber: RequestBody,
        @Part file:          MultipartBody.Part
    ): Response<FaxSendResponse>

    @GET("api/voicemails/{extension}")
    suspend fun getVoicemail(
        @Path("extension")    extension: String,
        @Query("tenant_code") tenantCode: String,
        @Query("password")    password: String
    ): Response<VoicemailResponse>

    @POST("api/mobile/voicemail/read")
    suspend fun markVoicemailRead(@Body body: Map<String, String>): Response<Map<String, Boolean>>

    @GET("api/ring-groups/{extension}")
    suspend fun getRingGroups(@Path("extension") extension: String): Response<RingGroupResponse>

    @GET("api/ivr-list/{extension}")
    suspend fun getIvrMenus(@Path("extension") extension: String): Response<IvrListResponse>

    @GET("api/mobile/wakeup/{extension}")
    suspend fun getWakeUpCalls(@Path("extension") extension: String): Response<WakeUpResponse>

    @POST("api/mobile/wakeup/set")
    suspend fun scheduleWakeUp(@Body body: Map<String, String>): Response<WakeUpActionResponse>

    @HTTP(method = "DELETE", path = "api/mobile/wakeup/delete", hasBody = true)
    suspend fun cancelWakeUp(@Body body: Map<String, String>): Response<WakeUpActionResponse>

    @GET("api/contacts/{extension}")
    suspend fun getContacts(@Path("extension") extension: String): Response<ContactsResponse>

    // ── ✅ NEW ENDPOINTS: DND & Blocking ───────────────────────────────────────

    @GET("api/dnd/status/{extension}")
    suspend fun getDndStatus(@Path("extension") extension: String): Response<DndStatusResponse>

    @POST("api/dnd/toggle/{extension}")
    suspend fun toggleDnd(@Path("extension") extension: String): Response<DndStatusResponse>

    @GET("api/blocked-numbers/{extension}")
    suspend fun getBlockedNumbers(@Path("extension") extension: String): Response<BlockedNumbersResponse>

    @POST("api/blocked-numbers/add/{extension}")
    suspend fun addBlockedNumber(
        @Path("extension") extension: String,
        @Body body: Map<String, String>
    ): Response<GeneralActionResponse>

    @HTTP(method = "DELETE", path = "api/blocked-numbers/remove/{extension}", hasBody = true)
    suspend fun removeBlockedNumber(
        @Path("extension") extension: String,
        @Body body: Map<String, String>
    ): Response<GeneralActionResponse>

    @GET("api/call-history/{extension}")
    suspend fun getCallHistory(
        @Path("extension") extension: String,
        @Query("page") page: Int = 1,
        @Query("from_date") fromDate: String? = null,
        @Query("to_date") toDate: String? = null
    ): Response<CallHistoryResponse>
}

// ─────────────────────────────────────────────────────────────────────────────
// Retrofit singleton
// ─────────────────────────────────────────────────────────────────────────────

val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl("https://core.cnetvoip.cloud/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val apiService: OfficeApiService = retrofit.create(OfficeApiService::class.java)