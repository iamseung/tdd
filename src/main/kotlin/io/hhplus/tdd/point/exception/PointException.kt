package io.hhplus.tdd.point.exception

sealed class PointException(message: String) : RuntimeException(message)

class InsufficientPointException(
    val currentPoint: Long,
    val requiredPoint: Long
) : PointException("Not enough point. Current: $currentPoint, Required: $requiredPoint")

class InvalidAmountException(
    val amount: Long
) : PointException("Amount must be positive. Given: $amount")
