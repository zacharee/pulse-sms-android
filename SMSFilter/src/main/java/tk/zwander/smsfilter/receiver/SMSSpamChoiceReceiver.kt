package tk.zwander.smsfilter.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tk.zwander.smsfilter.SMSChecker

class SMSSpamChoiceReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_MESSAGE_SPAM_CHOICE = "tk.zwander.smsfilter.ACTION_MESSAGE_SPAM_CHOICE"

        const val EXTRA_SPAM_CHOICE = "SPAM_CHOICE"
        const val EXTRA_MESSAGE = "MESSAGE"

        const val VALUE_SPAM_CHOICE_SPAM = "SPAM"
        const val VALUE_SPAM_CHOICE_GOOD = "GOOD"

        fun makeSpamChoiceUpdateIntent(context: Context, message: String?, which: String): Intent {
            if (which != VALUE_SPAM_CHOICE_GOOD
                    || which != VALUE_SPAM_CHOICE_SPAM) {
                throw IllegalArgumentException("Variable 'which' must either be 'SPAM' or 'GOOD'.")
            }

            val updateIntent = Intent(context, SMSSpamChoiceReceiver::class.java)

            updateIntent.action = ACTION_MESSAGE_SPAM_CHOICE
            updateIntent.putExtra(EXTRA_MESSAGE, message)
            updateIntent.putExtra(EXTRA_SPAM_CHOICE, which)

            return updateIntent
        }

        fun sendSpamChoiceUpdate(context: Context, message: String?, which: String) {
            context.sendBroadcast(makeSpamChoiceUpdateIntent(context, message, which))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MESSAGE_SPAM_CHOICE) {
            val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return
            val status = intent.getStringExtra(EXTRA_SPAM_CHOICE) ?: return
            val checker = SMSChecker.getInstance(context)

            when (status) {
                VALUE_SPAM_CHOICE_GOOD -> checker.onUserMarkedMessageGood(message)
                VALUE_SPAM_CHOICE_SPAM -> checker.onUserMarkedMessageSpam(message)
            }
        }
    }
}