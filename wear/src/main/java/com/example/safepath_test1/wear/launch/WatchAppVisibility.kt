package com.example.safepath_test1.wear.launch

/**
 * 워치 앱 화면(MainActivity)이 지금 보이는지. MainActivity의 onStart/onStop에서 바꾼다.
 *
 * 앱이 보이면 화면의 WatchDataReceiver가 폰의 값을 직접 받으므로, 백그라운드 서비스
 * (WatchRiskCheckListenerService)는 앱을 다시 열지 않는다. 서비스와 화면은 같은 프로세스라 이 값을 함께 본다.
 * 프로세스가 새로 뜨면 false로 시작하는데, 그때는 화면도 없으니 맞는 값이다.
 */
internal object WatchAppVisibility {
    @Volatile
    var isVisible: Boolean = false
}
