package io.hhplus.tdd.point.lock

interface UserPointLockManager {
    /**
     * Execute action with lock for specific user
     *
     * @param userId user id to lock
     * @param action action to execute
     * @return result of action
     */
    fun <T> withLock(userId: Long, action: () -> T): T
}