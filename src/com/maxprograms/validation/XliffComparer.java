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

package com.maxprograms.validation;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.text.MessageFormat;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Utils;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;

public class XliffComparer {

    private static Logger logger = System.getLogger(XliffComparer.class.getName());
    private static int differenceCount = 0;

    public static void main(String[] args) {
        String[] fixedArgs = Utils.fixPath(args);

        String file1 = "";
        String file2 = "";
        String catalog = "";

        for (int i = 0; i < fixedArgs.length; i++) {
            String arg = fixedArgs[i];
            if (arg.equals("-help")) {
                help();
                return;
            }
            if (arg.equals("-file1") && (i + 1) < fixedArgs.length) {
                file1 = fixedArgs[i + 1];
            }
            if (arg.equals("-file2") && (i + 1) < fixedArgs.length) {
                file2 = fixedArgs[i + 1];
            }
            if (arg.equals("-catalog") && (i + 1) < fixedArgs.length) {
                catalog = fixedArgs[i + 1];
            }
        }

        if (file1.isEmpty() || file2.isEmpty()) {
            logger.log(Level.ERROR, Messages.getString("XliffComparer.1"));
            help();
            return;
        }

        if (catalog.isEmpty()) {
            String catalogPath = System.getenv("OpenXLIFF_HOME");
            if (catalogPath != null) {
                catalog = catalogPath + System.getProperty("file.separator") + "catalog" +
                        System.getProperty("file.separator") + "catalog.xml";
            }
        }

        try {
            Catalog cat = null;
            if (!catalog.isEmpty()) {
                cat = CatalogBuilder.getCatalog(catalog);
            }

            boolean result = compareXliff(file1, file2, cat);
            if (result) {
                System.out.println(Messages.getString("XliffComparer.3"));
                MessageFormat mf = new MessageFormat(Messages.getString("XliffComparer.4"));
                System.out.println(mf.format(new Object[] { differenceCount }));
            } else {
                System.out.println(Messages.getString("XliffComparer.5"));
                System.exit(1);
            }
        } catch (Exception e) {
            logger.log(Level.ERROR, Messages.getString("XliffComparer.2"), e);
            System.exit(1);
        }
    }

    public static boolean compareXliff(String file1, String file2, Catalog catalog)
            throws SAXException, IOException, ParserConfigurationException {

        SAXBuilder builder = new SAXBuilder();
        if (catalog != null) {
            builder.setEntityResolver(catalog);
        }

        Document doc1 = builder.build(file1);
        Document doc2 = builder.build(file2);

        Element root1 = doc1.getRootElement();
        Element root2 = doc2.getRootElement();

        List<Element> files1 = root1.getChildren("file");
        List<Element> files2 = root2.getChildren("file");

        if (files1.size() != files2.size()) {
            System.out.println(Messages.getString("XliffComparer.6"));
            MessageFormat mf = new MessageFormat(Messages.getString("XliffComparer.7"));
            System.out.println(mf.format(new Object[] { files1.size(), files2.size() }));
            return false;
        }

        boolean identical = true;
        int totalSegments = 0;

        for (int i = 0; i < files1.size(); i++) {
            Element fileElement1 = files1.get(i);
            Element fileElement2 = files2.get(i);

            String original1 = fileElement1.getAttributeValue("original");
            String original2 = fileElement2.getAttributeValue("original");

            List<Element> units1 = getUnits(fileElement1);
            List<Element> units2 = getUnits(fileElement2);

            boolean fileIdentical = compareFileUnits(original1, original2, i, units1, units2);
            if (fileIdentical) {
                totalSegments += units1.size();
            } else {
                identical = false;
            }
        }

        differenceCount = totalSegments;
        return identical;
    }

    private static boolean compareFileUnits(String original1, String original2, int fileIndex,
            List<Element> units1, List<Element> units2) {
        if (!original1.equals(original2)) {
            MessageFormat mf = new MessageFormat(Messages.getString("XliffComparer.8"));
            System.out.println(mf.format(new Object[] { fileIndex }));
            MessageFormat mf1 = new MessageFormat(Messages.getString("XliffComparer.9"));
            System.out.println(mf1.format(new Object[] { original1 }));
            MessageFormat mf2 = new MessageFormat(Messages.getString("XliffComparer.10"));
            System.out.println(mf2.format(new Object[] { original2 }));
            return false;
        }

        if (units1.size() != units2.size()) {
            MessageFormat mf = new MessageFormat(Messages.getString("XliffComparer.11"));
            System.out.println(mf.format(new Object[] { original1, fileIndex }));
            MessageFormat mf2 = new MessageFormat(Messages.getString("XliffComparer.12"));
            System.out.println(mf2.format(new Object[] { units1.size(), units2.size() }));
            return false;
        }

        boolean identical = true;
        for (int i = 0; i < units1.size(); i++) {
            Element unit1 = units1.get(i);
            Element stripped1 = removeSpaces(unit1);
            Element unit2 = units2.get(i);
            Element stripped2 = removeSpaces(unit2);

            if (!stripped1.equals(stripped2)) {
                String id1 = unit1.getAttributeValue("id", String.valueOf(i));
                String id2 = unit2.getAttributeValue("id", String.valueOf(i));
                MessageFormat mf = new MessageFormat(Messages.getString("XliffComparer.13"));
                System.out.println(mf.format(new Object[] { original1, fileIndex, i }));
                MessageFormat mfId = new MessageFormat(Messages.getString("XliffComparer.14"));
                System.out.println(mfId.format(new Object[] { id1 }));
                MessageFormat mfId2 = new MessageFormat(Messages.getString("XliffComparer.15"));
                System.out.println(mfId2.format(new Object[] { id2 }));
                MessageFormat mfUnit1 = new MessageFormat(Messages.getString("XliffComparer.16"));
                System.out.println(mfUnit1.format(new Object[] { unit1.toString() }));
                MessageFormat mfUnit2 = new MessageFormat(Messages.getString("XliffComparer.17"));
                System.out.println(mfUnit2.format(new Object[] { unit2.toString() }));
                identical = false;
            }
        }

        return identical;
    }

    private static Element removeSpaces(Element element) {
        Element copy = new Element(element.getName());
        copy.setAttributes(element.getAttributes());
        List<XMLNode> content = element.getContent();
        for (XMLNode node : content) {
            if (node instanceof Element child) {
                if ("source".equals(child.getName()) || "target".equals(child.getName())) {
                    copy.addContent(child);
                } else {
                    copy.addContent(removeSpaces(child));
                }
            } else if (node instanceof TextNode text) {
                if (!text.getText().isBlank()) {
                    copy.addContent(text);
                }
            }
        }
        return copy;
    }

    private static List<Element> getUnits(Element fileElement) {
        // For XLIFF 2.x, get <unit> elements directly
        List<Element> units = fileElement.getChildren("unit");
        if (!units.isEmpty()) {
            return units;
        }

        // For XLIFF 1.x, get <trans-unit> elements from <body>
        Element body = fileElement.getChild("body");
        if (body != null) {
            return body.getChildren("trans-unit");
        }

        return new java.util.ArrayList<>();
    }

    private static void help() {
        String help = Messages.getString("XliffComparer.help");
        System.out.println(help);
    }
}
