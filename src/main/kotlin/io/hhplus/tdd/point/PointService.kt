package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import io.hhplus.tdd.point.lock.UserPointLockManager
import org.springframework.stereotype.Service

@Service
class PointService(
    private val userPointTable: UserPointTable,
    private val pointHistoryTable: PointHistoryTable,
    private val lockManager: UserPointLockManager,
) {
    /**
     * Get user point
     *
     * @param id
     * @return
     */
    fun getUserPoint(id: Long): UserPoint {
        return userPointTable.selectById(id)
    }

    /**
     * Get user point histories
     *
     * @param id
     * @return
     */
    fun getUserPointHistories(id: Long): List<PointHistory> {
        return pointHistoryTable.selectAllByUserId(id)
    }

    /**
     * Charge user point
     *
     * @param id
     * @param amount
     * @return
     */
    fun chargeUserPoint(id: Long, amount: Long, currentTimeMillis: Long): UserPoint {
        return lockManager.withLock(id) {
            val userPoint = userPointTable.selectById(id)
            userPoint.validatePositivePoint(amount)

            val updatedPoint = userPointTable.insertOrUpdate(id, userPoint.point + amount)
            saveHistory(id, amount, TransactionType.CHARGE, currentTimeMillis)

            updatedPoint
        }
    }

    /**
     * Use user point
     *
     * @param id
     * @param amount
     * @return
     */
    fun useUserPoint(id: Long, amount: Long, currentTimeMillis: Long): UserPoint {
        return lockManager.withLock(id) {
            val userPoint = userPointTable.selectById(id)
            userPoint.validateSufficientPoints(amount)

            val updatedPoint = userPointTable.insertOrUpdate(id, userPoint.point - amount)
            saveHistory(id, amount, TransactionType.USE, currentTimeMillis)

            updatedPoint
        }
    }

    /**
     * Save history
     *
     * @param id
     * @param amount
     * @param transactionType
     */
    private fun saveHistory(id: Long, amount: Long, transactionType: TransactionType, currentTimeMillis: Long) {
        pointHistoryTable.insert(id, amount, transactionType, currentTimeMillis)
    }
}