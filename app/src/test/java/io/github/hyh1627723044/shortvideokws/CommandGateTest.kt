package io.github.hyh1627723044.shortvideokws

import org.junit.Assert.*
import org.junit.Test

class CommandGateTest {
    private fun local(command: Command, at: Long) = CommandRequest.local(command, at)

    @Test fun onlyKnownKeywordsDispatch() {
        assertEquals(Command.NEXT, Command.fromKeyword("NEXT"))
        listOf("okok", "yes", "emmm", "", "NEXT PAUSE").forEach { assertNull(Command.fromKeyword(it)) }
    }
    @Test fun staleStoppedAndBusyCallbacksNeverDispatch() {
        assertFalse(CommandGate().accept(local(Command.NEXT, 1), 1000, true, false))
        assertFalse(CommandGate().accept(local(Command.NEXT, 1100), 1000, true, false))
        assertFalse(CommandGate().accept(local(Command.NEXT, 1000), 1000, false, false))
        assertFalse(CommandGate().accept(local(Command.NEXT, 1000), 1000, true, true))
    }
    @Test fun duplicatesAreSuppressedButLaterCommandsWork() {
        val gate = CommandGate()
        assertTrue(gate.accept(local(Command.NEXT, 1000), 1000, true, false))
        assertFalse(gate.accept(local(Command.NEXT, 1400), 1400, true, false))
        assertTrue(gate.accept(local(Command.NEXT, 2000), 2000, true, false))
        assertFalse(gate.accept(local(Command.PAUSE, 2100), 2100, true, false))
        assertTrue(gate.accept(local(Command.PAUSE, 2300), 2300, true, false))
    }
    @Test fun localRequestsExpireAfter700ms() {
        assertTrue(CommandGate().accept(local(Command.NEXT, 1000), 1700, true, false))
        assertFalse(CommandGate().accept(local(Command.NEXT, 1000), 1701, true, false))
    }
    @Test fun cloudRequestsExpireFiveSecondsAfterUtteranceEnd() {
        val request = CommandRequest.cloud(Command.LIKE, 7, 10_000)
        assertEquals(CommandSource.CLOUD, request.source)
        assertTrue(CommandGate().accept(request, 15_000, true, false))
        assertFalse(CommandGate().accept(request, 15_001, true, false))
        assertFalse(CommandGate().accept(request, 9_999, true, false))
    }
}
