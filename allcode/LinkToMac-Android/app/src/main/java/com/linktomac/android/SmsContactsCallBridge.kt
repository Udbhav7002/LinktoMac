package com.linktomac.android

import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject

object SmsContactsCallBridge {

    @SuppressLint("Range")
    fun syncSmsAndContactsToMac(context: Context) {
        val contactsMap = mutableMapOf<String, String>()
        val contactsArray = JSONArray()

        // 1. Load Contacts
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < 200) {
                    val id = cursor.getString(0) ?: ""
                    val name = cursor.getString(1) ?: ""
                    val number = cursor.getString(2)?.replace(" ", "") ?: ""
                    if (number.isNotEmpty()) {
                        contactsMap[number] = name
                        contactsArray.put(JSONObject().apply {
                            put("id", "$id-$number")
                            put("name", name)
                            put("phoneNumber", number)
                        })
                        count++
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Load Recent SMS Messages & Group by Address
        val threadsMap = linkedMapOf<String, MutableList<JSONObject>>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                null, null,
                Telephony.Sms.DATE + " DESC LIMIT 120"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: ""
                    val addr = cursor.getString(1) ?: "Unknown"
                    val body = cursor.getString(2) ?: ""
                    val date = cursor.getLong(3)
                    val type = cursor.getInt(4)
                    val isIncoming = (type == Telephony.Sms.MESSAGE_TYPE_INBOX)

                    val msgJson = JSONObject().apply {
                        put("id", id)
                        put("address", addr)
                        put("body", body)
                        put("date", date)
                        put("isIncoming", isIncoming)
                    }
                    threadsMap.getOrPut(addr) { mutableListOf() }.add(0, msgJson)
                }
            }
        } catch (_: Exception) {}

        val threadsArray = JSONArray()
        for ((addr, messages) in threadsMap) {
            val lastMsg = messages.lastOrNull()
            val contactName = contactsMap[addr] ?: addr
            threadsArray.put(JSONObject().apply {
                put("id", addr)
                put("contactName", contactName)
                put("address", addr)
                put("lastMessage", lastMsg?.optString("body") ?: "")
                put("timestamp", lastMsg?.optLong("date") ?: System.currentTimeMillis())
                put("messages", JSONArray(messages))
            })
        }

        val frame = WireProtocol.packJson("SMS_THREADS_SYNC") {
            put("smsThreads", threadsArray)
            put("contacts", contactsArray)
        }
        LinkConnectionService.instance?.sendRawFrame(frame)
    }

    fun sendSms(context: Context, address: String, body: String) {
        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(address, null, body, null, null)
        } catch (_: Exception) {}
    }

    // MARK: - Call Controls (Cellular Audio Remains on Phone)
    @SuppressLint("MissingPermission")
    fun handleCallAction(context: Context, action: String, number: String, smsReply: String) {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        try {
            when (action) {
                "ANSWER" -> tm.acceptRingingCall()
                "DECLINE", "END" -> tm.endCall()
                "DECLINE_WITH_SMS" -> {
                    tm.endCall()
                    if (number.isNotEmpty() && smsReply.isNotEmpty()) {
                        sendSms(context, number, smsReply)
                    }
                }
                "DIAL" -> {
                    if (number.isNotEmpty()) {
                        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(callIntent)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun registerCallStateReceiver(context: Context) {
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val stateStr = intent?.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Cellular Call"
                val mappedState = when (stateStr) {
                    TelephonyManager.EXTRA_STATE_RINGING -> "RINGING"
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> "OFFHOOK"
                    else -> "IDLE"
                }
                val frame = WireProtocol.packJson(
                    type = "CALL_STATE",
                    payloadMap = mapOf(
                        "state" to mappedState,
                        "number" to incomingNumber,
                        "contactName" to incomingNumber
                    )
                )
                LinkConnectionService.instance?.sendRawFrame(frame)
            }
        }, filter)
    }
}







