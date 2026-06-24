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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.converters.Convert;
import com.maxprograms.converters.FileFormats;
import com.maxprograms.converters.Join;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLOutputter;
import com.maxprograms.xml.XMLUtils;

public class Qtip2Xliff {

    private String inputFile;
    private String skeleton;
    private ZipOutputStream out;

    private Qtip2Xliff() {
        // do not instantiate this class
        // use run method instead
    }

    public static List<String> run(Map<String, String> params) {
        return new Qtip2Xliff().convert(params);
    }

    private List<String> convert(Map<String, String> params) {

        List<String> result = new ArrayList<>();

        inputFile = params.get("source");
        String xliff = params.get("xliff");
        skeleton = params.get("skeleton");
        String sourceLanguage = params.get("srcLang");
        String targetLanguage = params.get("tgtLang");
        if (targetLanguage == null) {
            targetLanguage = "";
        }

        try {
            if ("yes".equals(params.get("strict"))) {
                Catalog catalog = CatalogBuilder.getCatalog(params.get("catalog"));
                List<String> list = QtiCheck.validatePackage(inputFile, catalog);
                if (!Constants.SUCCESS.equals(list.get(0))) {
                    return list;
                }
            }
            out = new ZipOutputStream(new FileOutputStream(skeleton));
            List<String> xliffList = new ArrayList<>();
            try (ZipInputStream in = new ZipInputStream(new FileInputStream(inputFile))) {
                ZipEntry entry = null;
                while ((entry = in.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    String name = entryName;
                    if (entryName.indexOf("\\") != -1) {
                        name = entryName.substring(entryName.lastIndexOf("\\") + 1);
                    }
                    if (entryName.indexOf("/") != -1) {
                        name = entryName.substring(entryName.lastIndexOf("/") + 1);
                    }
                    if (name.toLowerCase().endsWith(".xml") || name.toLowerCase().endsWith(".svg")) {
                        File tmp = File.createTempFile(name.substring(0, name.lastIndexOf('.')), ".xml");
                        try (FileOutputStream output = new FileOutputStream(tmp.getAbsolutePath())) {
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                output.write(buf, 0, len);
                            }
                        }
                        String format = FileFormats.detectFormat(tmp.getAbsolutePath());
                        Map<String, String> table = new HashMap<>();
                        table.put("source", tmp.getAbsolutePath());
                        table.put("xliff", tmp.getAbsolutePath() + ".xlf");
                        table.put("skeleton", tmp.getAbsolutePath() + ".skl");
                        table.put("catalog", params.get("catalog"));
                        table.put("srcEncoding", params.get("srcEncoding"));
                        table.put("paragraph", params.get("paragraph"));
                        table.put("srxFile", params.get("srxFile"));
                        table.put("format", format);
                        table.put("srcLang", sourceLanguage);
                        table.put("tgtLang", targetLanguage);
                        table.put("xmlfilter", params.get("xmlfilter"));
                        List<String> res = Convert.run(table);
                        if (Constants.SUCCESS.equals(res.get(0))) {
                            updateXliff(tmp.getAbsolutePath() + ".xlf", entry.getName());
                            ZipEntry content = new ZipEntry(entry.getName() + ".skl");
                            content.setMethod(ZipEntry.DEFLATED);
                            out.putNextEntry(content);
                            try (FileInputStream input = new FileInputStream(tmp.getAbsolutePath() + ".skl")) {
                                byte[] array = new byte[1024];
                                int len;
                                while ((len = input.read(array)) > 0) {
                                    out.write(array, 0, len);
                                }
                                out.closeEntry();
                            }
                            File skl = new File(tmp.getAbsolutePath() + ".skl");
                            Files.delete(skl.toPath());
                            File xlf = new File(tmp.getAbsolutePath() + ".xlf");
                            xliffList.add(xlf.getAbsolutePath());
                        } else {
                            saveEntry(entry, tmp.getAbsolutePath());
                        }
                        Files.delete(tmp.toPath());
                    } else {
                        // preserve untranslatable files
                        File tmp = File.createTempFile("zip", ".tmp");
                        try (FileOutputStream output = new FileOutputStream(tmp.getAbsolutePath())) {
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                output.write(buf, 0, len);
                            }
                        }
                        saveEntry(entry, tmp.getAbsolutePath());
                        Files.delete(tmp.toPath());
                    }
                }

            }
            out.close();

            // generate final XLIFF

            Join.join(xliffList, xliff);

            for (int i = 0; i < xliffList.size(); i++) {
                File xlf = new File(xliffList.get(i));
                Files.delete(Paths.get(xlf.toURI()));
            }

            result.add(Constants.SUCCESS);
        } catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
            Logger logger = System.getLogger(Qtip2Xliff.class.getName());
            logger.log(Level.ERROR, Messages.getString("Qtip2Xliff.2"), e);
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }

    private void saveEntry(ZipEntry entry, String name) throws IOException {
        ZipEntry content = new ZipEntry(entry.getName());
        content.setMethod(ZipEntry.DEFLATED);
        out.putNextEntry(content);
        try (FileInputStream input = new FileInputStream(name)) {
            byte[] array = new byte[1024];
            int len;
            while ((len = input.read(array)) > 0) {
                out.write(array, 0, len);
            }
            out.closeEntry();
        }
    }

    private void updateXliff(String xliff, String original)
            throws SAXException, IOException, ParserConfigurationException {
        SAXBuilder builder = new SAXBuilder();
        Document doc = builder.build(xliff);
        Element root = doc.getRootElement();
        Element file = root.getChild("file");
        file.setAttribute("datatype", "x-qtipackage");
        file.setAttribute("original", XMLUtils.cleanText(inputFile));
        Element header = file.getChild("header");
        Element propGroup = new Element("prop-group");
        propGroup.setAttribute("name", "document");
        Element prop = new Element("prop");
        prop.setAttribute("prop-type", "original");
        prop.setText(original);
        propGroup.addContent(prop);
        header.addContent(propGroup);

        Element ext = header.getChild("skl").getChild("external-file");
        ext.setAttribute("href", XMLUtils.cleanText(skeleton));

        XMLOutputter outputter = new XMLOutputter();
        outputter.preserveSpace(true);
        Indenter.indent(root, 2);
        try (FileOutputStream output = new FileOutputStream(xliff)) {
            outputter.output(doc, output);
        }
    }
}
