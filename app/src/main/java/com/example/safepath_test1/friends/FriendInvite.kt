package com.example.safepath_test1.friends

/** 요청자의 국내 휴대전화 번호를 일관된 형식으로 변환한다. */
fun normalizeFriendPhone(value: String): String? {
    val text = value.trim()
    if (text.any { !it.isDigit() && it !in " +()-" }) return null
    val digits = text.filter(Char::isDigit)
    val local =
        when {
            text.startsWith("+82") -> "0" + digits.removePrefix("82")
            text.startsWith("0082") -> "0" + digits.removePrefix("0082")
            else -> digits
        }
    return if (Regex("010[0-9]{8}").matches(local)) "+82" + local.drop(1) else null
}
