package com.naniger.arzikina.domain.issuedreceipt.pdf

/** Retour à la ligne : respecte les sauts de ligne saisis, coupe aux espaces, et casse un mot plus long que la ligne. */
object PdfTextWrapper {

    fun wrap(text: String, maxWidth: Float, style: PdfTextStyle, measurer: PdfTextMeasurer): List<String> {
        val result = mutableListOf<String>()
        for (paragraph in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            wrapParagraph(paragraph.trimEnd(), maxWidth, style, measurer, result)
        }
        return result
    }

    private fun wrapParagraph(paragraph: String, maxWidth: Float, style: PdfTextStyle, measurer: PdfTextMeasurer, out: MutableList<String>) {
        if (paragraph.isEmpty()) {
            out += ""
            return
        }
        var line = ""
        for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (measurer.width(candidate, style) <= maxWidth) {
                line = candidate
                continue
            }
            if (line.isNotEmpty()) out += line
            line = ""
            var rest = word
            while (measurer.width(rest, style) > maxWidth && rest.length > 1) {
                var cut = rest.length - 1
                while (cut > 1 && measurer.width(rest.substring(0, cut), style) > maxWidth) cut--
                out += rest.substring(0, cut)
                rest = rest.substring(cut)
            }
            line = rest
        }
        out += line
    }
}
