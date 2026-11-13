package com.example.safepath_test1.auth

import com.example.safepath_test1.friends.normalizeFriendPhone

/** Matches the existing friend API's Korean 010 mobile contract, in E.164 format. */
fun normalizeKoreanPhoneNumber(value: String): String? {
    val text = value.trim()
    if (text.isEmpty() || text.any { it !in '0'..'9' && it !in " +()-" }) return null
    val compact = text.filter { it !in " ()-" }
    if (!Regex("(010[0-9]{8}|\\+8210[0-9]{8}|008210[0-9]{8})").matches(compact)) return null
    return normalizeFriendPhone(compact)
}
