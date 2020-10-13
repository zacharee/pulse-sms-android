package tk.zwander.smsfilter

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Reader


fun <T> Gson.fromJson(reader: Reader): T {
    return fromJson(reader, object : TypeToken<T>() {}.type)
}
