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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLUtils;

public class Xliff2json {

    private static Map<String, Element> segments;
    private static String encoding;
    private static boolean escaped;
    private static boolean exportHTML;
    private static Map<String, String> foundEntities;
    private static List<String[]> entities;

    private Xliff2json() {
        // do not instantiate this class
        // use run method instead
    }

    public static List<String> run(Map<String, String> params) {
        List<String> result = new ArrayList<>();
        String sklFile = params.get("skeleton");
        String xliffFile = params.get("xliff");
        String catalogFile = params.get("catalog");
        String outputFile = params.get("backfile");
        foundEntities = new Hashtable<>();

        try {
            Catalog catalog = CatalogBuilder.getCatalog(catalogFile);
            loadSegments(xliffFile, catalog);
            Object json = Json2Xliff.loadFile(sklFile, encoding);
            if (json instanceof JSONObject obj) {
                parseJson(obj);
            } else {
                parseArray((JSONArray) json);
            }

            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                if (json instanceof JSONObject obj) {
                    out.write(obj.toString(2).getBytes(StandardCharsets.UTF_8));
                } else {
                    out.write(((JSONArray) json).toString(2).getBytes(StandardCharsets.UTF_8));
                }
            }
            if (exportHTML) {
                File output = new File(outputFile);
                String content = Files.readString(output.toPath(), StandardCharsets.UTF_8);
                content = content.replace("<\\/", "</");
                if (!foundEntities.isEmpty()) {
                    for (Map.Entry<String, String> entry : foundEntities.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        content = content.replace(value, key);
                    }
                }
                content = replaceUnicodeEntities(content);
                Files.write(output.toPath(), content.getBytes(StandardCharsets.UTF_8));
            }
            result.add(Constants.SUCCESS);
        } catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
            Logger logger = System.getLogger(Xliff2json.class.getName());
            logger.log(Level.ERROR, Messages.getString("Xliff2json.1"), e);
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }

    private static String replaceUnicodeEntities(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String hexString = matcher.group(1);
            int codePoint = Integer.parseInt(hexString, 16);
            char unicodeChar = (char) codePoint;
            matcher.appendReplacement(result, Character.toString(unicodeChar));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static void loadSegments(String xliffFile, Catalog catalog)
            throws SAXException, IOException, ParserConfigurationException {
        SAXBuilder builder = new SAXBuilder();
        builder.setEntityResolver(catalog);

        Document doc = builder.build(xliffFile);
        Element root = doc.getRootElement();
        Element file = root.getChild("file");

        escaped = !file.getPI("escaped").isEmpty();
        exportHTML = !file.getPI("exportHTML").isEmpty();
        if (!file.getPI("entities").isEmpty()) {
            String list = file.getPI("entities").get(0).getData();
            String[] pairs = list.split(",");
            for (String pair : pairs) {
                String[] parts = pair.split("\\|");
                if (parts.length == 2) {
                    foundEntities.put(parts[0], parts[1]);
                } else {
                    MessageFormat mf = new MessageFormat(Messages.getString("Xliff2json.0"));
                    throw new IOException(mf.format(new String[] { pair }));
                }
            }
        }
        entities = escaped ? Json2Xliff.loadEntities(catalog) : new ArrayList<>();
        if (!entities.isEmpty()) {
            entities.add(0, new String[] { "&amp;", "&" });
            entities.add(new String[] { "&lt;", "<" });
        }

        List<PI> encodings = file.getPI("encoding");
        if (encodings.isEmpty()) {
            throw new IOException(Messages.getString("Xliff2json.2"));
        }
        encoding = encodings.get(0).getData();
        Element body = file.getChild("body");
        List<Element> units = body.getChildren("trans-unit");

        segments = new HashMap<>();

        for (Element unit : units) {
            segments.put(unit.getAttributeValue("id"), unit);
        }
    }

    private static void parseJson(JSONObject json) throws IOException {
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object obj = json.get(key);
            if (obj instanceof JSONObject) {
                parseJson(json.getJSONObject(key));
            } else if (obj instanceof String) {
                json.put(key, parseText(json.getString(key)));
            } else if (obj instanceof JSONArray) {
                parseArray(json.getJSONArray(key));
            }
        }
    }

    private static String parseText(String line) throws IOException {
        int index = line.indexOf("%%%");
        while (index != -1) {
            String code = line.substring(index + 3, line.indexOf("%%%", index + 1));
            Element segment = segments.get(code);
            if (segment != null) {
                Element target = segment.getChild("target");
                Element source = segment.getChild("source");
                boolean restoreCrlf = "crlf".equals(segment.getAttributeValue("ts"));
                if (target != null) {
                    if ("yes".equals(segment.getAttributeValue("approved"))) {
                        line = line.replace("%%%" + code + "%%%", extractText(target, restoreCrlf));
                    } else {
                        line = line.replace("%%%" + code + "%%%", extractText(source, restoreCrlf));
                    }
                } else {
                    line = line.replace("%%%" + code + "%%%", extractText(source, restoreCrlf));
                }
            } else {
                MessageFormat mf = new MessageFormat(Messages.getString("Xliff2json.3"));
                throw new IOException(mf.format(new String[] { code }));
            }
            index = line.indexOf("%%%");
        }
        return line;
    }

    private static void parseArray(JSONArray array) throws IOException {
        for (int i = 0; i < array.length(); i++) {
            Object obj = array.get(i);
            if (obj instanceof String) {
                array.put(i, parseText(array.getString(i)));
            } else if (obj instanceof JSONArray) {
                parseArray(array.getJSONArray(i));
            } else if (obj instanceof JSONObject) {
                parseJson(array.getJSONObject(i));
            }
        }
    }

    private static String extractText(Element element, boolean restoreCrlf) {
        StringBuilder result = new StringBuilder();
        List<XMLNode> content = element.getContent();
        for (XMLNode n : content) {
            if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
                Element e = (Element) n;
                result.append(extractText(e, restoreCrlf));
            }
            if (n.getNodeType() == XMLNode.TEXT_NODE) {
                String text = ((TextNode) n).getText();
                if (restoreCrlf) {
                    text = text.replace("\r", "");
                    text = text.replace("\n", "\r\n");
                }
                if ("ph".equals(element.getName())) {
                    result.append(text);
                } else {
                    if (escaped) {
                        result.append(replaceEntities(text));
                    } else if (exportHTML) {
                        result.append(XMLUtils.cleanText(text));
                    } else {
                        result.append(text);
                    }
                }
            }
        }
        return result.toString();
    }

    private static String replaceEntities(String string) {
        if (string.isEmpty()) {
            return string;
        }
        String result = string;
        for (int i = 0; i < entities.size(); i++) {
            String[] entry = entities.get(i);
            String entity = entry[0];
            String character = entry[1];
            if ("&".equals(character)) {
                entity = "+++amp+++";
            }
            int index = result.indexOf(character);
            while (index != -1) {
                String start = result.substring(0, index);
                String end = result.substring(index + character.length());
                result = start + entity + end;
                index = result.indexOf(character);
            }
            if ("&".equals(character)) {
                result = result.replace("+++amp+++", "&amp;");
            }
        }
        return result;
    }
}
