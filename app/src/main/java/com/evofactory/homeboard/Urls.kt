package com.evofactory.homeboard

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Address as typed → base URL. [defaultScheme] is assumed when the text names none, and
 * [defaultPort] when it names no port either; anything after the server (a copied path, query or
 * fragment) is dropped. Null if the text is empty or not a URL.
 */
internal fun normalizeUrl(input: String, defaultScheme: String, defaultPort: Int): String? {
    val text = input.trim()
    if (text.isEmpty()) return null
    val withScheme = when {
        "://" in text -> text
        ':' in text -> "$defaultScheme://$text"
        else -> "$defaultScheme://${text.trimEnd('/')}:$defaultPort"
    }
    val url = withScheme.toHttpUrlOrNull() ?: return null
    return url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().trimEnd('/')
}
