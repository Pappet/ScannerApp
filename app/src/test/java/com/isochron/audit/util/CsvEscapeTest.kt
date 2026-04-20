package com.scanner.app.util

import org.junit.Assert.*
import org.junit.Test

class CsvEscapeTest {

    // ─── Formula injection prevention ───────────────────────────────────────────

    @Test
    fun `escape prefixes equals-sign cell with single quote`() {
        assertEquals("'=SUM(A1:A10)", CsvEscape.escape("=SUM(A1:A10)"))
    }

    @Test
    fun `escape prefixes plus-sign cell with single quote`() {
        assertEquals("'+CMD", CsvEscape.escape("+CMD"))
    }

    @Test
    fun `escape prefixes minus-sign cell with single quote`() {
        assertEquals("'-1+1", CsvEscape.escape("-1+1"))
    }

    @Test
    fun `escape prefixes at-sign cell with single quote`() {
        assertEquals("'@SUM", CsvEscape.escape("@SUM"))
    }

    @Test
    fun `escape prefixes pipe-sign cell with single quote`() {
        assertEquals("'|evil", CsvEscape.escape("|evil"))
    }

    @Test
    fun `escape prefixes percent cell with single quote`() {
        assertEquals("'%0A", CsvEscape.escape("%0A"))
    }

    // ─── Normal values (no injection risk) ──────────────────────────────────────

    @Test
    fun `escape returns unchanged safe string`() {
        assertEquals("normalSSID", CsvEscape.escape("normalSSID"))
    }

    @Test
    fun `escape returns empty string unchanged`() {
        assertEquals("", CsvEscape.escape(""))
    }

    @Test
    fun `escape returns numeric string unchanged`() {
        assertEquals("12345", CsvEscape.escape("12345"))
    }

    // ─── CSV quoting (semicolon separator, default) ──────────────────────────────

    @Test
    fun `escape wraps value containing semicolon in double quotes`() {
        assertEquals("\"hello;world\"", CsvEscape.escape("hello;world"))
    }

    @Test
    fun `escape doubles internal double quotes`() {
        assertEquals("\"say \"\"hi\"\"\"", CsvEscape.escape("say \"hi\""))
    }

    @Test
    fun `escape wraps value containing newline in double quotes`() {
        val result = CsvEscape.escape("line1\nline2")
        assertTrue(result.startsWith("\"") && result.endsWith("\""))
    }

    // ─── CSV quoting (comma separator for WiGLE) ────────────────────────────────

    @Test
    fun `escape with comma separator wraps comma-containing value`() {
        assertEquals("\"hello,world\"", CsvEscape.escape("hello,world", ','))
    }

    @Test
    fun `escape with comma separator does not quote semicolon`() {
        assertEquals("hello;world", CsvEscape.escape("hello;world", ','))
    }

    @Test
    fun `escape with comma separator still applies formula injection`() {
        assertEquals("\"'=EVIL,CMD\"", CsvEscape.escape("=EVIL,CMD", ','))
    }

    // ─── Edge cases ──────────────────────────────────────────────────────────────

    @Test
    fun `escape handles value starting with allowed special char`() {
        // Tilde is not a trigger char
        assertEquals("~test", CsvEscape.escape("~test"))
    }

    @Test
    fun `escape handles single trigger char`() {
        assertEquals("'=", CsvEscape.escape("="))
    }
}
