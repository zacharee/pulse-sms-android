package tk.zwander.smsfilter.util

import java.util.function.BiFunction

class ObservableHashMap<K, V>(private val modificationCallback: ObservableHashMap<K, V>.() -> Unit) : HashMap<K, V>() {
    override fun put(key: K, value: V): V? {
        return super.put(key, value).also {
            modificationCallback()
        }
    }

    override fun putAll(from: Map<out K, V>) {
        super.putAll(from).also {
            modificationCallback()
        }
    }

    override fun putIfAbsent(key: K, value: V): V? {
        return super.putIfAbsent(key, value).also {
            modificationCallback()
        }
    }

    override fun remove(key: K): V? {
        return super.remove(key).also {
            modificationCallback()
        }
    }

    override fun remove(key: K, value: V): Boolean {
        return super.remove(key, value).also {
            modificationCallback()
        }
    }

    override fun replace(key: K, value: V): V? {
        return super.replace(key, value).also {
            modificationCallback()
        }
    }

    override fun replace(key: K, oldValue: V, newValue: V): Boolean {
        return super.replace(key, oldValue, newValue).also {
            modificationCallback()
        }
    }

    override fun replaceAll(function: BiFunction<in K, in V, out V>) {
        super.replaceAll(function).also {
            modificationCallback()
        }
    }

    override fun merge(key: K, value: V, remappingFunction: BiFunction<in V, in V, out V?>): V? {
        return super.merge(key, value, remappingFunction).also {
            modificationCallback()
        }
    }
}