package com.app.carry.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The Python semantics the import's report depends on: `json.loads`, which
 * decides what is readable; `repr`, which writes a value into a message; and
 * `validate`, whose first problem the report quotes. Every expected string
 * here is what gym-coach's Python 3.13 printed for the same input, not a
 * reading of it.
 */
class PyJsonTest {
    @Test
    fun `a loaded value reprs as Python writes it`() {
        val wrong = reprs.mapNotNull { (text, want) ->
            val got = pyRepr(pyJsonLoads(text))
            "$text: python $want vs kotlin $got".takeIf { got != want }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun `what Python refuses to load is refused, under its name`() {
        val wrong = refused.mapNotNull { (text, want) ->
            val got = try {
                pyJsonLoads(text)
                "loaded"
            } catch (e: PyException) {
                e.pyName
            }
            "${pyStrRepr(text)}: python $want vs kotlin $got".takeIf { got != want }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
        // Python's limit is ints of 4300 digits; one at the limit still loads.
        assertEquals("1".repeat(4300), pyRepr(pyJsonLoads("1".repeat(4300))))
        assertEquals("ValueError", failure("-" + "1".repeat(4301)))
    }

    @Test
    fun `nesting past the port's limit is refused as Python's RecursionError`() {
        // Python's own limit is its stack's and lies further out; nothing a client writes comes near either.
        val deep = "[".repeat(MAX_JSON_DEPTH + 1) + "]".repeat(MAX_JSON_DEPTH + 1)
        assertEquals("RecursionError", failure(deep))
        val ok = "[".repeat(MAX_JSON_DEPTH) + "]".repeat(MAX_JSON_DEPTH)
        pyJsonLoads(ok)
    }

    @Test
    fun `validate words every problem as Python does`() {
        val wrong = validated.mapNotNull { (text, want) ->
            val got = validate(pyJsonLoads(text))
            "$text:\n  python $want\n  kotlin $got".takeIf { got != want }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    private fun failure(text: String): String = try {
        pyJsonLoads(text)
        fail("loaded: ${text.take(20)}")
        ""
    } catch (e: PyException) {
        e.pyName
    }

    private val reprs = listOf(
        "null" to "None",
        "true" to "True",
        "false" to "False",
        "0" to "0",
        "-0" to "0",
        "30" to "30",
        "30.0" to "30.0",
        "-0.0" to "-0.0",
        "12.5" to "12.5",
        "1e16" to "1e+16",
        "1e15" to "1000000000000000.0",
        "0.0001" to "0.0001",
        "0.00001" to "1e-05",
        "1.5e300" to "1.5e+300",
        "1e400" to "inf",
        "-1e400" to "-inf",
        "NaN" to "nan",
        "0.1" to "0.1",
        "2.675" to "2.675",
        "123456789012345678901234567890" to "123456789012345678901234567890",
        "1E2" to "100.0",
        "5e-324" to "5e-324",
        "1.7976931348623157e308" to "1.7976931348623157e+308",
        "9007199254740993" to "9007199254740993",
        "0.30000000000000004" to "0.30000000000000004",
        "100.0" to "100.0",
        "1234567890123456.7" to "1234567890123456.8",
        "\"plain\"" to "'plain'",
        "\"it's\"" to "\"it's\"",
        "\"say \\\"hi\\\"\"" to "'say \"hi\"'",
        "\"both ' and \\\"\"" to "'both \\' and \"'",
        "\"back\\\\slash\"" to "'back\\\\slash'",
        "\"tab\\tnl\\nret\\r\"" to "'tab\\tnl\\nret\\r'",
        "\"\\u0001\\u001f\\u007f\"" to "'\\x01\\x1f\\x7f'",
        "\"caf\\u00e9\"" to "'caf\u00e9'",
        "\"\\u00a0nbsp\"" to "'\\xa0nbsp'",
        "\"\\u00adsoft\"" to "'\\xadsoft'",
        "\"\\u2028sep\"" to "'\\u2028sep'",
        "\"\\ud800lone\"" to "'\\ud800lone'",
        "\"\\ud83d\\ude00\"" to "'\ud83d\ude00'",
        "\"\\ue000private\"" to "'\\ue000private'",
        "\"\\u0378unassigned\"" to "'\\u0378unassigned'",
        "\"\\u200bzw\"" to "'\\u200bzw'",
        "\"\u65e5\u672c\"" to "'\u65e5\u672c'",
        "[]" to "[]",
        "{}" to "{}",
        "[1, \"a\", null, true, 2.5]" to "[1, 'a', None, True, 2.5]",
        "{\"k\": null, \"j\": [1, {\"x\": \"y\"}]}" to "{'k': None, 'j': [1, {'x': 'y'}]}",
        "{\"a\":1,\"a\":2}" to "{'a': 2}",
        " [1] " to "[1]",
        "{\"a\" : [ ] }" to "{'a': []}",
        "\"\\/\"" to "'/'",
        "-Infinity" to "-inf",
        "Infinity" to "inf",
        "[NaN]" to "[nan]",
    )

    private val refused = listOf(
        "" to "JSONDecodeError",
        "{not json" to "JSONDecodeError",
        "\ufeff{}" to "JSONDecodeError",
        "[1,]" to "JSONDecodeError",
        "{\"a\":1,}" to "JSONDecodeError",
        "01" to "JSONDecodeError",
        "1." to "JSONDecodeError",
        ".5" to "JSONDecodeError",
        "+1" to "JSONDecodeError",
        "-" to "JSONDecodeError",
        "1e" to "JSONDecodeError",
        "\"\u0001\"" to "JSONDecodeError",
        "\"\\x\"" to "JSONDecodeError",
        "\"\\u12\"" to "JSONDecodeError",
        "[1] x" to "JSONDecodeError",
        "nul" to "JSONDecodeError",
        "True" to "JSONDecodeError",
        "'a'" to "JSONDecodeError",
        "{\"a\" 1}" to "JSONDecodeError",
        "{1:2}" to "JSONDecodeError",
        "[1 2]" to "JSONDecodeError",
        "\"abc" to "JSONDecodeError",
        "\u000c1" to "JSONDecodeError",
    )

    private val validated = listOf(
        "[]" to listOf("not a JSON object"),
        "{\"schema\": \"gym-session/1\"}" to listOf("missing 'sheet_id'", "missing 'date'", "missing 'class'", "missing 'sets'", "date is None, expected YYYY-MM-DD"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf(),
        "{\"schema\": \"gym-session/2\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf("schema is 'gym-session/2', expected 'gym-session/1'"),
        "{\"schema\": null, \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf("schema is None, expected 'gym-session/1'"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": {\"a\": 1}, \"added\": []}" to listOf("'sets' is not a list"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": null, \"added\": []}" to listOf(),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [1, \"x\"], \"added\": []}" to listOf("sets[0] is not an object", "sets[1] is not an object"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{}], \"added\": []}" to listOf("sets[0] missing 'ref'", "sets[0] missing 'line'"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": -1, \"line\": \"x\"}], \"added\": []}" to listOf("sets[0].ref is -1, expected a whole number"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 1.0, \"line\": \"x\"}], \"added\": []}" to listOf("sets[0].ref is 1.0, expected a whole number"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": true, \"line\": \"x\"}], \"added\": []}" to listOf("sets[0].ref is True, expected a whole number"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": \"0\", \"line\": \"x\"}], \"added\": []}" to listOf("sets[0].ref is '0', expected a whole number"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": null, \"line\": \"x\"}], \"added\": []}" to listOf("sets[0].ref is None, expected a whole number"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 1000000000000000000000000000000, \"line\": \"x\"}], \"added\": []}" to listOf(),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"weight\": -1}], \"added\": []}" to listOf("sets[0].weight is -1, expected a number from 0 to 1000, or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"weight\": 1000.5}], \"added\": []}" to listOf("sets[0].weight is 1000.5, expected a number from 0 to 1000, or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"weight\": 1000}], \"added\": []}" to listOf(),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"weight\": true}], \"added\": []}" to listOf("sets[0].weight is True, expected a number from 0 to 1000, or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"weight\": \"20\"}], \"added\": []}" to listOf("sets[0].weight is '20', expected a number from 0 to 1000, or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"weight\": NaN}], \"added\": []}" to listOf("sets[0].weight is nan, expected a number from 0 to 1000, or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"weight\": Infinity}], \"added\": []}" to listOf("sets[0].weight is inf, expected a number from 0 to 1000, or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"weight\": -0.0}], \"added\": []}" to listOf(),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"weight\": 1000000000000000000000000000000}], \"added\": []}" to listOf("sets[0].weight is 1000000000000000000000000000000, expected a number from 0 to 1000, or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"reps\": 3.5}], \"added\": []}" to listOf("sets[0].reps is 3.5, expected a whole number from 0 to 1000, or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"reps\": 3.0}], \"added\": []}" to listOf(),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"reps\": -0.0}], \"added\": []}" to listOf(),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"prescribed_reps\": 1000.0}], \"added\": []}" to listOf(),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"prescribed_reps\": 1001}], \"added\": []}" to listOf("sets[0].prescribed_reps is 1001, expected a whole number from 0 to 1000, or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"rounds\": [8]}], \"added\": []}" to listOf("sets[0].rounds is [8], expected two or more whole numbers, or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"rounds\": [8, 6.0]}], \"added\": []}" to listOf("sets[0].rounds is [8, 6.0], expected two or more whole numbers, or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"rounds\": [8, 0]}], \"added\": []}" to listOf("sets[0].rounds is [8, 0], expected two or more whole numbers, or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"rounds\": [8, true]}], \"added\": []}" to listOf("sets[0].rounds is [8, True], expected two or more whole numbers, or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"rounds\": [8, 6]}], \"added\": []}" to listOf(),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"rounds\": \"8/6\"}], \"added\": []}" to listOf("sets[0].rounds is '8/6', expected two or more whole numbers, or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"canonical\": 5}], \"added\": []}" to listOf("sets[0].canonical is 5, expected text or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"note\": [\"a\"]}], \"added\": []}" to listOf("sets[0].note is ['a'], expected text or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"breakdown\": {\"a\": 1}}], \"added\": []}" to listOf("sets[0].breakdown is {'a': 1}, expected text or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"easy\": \"false\"}], \"added\": []}" to listOf("sets[0].easy is 'false', expected true, false or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"hurt\": 1}], \"added\": []}" to listOf("sets[0].hurt is 1, expected true, false or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"done\": null}], \"added\": []}" to listOf(),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"components\": [\"a\", 1]}], \"added\": []}" to listOf("sets[0].components is ['a', 1], expected a list of names or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"components\": \"squat\"}], \"added\": []}" to listOf("sets[0].components is 'squat', expected a list of names or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"variant\": [1]}], \"added\": []}" to listOf("sets[0].variant is [1], expected an object or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"variant\": {\"unknown\": [\"a\"], \"reps\": 2.5, \"x\": null}}], \"added\": []}" to listOf(),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"variant\": {\"unknown\": \"a\", \"y\": [1], \"z\": true, \"w\": {\"q\": 1}}}], \"added\": []}" to listOf("sets[0].variant.y is [1], expected text, a number or null", "sets[0].variant.z is True, expected text, a number or null", "sets[0].variant.w is {'q': 1}, expected text, a number or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"variant\": {\"other\": [\"a\"]}}], \"added\": []}" to listOf("sets[0].variant.other is ['a'], expected text, a number or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": {\"x\": 1}}" to listOf("'added' is not a list"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": [{}, {\"name\": \"  \"}, {\"name\": 0}, {\"name\": [1]}, {\"name\": true}, \"x\", {\"name\": \"row\", \"weight\": -2, \"easy\": \"yes\"}]}" to listOf("added[0] needs a name", "added[1] needs a name", "added[2] needs a name", "added[5] needs a name", "added[6].weight is -2, expected a number from 0 to 1000, or null", "added[6].easy is 'yes', expected true, false or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G#\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf("class is '2G#', expected letters and digits"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": 2, \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf("class is 2, expected letters and digits"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": null, \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf("class is None, expected letters and digits"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"\\uff12G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf("class is '\uff12G', expected letters and digits"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf("class is '', expected letters and digits"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-02-30\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf("date is '2026-02-30', expected YYYY-MM-DD"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"0000-01-01\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf("date is '0000-01-01', expected YYYY-MM-DD"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"20261015\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf("date is '20261015', expected YYYY-MM-DD"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-W42-4\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf("date is '2026-W42-4', expected YYYY-MM-DD"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"\\uff12\\uff10\\uff12\\uff16-01-01\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf("date is '\uff12\uff10\uff12\uff16-01-01', expected YYYY-MM-DD"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": null, \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf("date is None, expected YYYY-MM-DD"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"9999-12-31\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf(),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-1-01\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf("date is '2026-1-01', expected YYYY-MM-DD"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2024-02-29\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf(),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"date\": \"2026-01-02\", \"class\": \"2G\", \"sets\": [{\"ref\": 0, \"line\": \"x\", \"note\": \"it's \\\"q\\\"\", \"weight\": \"it's\"}], \"added\": []}" to listOf("sets[0].weight is \"it's\", expected a number from 0 to 1000, or null"),
        "{\"schema\": \"gym-session/1\", \"sheet_id\": \"2026-01-02:2G\", \"sets\": [{\"ref\": 0, \"line\": \"10 x squat\"}], \"added\": []}" to listOf("missing 'date'", "missing 'class'", "date is None, expected YYYY-MM-DD"),
    )
}
