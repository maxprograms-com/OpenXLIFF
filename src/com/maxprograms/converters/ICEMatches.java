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
package com.maxprograms.converters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.xliff2.FromXliff2;
import com.maxprograms.xliff2.ToXliff2;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;

public class ICEMatches {

    private static final System.Logger logger = System.getLogger(ICEMatches.class.getName());

    public static void main(String[] args) {
        String oldFile = "";
        String newFile = "";
        String catalog = "";

        String[] arguments = Utils.fixPath(args);
        for (int i = 0; i < arguments.length; i++) {
            String arg = arguments[i];
            if (arg.equals("-help")) {
                help();
                return;
            }
            if (arg.equals("-old") && (i + 1) < arguments.length) {
                oldFile = arguments[i + 1];
            }
            if (arg.equals("-new") && (i + 1) < arguments.length) {
                newFile = arguments[i + 1];
            }
            if (arg.equals("-catalog") && (i + 1) < arguments.length) {
                catalog = arguments[i + 1];
            }
        }
        if (oldFile.isEmpty() || newFile.isEmpty()) {
            help();
            System.exit(1);
        }
        if (catalog.isEmpty()) {
            try {
                catalog = Convert.defaultCatalog();
            } catch (IOException e) {
                logger.log(Level.ERROR, e.getMessage());
                return;
            }
        }
        try {
            new ICEMatches(oldFile, newFile, catalog);
        } catch (SAXException | IOException | ParserConfigurationException | URISyntaxException e) {
            logger.log(Level.ERROR, e.getMessage());
        }
    }

    public ICEMatches(String oldXliff, String newXliff, String catalogFile)
            throws SAXException, IOException, ParserConfigurationException, URISyntaxException {
        Catalog catalog = CatalogBuilder.getCatalog(catalogFile);
        SAXBuilder builder = new SAXBuilder();
        builder.setEntityResolver(catalog);
        File oldFile = new File(oldXliff);
        if (!oldFile.isAbsolute()) {
            oldXliff = oldFile.getAbsoluteFile().getAbsolutePath();
            oldFile = new File(oldXliff);
        }
        Document doc = builder.build(oldFile);
        Element root = doc.getRootElement();
        if (!root.getName().equals("xliff")) {
            logger.log(Level.ERROR, Messages.getString("ICEMatches.1"));
            return;
        }
        String version = root.getAttributeValue("version");
        if (!"1.2".equals(version)) {
            // convert old file to XLIFF 1.2
            File tempFile = File.createTempFile("old__", ".xlf");
            FromXliff2.run(oldFile.getAbsolutePath(), tempFile.getAbsolutePath(), catalogFile);
            oldFile = tempFile;
        }
        File newFile = new File(newXliff);
        if (!newFile.isAbsolute()) {
            newXliff = newFile.getAbsoluteFile().getAbsolutePath();
            newFile = new File(newXliff);
        }
        doc = builder.build(newFile);
        root = doc.getRootElement();
        if (!root.getName().equals("xliff")) {
            logger.log(Level.ERROR, Messages.getString("ICEMatches.1"));
            return;
        }
        if (!"1.2".equals(root.getAttributeValue("version"))) {
            // convert new file to XLIFF 1.2
            File tempFile = File.createTempFile("new__", ".xlf");
            FromXliff2.run(newFile.getAbsolutePath(), tempFile.getAbsolutePath(), catalogFile);
            newFile = tempFile;
        }
        leverage(newFile, oldFile);
        if (oldFile.getName().startsWith("old__")) {
            Files.delete(oldFile.toPath());
        }
        if (newFile.getName().startsWith("new__")) {
            ToXliff2.run(newFile.getAbsolutePath(), newFile.getAbsolutePath(), catalogFile, version, false);
            Files.delete(new File(newXliff).toPath());
            Files.copy(newFile.toPath(), new File(newXliff).toPath());
            Files.delete(newFile.toPath());
        }
    }

