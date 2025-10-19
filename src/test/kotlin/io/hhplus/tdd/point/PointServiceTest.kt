package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PointServiceTest {

    @InjectMocks
    private lateinit var pointService: PointService

    @Mock
    private lateinit var userPointTable: UserPointTable

    @Mock
    private lateinit var pointHistoryTable: PointHistoryTable

    @Test
    fun `특정 유저의 아이디를 기반으로 포인트를 조회할 수 있다`() {
        // given
        val userId = 1L
        val expectedUserPoint = UserPoint(id = userId, point = 1000L, updateMillis = System.currentTimeMillis())

        `when`(userPointTable.selectById(anyLong())).thenReturn(expectedUserPoint)

        // when
        val result = pointService.getUserPoint(userId)

        // then
        assertThat(result.id).isEqualTo(expectedUserPoint.id)
        assertThat(result.point).isEqualTo(expectedUserPoint.point)
        verify(userPointTable).selectById(userId)
    }

    @Test
    fun `특정 유저의 아이디를 기반으로 포인트 충전,이용 내역을 조회할 수 있다`() {
        // given
        val userId = 1L
        val expectedPointHistories = listOf(
            PointHistory(id = 1L, userId = userId, amount = 1000L, type = TransactionType.CHARGE, timeMillis = System.currentTimeMillis()),
            PointHistory(id = 2L, userId = userId, amount = 2000L, type = TransactionType.USE, timeMillis = System.currentTimeMillis()),
            PointHistory(id = 3L, userId = userId, amount = 3000L, type = TransactionType.CHARGE, timeMillis = System.currentTimeMillis()),
        )

        `when`(pointHistoryTable.selectAllByUserId(anyLong())).thenReturn(expectedPointHistories)

        // when
        val results = pointService.getUserPointHistories(userId)

        // then
        assertThat(results).hasSize(3)
            .containsExactlyElementsOf(expectedPointHistories)

        verify(pointHistoryTable).selectAllByUserId(userId)
    }

    @Test
    fun `특정 유저의 포인트를 충전할 수 있다`() {
        // given
        val userId = 1L
        val initialPoint = 1000L
        val chargeAmount = 500L
        val expectedPoint = initialPoint + chargeAmount
        val currentTimeMillis = System.currentTimeMillis()

        val userPoint = UserPoint(id = userId, point = initialPoint, updateMillis = currentTimeMillis)
        val chargedUserPoint = UserPoint(id = userId, point = expectedPoint, updateMillis = currentTimeMillis)

        `when`(userPointTable.selectById(anyLong())).thenReturn(userPoint)
        `when`(userPointTable.insertOrUpdate(anyLong(), anyLong())).thenReturn(chargedUserPoint)

        // when
        val result = pointService.chargeUserPoint(userId, chargeAmount, currentTimeMillis)

        // then
        assertThat(result.point).isEqualTo(expectedPoint)

        verify(userPointTable, times(1)).selectById(anyLong())
        verify(userPointTable, times(1)).insertOrUpdate(anyLong(), anyLong())
        verify(pointHistoryTable, times(1)).insert(userId, chargeAmount, TransactionType.CHARGE, currentTimeMillis)
    }

    @Test
    fun `특정 유저의 포인트를 사용할 수 있다`() {
        // given
        val userId = 1L
        val initialPoint = 1000L
        val useAmount = 500L
        val expectedPoint = initialPoint - useAmount
        val currentTimeMillis = System.currentTimeMillis()

        val userPoint = UserPoint(id = userId, point = initialPoint, updateMillis = currentTimeMillis)
        val usedUserPoint = UserPoint(id = userId, point = expectedPoint, updateMillis = currentTimeMillis)

        `when`(userPointTable.selectById(anyLong())).thenReturn(userPoint)
        `when`(userPointTable.insertOrUpdate(anyLong(), anyLong())).thenReturn(usedUserPoint)

        // when
        val result = pointService.useUserPoint(userId, useAmount, currentTimeMillis)

        // then
        assertThat(result.point).isEqualTo(expectedPoint)

        verify(userPointTable, times(1)).selectById(anyLong())
        verify(userPointTable, times(1)).insertOrUpdate(anyLong(), anyLong())
        verify(pointHistoryTable, times(1)).insert(userId, useAmount, TransactionType.USE, currentTimeMillis)
    }

    @Test
    fun `포인트가 부족하면 예외를 발생시킨다`() {
        // given
        val userId = 1L
        val initialPoint = 500L
        val useAmount = 1000L
        val currentTimeMillis = System.currentTimeMillis()

        val userPoint = UserPoint(userId, initialPoint, currentTimeMillis)
        `when`(userPointTable.selectById(userId)).thenReturn(userPoint)

        // when & then
        val exception = assertThrows<IllegalArgumentException> {
            pointService.useUserPoint(userId, useAmount, currentTimeMillis)
        }

        assertThat(exception.message).contains("Not enough point")
    }
}