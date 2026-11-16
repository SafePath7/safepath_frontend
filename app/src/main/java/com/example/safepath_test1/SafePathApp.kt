package com.example.safepath_test1

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.safepath_test1.friends.CreatedFriendInvite
import com.example.safepath_test1.location.TestJourneyClient
import com.example.safepath_test1.model.GeoPoint
import com.example.safepath_test1.model.PlaceSelection
import com.example.safepath_test1.ui.SafePathTab
import com.example.safepath_test1.ui.components.SafePathBottomBar
import com.example.safepath_test1.ui.guardian.GuardianScreen
import com.example.safepath_test1.ui.home.HomeScreen
import com.example.safepath_test1.ui.login.LoginPage
import com.example.safepath_test1.ui.login.SignupScreen
import com.example.safepath_test1.ui.profile.ProfileScreen
import com.example.safepath_test1.ui.theme.AppBackground
import com.example.safepath_test1.ui.theme.SafePathTheme
import com.google.firebase.auth.FirebaseAuth
import com.example.safepath_test1.api.AccountRepository
import com.example.safepath_test1.api.accountErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun SafePathApp() {
    val permissionContext = LocalContext.current
    var permissionRevision by remember { mutableIntStateOf(0) }
    val startupPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionRevision++ }
    LaunchedEffect(Unit) {
        val requested = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= 33) requested.add(Manifest.permission.POST_NOTIFICATIONS)
        if (android.os.Build.VERSION.SDK_INT >= 37 && BuildConfig.DEBUG) requested.add("android.permission.ACCESS_LOCAL_NETWORK")
        val missing = requested.filter { ContextCompat.checkSelfPermission(permissionContext, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) startupPermissions.launch(missing.toTypedArray())
    }
    val auth = remember { FirebaseAuth.getInstance() }
    val appScope = rememberCoroutineScope()
    var firebaseUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val context = LocalContext.current
    var profileVersion by remember { mutableIntStateOf(0) }
    val profile =
        context.getSharedPreferences("safe_path_profile", android.content.Context.MODE_PRIVATE)
    val repository = remember(context) { AccountRepository(context) }
    var retry by remember { mutableIntStateOf(0) }
    var membership by remember(firebaseUser?.uid) { mutableStateOf("checking") }
    var membershipError by remember(firebaseUser?.uid) { mutableStateOf<String?>(null) }
    LaunchedEffect(firebaseUser?.uid, membership) {
        if (firebaseUser != null && membership == "registered") {
            try {
                val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                com.example.safepath_test1.api.BackendRepository(context).registerToken(token)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.w("SafePathFCM", "Token registration failed", e) }
        }
    }
    LaunchedEffect(firebaseUser?.uid, profileVersion, retry) {
        val uid = firebaseUser?.uid ?: return@LaunchedEffect
        membership = "checking"
        try {
            membership = when (repository.login().status) {
                "SIGNUP_REQUIRED" -> "signup"
                "REGISTERED" -> {
                    val serverProfile = repository.profile()
                    if (auth.currentUser?.uid == uid) {
                        profile.edit().putString("onboarded_uid", uid)
                            .putString("user_name", serverProfile.name)
                            .putString("user_phone", serverProfile.phoneNumber).apply()
                    }
                    "registered"
                }
                else -> error("서버의 회원 상태를 확인할 수 없습니다.")
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            membershipError = accountErrorMessage(error)
            membership = "error"
        }
    }
    SafePathTheme {
        if (firebaseUser == null) {
            LoginPage(onLoginSuccess = { firebaseUser = auth.currentUser })
        } else if (membership == "signup") {
            SignupScreen(onComplete = { profileVersion++ }, onBack = { auth.signOut() })
        } else if (membership == "registered") {
            SafePathMain(onLogout = {
                appScope.launch {
                    try { com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken().await() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { Log.w("SafePathFCM", "Could not invalidate token on logout", e) }
                    auth.signOut()
                }
            }, permissionRevision = permissionRevision)
        } else {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (membership == "checking") androidx.compose.material3.CircularProgressIndicator()
                else {
                    Text(membershipError ?: "서버에 연결할 수 없습니다.")
                    Button(onClick = { retry++ }) { Text("다시 시도") }
                    TextButton(onClick = { auth.signOut() }) { Text("로그아웃") }
                }
            }
        }
    }
}

