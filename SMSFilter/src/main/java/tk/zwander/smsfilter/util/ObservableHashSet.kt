package tk.zwander.smsfilter.util

import java.util.function.Predicate

/**
 * A HashSet implementation with a modification callback.
 *
 * @param T the data type.
 * @property modificationCallback invoked when the set is modified.
 */
class ObservableHashSet<T>(private val modificationCallback: ObservableHashSet<T>.() -> Unit) : HashSet<T>() {
    override fun add(element: T): Boolean {
        return super.add(element).also {
            if (it) modificationCallback()
        }
    }

    override fun addAll(elements: Collection<T>): Boolean {
        return super.addAll(elements).also {
            if (it) modificationCallback()
        }
    }

    override fun remove(element: T): Boolean {
        return super.remove(element).also {
            if (it) modificationCallback()
        }
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        return super.removeAll(elements).also {
            if (it) modificationCallback()
        }
    }

    override fun removeIf(filter: Predicate<in T>): Boolean {
        return super.removeIf(filter).also {
            if (it) modificationCallback()
        }
    }
}