    private void leverage(File xliff, File previousBuild)
            throws IOException, SAXException, ParserConfigurationException {

        SAXBuilder builder = new SAXBuilder();
        Document doc = builder.build(xliff);
        Element root = doc.getRootElement();
        List<Element> segments = new Vector<>();

        Document doc2 = builder.build(previousBuild);
        Element root2 = doc2.getRootElement();
        List<Element> leveraged = new Vector<>();

        List<Element> originalFiles = root.getChildren("file");
        List<Element> oldFiles = root2.getChildren("file");

        for (int fi = 0; fi < originalFiles.size(); fi++) {
            Element currentFile = originalFiles.get(fi);
            Element oldFile = null;
            for (int j = 0; j < oldFiles.size(); j++) {
                if (oldFiles.get(j).getAttributeValue("original").equals(currentFile.getAttributeValue("original"))) {
                    oldFile = oldFiles.get(j);
                    break;
                }
            }
            if (oldFile == null) {
                continue;
            }
            segments.clear();
            recurseSegments(currentFile, segments);

            leveraged.clear();
            recurseSegments(oldFile, leveraged);

            Element previous = null;
            Element current = null;
            Element next = null;
            int size = segments.size();
            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    previous = segments.get(i - 1).getChild("source");
                }
                if (segments.get(i).getAttributeValue("approved", "no").equalsIgnoreCase("yes")) {
                    continue;
                }
                if (segments.get(i).getAttributeValue("translate", "yes").equalsIgnoreCase("no")) {
                    continue;
                }
                current = segments.get(i).getChild("source");
                String pureText = Utils.pureText(current);
                if (i + 1 < segments.size()) {
                    next = segments.get(i + 1).getChild("source");
                } else {
                    next = null;
                }
                for (int j = 0; j < leveraged.size(); j++) {
                    Element newUnit = leveraged.get(j);
                    if (newUnit.getAttributeValue("approved", "no").equals("no")) {
                        continue;
                    }
                    Element newSource = newUnit.getChild("source");
                    if (pureText.equals(Utils.pureText(newSource))) {
                        double mismatches = Utils.wrongTags(current, newSource, 1.0);
                        if (mismatches > 0.0) {
                            continue;
                        }
                        if (previous != null) {
                            if (j == 0) {
                                continue;
                            }
                            Element e = leveraged.get(j - 1).getChild("source");
                            if (!Utils.pureText(previous).equals(Utils.pureText(e))) {
                                continue;
                            }
                        }
                        if (next != null) {
                            if (j + 1 == leveraged.size()) {
                                continue;
                            }
                            Element e = leveraged.get(j + 1).getChild("source");
                            if (!Utils.pureText(next).equals(Utils.pureText(e))) {
                                continue;
                            }
                        }
                        Element newTarget = newUnit.getChild("target");
                        if (newTarget != null) {
                            Element target = segments.get(i).getChild("target");
                            if (target == null) {
                                target = new Element("target");
                                addTarget(segments.get(i), target);
                            }
                            target.clone(newTarget);
                            target.setAttribute("state", "signed-off");
                            target.setAttribute("state-qualifier", "leveraged-inherited");
                            segments.get(i).setAttribute("approved", "yes");
                        }
                    }
                }
            }
        }
        try (FileOutputStream output = new FileOutputStream(xliff)) {
            XMLOutputter outputter = new XMLOutputter();
            outputter.preserveSpace(true);
            outputter.output(doc, output);
        }
    }

    private static void addTarget(Element el, Element tg) {
        el.removeChild("target");
        List<XMLNode> content = el.getContent();
        for (int i = 0; i < content.size(); i++) {
            XMLNode o = content.get(i);
            if (o.getNodeType() == XMLNode.ELEMENT_NODE) {
                Element e = (Element) o;
                if (e.getName().equals("source")) {
                    content.add(i + 1, tg);
                    break;
                }
            }
        }
        el.setContent(content);
    }

    private void recurseSegments(Element root, List<Element> segments) {
        if (root.getName().equals("trans-unit")) {
            segments.add(root);
        } else {
            List<Element> list = root.getChildren();
            for (Element child : list) {
                recurseSegments(child, segments);
            }
        }
    }

    private static void help() {
        MessageFormat mf = new MessageFormat(Messages.getString("ICEMatches.help"));
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
        String help = mf.format(new String[] { isWindows ? "iceMatches.cmd" : "iceMatches.sh" });
        System.out.println(help);
    }

}
