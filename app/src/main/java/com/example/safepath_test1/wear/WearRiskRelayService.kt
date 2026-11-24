package com.example.safepath_test1.wear

import android.util.Log
import kotlinx.coroutines.*
import com.google.firebase.auth.FirebaseAuth
import com.example.safepath_test1.api.BackendRepository
import com.example.safepath_test1.wear.comm.DataLayerPaths
import com.example.safepath_test1.wear.comm.RiskAnswerKeys
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "WearRiskRelayService"

class WearRiskRelayService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != DataLayerPaths.RISK_ANSWER) continue

            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            val id = dataMap.getString(RiskAnswerKeys.EVENT_ID)?.toLongOrNull() ?: continue
            val status = serverRiskStatus(dataMap.getString(RiskAnswerKeys.ANSWER)) ?: continue
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: continue
            val session = getSharedPreferences("backend_sessions", MODE_PRIVATE).getString(uid, null) ?: continue
            val repo = BackendRepository(applicationContext)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val event = repo.riskEvent(id)
                    if (event.sessionId == session && event.status == "PENDING" && FirebaseAuth.getInstance().currentUser?.uid == uid) {
                        repo.updateRiskEventStatus(id, status)
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { Log.w(TAG, "Risk answer delivery failed", e) }
            }
            Log.i(
                TAG,
                "Risk answer received from watch " +
                    "eventId=${dataMap.getString(RiskAnswerKeys.EVENT_ID)} " +
                    "answer=${dataMap.getString(RiskAnswerKeys.ANSWER)} " +
                    "answeredAt=${dataMap.getLong(RiskAnswerKeys.ANSWERED_AT)}",
            )
        }
    }
}

internal fun serverRiskStatus(answer: String?): String? = when (answer) {
    "SAFE" -> "DISMISSED"
    "HELP", "NO_RESPONSE" -> "CONFIRMED"
    else -> null
}
