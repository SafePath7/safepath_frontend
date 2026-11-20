package com.example.safepath_test1.wear.comm

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "WatchDataReceiver"

/**
 * 폰이 Data Layer로 보낸 값(길안내 상태, 지도 이미지, 위험상황 해제 신호, SOS 답장)을 받아 콜백으로 넘긴다.
 *
 * 콜백은 DataClient가 부르는 스레드(메인)에서 불린다. 지도 이미지는 [scope]에서 내려받아 디코딩한 뒤
 * 그 스코프의 스레드로 돌아와 [onMapImage]를 부른다. 화면이 사라질 때 [stop]을 부른다.
 *
 * - 리스너를 먼저 등록하고 이미 있는 값을 읽는다. 그래야 읽는 도중에 온 새 값을 놓치지 않는다.
 * - 이미 있는 값을 읽은 결과는, 그 종류의 새 값이 리스너로 이미 온 뒤라면 버린다(옛 값이 새 값을 덮지 않게).
 * - 지도 이미지는 가장 최근 요청의 결과만 쓴다. 이미지 두 장이 연달아 와도 늦게 끝난 옛 이미지가 새 이미지를 덮지 않는다.
 * - 해제 신호와 SOS 답장은 리스너로 온 것만 쓴다. 앱을 켤 때 남아 있던 옛 값은 무시한다.
 */
internal class WatchDataReceiver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onStatus: (StatusUpdate) -> Unit,
    private val onMapImage: (MapImage) -> Unit,
    private val onRelease: (updatedAt: Long) -> Unit,
    private val onSosAck: (triggeredAt: Long) -> Unit,
) {
    private val dataClient = Wearable.getDataClient(context)

    private var statusSeenFromListener = false
    private var mapSeenFromListener = false
    private var mapRequestId = 0

    private val listener =
        DataClient.OnDataChangedListener { dataEvents ->
            for (event in dataEvents) {
                Log.i(TAG, "DataEvent type=${event.type} path=${event.dataItem.uri.path}")
                if (event.type == DataEvent.TYPE_CHANGED) {
                    handle(event.dataItem, fromListener = true)
                }
            }
            dataEvents.release()
        }

    fun start() {
        dataClient.addListener(listener)
        Log.i(TAG, "Registered DataClient listener")

        // 리스너는 앞으로 바뀌는 값만 알려 주므로, 이미 있는 값은 따로 읽는다.
        dataClient.dataItems
            .addOnSuccessListener { dataItems ->
                for (i in 0 until dataItems.count) handle(dataItems[i], fromListener = false)
                dataItems.release()
            }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to read existing DataItems", e) }
    }

    fun stop() {
        dataClient.removeListener(listener)
        Log.i(TAG, "Removed DataClient listener")
    }

    private fun handle(item: DataItem, fromListener: Boolean) {
        when (item.uri.path) {
            DataLayerPaths.STATUS -> {
                if (fromListener) {
                    statusSeenFromListener = true
                } else if (statusSeenFromListener) {
                    return
                }
                onStatus(decodeStatus(DataMapItem.fromDataItem(item).dataMap.toStatusPayload()))
            }
            DataLayerPaths.MAP -> {
                if (fromListener) {
                    mapSeenFromListener = true
                } else if (mapSeenFromListener) {
                    return
                }
                loadMap(DataMapItem.fromDataItem(item).dataMap)
            }
            DataLayerPaths.RELEASE ->
                if (fromListener) {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    onRelease(dataMap.getLong(ReleaseKeys.UPDATED_AT))
                }
            DataLayerPaths.SOS_ACK ->
                if (fromListener) {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    onSosAck(dataMap.getLong(SosAckKeys.TRIGGERED_AT))
                }
        }
    }

    private fun loadMap(dataMap: DataMap) {
        val requestId = ++mapRequestId
        scope.launch {
            val image = loadMapImage(context, dataMap) ?: return@launch
            // 그 사이에 더 새 지도가 왔다면 이 결과는 낡았다.
            if (requestId == mapRequestId) onMapImage(image)
        }
    }
}
