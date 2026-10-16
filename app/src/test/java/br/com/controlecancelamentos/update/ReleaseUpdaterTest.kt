package br.com.controlecancelamentos.update

import org.junit.Assert.*
import org.junit.Test

class ReleaseUpdaterTest {
    @Test fun detectaVersaoNova() {
        assertTrue(ReleaseUpdater.isNewer("v4.3.0", "4.2.0"))
        assertTrue(ReleaseUpdater.isNewer("4.2.1", "4.2.0"))
        assertFalse(ReleaseUpdater.isNewer("v4.2.0", "4.2.0"))
        assertFalse(ReleaseUpdater.isNewer("4.1.9", "4.2.0"))
    }
    @Test fun recusaVersoesInvalidasOuRebaixamento() {
        assertFalse(ReleaseUpdater.isNewer("abc", "4.2.0"))
        assertFalse(ReleaseUpdater.isNewer("4.2.0", "invalida"))
        assertFalse(ReleaseUpdater.isNewer("4.2", "4.1.0"))
        assertFalse(ReleaseUpdater.isNewer("4.1.0", "4.2.0"))
    }
}
