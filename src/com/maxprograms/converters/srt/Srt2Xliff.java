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
package com.maxprograms.converters.srt;

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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLUtils;

public class Srt2Xliff {

    private static int segId;
    private static String segTime;
    private static FileOutputStream output;
    private static FileOutputStream skeleton;

    private static Pattern pattern = Pattern.compile("<[A-Za-z]+([\\s][A-Za-z]+=[\"|\'][^<&>]*[\"|\'])*[/]?>");
    private static Pattern endPattern = Pattern.compile("</[A-Za-z]+>");

    private Srt2Xliff() {
        // do not instantiate this class
        // use run method instead
    }

    public static List<String> run(Map<String, String> params) {
        List<String> result = new ArrayList<>();

        segId = 0;
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
                    + "\" tool-id=\"" + Constants.TOOLID + "\" datatype=\"x-srt\">\n");
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
                    StringBuilder sb = new StringBuilder();
                    while ((line = buffered.readLine()) != null) {
                        if (line.isBlank()) {
                            writeSkeleton(line + '\n');
                            if (sb.length() > 0) {
                                writeSegment(sb.toString());
                                sb = new StringBuilder();
                            }
                        } else {
                            if (line.trim().matches("[\\d]+")) {
                                segId = Integer.valueOf(line.trim());
                                writeSkeleton(line + '\n');
                            } else if (line.contains(" --> ")) {
                                segTime = line;
                                writeSkeleton(line);
                            } else {
                                sb.append(line);
                                sb.append('\n');
                            }
                        }
                    }
                    if (sb.length() > 0) {
                        writeSegment(sb.toString());
                    }
                }
            }

            writeString("</body>\n");
            writeString("</file>\n");
            writeString("</xliff>");

            output.close();
            skeleton.close();
            result.add(Constants.SUCCESS);
        } catch (IOException e) {
            Logger logger = System.getLogger(Srt2Xliff.class.getName());
            logger.log(Level.ERROR, Messages.getString("Srt2Xliff.0"), e);
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
        writeSkeleton("%%%" + segId + "%%%\n\n");
        writeString("<trans-unit id=\"" + segId++ + "\" xml:space=\"preserve\">\n");
        writeString("<source>" + getText(string.trim()) + "</source>\n");
        writeString("<note>" + segTime + "</note>\n");
        writeString("</trans-unit>\n");
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
