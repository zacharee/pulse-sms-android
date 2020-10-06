package tk.zwander.smsfilter

import android.content.Context
import java.io.File

class Database(private val context: Context, private val targetFile: File) {
    private val keywordScores = HashMap<String, Int>()
    private val reportedSpamMessages = HashMap<String, Int>()

    //TODO: There might be a better collection for this.
    private val knownGoodMessages = HashSet<String>()

    init {
        initializeDatabase()
    }

    fun checkKeywordScore(word: String): Int {
        //TODO: We should probably have some sort of
        //TODO: handling for when a word isn't found. Not really
        //TODO: sure how it would work, but the current method
        //TODO: is relying on an exhaustive database.
        return keywordScores[word] ?: 0
    }

    fun updateWordScore(word: String, overrideScore: Int = -1) {
        val score = if (overrideScore > -1)
            overrideScore else checkKeywordScore(word)

        keywordScores[word] = score
    }

    fun addMessageToSpamDatabase(msg: String) {
        reportedSpamMessages[msg] = msg.split(" ")
                .map {
                    checkKeywordScore(it).also { score ->
                        updateWordScore(it, score)
                    }
                }.sum()
    }

    fun addMessageToGoodDatabase(msg: String) {
        //TODO: It might make sense to either lessen the scores of or
        //TODO: remove any words from keywordScores, as well.
        knownGoodMessages.add(msg)
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