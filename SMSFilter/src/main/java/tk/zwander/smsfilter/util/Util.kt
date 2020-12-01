package tk.zwander.smsfilter.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Reader


/**
 * A helper method to parse JSON into an object from a Reader
 * using Gson.
 *
 * @param T the object type.
 * @param reader the Reader to read.
 * @return an object representation of the JSON.
 */
fun <T> Gson.fromJson(reader: Reader): T {
    return fromJson(reader, object : TypeToken<T>() {}.type)
}

/**
 * Strip all non-alphanumeric text from a String.
 *
 * @return an alphanumeric String.
 */
fun String.toAlphaNumeric(): String {
    return replace(Regex("[^A-Za-z0-9 ]"), "")
}
