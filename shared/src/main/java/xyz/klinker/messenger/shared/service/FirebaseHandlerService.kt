/*
 * Copyright (C) 2017 Luke Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.klinker.messenger.shared.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.util.Log
import com.klinker.android.send_message.Utils
import org.json.JSONException
import org.json.JSONObject
import xyz.klinker.messenger.api.implementation.Account
import xyz.klinker.messenger.api.implementation.ApiUtils
import xyz.klinker.messenger.api.implementation.LoginActivity
import xyz.klinker.messenger.api.implementation.firebase.FirebaseDownloadCallback
import xyz.klinker.messenger.api.implementation.firebase.MessengerFirebaseMessagingService
import xyz.klinker.messenger.encryption.EncryptionUtils
import xyz.klinker.messenger.shared.R
import xyz.klinker.messenger.shared.data.*
import xyz.klinker.messenger.shared.data.model.*
import xyz.klinker.messenger.shared.receiver.ConversationListUpdatedReceiver
import xyz.klinker.messenger.shared.receiver.MessageListUpdatedReceiver
import xyz.klinker.messenger.shared.service.jobs.MarkAsSentJob
import xyz.klinker.messenger.shared.service.jobs.ScheduledMessageJob
import xyz.klinker.messenger.shared.service.jobs.SignoutJob
import xyz.klinker.messenger.shared.service.jobs.SubscriptionExpirationCheckJob
import xyz.klinker.messenger.shared.service.notification.NotificationService
import xyz.klinker.messenger.shared.util.*
import java.io.File
import java.util.*

/**
 * Receiver responsible for processing firebase data messages and persisting to the database.
 */
class FirebaseHandlerService : WakefulIntentService("FirebaseHandlerService") {

    override fun doWakefulWork(intent: Intent?) {
        if (intent != null && intent.action != null && intent.action == MessengerFirebaseMessagingService.ACTION_FIREBASE_MESSAGE_RECEIVED) {
            val operation = intent.getStringExtra(MessengerFirebaseMessagingService.EXTRA_OPERATION)
            val data = intent.getStringExtra(MessengerFirebaseMessagingService.EXTRA_DATA)

            process(this, operation, data)
        }
    }

