package io.github.hyh1627723044.shortvideokws

import org.junit.Assert.*
import org.junit.Test

class CommandGateTest {
    @Test fun onlyKnownKeywordsDispatch() {
        assertEquals(Command.NEXT, Command.fromKeyword("NEXT"))
        listOf("okok", "yes", "emmm", "", "NEXT PAUSE").forEach { assertNull(Command.fromKeyword(it)) }
    }
    @Test fun staleStoppedAndBusyCallbacksNeverDispatch() {
        assertFalse(CommandGate().accept(Command.NEXT, 1, 1000, true, false))
        assertFalse(CommandGate().accept(Command.NEXT, 1100, 1000, true, false))
        assertFalse(CommandGate().accept(Command.NEXT, 1000, 1000, false, false))
        assertFalse(CommandGate().accept(Command.NEXT, 1000, 1000, true, true))
    }
    @Test fun duplicatesAreSuppressedButLaterCommandsWork() {
        val gate = CommandGate()
        assertTrue(gate.accept(Command.NEXT, 1000, 1000, true, false))
        assertFalse(gate.accept(Command.NEXT, 1400, 1400, true, false))
        assertTrue(gate.accept(Command.NEXT, 2000, 2000, true, false))
        assertFalse(gate.accept(Command.PAUSE, 2100, 2100, true, false))
        assertTrue(gate.accept(Command.PAUSE, 2300, 2300, true, false))
    }
}
