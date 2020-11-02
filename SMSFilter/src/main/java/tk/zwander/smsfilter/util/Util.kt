package tk.zwander.smsfilter.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Reader


fun <T> Gson.fromJson(reader: Reader): T {
    return fromJson(reader, object : TypeToken<T>() {}.type)
}

fun String.toAlphaNumeric(): String {
    return replace(Regex("[^A-Za-z0-9 ]"), "")
}
