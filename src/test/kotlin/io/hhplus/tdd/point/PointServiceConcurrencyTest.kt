package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import io.hhplus.tdd.point.lock.InMemoryUserPointLockManager
import io.hhplus.tdd.point.lock.UserPointLockManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PointServiceConcurrencyTest {

    private lateinit var pointService: PointService
    private lateinit var userPointTable: UserPointTable
    private lateinit var pointHistoryTable: PointHistoryTable
    private lateinit var lockManager: UserPointLockManager

    @BeforeEach
    fun setUp() {
        userPointTable = UserPointTable()
        pointHistoryTable = PointHistoryTable()
        lockManager = InMemoryUserPointLockManager()
        pointService = PointService(userPointTable, pointHistoryTable, lockManager)
    }

    @Test
    fun `동일 사용자에 대한 동시 충전 요청이 순차적으로 처리된다`() {
        // given
        val userId = 1L
        val chargeAmount = 100L
        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val currentTimeMillis = System.currentTimeMillis()

        // when: 동시에 10번 충전 요청
        repeat(threadCount) {
            executor.submit {
                try {
                    pointService.chargeUserPoint(userId, chargeAmount, currentTimeMillis)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // then: 최종 포인트는 정확히 1000이어야 함 (100 * 10)
        val finalPoint = pointService.getUserPoint(userId)
        assertThat(finalPoint.point).isEqualTo(chargeAmount * threadCount)

        // 히스토리도 정확히 10개여야 함
        val histories = pointService.getUserPointHistories(userId)
        assertThat(histories).hasSize(threadCount)
        assertThat(histories.all { it.type == TransactionType.CHARGE }).isTrue()
    }

    @Test
    fun `동일 사용자에 대한 동시 사용 요청이 순차적으로 처리된다`() {
        // given
        val userId = 1L
        val initialPoint = 1000L
        val useAmount = 100L
        val threadCount = 10
        val currentTimeMillis = System.currentTimeMillis()

        // 초기 포인트 충전
        pointService.chargeUserPoint(userId, initialPoint, currentTimeMillis)

        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        // when: 동시에 10번 사용 요청
        repeat(threadCount) {
            executor.submit {
                try {
                    pointService.useUserPoint(userId, useAmount, currentTimeMillis)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // then: 최종 포인트는 정확히 0이어야 함 (1000 - 100 * 10)
        val finalPoint = pointService.getUserPoint(userId)
        assertThat(finalPoint.point).isEqualTo(0L)

        // 히스토리는 11개여야 함 (충전 1개 + 사용 10개)
        val histories = pointService.getUserPointHistories(userId)
        assertThat(histories).hasSize(threadCount + 1)
        assertThat(histories.count { it.type == TransactionType.USE }).isEqualTo(threadCount)
    }

    @Test
    fun `동일 사용자에 대한 충전과 사용이 동시에 발생해도 데이터 정합성이 유지된다`() {
        // given
        val userId = 1L
        val chargeAmount = 100L
        val useAmount = 50L
        val threadCount = 20 // 충전 10번, 사용 10번
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val currentTimeMillis = System.currentTimeMillis()

        // when: 충전과 사용을 동시에 요청
        repeat(threadCount / 2) {
            // 충전 스레드
            executor.submit {
                try {
                    pointService.chargeUserPoint(userId, chargeAmount, currentTimeMillis)
                } finally {
                    latch.countDown()
                }
            }
            // 사용 스레드
            executor.submit {
                try {
                    pointService.useUserPoint(userId, useAmount, currentTimeMillis)
                } catch (e: IllegalArgumentException) {
                    // 포인트 부족 예외는 정상적인 경우
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // then: 최종 포인트 검증
        val finalPoint = pointService.getUserPoint(userId)
        val histories = pointService.getUserPointHistories(userId)

        val totalCharged = histories.filter { it.type == TransactionType.CHARGE }.sumOf { it.amount }
        val totalUsed = histories.filter { it.type == TransactionType.USE }.sumOf { it.amount }

        // 최종 포인트 = 총 충전 - 총 사용
        assertThat(finalPoint.point).isEqualTo(totalCharged - totalUsed)

        // 모든 트랜잭션이 기록되어야 함
        assertThat(histories).isNotEmpty()
    }

    @Test
    fun `서로 다른 사용자에 대한 동시 요청은 독립적으로 처리된다`() {
        // given
        val userCount = 5
        val chargeAmount = 100L
        val threadCount = userCount * 10 // 각 유저당 10번씩
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val currentTimeMillis = System.currentTimeMillis()

        // when: 여러 사용자에 대해 동시에 충전 요청
        repeat(userCount) { userId ->
            repeat(10) {
                executor.submit {
                    try {
                        pointService.chargeUserPoint(userId.toLong() + 1, chargeAmount, currentTimeMillis)
                    } finally {
                        latch.countDown()
                    }
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // then: 각 사용자의 포인트가 정확히 1000이어야 함
        repeat(userCount) { userId ->
            val finalPoint = pointService.getUserPoint(userId.toLong() + 1)
            assertThat(finalPoint.point).isEqualTo(1000L)

            val histories = pointService.getUserPointHistories(userId.toLong() + 1)
            assertThat(histories).hasSize(10)
        }
    }

    @Test
    fun `동시성 환경에서 포인트 부족 예외가 정상적으로 동작한다`() {
        // given
        val userId = 1L
        val initialPoint = 500L
        val useAmount = 100L
        val threadCount = 10
        val currentTimeMillis = System.currentTimeMillis()

        // 초기 포인트 충전 (500원)
        pointService.chargeUserPoint(userId, initialPoint, currentTimeMillis)

        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // when: 동시에 10번 사용 시도 (500원으로는 5번만 가능)
        repeat(threadCount) {
            executor.submit {
                try {
                    pointService.useUserPoint(userId, useAmount, currentTimeMillis)
                    successCount.incrementAndGet()
                } catch (e: IllegalArgumentException) {
                    failCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // then: 성공 5번, 실패 5번
        assertThat(successCount.get()).isEqualTo(5)
        assertThat(failCount.get()).isEqualTo(5)

        // 최종 포인트는 0
        val finalPoint = pointService.getUserPoint(userId)
        assertThat(finalPoint.point).isEqualTo(0L)

        // 히스토리는 6개 (충전 1개 + 사용 5개)
        val histories = pointService.getUserPointHistories(userId)
        assertThat(histories).hasSize(6)
        assertThat(histories.count { it.type == TransactionType.USE }).isEqualTo(5)
    }
}