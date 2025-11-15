package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class PointControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userPointTable: UserPointTable

    @Autowired
    private lateinit var pointHistoryTable: PointHistoryTable

    @Test
    fun `특정 유저의 포인트를 조회할 수 있다`() {
        // given
        val userId = 1L
        userPointTable.insertOrUpdate(userId, 1000L)

        // when & then
        mockMvc.perform(get("/point/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(1000))
            .andExpect(jsonPath("$.updateMillis").exists())
    }

    @Test
    fun `특정 유저의 포인트 충전 및 이용 내역을 조회할 수 있다`() {
        // given
        val userId = 2L
        val currentTime = System.currentTimeMillis()

        userPointTable.insertOrUpdate(userId, 1000L)
        pointHistoryTable.insert(userId, 1000L, TransactionType.CHARGE, currentTime)
        pointHistoryTable.insert(userId, 500L, TransactionType.USE, currentTime + 1000)
        pointHistoryTable.insert(userId, 2000L, TransactionType.CHARGE, currentTime + 2000)

        // when & then
        mockMvc.perform(get("/point/$userId/histories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].userId").value(userId))
            .andExpect(jsonPath("$[0].amount").value(1000))
            .andExpect(jsonPath("$[0].type").value("CHARGE"))
            .andExpect(jsonPath("$[1].amount").value(500))
            .andExpect(jsonPath("$[1].type").value("USE"))
            .andExpect(jsonPath("$[2].amount").value(2000))
            .andExpect(jsonPath("$[2].type").value("CHARGE"))
    }

    @Test
    fun `특정 유저의 포인트를 충전할 수 있다`() {
        // given
        val userId = 3L
        val initialPoint = 1000L
        val chargeAmount = 500L

        userPointTable.insertOrUpdate(userId, initialPoint)

        // when & then
        mockMvc.perform(
            patch("/point/$userId/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(chargeAmount.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(1500))
    }

    @Test
    fun `포인트 충전 시 0 또는 음수 금액은 예외를 발생시킨다`() {
        // given
        val userId = 4L
        val invalidAmount = 0L

        userPointTable.insertOrUpdate(userId, 1000L)

        // when & then
        mockMvc.perform(
            patch("/point/$userId/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidAmount.toString())
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    fun `특정 유저의 포인트를 사용할 수 있다`() {
        // given
        val userId = 5L
        val initialPoint = 1000L
        val useAmount = 300L

        userPointTable.insertOrUpdate(userId, initialPoint)

        // when & then
        mockMvc.perform(
            patch("/point/$userId/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(useAmount.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(700))
    }

    @Test
    fun `포인트 사용 시 0 또는 음수 금액은 예외를 발생시킨다`() {
        // given
        val userId = 6L
        val invalidAmount = -100L

        userPointTable.insertOrUpdate(userId, 1000L)

        // when & then
        mockMvc.perform(
            patch("/point/$userId/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidAmount.toString())
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    fun `포인트 사용 시 잔액이 부족하면 예외를 발생시킨다`() {
        // given
        val userId = 7L
        val initialPoint = 1000L
        val useAmount = 2000L

        userPointTable.insertOrUpdate(userId, initialPoint)

        // when & then
        mockMvc.perform(
            patch("/point/$userId/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(useAmount.toString())
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINT"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.timestamp").exists())
    }
}
