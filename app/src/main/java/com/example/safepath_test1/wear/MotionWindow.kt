package com.example.safepath_test1.wear

import java.util.TreeMap

/** 워치 움직임 샘플 하나. [tMs]는 측정 시각(Unix epoch 밀리초)이다. */
internal data class MotionSample(
    val tMs: Long,
    val ax: Float,
    val ay: Float,
    val az: Float,
    val gx: Float,
    val gy: Float,
    val gz: Float,
)

/**
 * 워치가 보낸 1초 묶음(wear-contract의 MotionKeys, 같은 길이의 배열들)을 샘플 목록으로 푼다.
 * 배열이 없거나 길이가 서로 다르면 깨진 묶음이라 빈 목록을 돌려준다.
 */
internal fun motionSamplesOf(
    timestamps: LongArray?,
    ax: FloatArray?,
    ay: FloatArray?,
    az: FloatArray?,
    gx: FloatArray?,
    gy: FloatArray?,
    gz: FloatArray?,
): List<MotionSample> {
    if (timestamps == null || ax == null || ay == null || az == null) return emptyList()
    if (gx == null || gy == null || gz == null) return emptyList()
    val n = timestamps.size
    if (listOf(ax, ay, az, gx, gy, gz).any { it.size != n }) return emptyList()
    return List(n) { i -> MotionSample(timestamps[i], ax[i], ay[i], az[i], gx[i], gy[i], gz[i]) }
}

/**
 * 최근 [keepMs] 동안의 워치 움직임 샘플을 시각 순으로 들고 있다.
 *
 * AI 분석 요청 때 음성 3초 창과 같은 구간의 움직임을 꺼내는 데 쓴다([window]).
 * 묶음이 늦게 오거나 순서가 바뀌어 와도 시각 기준으로 정렬하고, 같은 시각의 샘플은 하나만 둔다.
 * 수신 서비스와 업로드 스레드가 함께 쓰므로 모든 함수를 동기화한다.
 */
internal class MotionWindowBuffer(private val keepMs: Long = 10_000L) {
    private val samples = TreeMap<Long, MotionSample>()

    @Synchronized
    fun add(batch: List<MotionSample>) {
        for (sample in batch) samples[sample.tMs] = sample
        if (samples.isEmpty()) return
        samples.headMap(samples.lastKey() - keepMs).clear()
    }

    /** [endAt]까지의 최근 [lengthMs] 구간(endAt - lengthMs 초과, endAt 이하)의 샘플을 시각 순으로 돌려준다. */
    @Synchronized
    fun window(endAt: Long, lengthMs: Long = 3_000L): List<MotionSample> =
        samples.subMap(endAt - lengthMs, false, endAt, true).values.toList()

    @Synchronized
    fun size(): Int = samples.size

    @Synchronized
    fun clear() {
        samples.clear()
    }
}

/** 앱 프로세스에 하나만 둔다. WearDataRelayService가 채우고, AI 분석 업로드가 음성 창과 같은 구간을 꺼내 쓴다. */
internal val recentMotion = MotionWindowBuffer()
