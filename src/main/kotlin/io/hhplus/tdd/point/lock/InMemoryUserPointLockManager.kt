package io.hhplus.tdd.point.lock

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component
class InMemoryUserPointLockManager : UserPointLockManager {
    private val userLocks = ConcurrentHashMap<Long, ReentrantLock>()

    /**
     * Get or create lock for user
     *
     * @param userId user id
     * @return lock for user
     */
    private fun getLock(userId: Long): ReentrantLock {
        return userLocks.computeIfAbsent(userId) { ReentrantLock() }
    }

    /**
     * Execute action with lock for specific user
     *
     * @param userId user id to lock
     * @param action action to execute
     * @return result of action
     */
    override fun <T> withLock(userId: Long, action: () -> T): T {
        return getLock(userId).withLock {
            action()
        }
    }
}