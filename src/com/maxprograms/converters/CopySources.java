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
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;

public class CopySources {

    private static Logger logger = System.getLogger(CopySources.class.getName());
    private static String version;

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
                logger.log(Level.ERROR, Messages.getString("CopySources.1"));
                return;
            }
            catalog = new File(catalogFolder, "catalog.xml").getAbsolutePath();
        }
        File catalogFile = new File(catalog);
        if (!catalogFile.exists()) {
            logger.log(Level.ERROR, Messages.getString("CopySources.2"));
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
            copySources(xliff, catalog);
        } catch (SAXException | IOException | ParserConfigurationException | URISyntaxException e) {
            logger.log(Level.ERROR, e);
        }
    }

    private static void help() {
        MessageFormat mf = new MessageFormat(Messages.getString("CopySources.help"));
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
        String help = mf.format(new String[] { isWindows ? "copysources.cmd" : "copysources.sh" });
        System.out.println(help);
    }

    public static void copySources(String xliff, String catalog)
            throws SAXException, IOException, ParserConfigurationException, URISyntaxException {
        SAXBuilder builder = new SAXBuilder();
        builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));
        Document doc = builder.build(xliff);
        Element root = doc.getRootElement();
        if (!"xliff".equals(root.getName())) {
            throw new IOException(Messages.getString("CopySources.3"));
        }
        version = root.getAttributeValue("version");
        recurse(root);
        Indenter.indent(root, 2);
        XMLOutputter outputter = new XMLOutputter();
        outputter.preserveSpace(true);
        try (FileOutputStream out = new FileOutputStream(xliff)) {
            outputter.output(doc, out);
        }
    }

    private static void recurse(Element root) throws IOException {
        String name = root.getName();
        if (("xliff".equals(name) && version.startsWith("2.") && root.getAttributeValue("trgLang").isEmpty())
                || ("file".equals(name) && version.startsWith("1.")
                        && root.getAttributeValue("target-language").isEmpty())) {
            throw new IOException(Messages.getString("CopySources.4"));
        }
        if (("file".equals(name) || "group".equals(name) || "trans-unit".equals(name)
                || "unit".equals(name)) && "no".equals(root.getAttributeValue("translate"))) {
            return;
        }
        if (("trans-unit".equals(name) && root.getChild("seg-source") == null) || "segment".equals(name)
                || "ignorable".equals(name)) {
            Element target = root.getChild("target");
            if (target == null || target.getContent().isEmpty()) {
                Element source = root.getChild("source");
                target = translate(source);
                if ("preserve".equals(source.getAttributeValue("xml:space"))) {
                    target.setAttribute("xml:space", "preserve");
                }
                if ("segment".equals(name)) {
                    root.setAttribute("state", "translated");
                }
                List<XMLNode> newContent = new Vector<>();
                List<XMLNode> content = root.getContent();
                for (XMLNode node : content) {
                    newContent.add(node);
                    if (node instanceof Element e && "source".equals(e.getName())) {
                        newContent.add(target);
                    }
                }
                root.setContent(newContent);
            }
        }
        List<Element> children = root.getChildren();
        for (Element child : children) {
            recurse(child);
        }
    }

    private static Element translate(Element source) {
        Element target = new Element("target");
        target.setContent(source.getContent());
        return target;
    }
}
