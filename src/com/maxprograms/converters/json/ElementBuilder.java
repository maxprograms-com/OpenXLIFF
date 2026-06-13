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

package com.maxprograms.converters.json;

import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.maxprograms.xml.Element;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;

public class ElementBuilder {

    private static Pattern pattern;
    private static Pattern endPattern;

    private ElementBuilder() {
        // private for security
    }

    public static ElementHolder buildElement(String name, String string, boolean trimTags, boolean mergeTags,
            List<String> htmlIgnore, boolean preserveSpaces) {
        Element element = new Element(name);
        element.setText(string);
        fixHtmlTags(element, mergeTags, htmlIgnore, preserveSpaces);
        String start = "";
        String end = "";
        if (!element.getChildren().isEmpty()) {
            int tagCount = element.getChildren().size();
            List<XMLNode> content = element.getContent();
            if (trimTags && tagCount == 1) {
                if (content.get(0).getNodeType() == XMLNode.ELEMENT_NODE) {
                    Element startTag = (Element) content.get(0);
                    start = startTag.getText();
                    content.remove(startTag);
                    element.setContent(content);
                }
                if (content.size() > 1 && content.get(content.size() - 1).getNodeType() == XMLNode.ELEMENT_NODE) {
                    Element endTag = (Element) content.get(content.size() - 1);
                    end = endTag.getText();
                    content.remove(endTag);
                    element.setContent(content);
                }
            }
            if (trimTags && tagCount == 2 && content.get(0).getNodeType() == XMLNode.ELEMENT_NODE
                    && content.get(content.size() - 1).getNodeType() == XMLNode.ELEMENT_NODE) {
                Element startTag = (Element) content.get(0);
                start = startTag.getText();
                Element endTag = (Element) content.get(content.size() - 1);
                end = endTag.getText();
                content.remove(endTag);
                content.remove(startTag);
                element.setContent(content);
            }
        } else {
            // restore spaces normalized when fixing HTML tags
            element.setText(string);
        }
        return new ElementHolder(element, start, end);
    }

    private static void fixHtmlTags(Element src, boolean mergeTags, List<String> htmlIgnore, boolean preserveSpaces) {
        if (pattern == null) {
            pattern = Pattern.compile("<[A-Za-z0-9]+([\\s]+[A-Za-z\\-\\.]+=[\"|\'][^<&>]*[\"|\'])*[\\s]*/?>");
        }
        if (endPattern == null) {
            endPattern = Pattern.compile("</[A-Za-z0-9]+>");
        }
        int count = 0;
        String e = preserveSpaces ? src.getText() : normalise(src.getText());

        Matcher matcher = pattern.matcher(e);
        if (matcher.find()) {
            List<XMLNode> newContent = new Vector<>();
            List<XMLNode> content = src.getContent();
            for (XMLNode node : content) {
                if (node.getNodeType() == XMLNode.TEXT_NODE) {
                    TextNode t = (TextNode) node;
                    String text = preserveSpaces ? t.getText() : normalise(t.getText());
                    matcher = pattern.matcher(text);
                    if (matcher.find()) {
                        matcher.reset();
                        while (matcher.find()) {
                            int start = matcher.start();
                            int end = matcher.end();

                            String s = text.substring(0, start);
                            if (!s.isEmpty()) {
                                newContent.add(new TextNode(s));
                            }
                            String tag = text.substring(start, end);
                            StringBuilder sb = new StringBuilder();
                            for (int i = 1; i < tag.length(); i++) {
                                char c = tag.charAt(i);
                                if (Character.isLetterOrDigit(c)) {
                                    sb.append(c);
                                } else {
                                    break;
                                }
                            }
                            String tagName = sb.toString();
                            if (htmlIgnore.contains(tagName)) {
                                newContent.add(new TextNode(tag));
                            } else {
                                Element ph = new Element("ph");
                                ph.setAttribute("id", "" + count++);
                                ph.setText(tag);
                                newContent.add(ph);
                            }
                            text = text.substring(end);
                            matcher = pattern.matcher(text);
                        }
                        if (!text.isEmpty()) {
                            newContent.add(new TextNode(text));
                        }
                    } else {
                        if (!((TextNode) node).getText().isEmpty()) {
                            newContent.add(node);
                        }
                    }
                } else {
                    newContent.add(node);
                }
            }
            src.setContent(newContent);
        }
        matcher = endPattern.matcher(e);
        if (matcher.find()) {
            List<XMLNode> newContent = new Vector<>();
            List<XMLNode> content = src.getContent();
            for (XMLNode node : content) {
                if (node.getNodeType() == XMLNode.TEXT_NODE) {
                    TextNode t = (TextNode) node;
                    String text = preserveSpaces ? t.getText() : normalise(t.getText());
                    matcher = endPattern.matcher(text);
                    if (matcher.find()) {
                        matcher.reset();
                        while (matcher.find()) {
                            int start = matcher.start();
                            int end = matcher.end();

                            String s = text.substring(0, start);
                            if (!s.isEmpty()) {
                                newContent.add(new TextNode(s));
                            }

                            String tag = text.substring(start, end);
                            Element ph = new Element("ph");
                            ph.setAttribute("id", "" + count++);
                            ph.setText(tag);
                            newContent.add(ph);

                            text = text.substring(end);
                            matcher = endPattern.matcher(text);
                        }
                        if (!text.isEmpty()) {
                            newContent.add(new TextNode(text));
                        }
                    } else {
                        if (!((TextNode) node).getText().isEmpty()) {
                            newContent.add(node);
                        }
                    }
                } else {
                    newContent.add(node);
                }
            }
            src.setContent(newContent);
        }
        if (mergeTags && src.getChildren().size() > 1) {
            mergeTags(src);
        }
    }

    private static void mergeTags(Element src) {
        int previous = -1;
        List<XMLNode> newContent = new Vector<>();
        List<XMLNode> content = src.getContent();
        for (int i = 0; i < content.size(); i++) {
            XMLNode node = content.get(i);
            int type = node.getNodeType();
            if (type == XMLNode.ELEMENT_NODE && type == previous) {
                Element previousTag = (Element) newContent.get(newContent.size() - 1);
                Element currentTag = (Element) node;
                List<XMLNode> tagContent = currentTag.getContent();
                for (XMLNode n : tagContent) {
                    previousTag.addContent(n);
                }
            } else {
                newContent.add(node);
                previous = node.getNodeType();
            }
        }
        src.setContent(newContent);
    }

    private static String normalise(String string) {
        String result = string;
        result = result.replace('\n', ' ');
        result = result.replaceAll("\\s(\\s)+", " ");
        return result;
    }
}
