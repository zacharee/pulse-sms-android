package tk.zwander.smsfilter

import android.content.Context
import android.content.res.AssetFileDescriptor
import com.google.gson.GsonBuilder
import tk.zwander.smsfilter.util.ObservableHashMap
import tk.zwander.smsfilter.util.ObservableHashSet
import tk.zwander.smsfilter.util.fromJson
import java.io.File

class Database(private val context: Context, private val scores: AssetFileDescriptor,
               private val reportedFile: File, private val goodFile: File) {
    companion object {
        private const val KEYWORD_SCORES_FILE = "keyword_scores.json"
        private const val REPORTED_SPAM_FILE = "reported_spam_msgs.json"
        private const val KNOWN_GOOD_FILE = "known_good_msgs.json"

        fun newInstance(context: Context): Database {
            val scores = context.assets.openFd(KEYWORD_SCORES_FILE)
            val reported = File(context.dataDir, REPORTED_SPAM_FILE)
            val good = File(context.dataDir, KNOWN_GOOD_FILE)

            return Database(context, scores, reported, good)
        }
    }

    private val keywordScores = HashMap<String, Int>()
    private val reportedSpamMessages = ObservableHashMap<String, Int> {
        saveDatabase(reportedFile, this)
    }

    //TODO: There might be a better collection for this.
    private val knownGoodMessages = ObservableHashSet<String> {
        saveDatabase(goodFile, this)
    }

    private val gson = GsonBuilder()
            .create()

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
        scores.createInputStream().bufferedReader().use { scoresReader ->
            keywordScores.putAll(gson.fromJson(scoresReader))
        }

        reportedFile.bufferedReader().use { reportedReader ->
            reportedSpamMessages.putAll(gson.fromJson(reportedReader))
        }

        goodFile.bufferedReader().use { goodReader ->
            knownGoodMessages.addAll(gson.fromJson(goodReader))
        }
    }

    private fun <T> saveDatabase(databaseFile: File, contents: T) {
        databaseFile.bufferedWriter().use { reportedWriter ->
            gson.toJson(contents, reportedWriter)
        }
    }
}