package xyz.martelle.clapet.apps

/** The ordered favorites, as app keys. Pure list operations; persistence is SettingsRepository's. */
object Favorites {

    /** The cover screen holds six rows under the header, not more. */
    const val MAX = 6

    fun add(favorites: List<String>, key: String): List<String> =
        if (key in favorites || favorites.size >= MAX) favorites else favorites + key

    fun remove(favorites: List<String>, key: String): List<String> = favorites - key

    /** Drops uninstalled apps' keys: left in, they hold a slot and shift the indices of a move. */
    fun prune(favorites: List<String>, installed: Set<String>): List<String> = favorites.filter { it in installed }

    fun move(favorites: List<String>, from: Int, to: Int): List<String> {
        if (from !in favorites.indices || to !in favorites.indices || from == to) return favorites
        return favorites.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Favorites whose app is still installed, in favorite order. */
    fun <T> resolve(favorites: List<String>, apps: List<T>, keyOf: (T) -> String): List<T> {
        val byKey = apps.associateBy(keyOf)
        return favorites.mapNotNull { byKey[it] }
    }
}
