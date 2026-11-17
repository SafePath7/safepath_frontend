package com.example.safepath_test1.friends

import android.content.Context
import com.example.safepath_test1.ui.guardian.GuardianItem
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

internal fun cacheServerFriends(context: Context, uid: String, friends: List<GuardianItem>) =
    cacheContacts(context, "safe_path_server_friends", uid, friends)

internal fun cacheServerGuardians(context: Context, uid: String, guardians: List<GuardianItem>) =
    cacheContacts(context, "safe_path_server_guardians", uid, guardians)

private fun cacheContacts(context: Context, store: String, uid: String, friends: List<GuardianItem>) {
    val array = JSONArray()
    friends.forEach { friend ->
        array.put(
            JSONObject().put("id", friend.id).put("name", friend.name).put("phone", friend.phone)
        )
    }
    context
        .getSharedPreferences(store, Context.MODE_PRIVATE)
        .edit()
        .putString(uid, array.toString())
        .apply()
}

fun readServerFriends(context: Context): List<GuardianItem> = readContacts(context, "safe_path_server_friends", "친구")

fun readServerGuardians(context: Context): List<GuardianItem> = readContacts(context, "safe_path_server_guardians", "보호자")

private fun readContacts(context: Context, store: String, relation: String): List<GuardianItem> {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()
    return runCatching {
            val array =
                JSONArray(
                    context
                        .getSharedPreferences(store, Context.MODE_PRIVATE)
                        .getString(uid, "[]")
                )
            (0 until array.length()).map { index ->
                array.getJSONObject(index).let {
                    GuardianItem(
                        it.getString("id"),
                        it.getString("name"),
                        relation,
                        it.optString("phone"),
                    )
                }
            }
        }
        .getOrDefault(emptyList())
}
