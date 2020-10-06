package tk.zwander.smsfilter

import android.content.Context
import java.io.File

class Database(private val context: Context, private val targetFile: File) {
    private val knownSpamKeywords = HashMap<String, Int>()
    private val reportedSpamMessages = HashMap<String, Int>()

    //TODO: There might be a better collection for this.
    private val knownGoodMessages = HashSet<String>()

    init {
        initializeDatabase()
    }

    fun checkKeywordScore(word: String): Int {
        return 0
    }

    fun addWordToSpamDatabase(word: String, overrideScore: Int = -1) {
        val score = if (overrideScore > -1)
            overrideScore else checkKeywordScore(word)

        knownSpamKeywords[word] = score
    }

    fun addMessageToSpamDatabase(msg: String) {
        reportedSpamMessages[msg] = msg.split(" ")
                .map {
                    checkKeywordScore(it).also { score ->
                        addWordToSpamDatabase(it, score)
                    }
                }.sum()
    }

    fun addMessageToGoodDatabase(msg: String) {
        //TODO: It might make sense to either lessen the scores of or
        //TODO: remove any words from knownSpamKeywords, as well.
        knownGoodMessages.add(msg)
    }

    fun removeWordFromSpamDatabase(word: String) {
        knownSpamKeywords.remove(word)
    }

    fun removeMessageFromSpamDatabase(msg: String) {
        reportedSpamMessages.remove(msg)
    }

    fun removeMessageFromGoodDatabase(msg: String) {
        //TODO: Similar to addMessageToGoodDatabase()
        knownGoodMessages.remove(msg)
    }

    private fun initializeDatabase() {

    }
}