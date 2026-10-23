package com.wenjunhuang.codeepiphany.storage

/** Same single-node Snowflake layout as DefaultIdGenerator(1, 1) used by existing records. */
internal class SnowflakeIds(private val clock: () -> Long = System::currentTimeMillis) {
    private var lastTimestamp = -1L
    private var sequence = 0L
    @Synchronized fun nextId(): Long {
        var timestamp = clock()
        check(timestamp >= lastTimestamp) { "Invalid system clock: Clock moved backwards. Refusing to generate id for ${lastTimestamp - timestamp} milliseconds" }
        sequence = if (timestamp == lastTimestamp) (sequence + 1) and 4095 else 0
        if (timestamp == lastTimestamp && sequence == 0L) {
            do { timestamp = clock(); Thread.onSpinWait() } while (timestamp <= lastTimestamp)
        }
        lastTimestamp = timestamp
        return ((timestamp - EPOCH) shl 22) or (1L shl 17) or (1L shl 12) or sequence
    }
    companion object { const val EPOCH = 1288834974657L }
}
object IdGenerator {
    private val generator = SnowflakeIds()
    @JvmStatic fun nextId(): Long = generator.nextId()
}
