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
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.text.MessageFormat;

import java.util.List;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLOutputter;

public class ApproveAll {

    private static Logger logger = System.getLogger(ApproveAll.class.getName());

    public static void main(String[] args) {

        String[] arguments = Utils.fixPath(args);
        String xliff = "";
        String catalog = "";

        for (int i = 0; i < arguments.length; i++) {
            String arg = arguments[i];
            if (arg.equals("-help")) {
                help();
                return;
            }
            if (arg.equals("-lang") && (i + 1) < arguments.length) {
                Locale.setDefault(Locale.forLanguageTag(arguments[i + 1]));
            }
            if (arg.equals("-xliff") && (i + 1) < arguments.length) {
                xliff = arguments[i + 1];
            }
            if (arg.equals("-catalog") && (i + 1) < arguments.length) {
                catalog = arguments[i + 1];
            }
        }
        if (arguments.length < 2) {
            help();
            return;
        }
        if (catalog.isEmpty()) {
            String home = System.getenv("OpenXLIFF_HOME");
            if (home == null) {
                home = System.getProperty("user.dir");
            }
            File catalogFolder = new File(new File(home), "catalog");
            if (!catalogFolder.exists()) {
                logger.log(Level.ERROR, Messages.getString("ApproveAll.1"));
                return;
            }
            catalog = new File(catalogFolder, "catalog.xml").getAbsolutePath();
        }
        File catalogFile = new File(catalog);
        if (!catalogFile.exists()) {
            logger.log(Level.ERROR, Messages.getString("ApproveAll.2"));
            return;
        }
        if (!catalogFile.isAbsolute()) {
            catalog = catalogFile.getAbsoluteFile().getAbsolutePath();
        }
        File xliffFile = new File(xliff);
        if (!xliffFile.isAbsolute()) {
            xliff = xliffFile.getAbsoluteFile().getAbsolutePath();
        }
        try {
            approveAll(xliff, catalog);
        } catch (SAXException | IOException | ParserConfigurationException | URISyntaxException e) {
            logger.log(Level.ERROR, e);
        }
    }

    private static void help() {
        MessageFormat mf = new MessageFormat(Messages.getString("ApproveAll.help"));
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
        String help = mf.format(new String[] { isWindows ? "approveall.cmd" : "approveall.sh" });
        System.out.println(help);
    }

    public static void approveAll(String xliff, String catalog)
            throws IOException, SAXException, ParserConfigurationException, URISyntaxException {
        SAXBuilder builder = new SAXBuilder();
        builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));
        Document doc = builder.build(xliff);
        Element root = doc.getRootElement();
        recurse(root);
        Indenter.indent(root, 2);
        XMLOutputter outputter = new XMLOutputter();
        outputter.preserveSpace(true);
        try (FileOutputStream out = new FileOutputStream(xliff)) {
            outputter.output(doc, out);
        }
    }

    private static void recurse(Element root) {
        if (("file".equals(root.getName()) || "group".equals(root.getName()) || "trans-unit".equals(root.getName())
                || "unit".equals(root.getName()))
                && "no".equals(root.getAttributeValue("translate"))) {
            return;
        }
        if (("trans-unit".equals(root.getName()) && root.getChild("seg-source") == null)) {
            if ("yes".equals(root.getAttributeValue("approved"))) {
                return;
            }
            Element target = root.getChild("target");
            if (target == null) {
                return;
            }
            if (!target.getContent().isEmpty()) {
                root.setAttribute("approved", "yes");
                return;
            }
        }
        if ("segment".equals(root.getName())) {
            if ("final".equals(root.getAttributeValue("state"))) {
                return;
            }
            Element target = root.getChild("target");
            if (target == null) {
                return;
            }
            if (!target.getContent().isEmpty()) {
                root.setAttribute("state", "final");
                return;
            }
        }
        List<Element> children = root.getChildren();
        for (Element child : children) {
            recurse(child);
        }
    }

}
