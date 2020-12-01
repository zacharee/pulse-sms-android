package tk.zwander.smsfilter

import android.content.Context
import com.google.gson.GsonBuilder
import tk.zwander.smsfilter.util.ObservableHashMap
import tk.zwander.smsfilter.util.ObservableHashSet
import tk.zwander.smsfilter.util.fromJson
import tk.zwander.smsfilter.util.toAlphaNumeric
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Handle persisting and reading data.
 *
 * @property context a Context object.
 * @property scores the keyword scores data.
 * @property reportedFile the known-spam data.
 * @property goodFile the known-good data.
 */
class Database(private val context: Context, private val scores: InputStream,
               private val reportedFile: File, private val goodFile: File) {
    companion object {
        private const val KEYWORD_SCORES_FILE = "keyword_scores.json"
        private const val REPORTED_SPAM_FILE = "reported_spam_msgs.json"
        private const val KNOWN_GOOD_FILE = "known_good_msgs.json"

        /**
         * Create a new default instance of the Database.
         *
         * @param context a Context object.
         * @return a new Database instance.
         */
        fun newInstance(context: Context): Database {
            val scores = context.assets.open(KEYWORD_SCORES_FILE)
            val reported = File(context.dataDir, REPORTED_SPAM_FILE)
            val good = File(context.dataDir, KNOWN_GOOD_FILE)

            return Database(context, scores, reported, good)
        }
    }

    /**
     * The scores of keywords. Higher scores mean more likely spam.
     */
    private val keywordScores = ConcurrentHashMap<String, Int>()

    /**
     * Messages that have been reported by the user as spam.
     */
    private val reportedSpamMessages = ObservableHashMap<String, Int> {
        saveDatabase(reportedFile, this)
    }

    //TODO: There might be a better collection for this.
    /**
     * Messages that have been reported by the user as not spam.
     */
    private val knownGoodMessages = ObservableHashSet<String> {
        saveDatabase(goodFile, this)
    }

    /**
     * A Gson instance.
     */
    private val gson = GsonBuilder()
            .create()

    init {
        initializeDatabase()
    }

    /**
     * Check the score of the given word.
     *
     * @param word the word to check.
     * @return the word's score.
     */
    fun checkKeywordScore(word: String): Int {
        //TODO: We should probably have some sort of
        //TODO: handling for when a word isn't found. Not really
        //TODO: sure how it would work, but the current method
        //TODO: is relying on an exhaustive database.

        //Currently, word splitting is done with " ".
        //That can cause "words" to include punctuation
        //(e.g "winner!!"). Looping through all score values
        //does cause complexity to go up, but it enables
        //fuzzy word checking.
        keywordScores.forEach { (w, s) ->
            if (word.toAlphaNumeric().equals(w.toAlphaNumeric(), true)) {
                return s
            }
        }

        return 0
    }

    /**
     * Add the given message to the known-spam database.
     *
     * @param msg the message to add.
     */
    fun addMessageToSpamDatabase(msg: String) {
        reportedSpamMessages[msg] = msg.split(" ")
                .map {
                    checkKeywordScore(it)
                }.sum()
    }

    /**
     * Add the given message to the known-good database.
     *
     * @param msg the message to add.
     */
    fun addMessageToGoodDatabase(msg: String) {
        //TODO: It might make sense to either lessen the scores of or
        //TODO: remove any words from keywordScores, as well.
        knownGoodMessages.add(msg)
    }

    /**
     * Remove the given message from the known-spam database.
     *
     * @param msg the message to remove.
     */
    fun removeMessageFromSpamDatabase(msg: String) {
        reportedSpamMessages.remove(msg)
    }

    /**
     * Remove the given message from the known-good database.
     *
     * @param msg the message to remove.
     */
    fun removeMessageFromGoodDatabase(msg: String) {
        //TODO: Similar to addMessageToGoodDatabase()
        knownGoodMessages.remove(msg)
    }

    /**
     * Retrieve a copy of the known-spam messages.
     *
     * @return the known-spam messages.
     */
    fun getSpamMessages(): HashMap<String, Int> {
        return HashMap(reportedSpamMessages)
    }

    /**
     * Retrieve a copy of the known-good messages.
     *
     * @return the known-good messages.
     */
    fun getGoodMessages(): HashSet<String> {
        return HashSet(knownGoodMessages)
    }

    /**
     * Check the given message against all messages in the known-spam
     * database, and calculate the highest match percent.
     *
     * @param msg the message to check.
     * @return how closely the message matches the best-matching
     * known-spam message.
     */
    fun calculateMessageMatchPercent(msg: String): Double {
        val split = msg.split(" ")

        return if (reportedSpamMessages.size > 0) {
            reportedSpamMessages.keys.maxOf {
                val splitKey = it.split(" ")
                val matchingWords = HashSet<String>()
                val totalWords = HashSet<String>(splitKey).size

                splitKey.forEach { k ->
                    matchingWords += split.filter { s ->
                        s.toAlphaNumeric().equals(k.toAlphaNumeric(), true)
                    }
                }

                (matchingWords.size.toDouble() / totalWords)
            }
        } else 0.0
    }

    /**
     * Initialize the database items by reading from the given inputs.
     */
    private fun initializeDatabase() {
        scores.bufferedReader().use { scoresReader ->
            keywordScores.putAll(gson.fromJson(scoresReader))
        }

        if (reportedFile.exists()) {
            reportedFile.bufferedReader().use { reportedReader ->
                reportedSpamMessages.putAll(gson.fromJson(reportedReader))
            }
        }

        if (goodFile.exists()) {
            goodFile.bufferedReader().use { goodReader ->
                knownGoodMessages.addAll(gson.fromJson(goodReader))
            }
        }
    }

    /**
     * Save a given database to storage.
     *
     * @param T the content type.
     * @param databaseFile the file to write to.
     * @param contents the contents of the database.
     */
    private fun <T> saveDatabase(databaseFile: File, contents: T) {
        databaseFile.bufferedWriter().use { reportedWriter ->
            gson.toJson(contents, reportedWriter)
        }
    }
}