package to.wim.content.data

import to.wim.content.sync.ShareResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SendToTest {
    @Test
    fun `addresses that look like email addresses`() {
        assertTrue(SendTo.isEmail("an@example.com"))
        assertTrue(SendTo.isEmail("  an@example.com "))
        assertFalse(SendTo.isEmail("an@example"))
        assertFalse(SendTo.isEmail("an example@x.com"))
        assertFalse(SendTo.isEmail(""))
    }

    @Test
    fun `the people offered are those shared with before, then the ones added here, each once`() {
        assertEquals(
            listOf("an@example.com", "bart@example.com", "new@example.com"),
            SendTo.people(listOf("an@example.com", "bart@example.com"), listOf("Bart@Example.com", "new@example.com")),
        )
    }

    @Test
    fun `tapping someone picks them, tapping again unpicks them, case ignored`() {
        val picked = SendTo.toggle(emptyList(), "an@example.com")
        assertEquals(listOf("an@example.com"), picked)
        assertEquals(listOf("an@example.com", "bart@example.com"), SendTo.toggle(picked, "bart@example.com"))
        assertEquals(emptyList<String>(), SendTo.toggle(picked, "AN@example.com"))
    }

    @Test
    fun `the button saves, saves and sends, or only sends`() {
        assertEquals(SendTo.Action.Save, SendTo.action(SaveChoice()))
        assertEquals(SendTo.Action.SaveAndSend, SendTo.action(SaveChoice(sendTo = listOf("an@example.com"))))
        assertEquals(SendTo.Action.Send, SendTo.action(SaveChoice(keep = false, sendTo = listOf("an@example.com"))))
    }

    @Test
    fun `Don't save needs someone to send to, and a new save its link`() {
        assertTrue(SendTo.canConfirm(SaveChoice(), hasLink = true))
        assertFalse(SendTo.canConfirm(SaveChoice(), hasLink = false))
        assertFalse(SendTo.canConfirm(SaveChoice(keep = false), hasLink = true))
        assertTrue(SendTo.canConfirm(SaveChoice(keep = false, sendTo = listOf("an@example.com")), hasLink = true))
    }

    @Test
    fun `one message for everyone it reached, or the first one it didn't`() {
        assertNull(SendTo.outcome(emptyList()))
        assertEquals(
            SendTo.Outcome.Sent(listOf("an@example.com", "bart@example.com")),
            SendTo.outcome(listOf("an@example.com" to ShareResult.Sent, "bart@example.com" to ShareResult.Sent)),
        )
        assertEquals(
            SendTo.Outcome.Failed("bart@example.com", ShareResult.NoAccount),
            SendTo.outcome(listOf("an@example.com" to ShareResult.Sent, "bart@example.com" to ShareResult.NoAccount, "cas@example.com" to ShareResult.Offline)),
        )
    }

    @Test
    fun `the To field suggests people not picked yet, by address or name`() {
        val recent = listOf("ann@example.com", "bob@example.com", "carol@work.be")
        val names = mapOf("bob@example.com" to "Robert Smith")
        assertEquals(listOf("bob@example.com", "carol@work.be"), SendTo.suggestions(recent, listOf("Ann@Example.com"), ""))
        assertEquals(listOf("carol@work.be"), SendTo.suggestions(recent, emptyList(), "WORK"))
        assertEquals(listOf("bob@example.com"), SendTo.suggestions(recent, emptyList(), "smith", names::get))
        assertEquals(SendTo.SUGGESTIONS, SendTo.suggestions((1..9).map { "p$it@x.be" }, emptyList(), "").size)
    }

    @Test
    fun `a whole new address typed in the To field is offered to add`() {
        val recent = listOf("ann@example.com")
        assertEquals("new@example.com", SendTo.newAddress(recent, emptyList(), " new@example.com "))
        assertEquals(null, SendTo.newAddress(recent, emptyList(), "new@exa"))
        assertEquals(null, SendTo.newAddress(recent, emptyList(), "ANN@example.com"))
        assertEquals(null, SendTo.newAddress(recent, listOf("new@example.com"), "new@example.com"))
    }
}
