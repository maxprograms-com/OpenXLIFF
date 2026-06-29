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
package com.maxprograms.converters.vtt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;

public class Xliff2Vtt {

    private static String xliffFile;
    private static Catalog catalog;
    private static Map<String, Element> segments;
    private static FileOutputStream output;

    private Xliff2Vtt() {
        // do not instantiate this class
        // use run method instead
    }

    public static List<String> run(Map<String, String> params) {
        List<String> result = new ArrayList<>();

        String sklFile = params.get("skeleton");
        xliffFile = params.get("xliff");

        try {
            catalog = CatalogBuilder.getCatalog(params.get("catalog"));
            String outputFile = params.get("backfile");
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
            output = new FileOutputStream(f);
            loadSegments();
            try (FileReader reader = new FileReader(sklFile, StandardCharsets.UTF_8)) {
                try (BufferedReader buffer = new BufferedReader(reader)) {
                    String line;
                    while ((line = buffer.readLine()) != null) {
                        line = line + "\n";
                        if (line.contains("%%%")) {
                            int index = line.indexOf("%%%");
                            while (index != -1) {
                                String start = line.substring(0, index);
                                writeString(start);
                                line = line.substring(index + 3);
                                String code = line.substring(0, line.indexOf("%%%"));
                                line = line.substring(line.indexOf("%%%") + 3);
                                Element segment = segments.get(code);
                                if (segment != null) {
                                    Element target = segment.getChild("target");
                                    Element source = segment.getChild("source");
                                    if (target != null) {
                                        if (segment.getAttributeValue("approved", "no").equals("yes")) {
                                            writeString(extractText(target));
                                        } else {
                                            writeString(extractText(source));
                                        }
                                    } else {
                                        writeString(extractText(source));
                                    }
                                } else {
                                    result.add(Constants.ERROR);
                                    MessageFormat mf = new MessageFormat(Messages.getString("Xliff2Vtt.0"));
                                    result.add(mf.format(new String[] { code }));
                                    return result;
                                }

                                index = line.indexOf("%%%");
                                if (index == -1) {
                                    writeString(line);
                                }
                            }
                        } else {
                            writeString(line);
                        }
                    }
                }
            }
            output.close();
            result.add(Constants.SUCCESS);
        } catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
            Logger logger = System.getLogger(Xliff2Vtt.class.getName());
            logger.log(Level.ERROR, Messages.getString("Xliff2Vtt.1"), e);
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }

    private static void writeString(String string) throws IOException {
        output.write(string.getBytes(StandardCharsets.UTF_8));
    }

    private static void loadSegments() throws SAXException, IOException, ParserConfigurationException {
        SAXBuilder builder = new SAXBuilder();
        if (catalog != null) {
            builder.setEntityResolver(catalog);
        }

        Document doc = builder.build(xliffFile);
        Element root = doc.getRootElement();
        Element body = root.getChild("file").getChild("body");
        List<Element> units = body.getChildren("trans-unit");

        segments = new HashMap<>();

        for (Element unit : units) {
            segments.put(unit.getAttributeValue("id"), unit);
        }
    }

    private static String extractText(Element target) {
        StringBuilder result = new StringBuilder();
        List<XMLNode> content = target.getContent();
        for (XMLNode n : content) {
            if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
                Element e = (Element) n;
                result.append(extractText(e));
            }
            if (n.getNodeType() == XMLNode.TEXT_NODE) {
                result.append(((TextNode) n).getText());
            }
        }
        return result.toString();
    }
}
