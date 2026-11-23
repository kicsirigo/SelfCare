package com.selfcare.app.domain.inci

/**
 * Extracts a clean cosmetic/product search query from text or URLs shared
 * from Google Lens, Chrome, or shopping apps via Android ACTION_SEND.
 */
object SharedProductTextParser {

    private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val STOREFRONT_SUFFIX_REGEX = Regex(
        """\s*[-|•–—]\s*(google(\s+search)?|rossmann(\.hu)?|dm(\.hu)?|notino(\.hu)?|douglas(\.hu)?|m[uü]ller|amazon(\.[a-z.]+)?|benu(\.hu)?|árukereső|arukereso|emag(\.hu)?|sephora|cult\s*beauty|lookfantastic|iherb|pingvin\s*patika|patika\d*|kremmania|krémmánia).*$""",
        RegexOption.IGNORE_CASE
    )
    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun extractProductQuery(subject: String?, sharedText: String?): String {
        val cleanSubject = cleanCandidateText(subject.orEmpty())
        val rawText = sharedText.orEmpty().trim()
        val textWithoutUrls = cleanCandidateText(URL_REGEX.replace(rawText, " "))

        val bestText = when {
            textWithoutUrls.length >= 3 -> textWithoutUrls
            cleanSubject.length >= 3 -> cleanSubject
            else -> {
                val firstUrl = URL_REGEX.find(rawText)?.value
                    ?: URL_REGEX.find(subject.orEmpty())?.value
                if (firstUrl != null) extractFromUrlSlug(firstUrl) else ""
            }
        }
        return bestText.take(120).trim()
    }

    private fun cleanCandidateText(raw: String): String {
        if (raw.isBlank()) return ""
        val firstLine = raw
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("http", ignoreCase = true) }
            .orEmpty()
        if (firstLine.isBlank()) return ""
        val withoutStore = STOREFRONT_SUFFIX_REGEX.replace(firstLine, "")
        return WHITESPACE_REGEX.replace(withoutStore, " ")
            .trim(' ', '-', '|', ':', '"', '\'')
    }

    private fun extractFromUrlSlug(url: String): String {
        val withoutQuery = url.substringBefore('?').substringBefore('#').trimEnd('/')
        val segments = withoutQuery.split('/').filter { it.isNotBlank() }
        // Skip scheme + host (e.g. ["https:", "www.notino.hu", ...])
        val pathSegments = if (segments.size > 2) segments.drop(2) else emptyList()
        val meaningful = pathSegments
            .filter { seg ->
                seg.length >= 3 &&
                    !seg.equals("p", ignoreCase = true) &&
                    !seg.equals("dp", ignoreCase = true) &&
                    !seg.equals("product", ignoreCase = true) &&
                    !seg.equals("termek", ignoreCase = true) &&
                    !seg.all { it.isDigit() }
            }
            .takeLast(2)
        if (meaningful.isEmpty()) return ""
        return meaningful
            .joinToString(" ") { seg ->
                seg.replace('-', ' ').replace('_', ' ')
            }
            .replace(Regex("""\.(html?|php|aspx)$""", RegexOption.IGNORE_CASE), "")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }
}
