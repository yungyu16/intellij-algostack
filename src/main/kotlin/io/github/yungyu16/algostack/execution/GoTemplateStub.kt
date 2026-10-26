package io.github.yungyu16.algostack.execution

/** 只填平台新骨架的顶层空函数/方法；不依赖 Go 插件，不改已有实现或函数字面量。 */
internal fun goTemplateStub(code: String): String {
    // 注释和字符串作为完整词法单元跳过，避免其中的 func、括号影响结构判断。
    val lexemes = Regex("//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/|`[^`]*`|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|[\\p{L}_][\\p{L}\\p{N}_]*|[^\\s]")
        .findAll(code).filterNot { it.value.startsWith("//") || it.value.startsWith("/*") }.toList()
    val insertions = mutableListOf<Pair<Int, String>>()
    val newline = if (code.contains("\r\n")) "\r\n" else "\n"
    var braces = 0
    var parentheses = 0
    var brackets = 0
    var declaration = false
    var index = 0
    while (index < lexemes.size) {
        val token = lexemes[index]
        when (token.value) {
            "func" -> if (braces == 0 && parentheses == 0 && brackets == 0) {
                // 函数声明以 func 开始一行；赋值中的匿名函数不作为待填骨架。
                val lineStart = code.lastIndexOf('\n', token.range.first - 1) + 1
                if (code.substring(lineStart, token.range.first).isBlank()) declaration = true
            }
            "(" -> parentheses++
            ")" -> parentheses--
            "[" -> brackets++
            "]" -> brackets--
            "{" -> {
                val isBody = declaration && braces == 0 && parentheses == 0 && brackets == 0 &&
                    lexemes.getOrNull(index - 1)?.value !in setOf("struct", "interface")
                if (isBody) {
                    declaration = false
                    val closing = lexemes.getOrNull(index + 1)
                    if (closing?.value == "}") {
                        val lineStart = code.lastIndexOf('\n', token.range.first - 1) + 1
                        val indent = code.substring(lineStart, token.range.first).takeWhile { it == ' ' || it == '\t' }
                        // 插入在右括号前，保留原注释；换行确保行注释不会吞掉 panic。
                        insertions.add(closing.range.first to "$newline$indent\tpanic(\"Not implemented\")$newline$indent")
                    }
                }
                braces++
            }
            "}" -> braces--
        }
        index++
    }
    return StringBuilder(code).apply {
        insertions.asReversed().forEach { (offset, text) -> insert(offset, text) }
    }.toString()
}
