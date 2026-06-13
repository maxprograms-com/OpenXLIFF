/*******************************************************************************
 * Copyright (c) 2018 - 2026 Maxprograms.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors: Maxprograms - initial API and implementation
 *******************************************************************************/
package com.maxprograms.converters.wpml;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.segmenter.Segmenter;
import com.maxprograms.segmenter.SegmenterPool;
import com.maxprograms.xml.CData;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;
import com.maxprograms.xml.XMLUtils;

public class Wpml2Xliff {

    private static final Pattern START_TAG_PATTERN = Pattern
            .compile("<[A-Za-z0-9]+([\\s][A-Za-z]+=[\"|\'][^<&>]*[\"|\'])*[\\s]*[/]?>");
    private static final Pattern END_TAG_PATTERN = Pattern.compile("</[A-Za-z0-9]+>");

    private static final class Context {
        String inputFile;
        String skeletonFile;
        String sourceLanguage;
        String targetLanguage;
        int segId;
        Segmenter segmenter;
        boolean paragraphSegmentation;
    }

    private Wpml2Xliff() {
        // do not instantiate this class
        // use run method instead
    }

    public static List<String> run(Map<String, String> params) {
        List<String> result = new ArrayList<>();
        Context ctx = new Context();
        ctx.inputFile = params.get("source");
        String xliffFile = params.get("xliff");
        ctx.skeletonFile = params.get("skeleton");
        ctx.sourceLanguage = params.get("srcLang");
        ctx.targetLanguage = params.get("tgtLang");
        ctx.segId = 0;
        String srxRules = params.get("srxFile");
        String catalogFile = params.get("catalog");
        String elementSegmentation = params.get("paragraph");
        if (elementSegmentation == null) {
            ctx.paragraphSegmentation = false;
        } else {
            if (elementSegmentation.equals("yes")) {
                ctx.paragraphSegmentation = true;
            } else {
                ctx.paragraphSegmentation = false;
            }
        }

        try {
            Catalog catalog = CatalogBuilder.getCatalog(catalogFile);
            if (!ctx.paragraphSegmentation) {
                ctx.segmenter = SegmenterPool.getSegmenter(srxRules, ctx.sourceLanguage, catalog);
            }
            SAXBuilder builder = new SAXBuilder();
            builder.setEntityResolver(catalog);
            Document doc = builder.build(ctx.inputFile);
            Element root = doc.getRootElement();

            Document newDoc = new Document(root.getNamespace(), root.getName(), doc.getPublicId(), doc.getSystemId());
            Element newRoot = newDoc.getRootElement();
            newRoot.addContent(new PI("encoding", doc.getEncoding().name()));

            recurse(ctx, root, newRoot);

            try (FileOutputStream out = new FileOutputStream(xliffFile)) {
                Indenter.indent(newRoot, 2);
                XMLOutputter outputter = new XMLOutputter();
                outputter.preserveSpace(true);
                outputter.output(newDoc, out);
            }
            try (FileOutputStream skl = new FileOutputStream(ctx.skeletonFile)) {
                XMLOutputter outputter = new XMLOutputter();
                outputter.preserveSpace(true);
                outputter.output(doc, skl);
            }
            result.add(Constants.SUCCESS);
        } catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
            Logger logger = System.getLogger(Wpml2Xliff.class.getName());
            logger.log(Level.ERROR, Messages.getString("Wpml2Xliff.1"), e);
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }

