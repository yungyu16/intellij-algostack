package com.wenjunhuang.codeepiphany.storage

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class IdGeneratorTest {
    @Test fun retainsTheLegacyEpochWorkerAndDatacenterBits() {
        val time = SnowflakeIds.EPOCH + 100
        val ids = SnowflakeIds { time }
        val first = ids.nextId()
        assertEquals(time, (first ushr 22) + SnowflakeIds.EPOCH)
        assertEquals(1L, (first ushr 12) and 31)
        assertEquals(1L, (first ushr 17) and 31)
        assertEquals(first + 1, ids.nextId())
    }
    @Test fun sequenceOverflowWaitsForTheNextMillisecond() {
        val reads = AtomicInteger()
        val ids = SnowflakeIds { SnowflakeIds.EPOCH + if (reads.incrementAndGet() <= 4097) 100 else 101 }
        val generated = List(4097) { ids.nextId() }
        assertEquals(4097, generated.toSet().size)
        assertEquals(101L, generated.last() ushr 22)
    }
    @Test fun aBackwardClockCannotGenerateDuplicateIds() {
        val now = AtomicLong(SnowflakeIds.EPOCH + 100)
        val ids = SnowflakeIds(now::get)
        ids.nextId(); now.decrementAndGet()
        assertThrows(IllegalStateException::class.java) { ids.nextId() }
    }
    @Test fun concurrentCallersReceiveDistinctIds() {
        val ids = SnowflakeIds()
        val executor = Executors.newFixedThreadPool(8)
        try {
            val values = executor.invokeAll(List(8) { Callable { List(256) { ids.nextId() } } }).flatMap { it.get() }
            assertEquals(values.size, values.toSet().size)
        } finally { executor.shutdownNow() }
    }
}
