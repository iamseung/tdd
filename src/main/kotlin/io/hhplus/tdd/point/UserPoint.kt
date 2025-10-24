package io.hhplus.tdd.point

import io.hhplus.tdd.point.exception.InsufficientPointException
import io.hhplus.tdd.point.exception.InvalidAmountException

data class UserPoint(
    val id: Long,
    val point: Long,
    val updateMillis: Long,
) {
    fun validateSufficientPoints(amount: Long) {
        validatePositiveAmount(amount)
        if (this.point < amount) {
            throw InsufficientPointException(this.point, amount)
        }
    }

    fun validatePositivePoint(amount: Long) {
        validatePositiveAmount(amount)
    }

    private fun validatePositiveAmount(amount: Long) {
        if (amount <= 0) {
            throw InvalidAmountException(amount)
        }
    }
}
