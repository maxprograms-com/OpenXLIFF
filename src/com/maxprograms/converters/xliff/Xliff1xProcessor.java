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

import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLUtils;

public class Xliff1xProcessor {

    private static int tag;
    private static boolean preserveSpaces = false;
    private static String currentFile;

    private Xliff1xProcessor() {
        // do not instantiate this class
        // use static methods instead
    }

    public static void processXliff1x(Element root, List<Element> units) {
        tag = 1;
        preserveSpaces = false;
        recurse1x(root, units);
    }

    private static void recurse1x(Element root, List<Element> units) {
        if ("file".equals(root.getName())) {
            currentFile = root.getAttributeValue("original");
        }
        if ("trans-unit".equals(root.getName()) && !root.getAttributeValue("translate").equals("no")) {
            Element segSource = root.getChild("seg-source");
            if (segSource != null) {
                List<XMLNode> content = segSource.getContent();
                Iterator<XMLNode> it = content.iterator();
                while (it.hasNext()) {
                    XMLNode node = it.next();
                    if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
                        Element e = (Element) node;
                        if ("mrk".equals(e.getName()) && "seg".equals(e.getAttributeValue("mtype"))) {
                            Element unit = new Element("trans-unit");
                            unit.setAttribute("id", "" + units.size());
                            unit.setAttribute("approved", root.getAttributeValue("approved", "no"));
                            boolean space = root.getAttributeValue("xml:space").equals("preserve");
                            if (space) {
                                unit.setAttribute("xml:space", "preserve");
                            }
                            Element source = new Element("source");
                            tag = 1;
                            source.setContent(getContent1x(e));
                            if (!hasTranslatableText(source)) {
                                return;
                            }
                            unit.addContent(source);
                            Element target = new Element("target");
                            Element tgt = root.getChild("target");
                            if (tgt != null) {
                                tag = 1;
                                Element mrk = locateMrk(tgt, e.getAttributeValue("mid"));
                                if (mrk != null) {
                                    target.setContent(getContent1x(mrk));
                                }
                            }
                            unit.addContent(target);
                            units.add(unit);
                            e.addContent(new PI(Constants.TOOLID, currentFile + "_" + unit.getAttributeValue("id")));
                        }
                    }
                }
            } else {
                Element unit = new Element("trans-unit");
                unit.setAttribute("id", "" + units.size());
                unit.setAttribute("approved", root.getAttributeValue("approved", "no"));
                boolean space = root.getAttributeValue("xml:space").equals("preserve");
                if (space || preserveSpaces) {
                    unit.setAttribute("xml:space", "preserve");
                }

                Element source = new Element("source");
                tag = 1;
                source.setContent(getContent1x(root.getChild("source")));
                if (!hasTranslatableText(source)) {
                    return;
                }
                unit.addContent(source);
                Element target = new Element("target");
                tag = 1;
                target.setContent(getContent1x(root.getChild("target")));
                unit.addContent(target);

                List<Element> matches = root.getChildren("alt-trans");
                Iterator<Element> it = matches.iterator();
                while (it.hasNext()) {
                    Element match = it.next();
                    Element altTrans = new Element("alt-trans");
                    String origin = match.getAttributeValue("origin");
                    if (!origin.isEmpty()) {
                        altTrans.setAttribute("origin", origin);
                    }
                    String quality = match.getAttributeValue("match-quality");
                    if (!quality.isBlank()) {
                        altTrans.setAttribute("match-quality", quality);
                    }
                    Element altSource = new Element("source");
                    tag = 1;
                    altSource.setContent(getContent1x(match.getChild("source")));
                    altTrans.addContent(altSource);
                    Element altTarget = new Element("target");
                    tag = 1;
                    altTarget.setContent(getContent1x(match.getChild("target")));
                    altTrans.addContent(altTarget);
                    if (!altSource.getContent().isEmpty() && !altTarget.getContent().isEmpty()) {
                        unit.addContent(altTrans);
                    }
                }
                List<Element> notes = root.getChildren("note");
                Iterator<Element> noteIt = notes.iterator();
                while (noteIt.hasNext()) {
                    Element note = noteIt.next();
                    Element n = new Element("note");
                    n.setText(note.getText());
                    if (note.hasAttribute("priority")) {
                        try {
                            Integer.parseInt(note.getAttributeValue("priority"));
                            n.setAttribute("priority", note.getAttributeValue("priority"));
                        } catch (NumberFormatException _) {
                            // ignore invalid priority
                        }
                    }
                    if (note.hasAttribute("annotates")) {
                        String value = note.getAttributeValue("annotates");
                        if ("source".equals(value) || "target".equals(value)) {
                            n.setAttribute("annotates", value);
                        }
                    }
                    unit.addContent(n);
                }
                List<Element> contextGroups = root.getChildren("context-group");
                for (Element contextGroup : contextGroups) {
                    Element group = new Element("context-group");
                    group.clone(contextGroup);
                    unit.addContent(group);
                }   
                units.add(unit);
                root.addContent(new PI(Constants.TOOLID, currentFile + "_" + unit.getAttributeValue("id")));
            }
            return;
        }
        List<Element> children = root.getChildren();
        Iterator<Element> it = children.iterator();
        while (it.hasNext()) {
            recurse1x(it.next(), units);
        }
    }

    private static boolean hasTranslatableText(Element e) {
        if (("source".equals(e.getName()) || "target".equals(e.getName()) || "mrk".equals(e.getName())
                || "pc".equals(e.getName()) || "sub".equals(e.getName())) && XliffUtils.hasText(e)) {
            return true;
        }
        List<Element> children = e.getChildren();
        Iterator<Element> it = children.iterator();
        while (it.hasNext()) {
            if (hasTranslatableText(it.next())) {
                return true;
            }
        }
        return false;
    }

    public static Element locateMrk(Element target, String mid) {
        List<XMLNode> content = target.getContent();
        Iterator<XMLNode> it = content.iterator();
        while (it.hasNext()) {
            XMLNode node = it.next();
            if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
                Element e = (Element) node;
                if ("mrk".equals(e.getName()) && e.getAttributeValue("mid").equals(mid)) {
                    return e;
                }
            }
        }
        return null;
    }

    private static List<XMLNode> getContent1x(Element child) {
        List<XMLNode> result = new Vector<>();
        if (child != null) {
            List<XMLNode> content = child.getContent();
            Iterator<XMLNode> it = content.iterator();
            while (it.hasNext()) {
                XMLNode node = it.next();
                if (node.getNodeType() == XMLNode.TEXT_NODE) {
                    result.add(node);
                }
                if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
                    Element e = (Element) node;
                    String name = e.getName();
                    if ("g".equals(name)) {
                        Element ph1 = new Element("ph");
                        ph1.setAttribute("id", "" + tag++);
                        ph1.setText(getCleanHead(e));
                        result.add(ph1);
                        if (e.getChildren().isEmpty()) {
                            result.add(new TextNode(e.getText()));
                        } else {
                            result.addAll(getContent1x(e));
                        }
                        Element ph2 = new Element("ph");
                        ph2.setAttribute("id", "" + tag++);
                        ph2.setText("</g>");
                        result.add(ph2);
                    }
                    if ("x".equals(name) || "bx".equals(name) || "ex".equals(name) || "ph".equals(name)
                            || "bpt".equals(name) || "ept".equals(name) || "it".equals(name)) {
                        Element ph = new Element("ph");
                        ph.setAttribute("id", "" + tag++);
                        ph.setText(toCleanString(e));
                        result.add(ph);
                    }
                    if ("mrk".equals(name)) {
                        // add <mrk> as is, ignore namespaces
                        result.add(e);
                    }
                }
            }
        }
        return result;
    }

    private static String toCleanString(Element e) {
        StringBuilder sb = new StringBuilder();
        sb.append(getCleanHead(e));
        List<XMLNode> content = e.getContent();
        Iterator<XMLNode> it = content.iterator();
        while (it.hasNext()) {
            XMLNode node = it.next();
            if (node.getNodeType() == XMLNode.TEXT_NODE) {
                TextNode textNode = (TextNode) node;
                sb.append(textNode.getText());
            }
            if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
                Element child = (Element) node;
                sb.append(toCleanString(child));
            }
        }
        sb.append("</").append(e.getName()).append(">");
        return sb.toString();
    }

    private static String getCleanHead(Element e) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(e.getName());
        List<Attribute> atts = e.getAttributes();
        Iterator<Attribute> it = atts.iterator();
        while (it.hasNext()) {
            Attribute a = it.next();
            String name = a.getName();
            if (name.indexOf(":") != -1) {
                name = name.replace(":", "__");
            }
            sb.append(' ');
            sb.append(name);
            sb.append("=\"");
            sb.append(XMLUtils.cleanText(a.getValue()));
            sb.append("\"");
        }
        sb.append(">");
        return sb.toString();
    }
}
