package tk.zwander.smsfilter

import android.content.Context

/**
 * TODO: docs
 */
class SMSChecker private constructor(private val context: Context) {
    companion object {
        private var instance: SMSChecker? = null

        fun getInstance(context: Context): SMSChecker {
            return instance ?: run {
                SMSChecker(context.applicationContext).apply {
                    instance = this
                }
            }
        }
    }
}