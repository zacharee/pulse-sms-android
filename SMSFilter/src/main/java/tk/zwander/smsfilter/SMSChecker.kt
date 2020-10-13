package tk.zwander.smsfilter

import android.content.Context
import java.io.File

/**
 * TODO: docs
 */
class SMSChecker private constructor(private val context: Context) {
    companion object {
        //TODO: These will need to be refined.
        private const val LOWER_SCORE = 10
        private const val HIGHER_SCORE = 12
        private val SCORE_THRESHOLD = LOWER_SCORE to HIGHER_SCORE

        private var instance: SMSChecker? = null

        fun getInstance(context: Context): SMSChecker {
            return instance ?: run {
                SMSChecker(context.applicationContext).apply {
                    instance = this
                }
            }
        }
    }

    private val database = Database.newInstance(context)

    fun checkMessage(msg: String): MessageStatus {
        val messageScore = calculateSpamScore(msg)

        return when {
            messageScore >= LOWER_SCORE || messageScore <= HIGHER_SCORE -> {
                MessageStatus.AMBIGUOUS
            }
            messageScore < LOWER_SCORE -> {
                MessageStatus.GOOD
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

    private fun calculateSpamScore(msg: String): Int {
        return msg.split(" ")
                .map { database.checkKeywordScore(it) }.sum()
    }
}