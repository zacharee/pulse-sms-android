package tk.zwander.smsfilter

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import tk.zwander.smsfilter.receiver.SMSSpamChoiceReceiver

/**
 * TODO: docs
 */
class SMSChecker private constructor(private val context: Context) {
    companion object {
        private const val SPAM_CHECK_CHANNEL_ID = "spam_check"

        //TODO: These will need to be refined.
        private const val LOWER_SCORE = 10
        private const val HIGHER_SCORE = 12

        private const val MESSAGE_MATCHES_KNOWN_PERCENT_LOW = 0.8
        private const val MESSAGE_MATCHES_KNOWN_PERCENT_HIGH = 0.9

        private var instance: SMSChecker? = null

        fun getInstance(context: Context): SMSChecker {
            return instance ?: run {
                SMSChecker(context.applicationContext).apply {
                    instance = this
                }
            }
        }

        @SuppressLint("WrongConstant")
        fun notifyForAmbiguousMessage(context: Context, number: String, message: String?) {
            val nmc = NotificationManagerCompat.from(context)

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                nmc.createNotificationChannel(NotificationChannel(
                        SPAM_CHECK_CHANNEL_ID,
                        context.resources.getString(R.string.notification_channel_label),
                        NotificationManagerCompat.IMPORTANCE_DEFAULT
                ))
            }

            val notification = NotificationCompat.Builder(context, SPAM_CHECK_CHANNEL_ID)
                    .setContentTitle(context.resources
                            .getString(R.string.potential_spam_detected, number))
                    .setContentText(message)
                    .setSmallIcon(R.drawable.ic_baseline_error_24)
                    .addAction(
                            0,
                            context.resources.getString(R.string.spam),
                            PendingIntent.getBroadcast(
                                    context,
                                    100,
                                    SMSSpamChoiceReceiver.makeSpamChoiceUpdateIntent(
                                            context,
                                            message,
                                            SMSSpamChoiceReceiver.VALUE_SPAM_CHOICE_SPAM
                                    ),
                                    PendingIntent.FLAG_UPDATE_CURRENT
                            )
                    )
                    .addAction(
                            0,
                            context.resources.getString(R.string.not_spam),
                            PendingIntent.getBroadcast(
                                    context,
                                    0,
                                    SMSSpamChoiceReceiver.makeSpamChoiceUpdateIntent(
                                            context,
                                            message,
                                            SMSSpamChoiceReceiver.VALUE_SPAM_CHOICE_GOOD
                                    ),
                                    PendingIntent.FLAG_UPDATE_CURRENT
                            )
                    )
                    .build()

            nmc.notify(1001, notification)
        }
    }

    private val database = Database.newInstance(context)

    fun checkMessage(msg: String): MessageStatus {
        val messageScore = calculateSpamScore(msg)

        return when {
            messageScore < HIGHER_SCORE -> {
                val matchingScore = calculateMessageMatchPercent(msg)

                when {
                    matchingScore > MESSAGE_MATCHES_KNOWN_PERCENT_HIGH -> {
                        MessageStatus.SPAM
                    }
                    messageScore in LOWER_SCORE..HIGHER_SCORE || matchingScore in
                            MESSAGE_MATCHES_KNOWN_PERCENT_LOW..MESSAGE_MATCHES_KNOWN_PERCENT_HIGH -> {
                        MessageStatus.AMBIGUOUS
                    }
                    else -> {
                        MessageStatus.GOOD
                    }
                }
            }
            else -> {
                MessageStatus.SPAM
            }
        }
    }

    fun onUserMarkedMessageSpam(msg: String) {
        database.addMessageToSpamDatabase(msg)
    }

    fun onUserMarkedMessageGood(msg: String) {
        database.addMessageToGoodDatabase(msg)
    }

    private fun calculateMessageMatchPercent(msg: String): Double {
        return database.calculateMessageMatchPercent(msg)
    }

    private fun calculateSpamScore(msg: String): Int {
        return msg.split(" ")
                .map { database.checkKeywordScore(it) }.sum()
    }
}