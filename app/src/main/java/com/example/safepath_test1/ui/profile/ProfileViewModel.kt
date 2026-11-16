package com.example.safepath_test1.ui.profile

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.safepath_test1.api.AccountProfile
import com.example.safepath_test1.api.AccountRepository
import com.example.safepath_test1.api.accountErrorMessage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class ProfileDialog { NAME, PHONE, WITHDRAW }

internal data class ProfileState(
    val profile: AccountProfile? = null,
    val name: String = "사용자",
    val phone: String = "전화번호 미등록",
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val dialog: ProfileDialog? = null,
    val editedName: String = "",
    val withdrawn: Boolean = false,
)

internal class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val repository = AccountRepository(application)
    private val prefs = application.getSharedPreferences("safe_path_profile", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(ProfileState())
    val state = mutableState.asStateFlow()
    private var uid: String? = null
    private var generation = 0
    private var job: Job? = null
    private val listener = FirebaseAuth.AuthStateListener { firebase ->
        val user = firebase.currentUser
        if (user?.uid != uid) {
            generation++
            job?.cancel()
            uid = user?.uid
            val cached = user != null && prefs.getString("onboarded_uid", null) == user.uid
            mutableState.value = ProfileState(
                signedIn = user != null,
                name = (if (cached) prefs.getString("user_name", null) else null) ?: user?.displayName ?: "사용자",
                phone = (if (cached) prefs.getString("user_phone", null) else null) ?: "전화번호 미등록",
            )
            if (user != null) refresh()
        }
    }

    init { auth.addAuthStateListener(listener) }

    private fun isCurrent(expectedUid: String, expectedGeneration: Int) =
        uid == expectedUid && auth.currentUser?.uid == expectedUid && generation == expectedGeneration

    private suspend fun load(expectedUid: String, expectedGeneration: Int) {
        val profile = repository.profile()
        check(isCurrent(expectedUid, expectedGeneration)) { "로그인 계정이 변경되었습니다." }
        prefs.edit().putString("user_name", profile.name).putString("user_phone", profile.phoneNumber).apply()
        mutableState.update { it.copy(profile = profile, name = profile.name,
            phone = profile.phoneNumber ?: prefs.getString("user_phone", null) ?: "전화번호 미등록") }
    }

    private fun perform(action: suspend (String, Int) -> Unit) {
        val expectedUid = uid ?: return
        if (mutableState.value.busy) return
        val expectedGeneration = generation
        mutableState.update { it.copy(busy = true, error = null) }
        job = viewModelScope.launch {
            try { action(expectedUid, expectedGeneration) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (isCurrent(expectedUid, expectedGeneration)) mutableState.update {
                    it.copy(error = accountErrorMessage(error), dialog = null)
                }
            } finally {
                if (isCurrent(expectedUid, expectedGeneration)) mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun refresh() = perform { account, version -> load(account, version) }
    fun openDialog(dialog: ProfileDialog) {
        if (!mutableState.value.busy) mutableState.update { it.copy(dialog = dialog, editedName = it.name) }
    }
    fun dismissDialog() { if (!mutableState.value.busy) mutableState.update { it.copy(dialog = null) } }
    fun editName(value: String) { mutableState.update { it.copy(editedName = value) } }
    fun saveName() = perform { account, version ->
        repository.update(mutableState.value.editedName)
        load(account, version)
        mutableState.update { it.copy(dialog = null) }
    }
    fun withdraw() = perform { account, version ->
        repository.withdraw()
        if (isCurrent(account, version)) {
            prefs.edit().remove("user_name").remove("user_phone").remove("onboarded_uid").apply()
            mutableState.update { it.copy(withdrawn = true, dialog = null) }
        }
    }
    fun consumeWithdrawal() { mutableState.update { it.copy(withdrawn = false) } }

    override fun onCleared() {
        auth.removeAuthStateListener(listener)
        job?.cancel()
        super.onCleared()
    }
}
