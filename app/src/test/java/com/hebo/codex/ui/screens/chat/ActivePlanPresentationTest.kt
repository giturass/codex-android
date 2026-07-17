package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.PlanStepStatus
import com.hebo.codex.domain.TurnPlanStep
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivePlanPresentationTest {
    @Test
    fun `进度优先表示正在执行的步骤位置`() {
        assertEquals(
            2,
            activePlanStepNumber(
                listOf(
                    step(PlanStepStatus.PENDING),
                    step(PlanStepStatus.IN_PROGRESS),
                    step(PlanStepStatus.PENDING),
                ),
            ),
        )
    }

    @Test
    fun `尚未开始时表示第一个待处理步骤`() {
        assertEquals(
            1,
            activePlanStepNumber(
                listOf(
                    step(PlanStepStatus.PENDING),
                    step(PlanStepStatus.PENDING),
                ),
            ),
        )
    }

    @Test
    fun `全部完成时表示最后一个步骤`() {
        assertEquals(
            2,
            activePlanStepNumber(
                listOf(
                    step(PlanStepStatus.COMPLETED),
                    step(PlanStepStatus.COMPLETED),
                ),
            ),
        )
    }

    private fun step(status: PlanStepStatus) = TurnPlanStep(
        text = "步骤",
        status = status,
    )
}
