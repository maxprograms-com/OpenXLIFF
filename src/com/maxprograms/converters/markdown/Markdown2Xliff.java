/*******************************************************************************
 * Copyright (c) 2018 - 2026 Maxprograms.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors:
 *     Maxprograms - initial API and implementation
 *******************************************************************************/
package com.maxprograms.converters.markdown;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.segmenter.Segmenter;
import com.maxprograms.segmenter.SegmenterPool;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLUtils;

public class Markdown2Xliff {

    private static final Pattern REF_DEF = Pattern.compile(
            "^ {0,3}\\[([^\\]]+)\\]:\\s+(\\S+)(?:\\s+(?:\"[^\"]*\"|'[^']*'|\\([^)]*\\)))?\\s*$");
    private static final Pattern FOOTNOTE_DEF = Pattern.compile(
            "^ {0,3}\\[\\^([^\\]]+)\\]:\\s*(.*)$");
    private static final Pattern FENCED_DIV = Pattern.compile("^(:{3,})(.*)$");
    private static final Pattern DEFINITION_MARKER = Pattern.compile("^:([ \\t]+)(.*)$");
    private static final Pattern BLOCKQUOTE_PREFIX = Pattern.compile("^(>(?:[ ]?>)*)([ ]?)(.*)");

    private Map<String, String> refMap;

    private static Map<String, String> loadEmojiMap() throws IOException, SAXException, ParserConfigurationException {
        Map<String, String> map = new Hashtable<>();
        SAXBuilder builder = new SAXBuilder();
        Document doc = builder.build(Markdown2Xliff.class.getResource("emoji.xml"));
        for (Element e : doc.getRootElement().getChildren("emoji")) {
            map.put(e.getAttributeValue("name"), e.getText());
        }
        return map;
    }

    private FileOutputStream xliffOutput;
    private FileOutputStream skeletonOutput;
    private int segId;
    private Segmenter segmenter;
    private StringBuilder paragraphBuffer;
    private Map<String, String> emojiMap;

    private Markdown2Xliff() {
        // do not instantiate this class
        // use run method instead
    }

    public static List<String> run(Map<String, String> params) {
        Markdown2Xliff converter = new Markdown2Xliff();
        try {
            converter.emojiMap = loadEmojiMap();
        } catch (IOException | SAXException | ParserConfigurationException e) {
            Logger logger = System.getLogger(Markdown2Xliff.class.getName());
            logger.log(Level.ERROR, Messages.getString("Markdown2Xliff.1"), e);
            List<String> result = new ArrayList<>();
            result.add(Constants.ERROR);
            result.add(e.getMessage());
            return result;
        }
        return converter.convert(params);
    }

