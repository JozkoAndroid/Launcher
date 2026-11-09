package de.mm20.launcher2.schoolbrief

import de.mm20.launcher2.schoolbrief.live.PendingOp
import de.mm20.launcher2.schoolbrief.live.PendingQueue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Round 29: a task refused while Schoolhelp's app is away is kept, not forgotten. */
class PendingPolicyTest {
    private val add = PendingOp("today.add_todo", buildJsonObject { put("text", "Vrátiť knihu") }, localId = "local-1")

    @Test
    fun aRefusalKeepsTheTask() {
        val once = add.afterRefusal()
        assertNotNull(once)
        assertEquals(1, once!!.refusals)
    }

    @Test
    fun givenUpOnlyAfterTwentyRefusals() {
        var op: PendingOp? = add
        repeat(PendingOp.MaxRefusals - 1) { op = op!!.afterRefusal(); assertNotNull(op) }
        assertNull(op!!.afterRefusal())
    }

    @Test
    fun queueKeepsTheCountAndOldFilesStillRead() {
        val f = File.createTempFile("pending", ".json").apply { deleteOnExit() }
        val q = PendingQueue(f)
        q.add(add)
        q.replace(add, add.afterRefusal()!!)
        assertEquals(1, q.all().single().refusals)
        // A file from an older version (no "refusals" field) still reads.
        f.writeText("""[{"action":"today.add_todo","args":{"text":"Staré"},"localId":"local-2"}]""")
        assertEquals("local-2", q.all().single().localId)
    }
}
