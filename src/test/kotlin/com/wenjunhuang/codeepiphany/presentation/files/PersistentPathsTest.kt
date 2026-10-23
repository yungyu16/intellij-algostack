package com.wenjunhuang.codeepiphany.presentation.files

import com.wenjunhuang.codeepiphany.domain.CodeDojo
import org.junit.Assert.*
import org.junit.Test

class PersistentPathsTest {
    @Test fun acceptsLegacyCircePathsWithoutChangingIds() {
        val note = NotePathCodec.deserialize("""{"solutionId":1234567890123456789,"challengeId":42,"codeDojo":{"LeetCodeCN":{}},"projectHash":"old-project"}""")
        assertEquals(1234567890123456789L, note.solutionId)
        assertEquals(CodeDojo.LeetCodeCN, note.dojo)
        assertEquals(note, NotePathCodec.deserialize(NotePathCodec.serialize(note)))
        val code = CodePathCodec.deserialize("""{"submissionId":99,"projectHash":"old-project","codeType":{"Local":{}}}""")
        assertEquals(CodeType.Local, code.codeType)
        assertEquals(code, CodePathCodec.deserialize(CodePathCodec.serialize(code)))
    }
    @Test fun acceptsStringEnumPathsAndSeparatesProjectCaches() {
        val first = CodePathCodec.deserialize("""{"submissionId":99,"projectHash":"a","codeType":"Submission"}""")
        assertNotEquals(first, first.copy(ownerHash = "b"))
        val note = NotePathCodec.deserialize("""{"solutionId":1,"challengeId":42,"codeDojo":"leetcode","projectHash":"a"}""")
        assertEquals(CodeDojo.LeetCode, note.dojo)
        assertNotEquals(note, note.copy(ownerHash = "b"))
    }
}
