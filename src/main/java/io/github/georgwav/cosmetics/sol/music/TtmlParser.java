package io.github.georgwav.cosmetics.sol.music;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Apple Music's TTML lyrics: {@code <p begin end ttm:agent>} lines, word (syllable) timing from their
 * {@code <span begin end>}s with spaces between them as the text has them, background vocals in
 * {@code <span ttm:role="x-bg">}, times as {@code ss.mmm}, {@code m:ss.mmm} or {@code h:mm:ss.mmm}. Line-level
 * TTML (no spans) works too. Pure; unit tested.
 */
public final class TtmlParser {
    private static final String TTM = "http://www.w3.org/ns/ttml#metadata";

    private TtmlParser() {
    }

    public static Lyrics parse(String ttml, String source) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setExpandEntityReferences(false);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(ttml.getBytes(StandardCharsets.UTF_8)));
            NodeList ps = doc.getElementsByTagNameNS("*", "p");
            List<Lyrics.Line> lines = new ArrayList<>();
            boolean timed = false;
            for (int i = 0; i < ps.getLength(); i++) {
                Element p = (Element) ps.item(i);
                long begin = time(p.getAttribute("begin")), end = time(p.getAttribute("end"));
                timed |= begin >= 0;
                String agent = p.getAttributeNS(TTM, "agent");
                List<Lyrics.Word> words = new ArrayList<>(), bg = new ArrayList<>();
                StringBuilder text = new StringBuilder(), bgText = new StringBuilder();
                collect(p, words, bg, text, bgText, false);
                String t = text.toString().strip().replaceAll("\\s+", " ");
                if (!t.isEmpty()) {
                    lines.add(new Lyrics.Line(Math.max(0, begin), Math.max(begin, end), t, List.copyOf(words),
                            agent.isEmpty() ? null : agent, false));
                }
                String b = bgText.toString().strip().replaceAll("\\s+", " ");
                if (!b.isEmpty()) {
                    long bs = bg.isEmpty() ? begin : bg.get(0).startMs(), be = bg.isEmpty() ? end : bg.get(bg.size() - 1).endMs();
                    lines.add(new Lyrics.Line(Math.max(0, bs), Math.max(bs, be), b, List.copyOf(bg), agent.isEmpty() ? null : agent, true));
                }
            }
            lines.sort((a, b) -> Long.compare(a.startMs(), b.startMs()));
            return new Lyrics(List.copyOf(lines), timed && !lines.isEmpty(), source);
        } catch (Exception e) {
            return Lyrics.NONE;
        }
    }

    /** Walks a line: timed spans become words (a space after a word is kept with it); x-bg spans go to {@code bg}. */
    private static void collect(Node node, List<Lyrics.Word> words, List<Lyrics.Word> bg, StringBuilder text, StringBuilder bgText,
                                boolean inBg) {
        NodeList kids = node.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node k = kids.item(i);
            if (k.getNodeType() == Node.TEXT_NODE) {
                String s = k.getTextContent();
                (inBg ? bgText : text).append(s);
                // A space after a word belongs to it, so words join back into the line.
                List<Lyrics.Word> into = inBg ? bg : words;
                if (!into.isEmpty() && !s.isEmpty() && s.isBlank()) {
                    Lyrics.Word last = into.remove(into.size() - 1);
                    into.add(new Lyrics.Word(last.startMs(), last.endMs(), last.text() + " "));
                }
            } else if (k instanceof Element e) {
                boolean bgSpan = inBg || "x-bg".equals(e.getAttributeNS(TTM, "role"));
                if (e.hasAttribute("begin") && !"x-bg".equals(e.getAttributeNS(TTM, "role"))) {
                    String w = e.getTextContent();
                    (bgSpan ? bgText : text).append(w);
                    (bgSpan ? bg : words).add(new Lyrics.Word(time(e.getAttribute("begin")), time(e.getAttribute("end")), w));
                } else {
                    collect(e, words, bg, text, bgText, bgSpan);
                }
            }
        }
    }

    /** "12.345", "1:02.345" or "1:02:03.456" (or with a trailing "s") in ms; -1 when missing or broken. */
    static long time(String t) {
        if (t == null || t.isBlank()) {
            return -1;
        }
        try {
            String s = t.strip();
            if (s.endsWith("s")) {
                s = s.substring(0, s.length() - 1);
            }
            String[] parts = s.split(":");
            double total = 0;
            for (String part : parts) {
                total = total * 60 + Double.parseDouble(part);
            }
            return Math.round(total * 1000.0);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
