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
 * The actual API for the spam checker.
 *
 * Apps can retrieve an instance with [getInstance] and call
 * the appropriate methods as needed.
 */
class SMSChecker private constructor(private val context: Context) {
    companion object {
        private const val SPAM_CHECK_CHANNEL_ID = "spam_check"

        //TODO: These will need to be refined.
        /**
         * The score range where a message is ambiguous.
         * Below [LOWER_SCORE], a message is not spam.
         * Above [HIGHER_SCORE], a message is spam.
         */
        private const val LOWER_SCORE = 10
        private const val HIGHER_SCORE = 12

        /**
         * The match percent range where a message checked against
         * known-spam messages is ambiguous.
         */
        private const val MESSAGE_MATCHES_KNOWN_PERCENT_LOW = 0.8
        private const val MESSAGE_MATCHES_KNOWN_PERCENT_HIGH = 0.9

        /**
         * The singleton instance.
         */
        private var instance: SMSChecker? = null

        /**
         * Retrieve the SMSChecker instance. If it doesn't
         * exist, it will be created.
         *
         * @param context a Context object.
         * @return the SMSChecker instance.
         */
        fun getInstance(context: Context): SMSChecker {
            return instance ?: run {
                SMSChecker(context.applicationContext).apply {
                    instance = this
                }
            }
        }

        /**
         * If a message is checked and found to be ambiguous, the user needs to be notified.
         * This method generates and shows the notification.
         *
         * @param context a Context object.
         * @param number the sender's phone number.
         * @param message the message text.
         */
        @SuppressLint("WrongConstant")
        fun notifyForAmbiguousMessage(context: Context, number: String, message: String?) {
            val nmc = NotificationManagerCompat.from(context)

            //On Android Oreo and above, use notification channels.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                nmc.createNotificationChannel(NotificationChannel(
                        SPAM_CHECK_CHANNEL_ID,
                        context.resources.getString(R.string.notification_channel_label),
                        NotificationManagerCompat.IMPORTANCE_DEFAULT
                ))
            }

            //Create the notification.
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

            //Notify.
            //TODO: the ID should probably be randomized, to support multiple messages
            //TODO: at once.
            nmc.notify(1001, notification)
        }
    }

    /**
     * The database instance.
     */
    private val database = Database.newInstance(context)

    /**
     * Check a given message to see if it's spam.
     * Host apps should call this when a message is received and evaluate
     * the returned status.
     *
     * @param msg the message to check.
     * @return the status of the message.
     */
    fun checkMessage(msg: String): MessageStatus {
        val messageScore = calculateSpamScore(msg)

        return when {
            messageScore < HIGHER_SCORE -> {
                //A score under HIGHER_SCORE means further checking is needed.
                //Check the percent match of this message against known-spam messages.
                val matchingScore = calculateMessageMatchPercent(msg)

                when {
                    matchingScore > MESSAGE_MATCHES_KNOWN_PERCENT_HIGH -> {
                        //If the match percent is above the max threshold, it's spam.
                        MessageStatus.SPAM
                    }
                    messageScore in LOWER_SCORE..HIGHER_SCORE || matchingScore in
                            MESSAGE_MATCHES_KNOWN_PERCENT_LOW..MESSAGE_MATCHES_KNOWN_PERCENT_HIGH -> {
                        //If it's within the threshold range, it's ambiguous.
                        MessageStatus.AMBIGUOUS
                    }
                    else -> {
                        //Otherwise, it's not spam.
                        MessageStatus.GOOD
                    }
                }
            }
            else -> {
                //Any score equal to or greater than HIGHER_SCORE means it's spam.
                MessageStatus.SPAM
            }
        }
    }

    /**
     * Invoked when the user marks a message as spam.
     *
     * @param msg the spam message.
     */
    fun onUserMarkedMessageSpam(msg: String) {
        database.addMessageToSpamDatabase(msg)
    }

    /**
     * Invoked when the user marks a message as good.
     *
     * @param msg the good message.
     */
    fun onUserMarkedMessageGood(msg: String) {
        database.addMessageToGoodDatabase(msg)
    }

    /**
     * A wrapper function for [Database.calculateMessageMatchPercent].
     *
     * @param msg the message to check.
     * @return the match percent.
     */
    private fun calculateMessageMatchPercent(msg: String): Double {
        return database.calculateMessageMatchPercent(msg)
    }

    /**
     * Calculate the score of the given message.
     * This splits the message into individual words
     * and then sums the results of [Database.checkKeywordScore]
     * from each word.
     *
     * @param msg the message to calculate.
     * @return the score of the message.
     */
    private fun calculateSpamScore(msg: String): Int {
        return msg.split(" ")
                .map { database.checkKeywordScore(it) }.sum()
    }
}