    companion object {

        private val TAG = "FirebaseHandlerService"
        private val INFORMATION_NOTIFICATION_ID = 13

        fun process(context: Context, operation: String, data: String) {
            val account = Account

            // received a message without having initialized an account yet
            // could happen if their subscription ends
            if (account.key == null) {
                return
            }

            val encryptionUtils = account.encryptor

            if (encryptionUtils == null && account.exists()) {
                context.startActivity(Intent(context, LoginActivity::class.java))
                return
            }


            Log.v(TAG, "operation: $operation, contents: $data")

            try {
                val json = JSONObject(data)
                when (operation) {
                    "removed_account" -> removeAccount(json, context)
                    "updated_account" -> updatedAccount(json, context)
                    "cleaned_account" -> cleanAccount(json, context)
                    "added_message" -> addMessage(json, context, encryptionUtils)
                    "update_message_type" -> updateMessageType(json, context)
                    "updated_message" -> updateMessage(json, context)
                    "removed_message" -> removeMessage(json, context)
                    "cleanup_messages" -> cleanupMessages(json, context)
                    "added_contact" -> addContact(json, context, encryptionUtils)
                    "updated_contact" -> updateContact(json, context, encryptionUtils)
                    "removed_contact" -> removeContact(json, context)
                    "removed_contact_by_id" -> removeContactById(json, context)
                    "added_conversation" -> addConversation(json, context, encryptionUtils)
                    "update_conversation_snippet" -> updateConversationSnippet(json, context, encryptionUtils)
                    "update_conversation_title" -> updateConversationTitle(json, context, encryptionUtils)
                    "updated_conversation" -> updateConversation(json, context, encryptionUtils)
                    "removed_conversation" -> removeConversation(json, context)
                    "read_conversation" -> readConversation(json, context)
                    "seen_conversation" -> seenConversation(json, context)
                    "archive_conversation" -> archiveConversation(json, context)
                    "seen_conversations" -> seenConversations(context)
                    "added_draft" -> addDraft(json, context, encryptionUtils)
                    "removed_drafts" -> removeDrafts(json, context)
                    "added_blacklist" -> addBlacklist(json, context, encryptionUtils)
                    "removed_blacklist" -> removeBlacklist(json, context)
                    "added_scheduled_message" -> addScheduledMessage(json, context, encryptionUtils)
                    "updated_scheduled_message" -> updatedScheduledMessage(json, context, encryptionUtils)
                    "removed_scheduled_message" -> removeScheduledMessage(json, context)
                    "update_setting" -> updateSetting(json, context)
                    "dismissed_notification" -> dismissNotification(json, context)
                    "update_subscription" -> updateSubscription(json, context)
                    "update_primary_device" -> updatePrimaryDevice(json, context)
                    "feature_flag" -> writeFeatureFlag(json, context)
                    "forward_to_phone" -> forwardToPhone(json, context)
                    else -> Log.e(TAG, "unsupported operation: " + operation)
                }
            } catch (e: JSONException) {
                Log.e(TAG, "error parsing data json", e)
            }

        }

        @Throws(JSONException::class)
        private fun removeAccount(json: JSONObject,context: Context) {
            val account = Account

            if (json.getString("id") == account.accountId) {
                Log.v(TAG, "clearing account")
                DataSource.clearTables(context)
                account.clearAccount(context)
            } else {
                Log.v(TAG, "ids do not match, did not clear account")
            }
        }

        @Throws(JSONException::class)
        private fun updatedAccount(json: JSONObject, context: Context) {
            val account = Account
            val name = json.getString("real_name")
            val number = json.getString("phone_number")

            if (json.getString("id") == account.accountId) {
                account.setName(context, name)
                account.setPhoneNumber(context, number)
                Log.v(TAG, "updated account name and number")
            } else {
                Log.v(TAG, "ids do not match, did not clear account")
            }
        }

        @Throws(JSONException::class)
        private fun cleanAccount(json: JSONObject,context: Context) {
            val account = Account

            if (json.getString("id") == account.accountId) {
                Log.v(TAG, "clearing account")
                DataSource.clearTables(context)
            } else {
                Log.v(TAG, "ids do not match, did not clear account")
            }
        }

        @Throws(JSONException::class)
        private fun addMessage(json: JSONObject,context: Context, encryptionUtils: EncryptionUtils?) {
            val id = getLong(json, "id")
            if (DataSource.getMessage(context, id) == null) {
                var conversation = DataSource.getConversation(context, getLong(json, "conversation_id"))

                val message = Message()
                message.id = id
                message.conversationId = if (conversation == null) getLong(json, "conversation_id") else conversation.id
                message.type = json.getInt("type")
                message.timestamp = getLong(json, "timestamp")
                message.read = json.getBoolean("read")
                message.seen = json.getBoolean("seen")
                message.simPhoneNumber = if (conversation == null || conversation.simSubscriptionId == null)
                    null
                else
                    DualSimUtils.getPhoneNumberFromSimSubscription(conversation.simSubscriptionId!!)

                if (json.has("sent_device")) {
                    try {
                        message.sentDeviceId = json.getLong("sent_device")
                    } catch (e: Exception) {
                        message.sentDeviceId = -1L
                    }

                } else {
                    message.sentDeviceId = -1L
                }

                try {
                    message.data = encryptionUtils!!.decrypt(json.getString("data"))
                    message.mimeType = encryptionUtils.decrypt(json.getString("mime_type"))
                    message.from = encryptionUtils.decrypt(if (json.has("from")) json.getString("from") else null)
                } catch (e: Exception) {
                    Log.v(TAG, "error adding message, from decyrption.")
                    message.data = context.getString(R.string.error_decrypting)
                    message.mimeType = MimeType.TEXT_PLAIN
                    message.from = null
                }

                if (json.has("color") && json.getString("color") != "null") {
                    message.color = json.getInt("color")
                }

                if (message.data == "firebase -1" && message.mimeType != MimeType.TEXT_PLAIN) {
                    Log.v(TAG, "downloading binary from firebase")

                    addMessageAfterFirebaseDownload(context, encryptionUtils!!, message)
                    return
                }

                val messageId = DataSource.insertMessage(context, message, message.conversationId, true, false)
                Log.v(TAG, "added message")

                if (!Utils.isDefaultSmsApp(context) && message.type == Message.TYPE_SENDING) {
                    Thread {
                        try {
                            Thread.sleep(500)
                        } catch (e: Exception) {
                        }

                        DataSource.updateMessageType(context, id, Message.TYPE_SENT, true)
                    }.start()
                }

                val isSending = message.type == Message.TYPE_SENDING

                if (!Utils.isDefaultSmsApp(context) && isSending) {
                    message.type = Message.TYPE_SENT
                }

                if (Account.primary && isSending) {
                    conversation = DataSource.getConversation(context, message.conversationId)

                    if (conversation != null) {
                        if (message.mimeType == MimeType.TEXT_PLAIN) {
                            SendUtils(conversation.simSubscriptionId)
                                    .send(context, message.data!!, conversation.phoneNumbers!!)
                            MarkAsSentJob.scheduleNextRun(context, messageId)
                        } else {
                            SendUtils(conversation.simSubscriptionId)
                                    .send(context, "", conversation.phoneNumbers!!,
                                            Uri.parse(message.data), message.mimeType)
                            MarkAsSentJob.scheduleNextRun(context, messageId)
                        }
                    } else {
                        Log.e(TAG, "trying to send message without the conversation, so can't find phone numbers")
                    }

                    Log.v(TAG, "sent message")
                }

                MessageListUpdatedReceiver.sendBroadcast(context, message)
                ConversationListUpdatedReceiver.sendBroadcast(context, message.conversationId,
                        if (message.mimeType == MimeType.TEXT_PLAIN) message.data else "",
                        message.type != Message.TYPE_RECEIVED)

                if (message.type == Message.TYPE_RECEIVED) {
                    context.startService(Intent(context, NotificationService::class.java))
                } else if (isSending) {
                    DataSource.readConversation(context, message.conversationId, false)
                    NotificationManagerCompat.from(context).cancel(message.conversationId.toInt())
                }
            } else {
                Log.v(TAG, "message already exists, not doing anything with it")
            }
        }

        private fun addMessageAfterFirebaseDownload(context: Context, encryptionUtils: EncryptionUtils, message: Message) {
            val apiUtils = ApiUtils
            apiUtils.saveFirebaseFolderRef(Account.accountId)
            val file = File(context.filesDir,
                    message.id.toString() + MimeType.getExtension(message.mimeType!!))

            DataSource.insertMessage(context, message, message.conversationId, false, false)
            Log.v(TAG, "added message")

            val isSending = message.type == Message.TYPE_SENDING

            if (!Utils.isDefaultSmsApp(context) && isSending) {
                message.type = Message.TYPE_SENT
            }

            val callback = FirebaseDownloadCallback {
                message.data = Uri.fromFile(file).toString()
                DataSource.updateMessageData(context, message.id, message.data!!)
                MessageListUpdatedReceiver.sendBroadcast(context, message.conversationId)

                if (Account.primary && isSending) {
                    val conversation = DataSource.getConversation(context, message.conversationId)

                    if (conversation != null) {
                        if (message.mimeType == MimeType.TEXT_PLAIN) {
                            SendUtils(conversation.simSubscriptionId)
                                    .send(context, message.data!!, conversation.phoneNumbers!!)
                        } else {
                            SendUtils(conversation.simSubscriptionId)
                                    .send(context, "", conversation.phoneNumbers!!,
                                            Uri.parse(message.data), message.mimeType)
                        }
                    } else {
                        Log.e(TAG, "trying to send message without the conversation, so can't find phone numbers")
                    }

                    Log.v(TAG, "sent message")
                }

                if (!Utils.isDefaultSmsApp(context) && message.type == Message.TYPE_SENDING) {
                    DataSource.updateMessageType(context, message.id, Message.TYPE_SENT, false)
                }

                MessageListUpdatedReceiver.sendBroadcast(context, message)
                ConversationListUpdatedReceiver.sendBroadcast(context, message.conversationId,
                        if (message.mimeType == MimeType.TEXT_PLAIN) message.data else "",
                        message.type != Message.TYPE_RECEIVED)

                when (message.type) {
                    Message.TYPE_RECEIVED -> context.startService(Intent(context, NotificationService::class.java))
                    Message.TYPE_SENDING -> {
                        DataSource.readConversation(context, message.conversationId, false)
                        NotificationManagerCompat.from(context).cancel(message.conversationId.toInt())
                    }
                    else -> {
                    }
                }
            }

            apiUtils.downloadFileFromFirebase(Account.accountId, file, message.id, encryptionUtils, callback, 0)

        }

        @Throws(JSONException::class)
        private fun updateMessage(json: JSONObject,context: Context) {
            val id = getLong(json, "id")
            val type = json.getInt("type")
            DataSource.updateMessageType(context, id, type, false)

            val message = DataSource.getMessage(context, id)
            if (message != null) {
                MessageListUpdatedReceiver.sendBroadcast(context, message)
            }

            Log.v(TAG, "updated message type")
        }

        @Throws(JSONException::class)
        private fun updateMessageType(json: JSONObject,context: Context) {
            val id = getLong(json, "id")
            val type = json.getInt("message_type")
            DataSource.updateMessageType(context, id, type, false)

            val message = DataSource.getMessage(context, id)
            if (message != null) {
                MessageListUpdatedReceiver.sendBroadcast(context, message)
            }

            Log.v(TAG, "updated message type")
        }

        @Throws(JSONException::class)
        private fun removeMessage(json: JSONObject,context: Context) {
            val id = getLong(json, "id")
            DataSource.deleteMessage(context, id, false)
            Log.v(TAG, "removed message")
        }

        @Throws(JSONException::class)
        private fun cleanupMessages(json: JSONObject,context: Context) {
            val timestamp = getLong(json, "timestamp")
            DataSource.cleanupOldMessages(context, timestamp, false)
            Log.v(TAG, "cleaned up old messages")
        }

        @Throws(JSONException::class)
        private fun addConversation(json: JSONObject,context: Context, encryptionUtils: EncryptionUtils?) {
            val conversation = Conversation()
            conversation.id = getLong(json, "id")
            conversation.colors.color = json.getInt("color")
            conversation.colors.colorDark = json.getInt("color_dark")
            conversation.colors.colorLight = json.getInt("color_light")
            conversation.colors.colorAccent = json.getInt("color_accent")
            conversation.ledColor = json.getInt("led_color")
            conversation.pinned = json.getBoolean("pinned")
            conversation.read = json.getBoolean("read")
            conversation.timestamp = getLong(json, "timestamp")
            conversation.title = encryptionUtils!!.decrypt(json.getString("title"))
            conversation.phoneNumbers = encryptionUtils.decrypt(json.getString("phone_numbers"))
            conversation.snippet = encryptionUtils.decrypt(json.getString("snippet"))
            conversation.ringtoneUri = encryptionUtils.decrypt(if (json.has("ringtone"))
                json.getString("ringtone")
            else
                null)
            conversation.imageUri = ContactUtils.findImageUri(conversation.phoneNumbers, context)
            conversation.idMatcher = encryptionUtils.decrypt(json.getString("id_matcher"))
            conversation.mute = json.getBoolean("mute")
            conversation.archive = json.getBoolean("archive")
            conversation.simSubscriptionId = -1

            val image = ImageUtils.getContactImage(conversation.imageUri, context)
            if (conversation.imageUri != null && image == null) {
                conversation.imageUri = null
            } else if (conversation.imageUri != null) {
                conversation.imageUri = conversation.imageUri!! + "/photo"
            }

            image?.recycle()

            try {
                DataSource.insertConversation(context, conversation, false)
            } catch (e: SQLiteConstraintException) {
                // conversation already exists
            }

        }

        @Throws(JSONException::class)
        private fun updateContact(json: JSONObject,context: Context, encryptionUtils: EncryptionUtils?) {
            try {
                val contact = Contact()
                contact.phoneNumber = encryptionUtils!!.decrypt(json.getString("phone_number"))
                contact.name = encryptionUtils.decrypt(json.getString("name"))
                contact.colors.color = json.getInt("color")
                contact.colors.colorDark = json.getInt("color_dark")
                contact.colors.colorLight = json.getInt("color_light")
                contact.colors.colorAccent = json.getInt("color_accent")

                DataSource.updateContact(context, contact, false)
                Log.v(TAG, "updated contact")
            } catch (e: RuntimeException) {
                Log.e(TAG, "failed to update contact b/c of decrypting data")
            }

        }

        @Throws(JSONException::class)
        private fun removeContact(json: JSONObject,context: Context) {
            val phoneNumber = json.getString("phone_number")
            DataSource.deleteContact(context, phoneNumber, false)
            Log.v(TAG, "removed contact")
        }

        @Throws(JSONException::class)
        private fun removeContactById(json: JSONObject,context: Context) {
            val ids = json.getString("id").split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            DataSource.deleteContacts(context, ids, false)
            Log.v(TAG, "removed contacts by id")
        }

        @Throws(JSONException::class)
        private fun addContact(json: JSONObject,context: Context, encryptionUtils: EncryptionUtils?) {

            try {
                val contact = Contact()
                contact.phoneNumber = encryptionUtils!!.decrypt(json.getString("phone_number"))
                contact.name = encryptionUtils.decrypt(json.getString("name"))
                contact.colors.color = json.getInt("color")
                contact.colors.colorDark = json.getInt("color_dark")
                contact.colors.colorLight = json.getInt("color_light")
                contact.colors.colorAccent = json.getInt("color_accent")

                DataSource.insertContact(context, contact, false)
                Log.v(TAG, "added contact")
            } catch (e: SQLiteConstraintException) {
                // contact already exists
                Log.e(TAG, "error adding contact", e)
            } catch (e: Exception) {
                // error decrypting
            }

        }

        @Throws(JSONException::class)
        private fun updateConversation(json: JSONObject,context: Context, encryptionUtils: EncryptionUtils?) {
            try {
                val conversation = Conversation()
                conversation.id = getLong(json, "id")
                conversation.title = encryptionUtils!!.decrypt(json.getString("title"))
                conversation.colors.color = json.getInt("color")
                conversation.colors.colorDark = json.getInt("color_dark")
                conversation.colors.colorLight = json.getInt("color_light")
                conversation.colors.colorAccent = json.getInt("color_accent")
                conversation.ledColor = json.getInt("led_color")
                conversation.pinned = json.getBoolean("pinned")
                conversation.ringtoneUri = encryptionUtils.decrypt(if (json.has("ringtone"))
                    json.getString("ringtone")
                else
                    null)
                conversation.mute = json.getBoolean("mute")
                conversation.read = json.getBoolean("read")
                conversation.read = json.getBoolean("read")
                conversation.archive = json.getBoolean("archive")
                conversation.privateNotifications = json.getBoolean("private_notifications")

                DataSource.updateConversationSettings(context, conversation, false)

                if (conversation.read) {
                    DataSource.readConversation(context, conversation.id, false)
                }
                Log.v(TAG, "updated conversation")
            } catch (e: RuntimeException) {
                Log.e(TAG, "failed to update conversation b/c of decrypting data")
            }

        }

        @Throws(JSONException::class)
        private fun updateConversationTitle(json: JSONObject,context: Context, encryptionUtils: EncryptionUtils?) {
            try {
                DataSource.updateConversationTitle(context, getLong(json, "id"),
                        encryptionUtils!!.decrypt(json.getString("title"))!!, false
                )

                Log.v(TAG, "updated conversation title")
            } catch (e: RuntimeException) {
                Log.e(TAG, "failed to update conversation title b/c of decrypting data")
            }

        }

        @Throws(JSONException::class)
        private fun updateConversationSnippet(json: JSONObject,context: Context, encryptionUtils: EncryptionUtils?) {
            try {
                DataSource.updateConversation(context,
                        getLong(json, "id"),
                        json.getBoolean("read"),
                        getLong(json, "timestamp"),
                        encryptionUtils!!.decrypt(json.getString("snippet")),
                        MimeType.TEXT_PLAIN,
                        json.getBoolean("archive"),
                        false
                )

                Log.v(TAG, "updated conversation snippet")
            } catch (e: RuntimeException) {
                Log.e(TAG, "failed to update conversation snippet b/c of decrypting data")
            }

        }

        @Throws(JSONException::class)
        private fun removeConversation(json: JSONObject,context: Context) {
            val id = getLong(json, "id")
            DataSource.deleteConversation(context, id, false)
            Log.v(TAG, "removed conversation")
        }

        @Throws(JSONException::class)
        private fun readConversation(json: JSONObject,context: Context) {
            val id = getLong(json, "id")
            val deviceId = json.getString("android_device")

            if (deviceId == null || deviceId != Account.deviceId) {
                val conversation = DataSource.getConversation(context, id)
                DataSource.readConversation(context, id, false)

                if (conversation != null && !conversation.read) {
                    ConversationListUpdatedReceiver.sendBroadcast(context, id, conversation.snippet, true)
                }

                Log.v(TAG, "read conversation")
            }
        }

        @Throws(JSONException::class)
        private fun seenConversation(json: JSONObject,context: Context) {
            val id = getLong(json, "id")
            DataSource.seenConversation(context, id, false)
            Log.v(TAG, "seen conversation")
        }

        @Throws(JSONException::class)
        private fun archiveConversation(json: JSONObject,context: Context) {
            val id = getLong(json, "id")
            val archive = json.getBoolean("archive")
            DataSource.archiveConversation(context, id, archive, false)
            Log.v(TAG, "archive conversation: " + archive)
        }

        @Throws(JSONException::class)
        private fun seenConversations(context: Context) {
            DataSource.seenConversations(context, false)
            Log.v(TAG, "seen all conversations")
        }

        @Throws(JSONException::class)
        private fun addDraft(json: JSONObject,context: Context, encryptionUtils: EncryptionUtils?) {
            val draft = Draft()
            draft.id = getLong(json, "id")
            draft.conversationId = getLong(json, "conversation_id")
            draft.data = encryptionUtils!!.decrypt(json.getString("data"))
            draft.mimeType = encryptionUtils.decrypt(json.getString("mime_type"))

            DataSource.insertDraft(context, draft, false)
            Log.v(TAG, "added draft")
        }

        @Throws(JSONException::class)
        private fun removeDrafts(json: JSONObject,context: Context) {
            val id = getLong(json, "id")
            val deviceId = json.getString("android_device")

            if (deviceId == null || deviceId != Account.deviceId) {
                DataSource.deleteDrafts(context, id, false)
                Log.v(TAG, "removed drafts")
            }
        }

        @Throws(JSONException::class)
        private fun addBlacklist(json: JSONObject,context: Context, encryptionUtils: EncryptionUtils?) {
            val id = getLong(json, "id")
            var phoneNumber: String? = json.getString("phone_number")
            phoneNumber = encryptionUtils!!.decrypt(phoneNumber)

            val blacklist = Blacklist()
            blacklist.id = id
            blacklist.phoneNumber = phoneNumber
            DataSource.insertBlacklist(context, blacklist, false)
            Log.v(TAG, "added blacklist")
        }

        @Throws(JSONException::class)
        private fun removeBlacklist(json: JSONObject,context: Context) {
            val id = getLong(json, "id")
            DataSource.deleteBlacklist(context, id, false)
            Log.v(TAG, "removed blacklist")
        }

        @Throws(JSONException::class)
        private fun addScheduledMessage(json: JSONObject,context: Context, encryptionUtils: EncryptionUtils?) {
            val message = ScheduledMessage()
            message.id = getLong(json, "id")
            message.to = encryptionUtils!!.decrypt(json.getString("to"))
            message.data = encryptionUtils.decrypt(json.getString("data"))
            message.mimeType = encryptionUtils.decrypt(json.getString("mime_type"))
            message.timestamp = getLong(json, "timestamp")
            message.title = encryptionUtils.decrypt(json.getString("title"))

            DataSource.insertScheduledMessage(context, message, false)
            ScheduledMessageJob.scheduleNextRun(context, DataSource)
            Log.v(TAG, "added scheduled message")
        }

        @Throws(JSONException::class)
        private fun updatedScheduledMessage(json: JSONObject,context: Context, encryptionUtils: EncryptionUtils?) {
            val message = ScheduledMessage()
            message.id = getLong(json, "id")
            message.to = encryptionUtils!!.decrypt(json.getString("to"))
            message.data = encryptionUtils.decrypt(json.getString("data"))
            message.mimeType = encryptionUtils.decrypt(json.getString("mime_type"))
            message.timestamp = getLong(json, "timestamp")
            message.title = encryptionUtils.decrypt(json.getString("title"))

            DataSource.updateScheduledMessage(context, message, false)
            ScheduledMessageJob.scheduleNextRun(context, DataSource)
            Log.v(TAG, "updated scheduled message")
        }

        @Throws(JSONException::class)
        private fun removeScheduledMessage(json: JSONObject,context: Context) {
            val id = getLong(json, "id")
            DataSource.deleteScheduledMessage(context, id, false)
            ScheduledMessageJob.scheduleNextRun(context, DataSource)
            Log.v(TAG, "removed scheduled message")
        }

        @Throws(JSONException::class)
        private fun dismissNotification(json: JSONObject,context: Context) {
            val conversationId = getLong(json, "id")
            val deviceId = json.getString("device_id")

            if (deviceId == null || deviceId != Account.deviceId) {
                val conversation = DataSource.getConversation(context, conversationId)

                // don't want to mark as read if this device was the one that sent the dismissal fcm message
                DataSource.readConversation(context, conversationId, false)
                if (conversation != null && !conversation.read) {
                    ConversationListUpdatedReceiver.sendBroadcast(context, conversationId, conversation.snippet, true)
                }

                NotificationManagerCompat.from(context).cancel(conversationId.toInt())
                Log.v(TAG, "dismissed notification for " + conversationId)
            }
        }

        @Throws(JSONException::class)
        private fun updateSetting(json: JSONObject, context: Context) {
            val pref = json.getString("pref")
            val type = json.getString("type")

            if (pref != null && type != null && json.has("value")) {
                val settings = Settings
                when (type.toLowerCase(Locale.getDefault())) {
                    "boolean" -> settings.setValue(context, pref, json.getBoolean("value"))
                    "long" -> settings.setValue(context, pref, getLong(json, "value"))
                    "int" -> settings.setValue(context, pref, json.getInt("value"))
                    "string" -> settings.setValue(context, pref, json.getString("value"))
                    "set" -> settings.setValue(context, pref, SetUtils.createSet(json.getString("value")))
                }
            }
        }

        @Throws(JSONException::class)
        private fun updateSubscription(json: JSONObject, context: Context) {
            val type = if (json.has("type")) json.getInt("type") else 0
            val expiration = if (json.has("expiration")) json.getLong("expiration") else 0L
            val fromAdmin = if (json.has("from_admin")) json.getBoolean("from_admin") else false

            val account = Account

            if (account.primary) {
                account.updateSubscription(context,
                        Account.SubscriptionType.findByTypeCode(type), expiration, false
                )

                SubscriptionExpirationCheckJob.scheduleNextRun(context)
                SignoutJob.writeSignoutTime(context, 0)

                if (fromAdmin) {
                    var content = "Enjoy the app!"

                    if (account.subscriptionType != Account.SubscriptionType.LIFETIME) {
                        content = "Expiration: " + Date(expiration).toString()
                    }

                    notifyUser(context, "Subscription Updated: " + StringUtils.titleize(account.subscriptionType!!.name), content)
                }
            }
        }

        @Throws(JSONException::class)
        private fun updatePrimaryDevice(json: JSONObject, context: Context) {
            val newPrimaryDeviceId = json.getString("new_primary_device_id")

            val account = Account
            if (newPrimaryDeviceId != null && newPrimaryDeviceId != account.deviceId) {
                account.setPrimary(context, false)
            }
        }

        @Throws(JSONException::class)
        private fun writeFeatureFlag(json: JSONObject, context: Context) {

            val identifier = json.getString("id")
            val value = json.getBoolean("value")
            val rolloutPercent = json.getInt("rollout") // 1 - 100

            if (!value) {
                // if we are turning the flag off, we want to do it for everyone immediately
                FeatureFlags.updateFlag(context, identifier, false)
            } else {
                val rand = Random()
                val random = rand.nextInt(100) + 1 // between 1 - 100

                if (random <= rolloutPercent) {
                    // they made it in the staged rollout!
                    FeatureFlags.updateFlag(context, identifier, true)
                }

                // otherwise, don't do anything. We don't want to turn the flag off for those
                // that had gotten it turned on in the past.
            }
        }

        @Throws(JSONException::class)
        private fun forwardToPhone(json: JSONObject,context: Context) {

            if (!Account.primary) {
                return
            }

            val text = json.getString("message")
            val toFromWeb = json.getString("to")
            val split = toFromWeb.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            var to = ""
            for (i in split.indices) {
                if (i != 0) {
                    to += ", "
                }

                to += PhoneNumberUtils.clearFormatting(split[i])
            }

            val message = Message()
            message.type = Message.TYPE_SENDING
            message.data = text
            message.timestamp = System.currentTimeMillis()
            message.mimeType = MimeType.TEXT_PLAIN
            message.read = true
            message.seen = true
            message.simPhoneNumber = DualSimUtils.defaultPhoneNumber

            if (json.has("sent_device")) {
                message.sentDeviceId = json.getLong("sent_device")
            } else {
                message.sentDeviceId = 0
            }

            val conversationId = DataSource.insertMessage(message, to, context, true)
            val conversation = DataSource.getConversation(context, conversationId)

            SendUtils(conversation?.simSubscriptionId)
                    .send(context, message.data!!, to)
        }

        private fun getLong(json: JSONObject, identifier: String) = try {
            val str = json.getString(identifier)
            java.lang.Long.parseLong(str)
        } catch (e: Exception) {
            0L
        }

        private fun notifyUser(context: Context, title: String, content: String) {
            val builder = NotificationCompat.Builder(context, NotificationUtils.GENERAL_CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).setSummaryText(content))
                    .setSmallIcon(R.drawable.ic_stat_notify_group)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setColor(Settings.mainColorSet.color)

            NotificationManagerCompat.from(context).notify(INFORMATION_NOTIFICATION_ID, builder.build())
        }
    }
}
