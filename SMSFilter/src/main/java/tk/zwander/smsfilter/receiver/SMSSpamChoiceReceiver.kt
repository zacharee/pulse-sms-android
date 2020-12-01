package tk.zwander.smsfilter.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tk.zwander.smsfilter.SMSChecker

/**
 * Handle the user's choice ("spam" or "not spam") on an ambiguous
 * message notification.
 */
class SMSSpamChoiceReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_MESSAGE_SPAM_CHOICE = "tk.zwander.smsfilter.ACTION_MESSAGE_SPAM_CHOICE"

        const val EXTRA_SPAM_CHOICE = "SPAM_CHOICE"
        const val EXTRA_MESSAGE = "MESSAGE"

        const val VALUE_SPAM_CHOICE_SPAM = "SPAM"
        const val VALUE_SPAM_CHOICE_GOOD = "GOOD"

        /**
         * A helper method to make the Intent to trigger this receiver.
         *
         * @param context a Context object.
         * @param message the ambiguous message.
         * @param which "SPAM" or "GOOD".
         * @return the Intent.
         */
        fun makeSpamChoiceUpdateIntent(context: Context, message: String?, which: String): Intent {
            if (which != VALUE_SPAM_CHOICE_GOOD
                    && which != VALUE_SPAM_CHOICE_SPAM) {
                throw IllegalArgumentException("Variable 'which' must either be 'SPAM' or 'GOOD'.")
            }

            val updateIntent = Intent(context, SMSSpamChoiceReceiver::class.java)

            updateIntent.action = ACTION_MESSAGE_SPAM_CHOICE
            updateIntent.putExtra(EXTRA_MESSAGE, message)
            updateIntent.putExtra(EXTRA_SPAM_CHOICE, which)

            return updateIntent
        }

        /**
         * A helper method to directly send a choice Intent.
         *
         * @param context a Context object.
         * @param message the ambiguous message.
         * @param which "SPAM" or "GOOD".
         */
        fun sendSpamChoiceUpdate(context: Context, message: String?, which: String) {
            context.sendBroadcast(makeSpamChoiceUpdateIntent(context, message, which))
        }
    }

    /**
     * Handle a received Intent.
     *
     * @param context a Context object.
     * @param intent the Intent to handle.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MESSAGE_SPAM_CHOICE) {
            val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return
            val status = intent.getStringExtra(EXTRA_SPAM_CHOICE) ?: return
            val checker = SMSChecker.getInstance(context)

            //Parse the choice and add the message to the correct database.
            when (status) {
                VALUE_SPAM_CHOICE_GOOD -> checker.onUserMarkedMessageGood(message)
                VALUE_SPAM_CHOICE_SPAM -> checker.onUserMarkedMessageSpam(message)
            }
        }
    }
}