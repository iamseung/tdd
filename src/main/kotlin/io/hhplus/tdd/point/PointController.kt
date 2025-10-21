package io.hhplus.tdd.point

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import java.lang.System.*

@RestController
@RequestMapping("/point")
class PointController(
    private val pointService: PointService
) {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    @GetMapping("{id}")
    fun point(
        @PathVariable id: Long,
    ): UserPoint {
        return pointService.getUserPoint(id)
    }

    @GetMapping("{id}/histories")
    fun history(
        @PathVariable id: Long,
    ): List<PointHistory> {
        return pointService.getUserPointHistories(id)
    }

    @PatchMapping("{id}/charge")
    fun charge(
        @PathVariable id: Long,
        @RequestBody amount: Long,
    ): UserPoint {
        val currentTimeMillis = currentTimeMillis()
        return pointService.chargeUserPoint(id, amount, currentTimeMillis)
    }

    @PatchMapping("{id}/use")
    fun use(
        @PathVariable id: Long,
        @RequestBody amount: Long,
    ): UserPoint {
        val currentTimeMillis = currentTimeMillis()
        return pointService.useUserPoint(id, amount, currentTimeMillis)
    }
}