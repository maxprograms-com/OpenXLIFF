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

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLOutputter;

public class ToOpenXliff {

    private ToOpenXliff() {
        // do not instantiate this class
        // use run method instead
    }

    public static List<String> run(Map<String, String> params) {
        List<String> result = new ArrayList<>();
        String inputFile = params.get("source");
        String xliffFile = params.get("xliff");
        String skeletonFile = params.get("skeleton");
        String sourceLanguage = params.get("srcLang");
        String targetLanguage = params.get("tgtLang");
        String catalog = params.get("catalog");
        try {
            SAXBuilder builder = new SAXBuilder();
            builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));
            Document doc = builder.build(inputFile);
            Element root = doc.getRootElement();

            Document newDoc = new Document(null, "xliff", null, null);
            Element newRoot = newDoc.getRootElement();
            newRoot.setAttribute("version", "1.2");
            newRoot.setAttribute("xmlns", "urn:oasis:names:tc:xliff:document:1.2");
            newRoot.addContent(new PI("encoding", doc.getEncoding().name()));

            List<Element> files = root.getChildren("file");
            if (files.isEmpty()) {
                result.add(Constants.ERROR);
                result.add(Messages.getString("ToOpenXliff.0"));
                return result;
            }
            Xliff1xProcessor.resetFileIndex();
            for (Element originalFile : files) {
                Element file = new Element("file");
                file.setAttribute("datatype", "x-xliff");
                file.setAttribute("source-language", sourceLanguage);
                if (targetLanguage != null && !targetLanguage.isEmpty()) {
                    file.setAttribute("target-language", targetLanguage);
                }
                file.setAttribute("original", inputFile);
                JSONObject json = new JSONObject();
                List<Attribute> atts = originalFile.getAttributes();
                for (Attribute att : atts) {
                    json.put(att.getName(), att.getValue());
                }
                file.setAttribute("ts", json.toString());
                Element metadata = originalFile.getChild("mda:metadata");
                if (metadata != null) {
                    file.addContent(new PI("metadata", metadata.toString()));
                }
                newRoot.addContent(file);

                Element header = new Element("header");
                file.addContent(header);

                Element skl = new Element("skl");
                header.addContent(skl);

                Element externalFile = new Element("external-file");
                externalFile.setAttribute("href", skeletonFile);
                skl.addContent(externalFile);

                Element tool = new Element("tool");
                tool.setAttribute("tool-id", Constants.TOOLID);
                tool.setAttribute("tool-name", Constants.TOOLNAME);
                tool.setAttribute("tool-version", Constants.VERSION);
                header.addContent(tool);

                List<Element> units = new Vector<>();

                if (root.getAttributeValue("version").startsWith("1")) {
                    Xliff1xProcessor.processXliff1x(originalFile, units);
                }
                if (root.getAttributeValue("version").startsWith("2")) {
                    Xliff2xProcessor.processXliff2x(originalFile, units);
                    if (!file.hasAttribute("target-language") && Xliff2xProcessor.hasTarget(units)) {
                        throw new IOException(Messages.getString("ToOpenXliff.3"));
                    }
                }
                if (units.isEmpty()) {
                    result.add(Constants.ERROR);
                    result.add(Messages.getString("ToOpenXliff.1"));
                    return result;
                }

                Element body = new Element("body");
                file.addContent(body);
                for (int i = 0; i < units.size(); i++) {
                    body.addContent(units.get(i));
                }
            }
            try (FileOutputStream out = new FileOutputStream(xliffFile)) {
                Indenter.indent(newRoot, 2);
                XMLOutputter outputter = new XMLOutputter();
                outputter.preserveSpace(true);
                outputter.output(newDoc, out);
            }
            try (FileOutputStream skeleton = new FileOutputStream(skeletonFile)) {
                XMLOutputter outputter = new XMLOutputter();
                outputter.preserveSpace(true);
                outputter.output(doc, skeleton);
            }
            result.add(Constants.SUCCESS);
        } catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
            Logger logger = System.getLogger(ToOpenXliff.class.getName());
            logger.log(Level.ERROR, Messages.getString("ToOpenXliff.2"), e);
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }
}