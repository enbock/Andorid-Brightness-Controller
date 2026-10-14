package com.ambientbrightness.core.BackgroundService

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Guards the F5 domain model: [BackgroundServiceState] must cover every state the four user stories
 * require and must stay exhaustively branchable for the notification (T2) and the F6/F7 UI.
 */
class BackgroundServiceStateTest {

    private val allStates: List<BackgroundServiceState> = listOf(
        BackgroundServiceState.Stopped,
        BackgroundServiceState.Starting,
        BackgroundServiceState.Running(BrightnessValue(1)),
        BackgroundServiceState.Running(appliedBrightness = null),
        BackgroundServiceState.StoppedBySystem,
        BackgroundServiceState.Failed("WRITE_SETTINGS revoked"),
    )

    @Test
    fun `the sealed hierarchy is exhaustive - every state maps to a distinct label`() {
        val labels = allStates.map { label(it) }

        assertEquals(
            listOf("stopped", "starting", "running", "running", "stopped-by-system", "failed"),
            labels,
        )
    }

    @Test
    fun `US1 - the controller can be starting and then running with an applied brightness`() {
        val starting: BackgroundServiceState = BackgroundServiceState.Starting
        val running: BackgroundServiceState = BackgroundServiceState.Running(BrightnessValue(200))

        assertEquals("starting", label(starting))
        assertEquals("running", label(running))
        assertEquals(BrightnessValue(200), (running as BackgroundServiceState.Running).appliedBrightness)
    }

    @Test
    fun `US1 and US4 - running tolerates a controller that has not applied a brightness yet`() {
        val running = BackgroundServiceState.Running()

        assertNull(running.appliedBrightness)
        assertEquals(BackgroundServiceState.Running(appliedBrightness = null), running)
    }

    @Test
    fun `US2 - a system stop is distinguishable from a deliberate user stop`() {
        assertNotEquals(BackgroundServiceState.Stopped, BackgroundServiceState.StoppedBySystem)
        assertEquals("stopped-by-system", label(BackgroundServiceState.StoppedBySystem))
    }

    @Test
    fun `US3 - a user stop is its own state`() {
        assertEquals("stopped", label(BackgroundServiceState.Stopped))
    }

    @Test
    fun `US3 AC3_4 - a failure carries a reason and never throws`() {
        val failed: BackgroundServiceState = BackgroundServiceState.Failed("Write failed")

        assertEquals("failed", label(failed))
        assertEquals("Write failed", (failed as BackgroundServiceState.Failed).reason)
    }

    @Test
    fun `Running is a value object - equality and copy over the brightness payload`() {
        val one = BackgroundServiceState.Running(BrightnessValue(10))
        val same = BackgroundServiceState.Running(BrightnessValue(10))
        val other = BackgroundServiceState.Running(BrightnessValue(20))
        val noBrightness = BackgroundServiceState.Running()

        assertEquals(one, same)
        assertEquals(one.hashCode(), same.hashCode())
        assertNotEquals(one, other)
        assertNotEquals(one, noBrightness)
        assertEquals(other, one.copy(appliedBrightness = BrightnessValue(20)))
        assertEquals(BrightnessValue(10), one.copy().appliedBrightness)
    }

    @Test
    fun `Failed is a value object - equality and copy over the reason payload`() {
        val one = BackgroundServiceState.Failed("WRITE_SETTINGS revoked")
        val same = BackgroundServiceState.Failed("WRITE_SETTINGS revoked")

        assertEquals(one, same)
        assertEquals(one.hashCode(), same.hashCode())
        assertNotEquals(one, BackgroundServiceState.Failed("Sensor unavailable"))
        assertEquals(
            BackgroundServiceState.Failed("Sensor unavailable"),
            one.copy(reason = "Sensor unavailable"),
        )
    }

    @Test
    fun `StartReason covers the three ways the controller can be started`() {
        assertEquals(
            listOf(StartReason.UserRequest, StartReason.SystemRestart, StartReason.Boot),
            StartReason.entries.toList(),
        )
    }

    @Test
    fun `StartOutcome is either Started or Blocked with a reason`() {
        val started: StartOutcome = StartOutcome.Started
        val blocked: StartOutcome = StartOutcome.Blocked("Battery optimization active")

        assertEquals("started", outcomeLabel(started))
        assertEquals("blocked", outcomeLabel(blocked))
        assertEquals("Battery optimization active", (blocked as StartOutcome.Blocked).reason)
        assertNotEquals(StartOutcome.Started, blocked)
    }

    /** Exhaustive without `else`: compilation itself proves the sealed hierarchy stays total. */
    private fun label(state: BackgroundServiceState): String = when (state) {
        BackgroundServiceState.Stopped -> "stopped"
        BackgroundServiceState.Starting -> "starting"
        is BackgroundServiceState.Running -> "running"
        BackgroundServiceState.StoppedBySystem -> "stopped-by-system"
        is BackgroundServiceState.Failed -> "failed"
    }

    private fun outcomeLabel(outcome: StartOutcome): String = when (outcome) {
        StartOutcome.Started -> "started"
        is StartOutcome.Blocked -> "blocked"
    }
}
