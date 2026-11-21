package com.example.safepath_test1.wear.sensors

/** 워치 움직임 측정 속도(Hz). AI 동작 모델이 기대하는 속도이고, 폰에도 이 값을 함께 보낸다. */
internal const val MOTION_RATE_HZ = 20

/** 폰으로 한 번에 보내는 1초 분량의 움직임 샘플. */
internal data class MotionBatch(val rateHz: Int, val samples: List<MotionData>)

/**
 * 20Hz 샘플을 [windowMs](기본 1초)씩 모아 묶음으로 내준다.
 *
 * 새 샘플이 지금 묶음의 첫 샘플보다 [windowMs] 이상 늦으면 지금까지 모은 것을 묶음으로 내주고,
 * 새 샘플부터 다음 묶음을 시작한다. 그래서 묶음 하나는 보통 샘플 20개다.
 * 기기 시계가 뒤로 가면(새 샘플이 첫 샘플보다 이르면) 순서가 섞이지 않게 지금 묶음을 바로 내준다.
 */
internal class MotionBatcher(private val windowMs: Long = 1_000L) {
    private val pending = ArrayList<MotionData>()

    /** 샘플을 넣고, 묶음이 다 찼으면 그 묶음을 돌려준다. 아니면 null이다. */
    fun add(sample: MotionData): MotionBatch? {
        val first = pending.firstOrNull()
        val windowFull = first != null && sample.timestamp - first.timestamp >= windowMs
        val clockWentBack = first != null && sample.timestamp < first.timestamp
        val batch =
            if (windowFull || clockWentBack) {
                MotionBatch(MOTION_RATE_HZ, pending.toList()).also { pending.clear() }
            } else {
                null
            }
        pending += sample
        return batch
    }

    /** 모으던 샘플을 버린다. 센서를 멈출 때 부른다. */
    fun reset() {
        pending.clear()
    }
}
