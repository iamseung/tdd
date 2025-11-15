package io.hhplus.tdd.point

import io.hhplus.tdd.point.exception.InsufficientPointException
import io.hhplus.tdd.point.exception.InvalidAmountException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UserPointTest {

    @Test
    fun `포인트 사용 시, 포인트가 충분하면 검증에 성공한다`() {
        // given
        val userPoint = UserPoint(1L, 1000L, System.currentTimeMillis())
        val useAmount = 500L

        // when & then
        assertDoesNotThrow {
            userPoint.validateSufficientPoints(useAmount)
        }
    }

    @Test
    fun `포인트 사용 시, 포인트가 부족하면 예외가 발생한다`() {
        // given
        val userPoint = UserPoint(1L, 500L, System.currentTimeMillis())
        val useAmount = 1000L

        // when & then
        val exception = assertThrows<InsufficientPointException> {
            userPoint.validateSufficientPoints(useAmount)
        }

        assertThat(exception.message).contains("Not enough point")
        assertThat(exception.message).contains("Current: 500")
        assertThat(exception.message).contains("Required: 1000")
        assertThat(exception.currentPoint).isEqualTo(500L)
        assertThat(exception.requiredPoint).isEqualTo(1000L)
    }

    @Test
    fun `포인트 사용 시, 사용 포인트와 보유 포인트가 같으면 검증에 성공한다`() {
        // given
        val userPoint = UserPoint(1L, 1000L, System.currentTimeMillis())
        val useAmount = 1000L

        // when & then
        assertDoesNotThrow {
            userPoint.validateSufficientPoints(useAmount)
        }
    }

    @Test
    fun `포인트 충전 시, 음수의 포인트를 입력받으면 예외가 발생한다`() {
        // given
        val userPoint = UserPoint(1L, 500L, System.currentTimeMillis())
        val chargeAmount = -1000L

        // when
        val exception = assertThrows<InvalidAmountException> {
            userPoint.validatePositivePoint(chargeAmount)
        }

        // then
        assertThat(exception.message).contains("Amount must be positive.")
        assertThat(exception.message).contains("Given: -1000")
        assertThat(exception.amount).isEqualTo(-1000L)
    }

    @Test
    fun `포인트 충전 시, 0 포인트를 입력받으면 예외가 발생한다`() {
        // given
        val userPoint = UserPoint(1L, 500L, System.currentTimeMillis())
        val chargeAmount = 0L

        // when
        val exception = assertThrows<InvalidAmountException> {
            userPoint.validatePositivePoint(chargeAmount)
        }

        // then
        assertThat(exception.message).contains("Amount must be positive.")
        assertThat(exception.message).contains("Given: 0")
        assertThat(exception.amount).isEqualTo(0L)
    }
}