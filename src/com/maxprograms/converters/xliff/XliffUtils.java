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

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.maxprograms.converters.Utils;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;

public class XliffUtils {

    private XliffUtils() {
        // do not instantiate this class
    }

    public static void main(String[] args) {
        String[] params = Utils.fixPath(args);
        String file = "";
        for (int i = 0; i < params.length; i++) {
            if ("-lang".equals(params[i]) && i + 1 < params.length) {
                Locale.setDefault(Locale.forLanguageTag(params[i + 1]));
            }
            if ("-file".equals(params[i]) && i + 1 < params.length) {
                file = params[i + 1];
            }
        }
        if (file.isEmpty()) {
            System.out.println(Messages.getString("XliffUtils.3"));
            return;
        }
        try {
            JSONObject json = getXliffLanguages(new File(file));
            System.out.println(json.toString(2));
        } catch (IOException | SAXException | ParserConfigurationException e) {
            Logger logger = System.getLogger(XliffUtils.class.getName());
            logger.log(Level.ERROR, e);
        }
    }

    public static String getHead(Element e) {
        StringBuilder builder = new StringBuilder();
        builder.append('<');
        builder.append(e.getName());
        List<Attribute> atts = e.getAttributes();
        for (Attribute a : atts) {
            builder.append(' ');
            builder.append(a.toString());
        }
        builder.append('>');
        return builder.toString();
    }

    public static boolean hasText(Element e) {
        List<XMLNode> nodes = e.getContent();
        for (XMLNode node : nodes) {
            if (node.getNodeType() == XMLNode.TEXT_NODE) {
                TextNode t = (TextNode) node;
                String text = t.getText();
                if (text != null) {
                    for (int i = 0; i < text.length(); i++) {
                        char c = text.charAt(i);
                        if (!(Character.isSpaceChar(c) || c == '\n')) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static JSONObject getXliffLanguages(File file)
            throws SAXException, IOException, ParserConfigurationException, JSONException {
        SAXBuilder builder = new SAXBuilder();
        Document doc = builder.build(file);
        Element root = doc.getRootElement();
        if (!"xliff".equals(root.getName())) {
            throw new IOException(Messages.getString("XliffUtils.1"));
        }
        JSONObject result = new JSONObject();
        if (root.getAttributeValue("version").startsWith("1.")) {
            Element firstFile = root.getChild("file");
            result.put("srcLang", firstFile.getAttributeValue("source-language"));
            result.put("tgtLang", firstFile.getAttributeValue("target-language"));
        } else if (root.getAttributeValue("version").startsWith("2.")) {
            result.put("srcLang", root.getAttributeValue("srcLang"));
            result.put("tgtLang", root.getAttributeValue("trgLang"));
        } else {
            throw new IOException(Messages.getString("XliffUtils.2"));
        }
        return result;
    }
}
