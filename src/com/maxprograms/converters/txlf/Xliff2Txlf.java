/*******************************************************************************
 * Copyright (c) 2018 - 2026 Maxprograms.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors: Maxprograms - initial API and implementation
 *******************************************************************************/

package com.maxprograms.converters.txlf;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.converters.xliff.FromOpenXliff;
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

public class Xliff2Txlf {

    private static Catalog catalog;
    private static Document skeleton;
    private static Map<String, Element> segments;
    private static String tgtLang;
    private static boolean hasTarget;

    private Xliff2Txlf() {
        // do not instantiate this class
        // use run method instead
    }

    public static List<String> run(Map<String, String> params) {
        List<String> result = new ArrayList<>();
        tgtLang = "";
        hasTarget = false;
        String xliffFile = params.get("xliff");
        String sklFile = params.get("skeleton");
        String outputFile = params.get("backfile");
        try {
            catalog = CatalogBuilder.getCatalog(params.get("catalog"));
            loadXliff(xliffFile);
            loadSkeleton(sklFile);
            Element root = skeleton.getRootElement();
            recurseSkeleton(root);
            FromOpenXliff.restoreAttributes(root);
            File f = new File(outputFile);
            File p = f.getParentFile();
            if (p == null) {
                p = new File(System.getProperty("user.dir"));
            }
            if (Files.notExists(p.toPath())) {
                Files.createDirectories(p.toPath());
            }
            if (!f.exists()) {
                Files.createFile(Paths.get(f.toURI()));
            }
            Element file = root.getChild("file");
            if (!file.hasAttribute("target-language") && hasTarget) {
                file.setAttribute("target-language", tgtLang);
            }
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                XMLOutputter outputter = new XMLOutputter();
                Indenter.indent(root, 0);
                outputter.preserveSpace(true);
                outputter.output(skeleton, out);
            }
            result.add(Constants.SUCCESS);
        } catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
            Logger logger = System.getLogger(Xliff2Txlf.class.getName());
            logger.log(Level.ERROR, Messages.getString("Xliff2Txlf.1"), e);
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }

    private static void loadXliff(String xliffFile) throws SAXException, IOException, ParserConfigurationException {
        SAXBuilder builder = new SAXBuilder();
        builder.setEntityResolver(catalog);
        Document xliff = builder.build(xliffFile);
        segments = new Hashtable<>();
        recurseXliff(xliff.getRootElement());
    }

    private static void recurseXliff(Element e) throws IOException {
        if ("file".equals(e.getName()) && tgtLang.isEmpty()) {
            tgtLang = e.getAttributeValue("target-language");
        }
        if ("trans-unit".equals(e.getName())) {
            segments.put(e.getAttributeValue("id"), e);
        } else {
            List<Element> children = e.getChildren();
            for (Element child : children) {
                recurseXliff(child);
            }
        }
    }

    private static void loadSkeleton(String sklFile) throws SAXException, IOException, ParserConfigurationException {
        SAXBuilder builder = new SAXBuilder();
        builder.setEntityResolver(catalog);
        skeleton = builder.build(sklFile);
    }

    private static void recurseSkeleton(Element root) throws SAXException, IOException, ParserConfigurationException {
        if ("file".equals(root.getName()) && root.hasAttribute("target-language")) {
            tgtLang = root.getAttributeValue("target-language");
        }
        if ("trans-unit".equals(root.getName()) && !root.getAttributeValue("translate").equals("no")) {
            List<PI> instructions = root.getPI(Constants.TOOLID);
            if (!instructions.isEmpty()) {
                String pi = instructions.get(0).getData();
                Element segment = segments.get(pi);
                if (segment.getAttributeValue("approved").equals("yes")) {
                    Element target = root.getChild("target");
                    if (target == null) {
                        addtarget(root);
                        target = root.getChild("target");
                    }
                    Element oldTarget = new Element("target");
                    oldTarget.setContent(target.getContent());
                    Element translation = segment.getChild("target");
                    target.setContent(translation.getContent());
                    if (!target.getChildren().isEmpty()) {
                        replaceTags(target);
                    }
                    target.setAttribute("state", "translated");
                }
                root.removePI(Constants.TOOLID);
            }
            if (!hasTarget) {
                hasTarget = root.getChild("target") != null;
            }
            return;
        }
        List<Element> children = root.getChildren();
        for (Element child : children) {
            recurseSkeleton(child);
        }
    }

    private static void addtarget(Element root) {
        List<XMLNode> newContent = new Vector<>();
        List<XMLNode> content = root.getContent();
        for (XMLNode node : content) {
            newContent.add(node);
            if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
                Element e = (Element) node;
                if (e.getName().equals("source")) {
                    newContent.add(new TextNode("\n"));
                    newContent.add(new Element("target"));
                }
            }
        }
        root.setContent(newContent);
    }

    private static void replaceTags(Element target) throws SAXException, IOException, ParserConfigurationException {
        StringBuilder sb = new StringBuilder();
        sb.append("<target>");
        List<XMLNode> content = target.getContent();
        for (XMLNode node : content) {
            if (node.getNodeType() == XMLNode.TEXT_NODE) {
                TextNode text = (TextNode) node;
                sb.append(XMLUtils.cleanText(text.getText()));
            }
            if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
                Element e = (Element) node;
                if ("mrk".equals(e.getName())) {
                    sb.append(e.toString());
                } else {
                    StringBuilder tagContent = new StringBuilder();
                    List<XMLNode> tagNodes = e.getContent();
                    for (XMLNode tagNode : tagNodes) {
                        tagContent = tagContent.append(tagNode.toString());
                    }
                    sb.append(uncleanString(tagContent.toString()));
                }
            }
        }
        sb.append("</target>");
        String string = sb.toString();
        if (string.indexOf("gs4tr:") != -1) {
            string = string.replace("<target>", "<target xmlns:gs4tr=\"http://www.gs4tr.org/schema/xliff-ext\">");
        }
        SAXBuilder builder = new SAXBuilder();
        Document d = builder.build(new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8)));
        target.setContent(d.getRootElement().getContent());
    }

    public static String uncleanString(String string) {
        String result = string.replace("&amp;", "&");
        result = result.replace("&gt;", ">");
        result = result.replace("&lt;", "<");
        return result;
    }
}
