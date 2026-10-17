package be.wimvandonck.reader.sync

/**
 * A small JSON reader and writer for the sync file, in plain Kotlin so it runs in unit tests.
 * Values are `null`, Boolean, Long, Double, String, List<Any?> and Map<String, Any?>.
 */
object Json {
    class ParseException(
        message: String,
    ) : Exception(message)

    fun write(value: Any?): String = StringBuilder().also { writeTo(value, it) }.toString()

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.skipSpace()
        if (!reader.atEnd()) throw ParseException("Unexpected text at ${reader.pos}")
        return value
    }

    private fun writeTo(
        value: Any?,
        out: StringBuilder,
    ) {
        when (value) {
            null -> out.append("null")

            is Boolean -> out.append(value)

            is Int, is Long -> out.append(value)

            is Number -> {
                val d = value.toDouble()
                require(d.isFinite()) { "JSON has no NaN or infinity" }
                out.append(d)
            }

            is String -> writeString(value, out)

            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) out.append(',')
                    first = false
                    writeString(k.toString(), out)
                    out.append(':')
                    writeTo(v, out)
                }
                out.append('}')
            }

            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (v in value) {
                    if (!first) out.append(',')
                    first = false
                    writeTo(v, out)
                }
                out.append(']')
            }

            else -> throw IllegalArgumentException("Not a JSON value: ${value::class}")
        }
    }

    private fun writeString(
        s: String,
        out: StringBuilder,
    ) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') out.append("\\u").append(c.code.toString(16).padStart(4, '0')) else out.append(c)
            }
        }
        out.append('"')
    }

    private class Reader(
        val text: String,
    ) {
        var pos = 0

        fun atEnd() = pos >= text.length

        fun skipSpace() {
            while (pos < text.length && text[pos] in " \t\r\n") pos++
        }

        fun value(): Any? {
            skipSpace()
            if (atEnd()) throw ParseException("Unexpected end")
            return when (val c = text[pos]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw ParseException("Unexpected '$c' at $pos")
            }
        }

        private fun literal(
            word: String,
            result: Any?,
        ): Any? {
            if (!text.startsWith(word, pos)) throw ParseException("Expected $word at $pos")
            pos += word.length
            return result
        }

        private fun obj(): Map<String, Any?> {
            pos++
            val map = LinkedHashMap<String, Any?>()
            skipSpace()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipSpace()
                if (peek() != '"') throw ParseException("Expected a key at $pos")
                val key = string()
                skipSpace()
                expect(':')
                map[key] = value()
                skipSpace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw ParseException("Expected , or } at $pos")
                }
            }
        }

        private fun array(): List<Any?> {
            pos++
            val list = ArrayList<Any?>()
            skipSpace()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(value())
                skipSpace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw ParseException("Expected , or ] at $pos")
                }
            }
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (atEnd()) throw ParseException("Unterminated string")
                val c = text[pos++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd()) throw ParseException("Unterminated string")
                        when (val e = text[pos++]) {
                            '"', '\\', '/' -> out.append(e)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) throw ParseException("Bad escape at $pos")
                                out.append(text.substring(pos, pos + 4).toIntOrNull(16)?.toChar() ?: throw ParseException("Bad escape at $pos"))
                                pos += 4
                            }
                            else -> throw ParseException("Bad escape at $pos")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun number(): Any {
            val start = pos
            if (peek() == '-') pos++
            while (pos < text.length && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
            val raw = text.substring(start, pos)
            return raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: throw ParseException("Bad number at $start")
        }

        private fun peek(): Char? = text.getOrNull(pos)

        private fun expect(c: Char) {
            if (peek() != c) throw ParseException("Expected '$c' at $pos")
            pos++
        }
    }
}
