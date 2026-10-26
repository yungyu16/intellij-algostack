package io.github.yungyu16.algostack.execution

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class GoTemplateStubTest {
    private val fixture = """
        type Cache struct {}
        // func fake() {}
        var text = "func fake() {}"
        var raw = `func fake() {}`
        var closure = func() {}
        func Constructor() Cache {}
        func (c *Cache) Get(key int) int { /* TODO */ }
        func (c *Cache) Put(key, value int) {
            // TODO
        }
        func values() (int, []string) {}
        func structure() struct{} {}
        func callback() func() {} 
        func argument(f func(), v interface{}) {}
        func done() int { return 42 }
        func nested() { if true {} }
        func existing() { panic("keep") }
    """.trimIndent()

    @Test
    fun onlyEmptyDeclarationsChangeAndTransformationIsIdempotent() {
        val result = goTemplateStub(fixture)
        assertEquals(7, Regex("panic\\(\"Not implemented\"\\)").findAll(result).count())
        for (untouched in listOf(
            "type Cache struct {}", "// func fake() {}", "var text = \"func fake() {}\"",
            "var raw = `func fake() {}`", "var closure = func() {}",
            "func done() int { return 42 }", "func nested() { if true {} }",
            "func existing() { panic(\"keep\") }", "/* TODO */", "// TODO"
        )) assertTrue(untouched, result.contains(untouched))
        assertEquals(result, goTemplateStub(result))
        assertFalse(goTemplateStub(fixture.replace("\n", "\r\n")).replace("\r\n", "").contains('\n'))
    }

    @Test
    fun optionalLocalGoCompileAndPanicCheck() {
        if (System.getenv("ALGOSTACK_VERIFY_GO") != "true") return
        val root = Files.createTempDirectory("go-stub-")
        try {
            Files.writeString(root.resolve("go.mod"), "module stubtest\n\ngo 1.20\n")
            Files.writeString(root.resolve("stub.go"), "package stubtest\n" + goTemplateStub(fixture))
            Files.writeString(root.resolve("stub_test.go"), """
                package stubtest
                import "testing"
                func TestPlaceholder(t *testing.T) {
                    defer func() {
                        if value := recover(); value != "Not implemented" { t.Fatalf("panic = %v", value) }
                    }()
                    Constructor()
                }
            """.trimIndent())
            val process = ProcessBuilder("go", "test", "./...").directory(root.toFile())
                .redirectErrorStream(true).apply {
                    environment().putAll(mapOf("GOFLAGS" to "", "GOWORK" to "off", "GOPROXY" to "off", "GOSUMDB" to "off", "GOTOOLCHAIN" to "local"))
                }.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(output, 0, process.waitFor())
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
