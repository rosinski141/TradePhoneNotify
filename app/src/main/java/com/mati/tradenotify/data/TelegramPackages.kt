package com.mati.tradenotify.data

/**
 * Telegram clients whose notifications are worth reading. Users run more forks than you'd expect,
 * so this is a default set rather than a hard-coded single package — Settings lets the user
 * enable whichever of these is actually installed.
 */
object TelegramPackages {

    const val OFFICIAL = "org.telegram.messenger"

    /** Package name to a human label, for the Settings list. */
    val KNOWN: Map<String, String> = linkedMapOf(
        OFFICIAL to "Telegram",
        "org.telegram.messenger.web" to "Telegram (direct APK)",
        "org.telegram.messenger.beta" to "Telegram Beta",
        "org.thunderdog.challegram" to "Telegram X",
        "org.telegram.plus" to "Plus Messenger",
        "nekox.messenger" to "NekoX",
    )

    val DEFAULT_WATCHED: Set<String> = KNOWN.keys.toSet()
}
