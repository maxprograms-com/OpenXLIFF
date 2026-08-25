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
package com.maxprograms.converters.xliff2;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;

public class FromOpenXliff2 {

    private FromOpenXliff2() {
        // do not instantiate this class
        // use run method instead
    }

    public static List<String> run(Map<String, String> params) {
        List<String> result = new Vector<>();
        String xliffFile = params.get("xliff");
        String skeletonFile = params.get("skeleton");
        String outputFile = params.get("backfile");
        String catalog = params.get("catalog");
        try {
            SAXBuilder builder = new SAXBuilder();
            builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));

            Document xliff = builder.build(xliffFile);
            Document skeleton = builder.build(skeletonFile);

            Element xliffRoot = xliff.getRootElement();
            if (!xliffRoot.getAttributeValue("version", "").startsWith("2.")) {
                result.add(Constants.ERROR);
                result.add(Messages.getString("FromOpenXliff2.1"));
                return result;
            }

            Map<String, SegmentData> segments = new HashMap<>();
            collectSegments(xliffRoot, segments);

            mergeSkeleton(skeleton.getRootElement(), segments);

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

            Indenter.indent(skeleton.getRootElement(), 2);
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                XMLOutputter outputter = new XMLOutputter();
                outputter.preserveSpace(true);
                outputter.output(skeleton, out);
            }
            result.add(Constants.SUCCESS);
        } catch (SAXException | IOException | ParserConfigurationException | URISyntaxException e) {
            Logger logger = System.getLogger(FromOpenXliff2.class.getName());
            logger.log(Level.ERROR, Messages.getString("FromOpenXliff2.2"), e);
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }

    private static void collectSegments(Element root, Map<String, SegmentData> segments) {
        if ("unit".equals(root.getName())) {
            Map<String, String> originalData = new HashMap<>();
            Element originalDataEl = root.getChild("originalData");
            if (originalDataEl != null) {
                for (Element data : originalDataEl.getChildren("data")) {
                    originalData.put(data.getAttributeValue("id"), data.getText());
                }
            }
            for (Element segment : root.getChildren("segment")) {
                List<PI> pis = segment.getPI(Constants.TOOLID);
                if (!pis.isEmpty()) {
                    String key = pis.get(0).getData();
                    SegmentData data = new SegmentData();
                    data.state = segment.getAttributeValue("state", "initial");
                    Element target = segment.getChild("target");
                    if (target != null) {
                        data.targetContent = restoreTarget(target, originalData);
                        data.targetSpace = target.getAttributeValue("xml:space", "default");
                        data.hasTarget = true;
                    }
                    segments.put(key, data);
                }
            }
            return;
        }
        for (Element child : root.getChildren()) {
            collectSegments(child, segments);
        }
    }

    private static List<XMLNode> restoreTarget(Element target, Map<String, String> originalData) {
        String xml = "<target>" + substituteTags(target.getContent(), originalData) + "</target>";
        try {
            SAXBuilder builder = new SAXBuilder();
            Document doc = builder.build(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            return doc.getRootElement().getContent();
        } catch (SAXException | IOException | ParserConfigurationException e) {
            return cloneContent(target.getContent());
        }
    }

    private static String substituteTags(List<XMLNode> content, Map<String, String> originalData) {
        StringBuilder result = new StringBuilder();
        for (XMLNode node : content) {
            if (node.getNodeType() != XMLNode.ELEMENT_NODE) {
                result.append(node.toString());
                continue;
            }
            Element e = (Element) node;
            String raw = "ph".equals(e.getName()) ? originalData.get(e.getAttributeValue("id")) : null;
            if (raw != null) {
                result.append(raw);
                continue;
            }
            result.append('<').append(e.getName());
            for (Attribute a : e.getAttributes()) {
                result.append(' ').append(a.toString());
            }
            if (e.getContent().isEmpty()) {
                result.append("/>");
            } else {
                result.append('>').append(substituteTags(e.getContent(), originalData));
                result.append("</").append(e.getName()).append('>');
            }
        }
        return result.toString();
    }

    private static void mergeSkeleton(Element root, Map<String, SegmentData> segments) {
        if ("segment".equals(root.getName()) || "ignorable".equals(root.getName())) {
            List<PI> pis = root.getPI(Constants.TOOLID);
            if (!pis.isEmpty()) {
                String key = pis.get(0).getData();
                SegmentData data = segments.get(key);
                if (data != null && data.hasTarget) {
                    Element target = root.getChild("target");
                    if (target == null) {
                        target = new Element("target");
                        root.addContent(target);
                    }
                    target.setContent(cloneContent(data.targetContent));
                    if ("preserve".equals(data.targetSpace)) {
                        target.setAttribute("xml:space", "preserve");
                    }
                    if ("final".equals(data.state)) {
                        root.setAttribute("state", "final");
                    } else if (!"initial".equals(root.getAttributeValue("state", "initial"))) {
                        root.setAttribute("state", data.state);
                    }
                } else if (root.getChild("target") != null) {
                    root.removeChild("target");
                }
                root.removePI(Constants.TOOLID);
            }
            return;
        }
        for (Element child : root.getChildren()) {
            mergeSkeleton(child, segments);
        }
    }

    private static List<XMLNode> cloneContent(List<XMLNode> content) {
        List<XMLNode> result = new Vector<>();
        for (XMLNode node : content) {
            if (node.getNodeType() == XMLNode.TEXT_NODE) {
                result.add(node);
            } else if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
                Element e = (Element) node;
                Element copy = new Element(e.getName());
                copy.clone(e);
                result.add(copy);
            }
        }
        return result;
    }

    private static class SegmentData {
        String state = "initial";
        List<XMLNode> targetContent = new Vector<>();
        String targetSpace = "default";
        boolean hasTarget = false;
    }
}
