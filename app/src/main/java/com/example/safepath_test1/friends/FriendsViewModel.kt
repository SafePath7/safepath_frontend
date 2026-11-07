package com.example.safepath_test1.friends

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.safepath_test1.ui.guardian.FriendshipRequest
import com.example.safepath_test1.ui.guardian.GuardianItem
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val friends: List<GuardianItem> = emptyList(),
    val received: List<FriendshipRequest> = emptyList(),
    val sent: List<FriendshipRequest> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val invite: CreatedFriendInvite? = null,
)

class FriendsViewModel(application: Application, private val repository: FriendsDataSource) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, FriendsRepository(application))
    private val state = MutableStateFlow(FriendsUiState())
    val uiState = state.asStateFlow()
    val isConfigured
        get() = repository.isConfigured

    private var refreshJob: Job? = null
    private var mutationJob: Job? = null
    private var accountId: String? = null
    private var submissionId = java.util.UUID.randomUUID().toString()
    private val auth = FirebaseAuth.getInstance()
    // 계정이 바뀌면 이전 요청을 취소해 다른 계정의 결과가 화면에 섞이지 않게 한다.
    private val authListener =
        FirebaseAuth.AuthStateListener { firebaseAuth ->
            val next = firebaseAuth.currentUser?.uid
            if (next != accountId) {
                accountId = next
                refreshJob?.cancel()
                mutationJob?.cancel()
                state.value = FriendsUiState(friends = readServerFriends(getApplication()))
                submissionId = java.util.UUID.randomUUID().toString()
                if (next != null) refresh()
            }
        }

    init {
        auth.addAuthStateListener(authListener)
    }

    fun refresh() {
        if (
            !repository.isConfigured ||
                accountId == null ||
                state.value.isSubmitting ||
                state.value.isLoading
        )
            return
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                state.update { it.copy(isLoading = true) }
                try {
                    val snapshot = repository.load()
                    state.update {
                        it.copy(
                            friends = snapshot.friends,
                            received = snapshot.received,
                            sent = snapshot.sent,
                            isLoading = false,
                            error = null,
                        )
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    state.update {
                        it.copy(
                            isLoading = false,
                            error = exception.message ?: "친구 목록을 불러오지 못했습니다.",
                        )
                    }
                }
            }
    }

    fun create() = mutate {
        val invite = repository.create(submissionId)
        state.update { it.copy(invite = invite) }
        submissionId = java.util.UUID.randomUUID().toString()
    }

    fun respond(id: String, action: String) = mutate { repository.respond(id, action) }

    fun clearError() {
        state.update { it.copy(error = null) }
    }

    fun consumeInvite() {
        state.update { it.copy(invite = null) }
    }

    private fun mutate(action: suspend () -> Unit) {
        if (state.value.isSubmitting) return
        refreshJob?.cancel()
        state.update { it.copy(isSubmitting = true, isLoading = false, error = null) }
        mutationJob =
            viewModelScope.launch {
                try {
                    action()
                    state.update { it.copy(isSubmitting = false) }
                    refresh()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    state.update {
                        it.copy(
                            isSubmitting = false,
                            error = exception.message ?: "친구 요청을 처리하지 못했습니다.",
                        )
                    }
                }
            }
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }
}