    private List<String> convert(Map<String, String> params) {
        List<String> result = new ArrayList<>();
        segId = 0;
        paragraphBuffer = new StringBuilder();
        StringBuilder codeFenceBuffer = new StringBuilder();
        StringBuilder indentedCodeBuffer = new StringBuilder();
        StringBuilder mathBlockBuffer = new StringBuilder();

        String inputFile = params.get("source");
        String xliffFile = params.get("xliff");
        String skeletonFile = params.get("skeleton");
        String sourceLanguage = params.get("srcLang");
        String targetLanguage = params.get("tgtLang");
        String srcEncoding = params.get("srcEncoding");
        String catalog = params.get("catalog");
        boolean segByElement = "yes".equals(params.get("paragraph"));

        String tgtLang = "";
        if (targetLanguage != null) {
            tgtLang = "\" target-language=\"" + targetLanguage;
        }

        try {
            if (!segByElement) {
                segmenter = SegmenterPool.getSegmenter(params.get("srxFile"), sourceLanguage,
                        CatalogBuilder.getCatalog(catalog));
            }

            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(inputFile), srcEncoding);
                    BufferedReader buffer = new BufferedReader(reader)) {

                xliffOutput = new FileOutputStream(xliffFile);
                skeletonOutput = new FileOutputStream(skeletonFile);

                writeXliff("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                writeXliff("<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");
                writeXliff("<file original=\"" + XMLUtils.cleanText(inputFile) + "\" source-language=\""
                        + sourceLanguage + tgtLang + "\" tool-id=\"" + Constants.TOOLID
                        + "\" datatype=\"x-markdown\">\n");
                writeXliff("<header>\n");
                writeXliff("   <skl>\n");
                writeXliff("      <external-file href=\"" + XMLUtils.cleanText(skeletonFile) + "\"/>\n");
                writeXliff("   </skl>\n");
                writeXliff("   <tool tool-version=\"" + Constants.VERSION + " " + Constants.BUILD + "\" tool-id=\""
                        + Constants.TOOLID + "\" tool-name=\"" + Constants.TOOLNAME + "\"/>\n");
                writeXliff("</header>\n");
                writeXliff("<?encoding " + srcEncoding + "?>\n");
                writeXliff("<body>\n");

                // Read all lines for two-pass processing
                List<String> allLines = new ArrayList<>();
                String rawLine;
                boolean firstLine = true;
                while ((rawLine = buffer.readLine()) != null) {
                    if (firstLine) {
                        if (rawLine.startsWith("\uFEFF")) {
                            rawLine = rawLine.substring(1);
                        }
                        firstLine = false;
                    }
                    allLines.add(rawLine);
                }

                // Pass 1: collect link reference definitions (context-aware: skip code fences
                // and front matter)
                refMap = new Hashtable<>();
                boolean p1InFrontMatter = false;
                String p1FrontMatterDelim = null;
                int p1JsonDepth = 0;
                boolean p1InCodeFence = false;
                String p1FenceDelimiter = null;
                for (int idx = 0; idx < allLines.size(); idx++) {
                    String l = allLines.get(idx);
                    if (idx == 0 && (l.equals("---") || l.equals("+++"))) {
                        p1InFrontMatter = true;
                        p1FrontMatterDelim = l;
                        continue;
                    }
                    if (idx == 0 && l.equals("{")) {
                        p1InFrontMatter = true;
                        p1FrontMatterDelim = "{";
                        p1JsonDepth = 1;
                        continue;
                    }
                    if (p1InFrontMatter) {
                        if ("{".equals(p1FrontMatterDelim)) {
                            for (char ch : l.toCharArray()) {
                                if (ch == '{')
                                    p1JsonDepth++;
                                else if (ch == '}')
                                    p1JsonDepth--;
                            }
                            if (p1JsonDepth <= 0) {
                                p1InFrontMatter = false;
                            }
                        } else if (l.equals(p1FrontMatterDelim)
                                || ("---".equals(p1FrontMatterDelim) && l.equals("..."))) {
                            p1InFrontMatter = false;
                        }
                        continue;
                    }
                    if (p1InCodeFence) {
                        if (isClosingFence(l, p1FenceDelimiter)) {
                            p1InCodeFence = false;
                            p1FenceDelimiter = null;
                        }
                        continue;
                    }
                    String p1Fence = getFenceDelimiter(l);
                    if (p1Fence != null) {
                        p1InCodeFence = true;
                        p1FenceDelimiter = p1Fence;
                        continue;
                    }
                    Matcher m = REF_DEF.matcher(l);
                    if (m.matches()) {
                        refMap.put(m.group(1).toLowerCase().trim(), m.group(2));
                    }
                }

                // Pass 2: process lines
                boolean inCodeFence = false;
                String codeFenceDelimiter = null;
                boolean inFrontMatter = false;
                String frontMatterDelim = null;
                int jsonDepth = 0;
                boolean inHtmlBlock = false;
                boolean inMathBlock = false;
                boolean inFootnote = false;
                boolean inDefinitionList = false;
                int fencedDivDepth = 0;
                boolean inIndentedCodeBlock = false;
                int pendingBlankLines = 0;
                int lastListIndent = 0;

                for (int lineIdx = 0; lineIdx < allLines.size(); lineIdx++) {
                    String line = allLines.get(lineIdx);
                    if (lineIdx == 0 && (line.equals("---") || line.equals("+++"))) {
                        inFrontMatter = true;
                        frontMatterDelim = line;
                        writeSkeleton(line + "\n");
                        continue;
                    }
                    if (lineIdx == 0 && line.equals("{")) {
                        inFrontMatter = true;
                        frontMatterDelim = "{";
                        jsonDepth = 1;
                        writeSkeleton(line + "\n");
                        continue;
                    }

                    if (inFrontMatter) {
                        writeSkeleton(line + "\n");
                        if ("{".equals(frontMatterDelim)) {
                            for (char ch : line.toCharArray()) {
                                if (ch == '{')
                                    jsonDepth++;
                                else if (ch == '}')
                                    jsonDepth--;
                            }
                            if (jsonDepth <= 0) {
                                inFrontMatter = false;
                            }
                        } else if (line.equals(frontMatterDelim)
                                || ("---".equals(frontMatterDelim) && line.equals("..."))) {
                            inFrontMatter = false;
                        }
                        continue;
                    }

                    if (inCodeFence) {
                        if (isClosingFence(line, codeFenceDelimiter)) {
                            if (!codeFenceBuffer.isEmpty()) {
                                addCodeBlockToSkeleton(codeFenceBuffer.toString());
                                writeSkeleton("\n");
                                codeFenceBuffer.setLength(0);
                            }
                            writeSkeleton(line + "\n");
                            inCodeFence = false;
                            codeFenceDelimiter = null;
                        } else {
                            if (!codeFenceBuffer.isEmpty()) {
                                codeFenceBuffer.append("\n");
                            }
                            codeFenceBuffer.append(line);
                        }
                        continue;
                    }

                    // Block HTML: write lines to skeleton until the blank line that ends the block
                    if (inHtmlBlock) {
                        writeSkeleton(line + "\n");
                        if (line.isBlank()) {
                            inHtmlBlock = false;
                        }
                        continue;
                    }

                    // Math block content: accumulate until closing $$
                    if (inMathBlock) {
                        if (line.stripTrailing().equals("$$")) {
                            addCodeBlockToSkeleton(mathBlockBuffer.toString());
                            writeSkeleton("\n$$\n");
                            mathBlockBuffer.setLength(0);
                            inMathBlock = false;
                        } else {
                            if (!mathBlockBuffer.isEmpty()) {
                                mathBlockBuffer.append("\n");
                            }
                            mathBlockBuffer.append(line);
                        }
                        continue;
                    }

                    // Footnote continuation: indented lines (4 spaces or tab) belonging to the
                    // definition
                    if (inFootnote) {
                        if (line.isBlank()) {
                            writeSkeleton("\n");
                            inFootnote = false;
                            continue;
                        }
                        if (line.startsWith("    ") || line.startsWith("\t")) {
                            String contIndent = line.startsWith("\t") ? "\t" : "    ";
                            String contContent = line.substring(contIndent.length());
                            writeSkeleton(contIndent);
                            if (!contContent.trim().isEmpty()) {
                                addSegmentToSkeleton(contContent);
                            }
                            writeSkeleton("\n");
                            continue;
                        }
                        inFootnote = false;
                        // fall through to process this line normally
                    }

                    // Definition list continuation: indented lines or subsequent : markers
                    if (inDefinitionList) {
                        if (line.isBlank()) {
                            // fall through to blank-line handler; stay in definition list
                        } else if (isIndentedCodeLine(line)) {
                            String dlIndent = line.startsWith("\t") ? "\t" : "    ";
                            String dlContent = line.substring(dlIndent.length());
                            writeSkeleton(dlIndent);
                            if (!dlContent.trim().isEmpty()) {
                                addSegmentToSkeleton(dlContent);
                            }
                            writeSkeleton("\n");
                            continue;
                        } else if (!DEFINITION_MARKER.matcher(line).matches()) {
                            inDefinitionList = false;
                            // fall through to process this line normally
                        }
                        // blank lines and further : markers fall through
                    }

                    // Indented code block continuation: absorb the line and any buffered blank
                    // lines
                    if (inIndentedCodeBlock && isIndentedCodeLine(line)) {
                        for (int b = 0; b < pendingBlankLines; b++) {
                            indentedCodeBuffer.append("\n");
                        }
                        pendingBlankLines = 0;
                        indentedCodeBuffer.append("\n").append(line);
                        continue;
                    }
                    // Indented code block ended by a non-blank, non-indented line
                    if (inIndentedCodeBlock) {
                        addCodeBlockToSkeleton(indentedCodeBuffer.toString());
                        writeSkeleton("\n");
                        for (int b = 0; b < pendingBlankLines; b++) {
                            writeSkeleton("\n");
                        }
                        pendingBlankLines = 0;
                        inIndentedCodeBlock = false;
                        // fall through to process this line normally
                    }

                    // Reference definition line: only at block level (not mid-paragraph)
                    if (paragraphBuffer.isEmpty() && REF_DEF.matcher(line).matches()) {
                        flushParagraph();
                        writeSkeleton(line + "\n");
                        continue;
                    }

                    // Footnote definition: [^label]: text (GFM / Pandoc)
                    if (paragraphBuffer.isEmpty()) {
                        Matcher fnm = FOOTNOTE_DEF.matcher(line);
                        if (fnm.matches()) {
                            String fnLabel = fnm.group(1);
                            String fnContent = fnm.group(2).trim();
                            writeSkeleton("[^" + fnLabel + "]: ");
                            if (!fnContent.isEmpty()) {
                                addSegmentToSkeleton(fnContent);
                            }
                            writeSkeleton("\n");
                            inFootnote = true;
                            continue;
                        }
                    }

                    // Fenced div: ::: (Pandoc) — opening when it has attributes OR depth is 0,
                    // closing when it has no attributes and depth > 0. Interior processed normally.
                    Matcher fdm = FENCED_DIV.matcher(line);
                    if (fdm.matches()) {
                        boolean hasAttrs = !fdm.group(2).trim().isEmpty();
                        flushParagraph();
                        writeSkeleton(line + "\n");
                        if (fencedDivDepth > 0 && !hasAttrs) {
                            fencedDivDepth--;
                        } else {
                            fencedDivDepth++;
                        }
                        continue;
                    }

                    // Display math block: $$ alone opens/closes a multi-line block;
                    // $$formula$$ on one line is a single-line display block
                    String mathLine = line.stripTrailing();
                    if (mathLine.equals("$$")) {
                        flushParagraph();
                        writeSkeleton("$$\n");
                        mathBlockBuffer.setLength(0);
                        inMathBlock = true;
                        continue;
                    }
                    if (paragraphBuffer.isEmpty() && mathLine.startsWith("$$")
                            && mathLine.endsWith("$$") && mathLine.length() > 4) {
                        String formula = mathLine.substring(2, mathLine.length() - 2);
                        writeSkeleton("$$");
                        addCodeBlockToSkeleton(formula);
                        writeSkeleton("$$\n");
                        continue;
                    }

                    // Definition list: : marker following a term (or a subsequent : for the same
                    // term)
                    Matcher dm = DEFINITION_MARKER.matcher(line);
                    if (dm.matches() && (!paragraphBuffer.isEmpty() || inDefinitionList)) {
                        if (!paragraphBuffer.isEmpty()) {
                            String term = paragraphBuffer.toString().trim();
                            paragraphBuffer.setLength(0);
                            addSegmentToSkeleton(term);
                            writeSkeleton("\n");
                        }
                        String dlPrefix = ":" + dm.group(1);
                        String dlContent = dm.group(2).trim();
                        writeSkeleton(dlPrefix);
                        if (!dlContent.isEmpty()) {
                            addSegmentToSkeleton(dlContent);
                        }
                        writeSkeleton("\n");
                        inDefinitionList = true;
                        continue;
                    }

                    // Code fence start: 3+ backticks or tildes
                    String fenceDelim = getFenceDelimiter(line);
                    if (fenceDelim != null) {
                        flushParagraph();
                        codeFenceDelimiter = fenceDelim;
                        inCodeFence = true;
                        codeFenceBuffer.setLength(0);
                        writeSkeleton(line + "\n");
                        continue;
                    }

                    // Blank line: buffer if in indented code block, otherwise flush paragraph
                    if (line.isBlank()) {
                        if (inIndentedCodeBlock) {
                            pendingBlankLines++;
                        } else {
                            flushParagraph();
                            writeSkeleton("\n");
                            lastListIndent = 0;
                        }
                        continue;
                    }

                    // Setext heading underline: line of = (h1) or - (h2) following accumulated text
                    if (!paragraphBuffer.isEmpty() && isSetextHeadingUnderline(line)) {
                        String headingText = paragraphBuffer.toString().trim();
                        paragraphBuffer.setLength(0);
                        addSegmentToSkeleton(headingText);
                        writeSkeleton("\n" + line + "\n");
                        continue;
                    }

                    // ATX heading: line starting with one or more # followed by a space
                    if (line.startsWith("#")) {
                        int level = 0;
                        while (level < line.length() && line.charAt(level) == '#') {
                            level++;
                        }
                        if (level < line.length() && line.charAt(level) == ' ') {
                            flushParagraph();
                            String headingText = line.substring(level + 1).trim();
                            // Strip optional closing hashes (e.g. "## Title ##")
                            int closingHash = headingText.lastIndexOf(" #");
                            if (closingHash != -1 && headingText.substring(closingHash + 1).matches("#+")) {
                                headingText = headingText.substring(0, closingHash).trim();
                            }
                            writeSkeleton(line.substring(0, level) + " ");
                            addSegmentToSkeleton(headingText);
                            writeSkeleton("\n");
                            continue;
                        }
                    }

                    // Horizontal rule: line with only -, * or _ characters (at least 3)
                    if (isHorizontalRule(line)) {
                        flushParagraph();
                        writeSkeleton(line + "\n");
                        continue;
                    }

                    // Blockquote: one or more > markers (supports nested: >>, > >, > > >, ...)
                    Matcher bqMatcher = BLOCKQUOTE_PREFIX.matcher(line);
                    if (bqMatcher.matches()) {
                        flushParagraph();
                        String bqPrefix = bqMatcher.group(1) + bqMatcher.group(2);
                        String bqContent = bqMatcher.group(3);
                        writeSkeleton(bqPrefix);
                        if (!bqContent.isEmpty()) {
                            addSegmentToSkeleton(bqContent);
                        }
                        writeSkeleton("\n");
                        continue;
                    }

                    // List item: unordered (- * +) or ordered (N.)
                    String listPrefix = getListPrefix(line);
                    if (listPrefix != null) {
                        flushParagraph();
                        lastListIndent = listPrefix.length();
                        writeSkeleton(listPrefix);
                        addSegmentToSkeleton(line.substring(listPrefix.length()));
                        writeSkeleton("\n");
                        continue;
                    }

                    // Table row: starts with |
                    if (line.startsWith("|")) {
                        flushParagraph();
                        if (isTableSeparator(line)) {
                            writeSkeleton(line + "\n");
                        } else {
                            processTableRow(line);
                        }
                        continue;
                    }

                    // Block-level HTML: a line starting with an HTML tag while no paragraph
                    // is being accumulated. The block runs until the next blank line.
                    if (paragraphBuffer.isEmpty() && isHtmlBlockStart(line)) {
                        inHtmlBlock = true;
                        writeSkeleton(line + "\n");
                        continue;
                    }

                    // List item continuation: indented paragraph within the current list item
                    if (paragraphBuffer.isEmpty() && lastListIndent > 0) {
                        int spaces = 0;
                        while (spaces < line.length() && line.charAt(spaces) == ' ') {
                            spaces++;
                        }
                        if (spaces >= lastListIndent) {
                            writeSkeleton(line.substring(0, lastListIndent));
                            addSegmentToSkeleton(line.substring(lastListIndent));
                            writeSkeleton("\n");
                            continue;
                        } else {
                            lastListIndent = 0;
                        }
                    }

                    // Indented code block start: 4 spaces or tab, only at block level
                    if (paragraphBuffer.isEmpty() && isIndentedCodeLine(line)) {
                        inIndentedCodeBlock = true;
                        indentedCodeBuffer.setLength(0);
                        indentedCodeBuffer.append(line);
                        continue;
                    }

                    // Paragraph: accumulate until a blank line or block element
                    if (!paragraphBuffer.isEmpty()) {
                        paragraphBuffer.append("\n");
                    }
                    paragraphBuffer.append(line);
                }

                if (inCodeFence && !codeFenceBuffer.isEmpty()) {
                    addCodeBlockToSkeleton(codeFenceBuffer.toString());
                    writeSkeleton("\n");
                }
                if (inMathBlock && !mathBlockBuffer.isEmpty()) {
                    addCodeBlockToSkeleton(mathBlockBuffer.toString());
                    writeSkeleton("\n");
                }
                if (inIndentedCodeBlock) {
                    addCodeBlockToSkeleton(indentedCodeBuffer.toString());
                    writeSkeleton("\n");
                }
                flushParagraph();

                skeletonOutput.close();
                writeXliff("</body>\n");
                writeXliff("</file>\n");
                writeXliff("</xliff>");
                xliffOutput.close();
            }
            result.add(Constants.SUCCESS);
        } catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
            Logger logger = System.getLogger(Markdown2Xliff.class.getName());
            logger.log(Level.ERROR, Messages.getString("Markdown2Xliff.1"), e);
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }

    private void flushParagraph() throws IOException {
        if (paragraphBuffer.isEmpty()) {
            return;
        }
        String text = paragraphBuffer.toString();
        paragraphBuffer.setLength(0);

        String[] segments;
        if (segmenter != null) {
            segments = segmenter.segment(text);
        } else {
            segments = new String[] { text };
        }
        boolean hasSegments = false;
        for (String seg : segments) {
            if (!seg.trim().isEmpty()) {
                addSegmentToSkeleton(seg);
                hasSegments = true;
            }
        }
        if (hasSegments) {
            writeSkeleton("\n");
        }
    }

    private void addCodeBlockToSkeleton(String text) throws IOException {
        writeXliff("   <trans-unit id=\"" + segId + "\" xml:space=\"preserve\" approved=\"no\">\n"
                + "      <source>" + XMLUtils.cleanText(text) + "</source>\n"
                + "   </trans-unit>\n");
        writeSkeleton("%%%" + segId++ + "%%%");
    }

    private void addSegmentToSkeleton(String text) throws IOException {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        int[] tagId = { 0 };
        String tagged = parseInline(text, tagId, refMap);
        writeXliff("   <trans-unit id=\"" + segId + "\" xml:space=\"preserve\" approved=\"no\">\n"
                + "      <source>" + tagged + "</source>\n"
                + "   </trans-unit>\n");
        writeSkeleton("%%%" + segId++ + "%%%");
    }

    private void processTableRow(String line) throws IOException {
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '\\' && i + 1 < trimmed.length()) {
                cell.append(c);
                cell.append(trimmed.charAt(++i));
            } else if (c == '|') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        writeSkeleton("| ");
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                writeSkeleton(" | ");
            }
            String cellText = cells.get(i).trim();
            if (cellText.isEmpty()) {
                writeSkeleton(cells.get(i));
            } else {
                addSegmentToSkeleton(cellText);
            }
        }
        writeSkeleton(" |\n");
    }

    private static boolean isIndentedCodeLine(String line) {
        return line.startsWith("    ") || line.startsWith("\t");
    }

    private static String getFenceDelimiter(String line) {
        int indent = 0;
        while (indent < 3 && indent < line.length() && line.charAt(indent) == ' ')
            indent++;
        if (indent >= line.length())
            return null;
        char fc = line.charAt(indent);
        if (fc != '`' && fc != '~')
            return null;
        int len = 0;
        while (indent + len < line.length() && line.charAt(indent + len) == fc)
            len++;
        return len >= 3 ? line.substring(indent, indent + len) : null;
    }

    private static boolean isClosingFence(String line, String openingDelimiter) {
        int indent = 0;
        while (indent < 3 && indent < line.length() && line.charAt(indent) == ' ')
            indent++;
        String stripped = line.substring(indent).stripTrailing();
        if (stripped.length() < openingDelimiter.length())
            return false;
        char fc = openingDelimiter.charAt(0);
        for (int i = 0; i < stripped.length(); i++) {
            if (stripped.charAt(i) != fc)
                return false;
        }
        return true;
    }

    private static boolean isSetextHeadingUnderline(String line) {
        String trimmed = line.trim();
        return trimmed.matches("=+") || trimmed.matches("-+");
    }

    private static boolean isHorizontalRule(String line) {
        String stripped = line.replaceAll("[ \t]", "");
        return stripped.matches("-{3,}") || stripped.matches("\\*{3,}") || stripped.matches("_{3,}");
    }

    private static boolean isTableSeparator(String line) {
        String stripped = line.replaceAll("[|:\\- \t]", "");
        return stripped.isEmpty() && line.contains("-");
    }

    private static String getListPrefix(String line) {
        int indent = 0;
        while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
            indent++;
        }
        String rest = line.substring(indent);
        if (rest.startsWith("- ") || rest.startsWith("* ") || rest.startsWith("+ ")) {
            return line.substring(0, indent + 2);
        }
        int dotIndex = rest.indexOf(". ");
        if (dotIndex > 0) {
            String num = rest.substring(0, dotIndex);
            if (num.matches("\\d+")) {
                return line.substring(0, indent + dotIndex + 2);
            }
        }
        int parenIndex = rest.indexOf(") ");
        if (parenIndex > 0) {
            String num = rest.substring(0, parenIndex);
            if (num.matches("\\d+")) {
                return line.substring(0, indent + parenIndex + 2);
            }
        }
        return null;
    }

    String parseInline(String text, int[] tagId, Map<String, String> refMap) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);

            // Backslash escape or hard line break (\ before \n)
            if (c == '\\' && i + 1 < text.length()) {
                if (text.charAt(i + 1) == '\n') {
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"lb\">\\</ph>\n");
                    i += 2;
                } else {
                    result.append(XMLUtils.cleanText(String.valueOf(text.charAt(i + 1))));
                    i += 2;
                }
                continue;
            }

            // Hard line break: two or more trailing spaces before \n
            if (c == ' ') {
                int spaceStart = i;
                while (i < text.length() && text.charAt(i) == ' ') {
                    i++;
                }
                if (i < text.length() && text.charAt(i) == '\n' && (i - spaceStart) >= 2) {
                    StringBuilder spaces = new StringBuilder();
                    for (int k = spaceStart; k < i; k++) {
                        spaces.append(' ');
                    }
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"lb\">")
                            .append(spaces).append("</ph>\n");
                    i++;
                } else {
                    for (int k = spaceStart; k < i; k++) {
                        result.append(' ');
                    }
                }
                continue;
            }

            // Inline code: one or more backticks
            if (c == '`') {
                int start = i;
                while (i < text.length() && text.charAt(i) == '`') {
                    i++;
                }
                String delimiter = text.substring(start, i);
                int end = findClosingBacktick(text, i, delimiter);
                if (end != -1) {
                    String code = text.substring(i, end);
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-code\">");
                    result.append(XMLUtils.cleanText(delimiter));
                    result.append("</ph>");
                    result.append(XMLUtils.cleanText(code));
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-code\">");
                    result.append(XMLUtils.cleanText(delimiter));
                    result.append("</ph>");
                    i = end + delimiter.length();
                } else {
                    result.append(XMLUtils.cleanText(delimiter));
                }
                continue;
            }

            // Image: ![alt](url) or reference image ![alt][ref]
            if (c == '!' && i + 1 < text.length() && text.charAt(i + 1) == '[') {
                int closeBracket = findClosingBracket(text, i + 2);
                if (closeBracket != -1) {
                    String altText = text.substring(i + 2, closeBracket);
                    int after = closeBracket + 1;
                    if (after < text.length() && text.charAt(after) == '(') {
                        int closeParens = findClosingParens(text, after + 1);
                        if (closeParens != -1) {
                            String[] dest = parseLinkDestination(text.substring(after + 1, closeParens));
                            result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"image\">![</ph>");
                            result.append(parseInline(altText, tagId, refMap));
                            if (dest[1] != null) {
                                result.append("<ph id=\"").append(tagId[0]++).append("\">](")
                                        .append(XMLUtils.cleanText(dest[0])).append(" ").append(dest[2])
                                        .append("</ph>");
                                result.append(XMLUtils.cleanText(dest[1]));
                                result.append("<ph id=\"").append(tagId[0]++).append("\">").append(dest[3])
                                        .append(")</ph>");
                            } else {
                                result.append("<ph id=\"").append(tagId[0]++).append("\">](")
                                        .append(XMLUtils.cleanText(dest[0])).append(")</ph>");
                            }
                            i = closeParens + 1;
                            continue;
                        }
                    }
                    if (after < text.length() && text.charAt(after) == '[') {
                        int closeRef = findClosingBracket(text, after + 1);
                        if (closeRef != -1) {
                            String refLabel = text.substring(after + 1, closeRef);
                            result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"image\">![</ph>");
                            result.append(parseInline(altText, tagId, refMap));
                            result.append("<ph id=\"").append(tagId[0]++).append("\">][")
                                    .append(XMLUtils.cleanText(refLabel)).append("]</ph>");
                            i = closeRef + 1;
                            continue;
                        }
                    }
                }
            }

            // Footnote reference: [^label]
            if (c == '[' && i + 1 < text.length() && text.charAt(i + 1) == '^') {
                int closeRef = findClosingBracket(text, i + 1);
                if (closeRef != -1) {
                    String fnRef = text.substring(i, closeRef + 1);
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-footnote\">")
                            .append(XMLUtils.cleanText(fnRef)).append("</ph>");
                    i = closeRef + 1;
                    continue;
                }
            }

            // Link: [text](url), [text][ref], [text][], or shortcut [ref]
            if (c == '[') {
                int closeBracket = findClosingBracket(text, i + 1);
                if (closeBracket != -1) {
                    String linkText = text.substring(i + 1, closeBracket);
                    int after = closeBracket + 1;
                    // Inline link: [text](url) or [text](url "title")
                    if (after < text.length() && text.charAt(after) == '(') {
                        int closeParens = findClosingParens(text, after + 1);
                        if (closeParens != -1) {
                            String[] dest = parseLinkDestination(text.substring(after + 1, closeParens));
                            result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-link-start\">[</ph>");
                            result.append(parseInline(linkText, tagId, refMap));
                            if (dest[1] != null) {
                                result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-link-end\">](")
                                        .append(XMLUtils.cleanText(dest[0])).append(" ").append(dest[2])
                                        .append("</ph>");
                                result.append(XMLUtils.cleanText(dest[1]));
                                result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-link-end\">")
                                        .append(dest[3]).append(")</ph>");
                            } else {
                                result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-link-end\">](")
                                        .append(XMLUtils.cleanText(dest[0])).append(")</ph>");
                            }
                            i = closeParens + 1;
                            continue;
                        }
                    }
                    // Full or collapsed reference link: [text][ref] or [text][]
                    if (after < text.length() && text.charAt(after) == '[') {
                        int closeRef = findClosingBracket(text, after + 1);
                        if (closeRef != -1) {
                            String refLabel = text.substring(after + 1, closeRef);
                            result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-link-start\">[</ph>");
                            result.append(parseInline(linkText, tagId, refMap));
                            result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-link-ref\">][")
                                    .append(XMLUtils.cleanText(refLabel)).append("]</ph>");
                            i = closeRef + 1;
                            continue;
                        }
                    }
                    // Shortcut reference link: [ref] where ref is a known label
                    if (refMap != null && refMap.containsKey(linkText.toLowerCase().trim())) {
                        result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-link-start\">[</ph>");
                        result.append(parseInline(linkText, tagId, refMap));
                        result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-link-ref\">]</ph>");
                        i = closeBracket + 1;
                        continue;
                    }
                }
            }

            // Bold+italic: ***text*** (must be checked before ** and *)
            if (text.startsWith("***", i)
                    && i + 3 < text.length()
                    && !Character.isWhitespace(text.charAt(i + 3))) {
                int end = text.indexOf("***", i + 3);
                if (end != -1) {
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-bold-italic\">***</ph>");
                    result.append(parseInline(text.substring(i + 3, end), tagId, refMap));
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-bold-italic\">***</ph>");
                    i = end + 3;
                    continue;
                }
            }

            // Bold: **text**
            if (text.startsWith("**", i)
                    && (i + 2 >= text.length() || text.charAt(i + 2) != '*')
                    && i + 2 < text.length()
                    && !Character.isWhitespace(text.charAt(i + 2))) {
                int end = text.indexOf("**", i + 2);
                if (end != -1) {
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-bold\">**</ph>");
                    result.append(parseInline(text.substring(i + 2, end), tagId, refMap));
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-bold\">**</ph>");
                    i = end + 2;
                    continue;
                }
            }

            // Italic: *text*
            if (c == '*'
                    && (i + 1 >= text.length() || text.charAt(i + 1) != '*')
                    && i + 1 < text.length()
                    && !Character.isWhitespace(text.charAt(i + 1))) {
                int end = text.indexOf('*', i + 1);
                if (end != -1) {
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-italic\">*</ph>");
                    result.append(parseInline(text.substring(i + 1, end), tagId, refMap));
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-italic\">*</ph>");
                    i = end + 1;
                    continue;
                }
            }

            // Bold+italic with underscores: ___text___ (must be checked before __ and _)
            // Can only open if not preceded by alphanumeric and not part of a longer run.
            if (text.startsWith("___", i)
                    && (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)))
                    && (i + 3 >= text.length() || text.charAt(i + 3) != '_')) {
                int end = findClosingUnderscoreRun(text, i + 3, 3);
                if (end != -1) {
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-bold-italic\">___</ph>");
                    result.append(parseInline(text.substring(i + 3, end), tagId, refMap));
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-bold-italic\">___</ph>");
                    i = end + 3;
                    continue;
                }
            }

            // Bold with underscores: __text__ — only when not part of ___
            if (text.startsWith("__", i)
                    && (i + 2 >= text.length() || text.charAt(i + 2) != '_')
                    && (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)))) {
                int end = findClosingUnderscoreRun(text, i + 2, 2);
                if (end != -1) {
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-bold\">__</ph>");
                    result.append(parseInline(text.substring(i + 2, end), tagId, refMap));
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-bold\">__</ph>");
                    i = end + 2;
                    continue;
                }
            }

            // Italic with underscores: _text_ — only when not part of __
            if (c == '_'
                    && (i + 1 >= text.length() || text.charAt(i + 1) != '_')
                    && (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)))) {
                int end = findClosingUnderscoreRun(text, i + 1, 1);
                if (end != -1) {
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-italic\">_</ph>");
                    result.append(parseInline(text.substring(i + 1, end), tagId, refMap));
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-italic\">_</ph>");
                    i = end + 1;
                    continue;
                }
            }

            // Strikethrough: ~~text~~
            if (text.startsWith("~~", i)) {
                int end = text.indexOf("~~", i + 2);
                if (end != -1) {
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-strikethrough\">~~</ph>");
                    result.append(parseInline(text.substring(i + 2, end), tagId, refMap));
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-strikethrough\">~~</ph>");
                    i = end + 2;
                    continue;
                }
            }

            // Subscript: ~text~ (Pandoc) — single tilde only; content must not contain
            // spaces
            if (c == '~' && (i + 1 >= text.length() || text.charAt(i + 1) != '~')) {
                int end = text.indexOf('~', i + 1);
                if (end != -1) {
                    String sub = text.substring(i + 1, end);
                    if (!sub.isEmpty() && !sub.contains(" ") && !sub.contains("\n")) {
                        result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-subscript\">~</ph>");
                        result.append(parseInline(sub, tagId, refMap));
                        result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-subscript\">~</ph>");
                        i = end + 1;
                        continue;
                    }
                }
            }

            // Superscript: ^text^ (Pandoc) — content must not contain spaces
            if (c == '^') {
                int end = text.indexOf('^', i + 1);
                if (end != -1) {
                    String sup = text.substring(i + 1, end);
                    if (!sup.isEmpty() && !sup.contains(" ") && !sup.contains("\n")) {
                        result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-superscript\">^</ph>");
                        result.append(parseInline(sup, tagId, refMap));
                        result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-superscript\">^</ph>");
                        i = end + 1;
                        continue;
                    }
                }
            }

            // Highlight: ==text== (Pandoc, Obsidian)
            if (text.startsWith("==", i)) {
                int end = text.indexOf("==", i + 2);
                if (end != -1) {
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-highlight\">==</ph>");
                    result.append(parseInline(text.substring(i + 2, end), tagId, refMap));
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-highlight\">==</ph>");
                    i = end + 2;
                    continue;
                }
            }

            // Inline display math: $$formula$$ — must be checked before single $
            if (c == '$' && text.startsWith("$$", i)) {
                int end = text.indexOf("$$", i + 2);
                if (end != -1) {
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-math\">")
                            .append(XMLUtils.cleanText(text.substring(i, end + 2))).append("</ph>");
                    i = end + 2;
                    continue;
                }
            }

            // Inline math: $formula$
            if (c == '$') {
                int end = text.indexOf('$', i + 1);
                if (end != -1) {
                    result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-math\">")
                            .append(XMLUtils.cleanText(text.substring(i, end + 1))).append("</ph>");
                    i = end + 1;
                    continue;
                }
            }

            // Inline HTML tag: <tag>, </tag>, <tag/>, <!-- comment -->
            if (c == '<' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (Character.isLetter(next) || next == '/' || next == '!') {
                    int end = findHtmlTagEnd(text, i);
                    if (end != -1) {
                        String tag = text.substring(i, end + 1);
                        result.append("<ph id=\"").append(tagId[0]++).append("\" ctype=\"x-html\">");
                        result.append(XMLUtils.cleanText(tag));
                        result.append("</ph>");
                        i = end + 1;
                        continue;
                    }
                }
            }

            // Emoji shortcode: :name:
            if (c == ':' && i + 1 < text.length()) {
                int end = text.indexOf(':', i + 1);
                if (end > i + 1) {
                    String shortcode = text.substring(i + 1, end);
                    if (shortcode.matches("[a-zA-Z0-9_+\\-]+")) {
                        String emoji = emojiMap.get(shortcode);
                        if (emoji != null) {
                            result.append(emoji);
                            i = end + 1;
                            continue;
                        }
                    }
                }
            }

            result.append(XMLUtils.cleanText(String.valueOf(c)));
            i++;
        }
        return result.toString();
    }

    private static String[] parseLinkDestination(String content) {
        content = content.trim();
        if (content.isEmpty())
            return new String[] { "", null, null, null };
        String url;
        String rest;
        if (content.startsWith("<")) {
            int closeAngle = content.indexOf('>');
            if (closeAngle == -1)
                return new String[] { content, null, null, null };
            url = content.substring(0, closeAngle + 1);
            rest = content.substring(closeAngle + 1).stripLeading();
        } else {
            int spaceIdx = -1;
            for (int k = 0; k < content.length(); k++) {
                if (Character.isWhitespace(content.charAt(k))) {
                    spaceIdx = k;
                    break;
                }
            }
            if (spaceIdx == -1)
                return new String[] { content, null, null, null };
            url = content.substring(0, spaceIdx);
            rest = content.substring(spaceIdx).stripLeading();
        }
        if (rest.isEmpty())
            return new String[] { url, null, null, null };
        char open = rest.charAt(0);
        char close = open == '(' ? ')' : open;
        if (open != '"' && open != '\'' && open != '(')
            return new String[] { url, null, null, null };
        int closeIdx = rest.lastIndexOf(close);
        if (closeIdx <= 0)
            return new String[] { url, null, null, null };
        return new String[] { url, rest.substring(1, closeIdx), String.valueOf(open), String.valueOf(close) };
    }

    private static int findHtmlTagEnd(String text, int start) {
        if (text.startsWith("<!--", start)) {
            int end = text.indexOf("-->", start + 4);
            return end != -1 ? end + 2 : -1;
        }
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!inQuote && (c == '"' || c == '\'')) {
                inQuote = true;
                quoteChar = c;
            } else if (inQuote && c == quoteChar) {
                inQuote = false;
            } else if (!inQuote && c == '>') {
                return i;
            } else if (!inQuote && c == '<') {
                return -1;
            }
        }
        return -1;
    }

    private static boolean isHtmlBlockStart(String line) {
        if (line.length() < 2 || line.charAt(0) != '<') {
            return false;
        }
        char next = line.charAt(1);
        return Character.isLetter(next) || next == '/' || next == '!';
    }

    private static int findClosingBacktick(String text, int start, String delimiter) {
        int i = start;
        while (i <= text.length() - delimiter.length()) {
            if (text.startsWith(delimiter, i)) {
                if (i + delimiter.length() >= text.length() || text.charAt(i + delimiter.length()) != '`') {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    private static int findClosingUnderscoreRun(String text, int start, int runLen) {
        int i = start;
        while (i <= text.length() - runLen) {
            boolean isRun = true;
            for (int k = 0; k < runLen; k++) {
                if (text.charAt(i + k) != '_') {
                    isRun = false;
                    break;
                }
            }
            if (isRun) {
                // Must not be part of a longer run
                if (i > 0 && text.charAt(i - 1) == '_') {
                    i++;
                    continue;
                }
                if (i + runLen < text.length() && text.charAt(i + runLen) == '_') {
                    i++;
                    continue;
                }
                // Must not be followed by alphanumeric (cannot close inside a word)
                int after = i + runLen;
                if (after < text.length() && Character.isLetterOrDigit(text.charAt(after))) {
                    i++;
                    continue;
                }
                return i;
            }
            i++;
        }
        return -1;
    }

    private static int findClosingBracket(String text, int start) {
        int depth = 1;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                i++;
                continue;
            }
            if (c == '[')
                depth++;
            if (c == ']' && --depth == 0)
                return i;
        }
        return -1;
    }

    private static int findClosingParens(String text, int start) {
        int depth = 1;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                i++;
                continue;
            }
            if (c == '(')
                depth++;
            if (c == ')' && --depth == 0)
                return i;
        }
        return -1;
    }

    private void writeXliff(String string) throws IOException {
        xliffOutput.write(string.getBytes(StandardCharsets.UTF_8));
    }

    private void writeSkeleton(String string) throws IOException {
        skeletonOutput.write(string.getBytes(StandardCharsets.UTF_8));
    }
}
