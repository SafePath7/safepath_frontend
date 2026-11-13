package com.example.safepath_test1.ui.login

/** All operations act on the captured Google user; there is deliberately no sign-in operation. */
internal interface PhoneAccountOperations<C> {
    fun currentAccount(): SignupAccount?
    suspend fun link(credential: C): SignupAccount
    suspend fun reload(): SignupAccount
    suspend fun reauthenticate(credential: C)
    suspend fun updatePhone(credential: C)
    suspend fun refreshToken()
}

internal class PhoneAccountLinker<C>(private val operations: PhoneAccountOperations<C>) {
    suspend fun link(uid: String, phone: String, credential: C): SignupAccount {
        checkOwner(uid)
        val linked = try {
            operations.link(credential)
        } catch (error: SignupLinkException) {
            if (error.reason != SignupLinkError.ALREADY_LINKED) throw error
            checkOwner(uid)
            val existing = operations.reload()
            checkOwner(uid)
            if (existing.uid != uid) throw SignupLinkException(SignupLinkError.ACCOUNT_CHANGED)
            if (existing.phone == phone) operations.reauthenticate(credential)
            else operations.updatePhone(credential)
            checkOwner(uid)
            operations.reload()
        }
        checkResult(uid, phone, linked)
        // Reload confirms server state, including Google provider retention, before success.
        val confirmed = operations.reload()
        checkResult(uid, phone, confirmed)
        operations.refreshToken()
        checkResult(uid, phone, operations.currentAccount())
        return confirmed
    }

    private fun checkOwner(uid: String) {
        if (operations.currentAccount()?.uid != uid) throw SignupLinkException(SignupLinkError.ACCOUNT_CHANGED)
    }

    private fun checkResult(uid: String, phone: String, result: SignupAccount?) {
        checkOwner(uid)
        if (result?.uid != uid || result.phone != phone || operations.currentAccount()?.phone != phone) {
            throw SignupLinkException(SignupLinkError.ACCOUNT_CHANGED)
        }
    }
}
