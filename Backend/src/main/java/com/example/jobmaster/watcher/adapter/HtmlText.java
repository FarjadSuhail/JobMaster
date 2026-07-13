package com.example.jobmaster.watcher.adapter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** Converts job-description HTML into readable plain text with line breaks. */
public final class HtmlText {

    private static final String NEWLINE_MARK = "@@NL@@";

    private HtmlText() {
    }

    public static String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Document doc = Jsoup.parse(html);
        doc.outputSettings().prettyPrint(false);
        doc.select("br").append(NEWLINE_MARK);
        doc.select("p, li, h1, h2, h3, h4, h5, h6, ul, ol, div, section, tr").append(NEWLINE_MARK);
        String text = doc.text()
                .replace(NEWLINE_MARK, "\n")
                .replaceAll("[ \\t]*\\n[ \\t]*", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return text.isBlank() ? null : text;
    }
}
