package com.scanner.app.util

/**
 * Utility object for safe CSV cell encoding.
 *
 * Addresses two concerns:
 * 1. Standard CSV escaping: values containing the separator, double-quotes, or
 *    newlines are wrapped in double-quotes with inner quotes doubled.
 * 2. Formula-injection prevention (Security Finding F-1): cells that begin with one of
 *    the characters `=`, `+`, `-`, `@`, `|`, or `%` are prefixed with a single-quote
 *    (`'`) so that spreadsheet applications (Excel, LibreOffice Calc, Google Sheets)
 *    treat them as plain text and do not execute them as formulas.
 *
 * Usage:
 *   CsvEscape.escape(value)              // semicolon-separated (default, ExportManager)
 *   CsvEscape.escape(value, ',')         // comma-separated (WardrivingTracker / WiGLE)
 */
object CsvEscape {

    /**
     * Characters that trigger formula execution in most spreadsheet applications
     * and therefore must be neutralised.
     */
    private val FORMULA_TRIGGER_CHARS = setOf('=', '+', '-', '@', '|', '%')

    /**
     * Sanitise and escape [value] for safe inclusion in a CSV cell.
     *
     * @param value     The raw cell value to encode.
     * @param separator The field delimiter used by the target CSV format.
     *                  Defaults to `;` (ExportManager). Use `,` for WiGLE/RFC-4180 CSV.
     *
     * Processing order:
     *  1. Neutralise leading formula-trigger characters by prepending `'`.
     *  2. Wrap in double-quotes if the value contains the separator, `"`, CR, or LF.
     */
    fun escape(value: String, separator: Char = ';'): String {
        if (value.isEmpty()) return value

        // Step 1 – formula injection prevention
        val safe = if (value[0] in FORMULA_TRIGGER_CHARS) "'$value" else value

        // Step 2 – standard CSV quoting
        return if (safe.contains(separator) || safe.contains('"') ||
            safe.contains('\n') || safe.contains('\r')
        ) {
            "\"${safe.replace("\"", "\"\"")}\""
        } else {
            safe
        }
    }
}
