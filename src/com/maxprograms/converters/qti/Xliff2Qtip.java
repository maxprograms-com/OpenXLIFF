
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
package com.maxprograms.converters.qti;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.converters.xml.Xliff2Xml;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLOutputter;

public class Xliff2Qtip {

    private ZipOutputStream out;

    private Xliff2Qtip() {
        // do not instantiate this class
        // use run method instead
    }

    public static List<String> run(Map<String, String> params) {
        return new Xliff2Qtip().convert(params);
    }

    private List<String> convert(Map<String, String> params) {
        List<String> result = new ArrayList<>();
        Map<String, String> filesMap = new HashMap<>();

        String sklFile = params.get("skeleton");
        String xliffFile = params.get("xliff");
        String catalog = params.get("catalog");
        String outputFile = params.get("backfile");

        try {
            SAXBuilder builder = new SAXBuilder();
            builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));
            Document doc = builder.build(xliffFile);
            Element root = doc.getRootElement();
            List<Element> files = root.getChildren("file");

            XMLOutputter outputter = new XMLOutputter();
            outputter.preserveSpace(true);
            for (Element file : files) {
                Element header = file.getChild("header");
                if (header == null) {
                    throw new SAXException(Messages.getString("Xliff2Qtip.0"));
                }
                Element skl = header.getChild("skl");
                if (skl == null) {
                    throw new SAXException("Missing <skl>");
                }
                List<Element> propGroups = header.getChildren("prop-group");
                if (propGroups == null) {
                    throw new SAXException(Messages.getString("Xliff2Qtip.1"));
                }
                String sdlxliffFile = "";
                for (Element propGroup : propGroups) {
                    if (propGroup.getAttributeValue("name").equals("document")) {
                        List<Element> props = propGroup.getChildren("prop");
                        for (Element prop : props) {
                            if (prop.getAttributeValue("prop-type").equals("original")) {
                                sdlxliffFile = prop.getText();
                            }
                        }
                    }
                }
                if (sdlxliffFile.isEmpty()) {
                    throw new SAXException(Messages.getString("Xliff2Qtip.2"));
                }
                Document d = new Document(null, "xliff", null, null);
                Element r = d.getRootElement();
                r.setAttribute("version", "1.2");
                r.setAttribute("xmlns", "urn:oasis:names:tc:xliff:document:1.2");
                r.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
                r.setAttribute("xsi:schemaLocation",
                        "urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd");
                r.addContent(file);

                File tempXliff = File.createTempFile("tmp", ".xlf");
                try (FileOutputStream outFile = new FileOutputStream(tempXliff)) {
                    outputter.output(d, outFile);
                }
                filesMap.put(sdlxliffFile, tempXliff.getAbsolutePath());
            }

            out = new ZipOutputStream(new FileOutputStream(outputFile));
            try (ZipInputStream in = new ZipInputStream(new FileInputStream(sklFile))) {
                ZipEntry entry = null;
                while ((entry = in.getNextEntry()) != null) {
                    File f = new File(entry.getName());
                    String name = f.getName();
                    String extension = name.substring(name.lastIndexOf('.'));
                    File tmp = File.createTempFile("tmp", extension);
                    try (FileOutputStream output = new FileOutputStream(tmp.getAbsolutePath())) {
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            output.write(buf, 0, len);
                        }
                    }

                    if (name.endsWith(".skl")) {
                        File back = File.createTempFile("tmp", ".xml");
                        String xliff = entry.getName();
                        xliff = xliff.substring(0, xliff.length() - 4);
                        Map<String, String> map = new HashMap<>();
                        map.put("xliff", filesMap.get(xliff));
                        map.put("skeleton", tmp.getAbsolutePath());
                        map.put("catalog", catalog);
                        map.put("backfile", back.getAbsolutePath());
                        List<String> res = Xliff2Xml.run(map);
                        if (!res.get(0).equals(Constants.SUCCESS)) {
                            return res;
                        }
                        saveEntry(xliff, back.getAbsolutePath());
                        Files.delete(back.toPath());
                    } else {
                        // store as is
                        saveEntry(entry.getName(), tmp.getAbsolutePath());
                    }
                    Files.delete(tmp.toPath());

                }
            }
            out.close();

            Set<String> keySet = filesMap.keySet();
            for (String key : keySet) {
                String file = filesMap.get(key);
                Files.delete(new File(file).toPath());
            }

            result.add(Constants.SUCCESS);
        } catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
            Logger logger = System.getLogger(Xliff2Qtip.class.getName());
            logger.log(Level.ERROR, Messages.getString("Xliff2Qtip.3"), e);
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }

    private void saveEntry(String name, String file) throws IOException {
        ZipEntry content = new ZipEntry(name);
        content.setMethod(ZipEntry.DEFLATED);
        out.putNextEntry(content);
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] array = new byte[1024];
            int len;
            while ((len = input.read(array)) > 0) {
                out.write(array, 0, len);
            }
            out.closeEntry();
        }
    }
}