/*******************************************************************************
 * Copyright (c) 2018 - 2026 Maxprograms.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors: Maxprograms - initial API and implementation
 *******************************************************************************/
package com.maxprograms.converters.xliff;

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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.Attribute;
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

public class FromOpenXliff {

    private static Catalog catalog;
    private static Document skeleton;
    private static Map<String, Element> segments;
    private static String tgtLang;
    private static boolean hasTarget;
    private static int auto;
    private static String currentFile;
    private static Map<String, Element> fileMetadata;
    private static Map<String, Element> unitMetadata;
    private static int skeletonFileIndex;

    private FromOpenXliff() {
        // do not instantiate this class
        // use run method instead
    }

    public static List<String> run(Map<String, String> params) {
        List<String> result = new ArrayList<>();
        tgtLang = "";
        String xliffFile = params.get("xliff");
        String sklFile = params.get("skeleton");
        String outputFile = params.get("backfile");
        fileMetadata = new Hashtable<>();
        unitMetadata = new Hashtable<>();
        try {
            catalog = CatalogBuilder.getCatalog(params.get("catalog"));
            loadXliff(xliffFile);
            loadSkeleton(sklFile);
            Element root = skeleton.getRootElement();
            recurseSkeleton(root);
            if (root.getAttributeValue("version").startsWith("1")) {
                restoreAttributes(root);
            }
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
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                XMLOutputter outputter = new XMLOutputter();
                Indenter.indent(root, 2);
                outputter.preserveSpace(true);
                outputter.output(skeleton, out);
            }
            result.add(Constants.SUCCESS);
        } catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
            Logger logger = System.getLogger(FromOpenXliff.class.getName());
            logger.log(Level.ERROR, Messages.getString("FromOpenXliff.1"), e);
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }

    private static void recurseSkeleton(Element rootElement)
            throws SAXException, IOException, ParserConfigurationException {
        String version = rootElement.getAttributeValue("version");
        if (version.startsWith("1")) {
            skeletonFileIndex = 0;
            recurse1x(rootElement);
        }
        if (version.startsWith("2")) {
            tgtLang = rootElement.getAttributeValue("trgLang");
            recurse2x(rootElement);
            if (hasTarget && rootElement.getAttributeValue("trgLang").isEmpty()) {
                rootElement.setAttribute("trgLang", tgtLang);
            }
        }
    }

    private static void recurse1x(Element root) throws SAXException, IOException, ParserConfigurationException {
        if ("file".equals(root.getName())) {
            skeletonFileIndex++;
            tgtLang = root.getAttributeValue("target-language");
            String fileKey = String.valueOf(skeletonFileIndex);
            if (fileMetadata.containsKey(fileKey)) {
                root.addContent(fileMetadata.get(fileKey));
            }
        }
        if ("trans-unit".equals(root.getName()) && !root.getAttributeValue("translate").equals("no")) {
            Element segSource = root.getChild("seg-source");
            if (segSource != null) {
                Element target = root.getChild("target");
                if (target == null) {
                    addtarget(root);
                    target = root.getChild("target");
                }
                if (target.getContent().isEmpty()) {
                    Element t = new Element("target");
                    t.clone(segSource);
                    target.setContent(t.getContent());
                }
                List<XMLNode> content = segSource.getContent();
                for (XMLNode node : content) {
                    if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
                        Element e = (Element) node;
                        if ("mrk".equals(e.getName()) && "seg".equals(e.getAttributeValue("mtype"))) {
                            String pi = e.getPI(Constants.TOOLID).get(0).getData();
                            Element segment = segments.get(pi);
                            if (segment.getAttributeValue("approved").equals("yes")) {
                                Element mrk = Xliff1xProcessor.locateMrk(target, e.getAttributeValue("mid"));
                                mrk.setContent(segment.getChild("target").getContent());
                                if (!mrk.getChildren().isEmpty()) {
                                    replaceTags(mrk, 1);
                                }
                            }
                            e.removePI(Constants.TOOLID);
                        }
                    }
                }
            } else {
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
                        target.clone(segment.getChild("target"));
                        if (!target.getContent().isEmpty() && ("new".equals(target.getAttributeValue("state"))
                                || "needs-translation".equals(target.getAttributeValue("state")))) {
                            target.setAttribute("state", "translated");
                        }
                        if (!target.getChildren().isEmpty()) {
                            replaceTags(target, 1);
                        }
                        root.setAttribute("approved", "yes");
                    }
                    root.removePI(Constants.TOOLID);
                }
            }
            return;
        }

        List<Element> children = root.getChildren();
        for (Element child : children) {
            recurse1x(child);
        }
    }

    private static void replaceTags(Element target, int version)
            throws SAXException, IOException, ParserConfigurationException {
        StringBuilder sb = new StringBuilder();
        sb.append("<target>");
        List<XMLNode> content = target.getContent();
        auto = 1;
        for (XMLNode node : content) {
            if (node.getNodeType() == XMLNode.TEXT_NODE) {
                TextNode text = (TextNode) node;
                sb.append(XMLUtils.cleanText(text.getText()));
            }
            if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
                Element e = (Element) node;
                if ("mrk".equals(e.getName())) {
                    if (version == 1) {
                        sb.append(e.toString());
                    } else {
                        Element mrk = processMrk(e);
                        sb.append(mrk.toString());
                    }
                } else {
                    sb.append(e.getText());
                }
            }
        }
        sb.append("</target>");
        SAXBuilder builder = new SAXBuilder();
        String string = sb.toString();
        Document d = builder.build(new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8)));
        target.setContent(d.getRootElement().getContent());
    }

    private static Element processMrk(Element e) {
        Element mrk = new Element("mrk");
        mrk.setAttribute("id", e.hasAttribute("mid") ? e.getAttributeValue("mid") : ("auto" + auto++));
        if (e.hasAttribute("ts")) {
            mrk.setAttribute("value", e.getAttributeValue("ts"));
        }
        String mtype = e.getAttributeValue("mtype");
        if ("protected".equals(mtype)) {
            mrk.setAttribute("translate", "no");
        }
        if (List.of("generic", "comment", "term").contains(mtype)) {
            mrk.setAttribute("type", mtype);
        } else {
            mrk.setAttribute("type", "oxlf:" + mtype.replace(":", "_"));
        }
        List<XMLNode> newContent = new Vector<>();
        List<XMLNode> content = e.getContent();
        for (XMLNode node : content) {
            if (node.getNodeType() == XMLNode.TEXT_NODE) {
                newContent.add(node);
            }
            if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
                Element child = (Element) node;
                if ("mrk".equals(child.getName())) {
                    Element processed = processMrk(child);
                    newContent.add(processed);
                } else {
                    String text = child.getText();
                    newContent.add(new TextNode(text));
                }
            }
        }
        mrk.setContent(newContent);
        return mrk;
    }

    private static void addtarget(Element root) {
        boolean hasSegSource = root.getChild("seg-source") != null;
        List<XMLNode> newContent = new Vector<>();
        List<XMLNode> content = root.getContent();
        for (XMLNode node : content) {
            newContent.add(node);
            if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
                Element e = (Element) node;
                if (!hasSegSource && e.getName().equals("source")) {
                    newContent.add(new TextNode("\n      "));
                    newContent.add(new Element("target"));
                }
                if (hasSegSource && e.getName().equals("seg-source")) {
                    newContent.add(new Element("target"));
                }
            }
        }
        root.setContent(newContent);
    }

    private static void recurse2x(Element root) throws SAXException, IOException, ParserConfigurationException {
        if ("file".equals(root.getName())) {
            currentFile = root.getAttributeValue("id");
            Element sklMetadata = root.getChild("mda:metadata");
            Element xliffMetadata = fileMetadata.get(currentFile);
            if (sklMetadata != null && xliffMetadata == null) {
                // remove old metadata
                root.removeChild(sklMetadata);
            } else if (sklMetadata == null && xliffMetadata != null) {
                Element metadata = new Element("mda:metadata");
                metadata.clone(xliffMetadata);
                List<XMLNode> content = root.getContent();
                content.add(0, metadata);
                root.setContent(content);
            } else if (sklMetadata != null && xliffMetadata != null) {
                sklMetadata.clone(xliffMetadata);
            }
        }
        if ("unit".equals(root.getName()) && !root.getAttributeValue("translate").equals("no")) {
            Element sklMetadata = root.getChild("mda:metadata");
            Element xliffMetadata = null;
            List<Element> children = root.getChildren("segment");
            if (!children.isEmpty()) {
                List<PI> toolId = children.get(0).getPI(Constants.TOOLID);
                if (!toolId.isEmpty()) {
                    String id = toolId.get(0).getData();
                    xliffMetadata = unitMetadata.get(id);
                }
            }
            if (sklMetadata != null && xliffMetadata == null) {
                root.removeChild(sklMetadata);
            } else if (sklMetadata == null && xliffMetadata != null) {
                Element metadata = new Element("mda:metadata");
                metadata.clone(xliffMetadata);
                List<XMLNode> content = root.getContent();
                content.add(0, metadata);
                root.setContent(content);
            } else if (sklMetadata != null && xliffMetadata != null) {
                sklMetadata.clone(xliffMetadata);
            }
            for (Element seg : children) {
                List<PI> list = seg.getPI(Constants.TOOLID);
                if (!list.isEmpty()) {
                    String pi = list.get(0).getData();
                    Element segment = segments.get(pi);
                    if (segment.getAttributeValue("approved").equals("yes")) {
                        Element target = seg.getChild("target");
                        if (target == null) {
                            addtarget(seg);
                            target = seg.getChild("target");
                        }
                        target.setContent(segment.getChild("target").getContent());
                        if (seg.getChild("source").getAttributeValue("xml:space").equals("preserve")) {
                            target.setAttribute("xml:space", "preserve");
                        }
                        if (!target.getChildren().isEmpty()) {
                            replaceTags(target, 2);
                        }
                        seg.setAttribute("state", "final");
                    }
                    if (!hasTarget && segment.getChild("target") != null) {
                        hasTarget = true;
                    }
                    seg.removePI(Constants.TOOLID);
                }
            }
        }
        List<Element> children = root.getChildren();
        for (Element child : children) {
            recurse2x(child);
        }
    }

    private static void loadXliff(String xliffFile) throws SAXException, IOException, ParserConfigurationException {
        SAXBuilder builder = new SAXBuilder();
        builder.setEntityResolver(catalog);
        Document xliff = builder.build(xliffFile);
        segments = new Hashtable<>();
        recurseXliff(xliff.getRootElement());
    }

    private static void recurseXliff(Element e) throws IOException {
        if ("xliff".equals(e.getName()) && !"1.2".equals(e.getAttributeValue("version"))) {
            throw new IOException(Messages.getString("FromOpenXliff.2"));
        }
        if ("file".equals(e.getName())) {
            List<PI> pi = e.getPI("ts");
            if (!pi.isEmpty()) {
                String json = pi.get(0).getData();
                if (json != null && !json.isEmpty()) {
                    JSONObject obj = new JSONObject(json);
                    if (obj.has("id")) {
                        // original was XLIFF 2.x
                        currentFile = obj.getString("id");
                    } else if (obj.has("fileId")) {
                        // original was XLIFF 1.x, new scheme (6.0+)
                        currentFile = obj.getString("fileId");
                    } else if (obj.has("original")) {
                        // original was XLIFF 1.x, old scheme
                        currentFile = obj.getString("original");
                    }
                }
            } else if (e.hasAttribute("ts")) {
                String json = e.getAttributeValue("ts");
                if (json != null && !json.isEmpty()) {
                    JSONObject obj = new JSONObject(json);
                    if (obj.has("id")) {
                        // original was XLIFF 2.x
                        currentFile = obj.getString("id");
                    } else if (obj.has("fileId")) {
                        // original was XLIFF 1.x, new scheme (6.0+)
                        currentFile = obj.getString("fileId");
                    } else if (obj.has("original")) {
                        // original was XLIFF 1.x, old scheme
                        currentFile = obj.getString("original");
                    }
                }
            }
            if (tgtLang.isEmpty()) {
                tgtLang = e.getAttributeValue("target-language");
            }
            List<PI> metadata = e.getPI("metadata");
            if (!metadata.isEmpty()) {
                fileMetadata.put(currentFile, toMetadata(metadata.get(0).getData()));
            }
        }
        if ("trans-unit".equals(e.getName())) {
            String id = e.getAttributeValue("id");
            segments.put(currentFile + "_" + id, e);
            List<PI> metadata = e.getPI("metadata");
            if (!metadata.isEmpty()) {
                String data = metadata.get(0).getData();
                Element unitData = toMetadata(data);
                unitMetadata.put(currentFile + "_" + id, unitData);
            }
            return;
        }
        List<Element> children = e.getChildren();
        for (Element child : children) {
            recurseXliff(child);
        }
    }

    private static Element toMetadata(String string) {
        Element metadata = new Element("mda:metadata");
        try {
            JSONObject json = new JSONObject(string);
            if (json.has("data")) {
                JSONArray groups = json.getJSONArray("data");
                for (int i = 0; i < groups.length(); i++) {
                    JSONObject group = groups.getJSONObject(i);
                    Element g = new Element("mda:metaGroup");
                    metadata.addContent(g);
                    if (group.has("id")) {
                        g.setAttribute("id", group.getString("id"));
                    }
                    if (group.has("category")) {
                        g.setAttribute("category", group.getString("category"));
                    }
                    if (group.has("appliesTo")) {
                        g.setAttribute("appliesTo", group.getString("appliesTo"));
                    }
                    if (group.has("meta")) {
                        JSONArray metas = group.getJSONArray("meta");
                        for (int j = 0; j < metas.length(); j++) {
                            JSONObject meta = metas.getJSONObject(j);
                            Element m = new Element("mda:meta");
                            g.addContent(m);
                            if (meta.has("type")) {
                                m.setAttribute("type", meta.getString("type"));
                            }
                            if (meta.has("value")) {
                                m.setText(meta.getString("value"));
                            }
                        }
                    }
                }
            }
        } catch (JSONException ex) {
            Logger logger = System.getLogger(FromOpenXliff.class.getName());
            logger.log(Level.ERROR, Messages.getString("FromOpenXliff.3"), ex);
        }
        return metadata;
    }

    private static void loadSkeleton(String sklFile) throws SAXException, IOException, ParserConfigurationException {
        SAXBuilder builder = new SAXBuilder();
        builder.setEntityResolver(catalog);
        skeleton = builder.build(sklFile);
    }

    public static void restoreAttributes(Element e) {
        List<Attribute> atts = e.getAttributes();
        Vector<String> change = new Vector<>();
        for (Attribute a : atts) {
            if (a.getName().indexOf("__") != -1) {
                change.add(a.getName());
            }
        }
        for (int i = 0; i < change.size(); i++) {
            String name = change.get(i);
            Attribute a = e.getAttribute(name);
            e.setAttribute(name.replace("__", ":"), a.getValue());
            e.removeAttribute(name);
        }
        List<Element> children = e.getChildren();
        for (Element child : children) {
            restoreAttributes(child);
        }
    }
}