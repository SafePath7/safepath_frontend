package com.example.safepath_test1.wear

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import java.io.File

private const val TAG = "VoiceDebugRecorder"
private const val DIRECTORY = "voice_debug"
private const val MAX_FILES = 30

/**
 * 워치에서 온 소리를 WAV 파일로 폰에 남기는 테스트용 저장소. AI 쪽에 실제 녹음 샘플을 넘기려는 용도다.
 * 디버그 빌드에서만 동작하고, 최근 [MAX_FILES]개만 남긴다(오래된 파일은 지운다).
 * 위치: Android/data/<패키지>/files/voice_debug/ (Android Studio의 Device Explorer로 꺼낼 수 있다).
 * 서버 전송이 붙으면 이 저장은 빼거나 끌 수 있다.
 */
internal object VoiceDebugRecorder {

    fun isEnabled(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun save(context: Context, wav: ByteArray, capturedAt: Long) {
        if (!isEnabled(context)) return
        try {
            val directory = File(context.getExternalFilesDir(null) ?: return, DIRECTORY)
            if (!directory.exists() && !directory.mkdirs()) return
            File(directory, "voice_$capturedAt.wav").writeBytes(wav)
            directory
                .listFiles { file -> file.name.endsWith(".wav") }
                ?.sortedByDescending { it.name }
                ?.drop(MAX_FILES)
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save debug voice file", e)
        }
    }
}