    private static void recurse(Context ctx, Element root, Element newRoot)
            throws SAXException, IOException, ParserConfigurationException {
        newRoot.setAttributes(root.getAttributes());
        if ("file".equals(root.getName())) {
            newRoot.setAttribute("datatype", "x-wpmlxliff");
            newRoot.setAttribute("original", XMLUtils.cleanText(ctx.inputFile));
            newRoot.setAttribute("source-language", ctx.sourceLanguage);
            if (ctx.targetLanguage != null) {
                newRoot.setAttribute("target-language", ctx.targetLanguage);
            }
            Element header = new Element("header");
            newRoot.addContent(header);
            Element skl = new Element("skl");
            header.addContent(skl);
            Element externalFile = new Element("external-file");
            externalFile.setAttribute("href", XMLUtils.cleanText(ctx.skeletonFile));
            skl.addContent(externalFile);
            Element tool = new Element("tool");
            tool.setAttribute("tool-version", Constants.VERSION + " " + Constants.BUILD);
            tool.setAttribute("tool-id", Constants.TOOLID);
            tool.setAttribute("tool-name", Constants.TOOLNAME);
            header.addContent(tool);
        }

        List<XMLNode> nodes = root.getContent();
        for (XMLNode node : nodes) {
            switch (node.getNodeType()) {
                case XMLNode.ELEMENT_NODE:
                    Element child = (Element) node;
                    if ("header".equals(child.getName()) || "source".equals(child.getName())
                            || "target".equals(child.getName())) {
                        // continue
                    } else if ("trans-unit".equals(child.getName())) {
                        ArrayList<XMLNode> newContent = new ArrayList<>();
                        Element src = new Element("source");
                        src.setContent(child.getChild("source").getContent());
                        removeCdata(src);
                        fixHtmlTags(src);
                        if (!containsText(src)) {
                            continue;
                        }
                        List<List<XMLNode>> array = new ArrayList<>();
                        List<XMLNode> currentContent = new ArrayList<>();
                        List<XMLNode> srcContent = src.getContent();
                        for (XMLNode n : srcContent) {
                            if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
                                currentContent.add(n);
                            }
                            if (n.getNodeType() == XMLNode.TEXT_NODE) {
                                String string = ((TextNode) n).getText();
                                StringBuilder builder = new StringBuilder();
                                for (int i = 0; i < string.length(); i++) {
                                    char c = string.charAt(i);
                                    if (c == '\n') {
                                        currentContent.add(new TextNode(builder.toString()));
                                        array.add(currentContent);
                                        List<XMLNode> newLine = new ArrayList<>();
                                        newLine.add(new TextNode("\n"));
                                        array.add(newLine);
                                        currentContent = new ArrayList<>();
                                        builder = new StringBuilder();
                                    } else {
                                        builder.append(c);
                                    }
                                }
                                currentContent.add(new TextNode(builder.toString()));
                            }
                        }
                        if (!currentContent.isEmpty()) {
                            array.add(currentContent);
                        }
                        for (int i = 0; i < array.size(); i++) {
                            Element source = new Element("source");
                            source.setContent(array.get(i));
                            if (!containsText(source)) {
                                newContent.add(new TextNode(getText(source)));
                                continue;
                            }
                            if (ctx.paragraphSegmentation) {
                                Element unit = new Element("trans-unit");
                                unit.setAttribute("id", "" + ctx.segId);
                                newRoot.addContent(unit);
                                unit.addContent(source);
                                newContent.add(new TextNode("%%%" + ctx.segId++ + "%%%"));
                            } else {
                                Element segmented = ctx.segmenter.segment(source);
                                List<Element> list = segmented.getChildren("mrk");
                                for (Element mrk : list) {
                                    Element unit = new Element("trans-unit");
                                    unit.setAttribute("id", "" + ctx.segId);
                                    newRoot.addContent(unit);
                                    Element segment = new Element("source");
                                    segment.setContent(mrk.getContent());
                                    unit.addContent(segment);
                                    newContent.add(new TextNode("%%%" + ctx.segId++ + "%%%"));
                                }
                            }
                        }
                        Element target = child.getChild("target");
                        if (target == null) {
                            target = new Element("target");
                            child.addContent(target);
                        }
                        target.setContent(newContent);
                    } else {
                        Element newChild = new Element(child.getName());
                        newChild.setAttributes(child.getAttributes());
                        newRoot.addContent(newChild);
                        recurse(ctx, child, newChild);
                    }
                    break;
                case XMLNode.TEXT_NODE:
                    newRoot.addContent(node);
                    break;
                default:
                    // do nothing
            }
        }

    }

    protected static String getText(Element e) {
        StringBuilder builder = new StringBuilder();
        List<XMLNode> list = e.getContent();
        for (XMLNode n : list) {
            if (n.getNodeType() == XMLNode.TEXT_NODE) {
                builder.append(((TextNode) n).getText());
            }
            if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
                builder.append(getText((Element) n));
            }
        }
        return builder.toString();
    }

    private static boolean containsText(Element child) {
        List<XMLNode> list = child.getContent();
        for (XMLNode n : list) {
            if (n.getNodeType() == XMLNode.TEXT_NODE) {
                String t = ((TextNode) n).getText();
                if (!t.isBlank()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void removeCdata(Element e) {
        List<XMLNode> newContent = new ArrayList<>();
        List<XMLNode> nodes = e.getContent();
        for (XMLNode node : nodes) {
            switch (node.getNodeType()) {
                case XMLNode.ELEMENT_NODE, XMLNode.TEXT_NODE:
                    newContent.add(node);
                    break;
                case XMLNode.CDATA_SECTION_NODE:
                    CData cdata = (CData) node;
                    newContent.add(new TextNode(cdata.getData()));
                    break;
                default:
                    // do nothing
            }
        }
        e.setContent(newContent);
    }

    private static void fixHtmlTags(Element src) {
        int count = 0;
        String e = src.getText();

        Matcher matcher = START_TAG_PATTERN.matcher(e);
        if (matcher.find()) {
            List<XMLNode> newContent = new ArrayList<>();
            List<XMLNode> content = src.getContent();
            for (XMLNode node : content) {
                if (node.getNodeType() == XMLNode.TEXT_NODE) {
                    TextNode t = (TextNode) node;
                    String text = t.getText();
                    matcher = START_TAG_PATTERN.matcher(text);
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
                            matcher = START_TAG_PATTERN.matcher(text);
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
        matcher = END_TAG_PATTERN.matcher(e);
        if (matcher.find()) {
            List<XMLNode> newContent = new ArrayList<>();
            List<XMLNode> content = src.getContent();
            for (XMLNode node : content) {
                if (node.getNodeType() == XMLNode.TEXT_NODE) {
                    TextNode t = (TextNode) node;
                    String text = t.getText();
                    matcher = END_TAG_PATTERN.matcher(text);
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
                            matcher = END_TAG_PATTERN.matcher(text);
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
    }

}
