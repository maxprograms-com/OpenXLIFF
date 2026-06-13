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

import java.util.List;
import java.util.Vector;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.XMLNode;

public class Xliff2xProcessor {

    private static int tag;
    private static List<String[]> sourcetags;
    private static String currentFile;

    private Xliff2xProcessor() {
        // do not instantiate this class
        // use static methods instead
    }

    public static void processXliff2x(Element root, List<Element> units) {
        tag = 1;
        sourcetags = new Vector<>();
        recurse2x(root, units);
    }

    public static boolean hasTarget(List<Element> units) {
        for (Element unit : units) {
            if (unit.getChild("target") != null) {
                return true;
            }
        }
        return false;
    }

    private static void recurse2x(Element root, List<Element> units) {
        if ("file".equals(root.getName())) {
            currentFile = root.getAttributeValue("id");
        }
        if ("unit".equals(root.getName()) && !root.getAttributeValue("translate").equals("no")) {
            boolean preserve = root.getAttributeValue("xml:space").equals("preserve");
            List<Element> segments = root.getChildren("segment");
            for (Element segment : segments) {
                boolean isFinal = segment.getAttributeValue("state").equals("final");
                Element unit = new Element("trans-unit");
                unit.setAttribute("id", "" + units.size());
                if (isFinal) {
                    unit.setAttribute("approved", "yes");
                }
                Element src = segment.getChild("source");
                if (preserve || src.getAttributeValue("xml:space").equals("preserve")) {
                    unit.setAttribute("xml:space", "preserve");
                }
                Element source = new Element("source");
                tag = 1;
                sourcetags = new Vector<>();
                source.setContent(getContent2x(src, true));
                if (!hasTranslatableText(source)) {
                    return;
                }
                unit.addContent(source);
                List<XMLNode> targetContent = getContent2x(segment.getChild("target"), false);
                if (!targetContent.isEmpty()) {
                    Element target = new Element("target");
                    target.setContent(targetContent);
                    unit.addContent(target);
                }                
                if (segments.size() == 1) {
                    Element notes = root.getChild("notes");
                    if (notes != null) {
                        List<Element> noteList = notes.getChildren("note");
                        for (Element note : noteList) {
                            Element n = new Element("note");
                            n.setText(note.getText());
                            if (note.hasAttribute("priority")) {
                                n.setAttribute("priority", note.getAttributeValue("priority"));
                            }
                            if (note.hasAttribute("annotates")) {
                                String value = note.getAttributeValue("annotates");
                                if ("source".equals(value) || "target".equals(value)) {
                                    n.setAttribute("appliesTo", value);
                                }
                            }
                            unit.addContent(n);
                        }
                    }
                }
                Element matchesHolder = root.getChild("mtc:matches");
                if (matchesHolder != null) {
                    List<Element> matches = matchesHolder.getChildren("mtc:match");
                    for (Element match : matches) {
                        String ref = match.getAttributeValue("ref");
                        if (ref.equals("#" + segment.getAttributeValue("id"))) {
                            Element altTrans = new Element("alt-trans");
                            String origin = match.getAttributeValue("origin");
                            if (!origin.isEmpty()) {
                                altTrans.setAttribute("origin", origin);
                            }
                            String quality = match.getAttributeValue("matchQuality");
                            if (!quality.isBlank()) {
                                altTrans.setAttribute("match-quality", quality);
                            }
                            Element altSource = new Element("source");
                            tag = 1;
                            sourcetags = new Vector<>();
                            altSource.setContent(getContent2x(match.getChild("source"), true));
                            altTrans.addContent(altSource);
                            Element altTarget = new Element("target");
                            altTarget.setContent(getContent2x(match.getChild("target"), false));
                            altTrans.addContent(altTarget);
                            if (!altSource.getContent().isEmpty() && !altTarget.getContent().isEmpty()) {
                                unit.addContent(altTrans);
                            }
                        }
                    }
                }
                Element metadata = root.getChild("mda:metadata");
                if (metadata != null) {
                    unit.addContent(new PI("metadata", metadata.toString()));
                }
                units.add(unit);
                segment.addContent(new PI(Constants.TOOLID, currentFile + "_" + unit.getAttributeValue("id")));
            }
            return;
        }
        List<Element> children = root.getChildren();
        for (Element child : children) {
            recurse2x(child, units);
        }
    }

