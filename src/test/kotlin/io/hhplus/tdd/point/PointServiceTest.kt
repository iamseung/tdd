package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import io.hhplus.tdd.point.lock.UserPointLockManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("포인트 기능 테스트")
class PointServiceTest {

    private lateinit var pointService: PointService
    private lateinit var userPointTable: UserPointTable
    private lateinit var pointHistoryTable: PointHistoryTable

    private val lockManager = object : UserPointLockManager {
        override fun <T> withLock(userId: Long, action: () -> T): T {
            return action()
        }
    }

    @BeforeEach
    fun setUp() {
        userPointTable = UserPointTable()
        pointHistoryTable = PointHistoryTable()
        pointService = PointService(userPointTable, pointHistoryTable, lockManager)
    }

    @Test
    fun `특정 유저의 아이디를 기반으로 포인트를 조회할 수 있다`() {
        // given
        val userId = 1L
        val expectedPoint = 1000L
        userPointTable.insertOrUpdate(userId, expectedPoint)

        // when
        val result = pointService.getUserPoint(userId)

        // then
        assertThat(result.id).isEqualTo(userId)
        assertThat(result.point).isEqualTo(expectedPoint)
    }

    @Test
    fun `특정 유저의 아이디를 기반으로 포인트 충전,이용 내역을 조회할 수 있다`() {
        // given
        val userId = 1L
        val chargeAmount = 1000L
        val currentTimeMillis = System.currentTimeMillis()

        pointHistoryTable.insert(userId, chargeAmount, TransactionType.CHARGE, currentTimeMillis)

        // when
        val results = pointService.getUserPointHistories(userId)

        // then
        assertThat(results).hasSize(1)
        assertThat(results[0].userId).isEqualTo(userId)
        assertThat(results[0].amount).isEqualTo(chargeAmount)
        assertThat(results[0].type).isEqualTo(TransactionType.CHARGE)
    }

    @Test
    fun `특정 유저의 포인트를 충전할 수 있다`() {
        // given
        val userId = 1L
        val chargeAmount = 500L
        val currentTimeMillis = System.currentTimeMillis()

        // when
        val result = pointService.chargeUserPoint(userId, chargeAmount, currentTimeMillis)

        // then
        assertThat(result.point).isEqualTo(chargeAmount)

        // 히스토리도 함께 검증
        val histories = pointService.getUserPointHistories(userId)
        assertThat(histories).hasSize(1)
        assertThat(histories[0].type).isEqualTo(TransactionType.CHARGE)
        assertThat(histories[0].amount).isEqualTo(chargeAmount)
    }

    @Test
    fun `특정 유저의 포인트를 사용할 수 있다`() {
        // given
        val userId = 1L
        val initialPoint = 1000L
        val useAmount = 500L
        val currentTimeMillis = System.currentTimeMillis()

        userPointTable.insertOrUpdate(userId, initialPoint)

        // when
        val result = pointService.useUserPoint(userId, useAmount, currentTimeMillis)

        // then
        assertThat(result.point).isEqualTo(initialPoint - useAmount)

        val histories = pointService.getUserPointHistories(userId)
        assertThat(histories).hasSize(1)
        assertThat(histories[0].type).isEqualTo(TransactionType.USE)
        assertThat(histories[0].amount).isEqualTo(useAmount)
    }

    @Test
    fun `포인트가 부족하면 예외를 발생시킨다`() {
        // given
        val userId = 1L
        val initialPoint = 500L
        val useAmount = 1000L
        val currentTimeMillis = System.currentTimeMillis()

        userPointTable.insertOrUpdate(userId, initialPoint)

        // when & then
        val exception = assertThrows<IllegalArgumentException> {
            pointService.useUserPoint(userId, useAmount, currentTimeMillis)
        }

        assertThat(exception.message).contains("Not enough point")
    }
}
