package io.hhplus.tdd.point

data class UserPoint(
    val id: Long,
    val point: Long,
    val updateMillis: Long,
) {
    fun validateSufficientPoints(amount: Long) {
        require(amount > 0) {
            "Amount must be positive. Given: $amount"
        }

        require(this.point >= amount) {
            "Not enough point. Current: ${this.point}, Required: $amount"
        }
    }

    fun validatePositivePoint(amount: Long) {
        require(amount > 0) {
            "Point must be positive. Given: $amount"
        }
    }
}