    private static List<XMLNode> getContent2x(Element child, boolean inSource) {
        List<XMLNode> result = new Vector<>();
        if (child != null) {
            List<XMLNode> content = child.getContent();
            for (XMLNode node : content) {
                if (node.getNodeType() == XMLNode.TEXT_NODE) {
                    result.add(node);
                }
                if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
                    Element e = (Element) node;
                    String name = e.getName();
                    if ("pc".equals(name)) {
                        String head = XliffUtils.getHead(e);
                        Element ph1 = new Element("ph");
                        ph1.setText(head);
                        if (inSource) {
                            ph1.setAttribute("id", "" + tag++);
                            sourcetags.add(new String[] { head, "" + (tag - 1) });
                        } else {
                            int i = findFirstTag(head);
                            if (i != -1) {
                                ph1.setAttribute("id", "" + i);
                                sourcetags.remove(new String[] { head, "" + i });
                            } else {
                                ph1.setAttribute("id", "" + tag++);
                            }
                        }
                        result.add(ph1);

                        List<XMLNode> nested = getContent2x(e, inSource);
                        result.addAll(nested);

                        Element ph2 = new Element("ph");
                        ph2.setText("</pc>");
                        if (inSource) {
                            ph2.setAttribute("id", "" + tag++);
                            sourcetags.add(new String[] { head + "<tail/>", "" + (tag - 1) });
                        } else {
                            int i = findFirstTag(head + "<tail/>");
                            if (i != -1) {
                                ph2.setAttribute("id", "" + i);
                                sourcetags.remove(new String[] { head + "<tail/>", "" + i });
                            } else {
                                ph2.setAttribute("id", "" + tag++);
                            }
                        }
                        result.add(ph2);
                    }
                    if ("cp".equals(name)) {
                        Element ph = new Element("ph");
                        String text = e.toString();
                        ph.setText(text);
                        if (inSource) {
                            ph.setAttribute("id", "" + tag++);
                            sourcetags.add(new String[] { text, "" + (tag - 1) });
                        } else {
                            int i = findFirstTag(e.toString());
                            if (i != -1) {
                                ph.setAttribute("id", "" + i);
                                sourcetags.remove(new String[] { text, "" + i });
                            } else {
                                ph.setAttribute("id", "" + tag++);
                            }
                        }
                        result.add(ph);
                    }
                    if ("ph".equals(name) || "sc".equals(name) || "sm".equals(name)) {
                        Element ph = new Element("ph");
                        String text = e.toString();
                        ph.setText(text);
                        if (inSource) {
                            ph.setAttribute("id", "" + tag++);
                            sourcetags.add(new String[] { text, "" + (tag - 1) });
                        } else {
                            int i = findFirstTag(e.toString());
                            if (i != -1) {
                                ph.setAttribute("id", "" + i);
                                sourcetags.remove(new String[] { text, "" + i });
                            } else {
                                ph.setAttribute("id", "" + tag++);
                            }
                        }
                        result.add(ph);
                    }
                    if ("ec".equals(name)) {
                        Element ph = new Element("ph");
                        String text = e.toString();
                        ph.setText(text);
                        if (inSource) {
                            ph.setAttribute("id", "" + tag++);
                            sourcetags.add(new String[] { text, "" + (tag - 1) });
                        } else {
                            int i = findFirstTag(e.toString());
                            if (i != -1) {
                                ph.setAttribute("id", "" + i);
                                sourcetags.remove(new String[] { text, "" + i });
                            } else {
                                ph.setAttribute("id", "" + tag++);
                            }
                        }
                        result.add(ph);
                    }
                    if ("em".equals(name)) {
                        Element ph = new Element("ph");
                        String text = e.toString();
                        ph.setText(text);
                        if (inSource) {
                            ph.setAttribute("id", "" + tag++);
                            sourcetags.add(new String[] { text, "" + (tag - 1) });
                        } else {
                            int i = findFirstTag(e.toString());
                            if (i != -1) {
                                ph.setAttribute("id", "" + i);
                                sourcetags.remove(new String[] { text, "" + i });
                            } else {
                                ph.setAttribute("id", "" + tag++);
                            }
                        }
                        result.add(ph);
                    }
                    if ("mrk".equals(name)) {
                        Element mrk = new Element("mrk");
                        result.add(mrk);
                        boolean translate = e.getAttributeValue("translate", "yes").equals("yes");
                        if (!translate) {
                            mrk.setAttribute("mtype", "protected");
                        } else {
                            String type = e.getAttributeValue("type");
                            if (type.startsWith("oxlf:")) {
                                mrk.setAttribute("mtype", type.substring(5).replace("_", ":"));
                            } else {
                                mrk.setAttribute("mtype", "x-other");
                            }
                        }
                        if (e.hasAttribute("value")) {
                            mrk.setAttribute("ts", e.getAttributeValue("value"));
                        }
                        List<XMLNode> nested = getContent2x(e, inSource);
                        mrk.setContent(nested);
                    }
                }
            }
        }
        return result;
    }

    private static int findFirstTag(String text) {
        for (String[] pair : sourcetags) {
            if (pair[0].equals(text)) {
                return Integer.parseInt(pair[1]);
            }
        }
        return -1;
    }

    private static boolean hasTranslatableText(Element e) {
        if (("source".equals(e.getName()) || "target".equals(e.getName()) || "mrk".equals(e.getName())
                || "pc".equals(e.getName()) || "sub".equals(e.getName())) && XliffUtils.hasText(e)) {
            return true;
        }
        List<Element> children = e.getChildren();
        for (Element child : children) {
            if (hasTranslatableText(child)) {
                return true;
            }
        }
        return false;
    }
}
