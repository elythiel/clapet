package xyz.martelle.clapet.apps

/** The ordered favorites, as app keys. Pure list operations; persistence is SettingsRepository's. */
object Favorites {

    /** The cover screen holds five rows under the header, not more. */
    const val MAX = 5

    fun add(favorites: List<String>, key: String): List<String> =
        if (key in favorites || favorites.size >= MAX) favorites else favorites + key

    fun remove(favorites: List<String>, key: String): List<String> = favorites - key

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
