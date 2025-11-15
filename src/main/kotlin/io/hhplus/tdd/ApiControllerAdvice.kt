package io.hhplus.tdd

import io.hhplus.tdd.point.exception.InsufficientPointException
import io.hhplus.tdd.point.exception.InvalidAmountException
import io.hhplus.tdd.point.exception.PointException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.time.LocalDateTime

data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

@RestControllerAdvice
class ApiControllerAdvice : ResponseEntityExceptionHandler() {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(ApiControllerAdvice::class.java)
    }

    @ExceptionHandler(PointException::class)
    fun handlePointException(e: PointException): ResponseEntity<ErrorResponse> {
        // sealed class의 when 표현식 - 모든 하위 타입을 강제로 처리
        return when (e) {
            is InsufficientPointException -> {
                log.warn("Insufficient point: currentPoint=${e.currentPoint}, requiredPoint=${e.requiredPoint}")
                ResponseEntity(
                    ErrorResponse("INSUFFICIENT_POINT", e.message ?: "잔고가 부족합니다."),
                    HttpStatus.BAD_REQUEST,
                )
            }
            is InvalidAmountException -> {
                log.warn("Invalid amount: amount=${e.amount}")
                ResponseEntity(
                    ErrorResponse("INVALID_AMOUNT", e.message ?: "금액은 양수여야 합니다."),
                    HttpStatus.BAD_REQUEST,
                )
            }
        }
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error occurred", e)
        return ResponseEntity(
            ErrorResponse("INTERNAL_SERVER_ERROR", "에러가 발생했습니다."),
            HttpStatus.INTERNAL_SERVER_ERROR,
        )
    }
}