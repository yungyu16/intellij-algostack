package com.wenjunhuang.codeepiphany.domain

import com.wenjunhuang.codeepiphany.leetcode.api.LeetCodeJson
import org.junit.Assert.*
import org.junit.Test

class LanguageContractTest {
    @Test fun storedIdentifiersDoNotDependOnEnumOrdinals() {
        val json = LeetCodeJson.format
        assertEquals("\"leetcodecn\"", json.encodeToString(CodeDojo.LeetCodeCN))
        assertEquals(CodeDojo.LeetCodeCN, json.decodeFromString<CodeDojo>("\"LEETCODECN\""))
        assertEquals("\"java\"", json.encodeToString(Language.Java))
        assertEquals(Language.Java, json.decodeFromString<Language>("\"JAVA\""))
        for (result in SubmissionResult.entries) assertEquals(result, SubmissionResult.fromString(result.value))
    }

    @Test fun submissionRegionsKeepTheSourceLanguageSyntaxAndLineEndings() {
        val code = "first\r\nsecond\r\n"
        for (language in listOf(Language.Java, Language.Python, Language.OCaml, Language.MySQL)) {
            val source = language.encloseCodeInRegion(code)
            assertEquals(language.name, code + "\n", language.extractCodeFromRegion(source))
        }
        assertEquals("", Language.Java.extractCodeFromRegion("// no markers\nclass Solution {}"))
    }

    @Test fun leetCodeLanguageMappingsKeepVersionAndPandasRules() {
        assertEquals("java8", Language.Java.toLeetCode(LanguageVersion.SpecificVersion("8")))
        assertEquals("golang", Language.GO.toLeetCode(LanguageVersion.AnyVersion))
        assertEquals(Language.Pandas to LanguageVersion.AnyVersion, languageFromLeetCode("pythondata"))
        assertTrue(LanguageVersion.SpecificVersion("8") < LanguageVersion.SpecificVersion("17"))
    }
}
