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
package com.maxprograms.converters.vtt;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.text.MessageFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLUtils;

public class Vtt2Xliff {

    private static int segId;
    private static String segTime;
    private static String cueId;
    private static FileOutputStream output;
    private static FileOutputStream skeleton;

    // Covers VTT annotation tags (<v Speaker>, <c.red>, <lang en>) and inline timestamp annotations (<00:00:01.000>)
    private static Pattern pattern = Pattern.compile("<(?:[A-Za-z][A-Za-z.]*(?:[\\s][^>]*)?|\\d{2}:\\d{2}:\\d{2}\\.\\d{3})>");
    private static Pattern endPattern = Pattern.compile("</[A-Za-z][A-Za-z.]*>");
    private static Pattern speakerPattern = Pattern.compile("<v\\s+([^>]+)>");

    private Vtt2Xliff() {
        // do not instantiate this class
        // use run method instead
    }

    public static List<String> run(Map<String, String> params) {
        List<String> result = new ArrayList<>();

        segId = 0;
        segTime = null;
        cueId = null;
        String inputFile = params.get("source");
        String xliffFile = params.get("xliff");
        String skeletonFile = params.get("skeleton");
        String sourceLanguage = params.get("srcLang");
        String targetLanguage = params.get("tgtLang");
        String srcEncoding = params.get("srcEncoding");
        String tgtLang = "";
        if (targetLanguage != null) {
            tgtLang = "\" target-language=\"" + targetLanguage;
        }

        try {
            output = new FileOutputStream(xliffFile);
            skeleton = new FileOutputStream(skeletonFile);

            writeString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writeString("<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" "
                    + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                    + "xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");

            writeString("<file original=\"" + inputFile + "\" source-language=\"" + sourceLanguage + tgtLang
                    + "\" tool-id=\"" + Constants.TOOLID + "\" datatype=\"x-vtt\">\n");
            writeString("<header>\n");
            writeString("   <skl>\n");
            writeString("      <external-file href=\"" + skeletonFile + "\"/>\n");
            writeString("   </skl>\n");
            writeString("   <tool tool-version=\"" + Constants.VERSION + " " + Constants.BUILD + "\" tool-id=\""
                    + Constants.TOOLID + "\" tool-name=\"" + Constants.TOOLNAME + "\"/>\n");
            writeString("</header>\n");
            writeString("<?encoding " + srcEncoding + "?>\n");
            writeString("<body>\n");

            try (FileReader reader = new FileReader(inputFile, Charset.forName(srcEncoding))) {
                try (BufferedReader buffered = new BufferedReader(reader)) {
                    String line = "";
                    String pendingLine = null;
                    StringBuilder sb = new StringBuilder();
                    while ((line = buffered.readLine()) != null) {
                        if (line.isBlank()) {
                            writeSkeleton(line + '\n');
                            if (pendingLine != null) {
                                writeSkeleton(pendingLine + '\n');
                                pendingLine = null;
                            }
                            if (!sb.isEmpty()) {
                                writeSegment(sb.toString());
                                sb = new StringBuilder();
                                segTime = null;
                                cueId = null;
                            }
                        } else if (line.contains(" --> ")) {
                            if (pendingLine != null) {
                                cueId = pendingLine;
                                writeSkeleton(pendingLine + '\n');
                                pendingLine = null;
                            }
                            segTime = line;
                            writeSkeleton(line);
                        } else if (segTime != null) {
                            sb.append(line);
                            sb.append('\n');
                        } else {
                            if (pendingLine != null) {
                                writeSkeleton(pendingLine + '\n');
                            }
                            pendingLine = line;
                        }
                    }
                    if (pendingLine != null) {
                        writeSkeleton(pendingLine + '\n');
                    }
                    if (!sb.isEmpty()) {
                        writeSkeleton("\n");
                        writeSegment(sb.toString());
                    }
                    writeSkeleton("\n");
                }
            }

            writeString("</body>\n");
            writeString("</file>\n");
            writeString("</xliff>");

            output.close();
            skeleton.close();
            result.add(Constants.SUCCESS);
        } catch (IOException e) {
            Logger logger = System.getLogger(Vtt2Xliff.class.getName());
            logger.log(Level.ERROR, Messages.getString("Vtt2Xliff.0"), e);
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }

    private static void writeString(String string) throws IOException {
        output.write(string.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeSkeleton(String string) throws IOException {
        skeleton.write(string.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeSegment(String string) throws IOException {
        String speaker = extractSpeaker(string);
        writeSkeleton("%%%" + segId + "%%%\n\n");
        writeString("<trans-unit id=\"" + segId++ + "\" xml:space=\"preserve\">\n");
        writeString("<source>" + getText(string.trim()) + "</source>\n");
        if (cueId != null && !cueId.isBlank()) {
            writeString("<note>" + XMLUtils.cleanText(cueId) + "</note>\n");
        }
        writeString("<note>" + segTime + "</note>\n");
        if (speaker != null) {
            MessageFormat mf = new MessageFormat(Messages.getString("Vtt2Xliff.1"));
            writeString("<note>" + mf.format(new String[] { XMLUtils.cleanText(speaker) }) + "</note>\n");
        }
        writeString("</trans-unit>\n");
    }

    private static String extractSpeaker(String text) {
        Matcher m = speakerPattern.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private static String getText(String string) {
        if (checkHtml(string)) {
            return fixHtml(string);
        }
        return XMLUtils.cleanText(string);
    }

    private static boolean checkHtml(String string) {
        Matcher matcher = pattern.matcher(string);
        if (matcher.find()) {
            return true;
        }
        matcher = endPattern.matcher(string);
        return matcher.find();
    }

    private static String fixHtml(String string) {
        int count = 1;
        Element src = new Element("src");
        src.setText(string);
        Matcher matcher = pattern.matcher(string);
        if (matcher.find()) {
            List<XMLNode> newContent = new ArrayList<>();
            List<XMLNode> content = src.getContent();
            for (XMLNode node : content) {
                if (node.getNodeType() == XMLNode.TEXT_NODE) {
                    TextNode t = (TextNode) node;
                    String text = t.getText();
                    matcher = pattern.matcher(text);
                    if (matcher.find()) {
                        matcher.reset();
                        while (matcher.find()) {
                            int start = matcher.start();
                            int end = matcher.end();

                            String s = text.substring(0, start);
                            newContent.add(new TextNode(s));

                            String tag = text.substring(start, end);
                            Element ph = new Element("ph");
                            ph.setAttribute("id", "" + count++);
                            ph.setText(tag);
                            newContent.add(ph);

                            text = text.substring(end);
                            matcher = pattern.matcher(text);
                        }
                        newContent.add(new TextNode(text));
                    } else {
                        newContent.add(node);
                    }
                } else {
                    newContent.add(node);
                }
            }
            src.setContent(newContent);
        }
        matcher = endPattern.matcher(string);
        if (matcher.find()) {
            List<XMLNode> newContent = new ArrayList<>();
            List<XMLNode> content = src.getContent();
            for (XMLNode node : content) {
                if (node.getNodeType() == XMLNode.TEXT_NODE) {
                    TextNode t = (TextNode) node;
                    String text = t.getText();
                    matcher = endPattern.matcher(text);
                    if (matcher.find()) {
                        matcher.reset();
                        while (matcher.find()) {
                            int start = matcher.start();
                            int end = matcher.end();

                            String s = text.substring(0, start);
                            newContent.add(new TextNode(s));

                            String tag = text.substring(start, end);
                            Element ph = new Element("ph");
                            ph.setAttribute("id", "" + count++);
                            ph.setText(tag);
                            newContent.add(ph);

                            text = text.substring(end);
                            matcher = endPattern.matcher(text);
                        }
                        newContent.add(new TextNode(text));
                    } else {
                        newContent.add(node);
                    }
                } else {
                    newContent.add(node);
                }
            }
            src.setContent(newContent);
        }
        return src.toString().replace("<src>", "").replace("</src>", "");
    }
}
