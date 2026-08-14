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
package com.maxprograms.xliff2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.converters.Utils;
import com.maxprograms.segmenter.Segmenter;
import com.maxprograms.segmenter.SegmenterPool;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;

public class Resegmenter {

    private static Logger logger = System.getLogger(Resegmenter.class.getName());

    private static final class Context {
        Segmenter segmenter;
        boolean canResegment;
        boolean translate;
    }

    private Resegmenter() {
        // do not instantiate this class
        // use run method instead
    }

    public static void main(String[] args) {

        String jsonFile = "";
        String xliffFile = "";
        String srxFile = "";
        String srcLang = "";
        String catalogFile = "";
        String maxThreadsParam = "";

        String[] arguments = Utils.fixPath(args);
        if (arguments.length == 0) {
            help();
            return;
        }
        for (int i = 0; i < arguments.length; i++) {
            String arg = arguments[i];
            if (arg.equals("-lang") && (i + 1) < arguments.length) {
                Locale.setDefault(Locale.forLanguageTag(arguments[i + 1]));
            }
            if (arg.equals("-version")) {
                MessageFormat mf = new MessageFormat(Messages.getString("Resegmenter.3"));
                logger.log(Level.INFO, mf.format(new String[] { Constants.VERSION, Constants.BUILD }));
                return;
            }
            if (arg.equals("-help")) {
                help();
                return;
            }
            if (arg.equals(("-xliff")) && (i + 1) < arguments.length) {
                xliffFile = arguments[i + 1];
            }
            if (arg.equals(("-srx")) && (i + 1) < arguments.length) {
                srxFile = arguments[i + 1];
            }
            if (arg.equals(("-srcLang")) && (i + 1) < arguments.length) {
                srcLang = arguments[i + 1];
            }
            if (arg.equals(("-catalog")) && (i + 1) < arguments.length) {
                catalogFile = arguments[i + 1];
            }
            if (arg.equals(("-maxThreads")) && (i + 1) < arguments.length) {
                maxThreadsParam = arguments[i + 1];
            }
            if (arg.equals("-json") && (i + 1) < arguments.length) {
                jsonFile = arguments[i + 1];
                run(jsonFile);
                return;
            }
        }
        if (xliffFile.isEmpty()) {
            MessageFormat mf = new MessageFormat(Messages.getString("Resegmenter.5"));
            logger.log(Level.ERROR, mf.format(new String[] { "-xliff" }));
            return;
        }
        if (srxFile.isEmpty()) {
            MessageFormat mf = new MessageFormat(Messages.getString("Resegmenter.5"));
            logger.log(Level.ERROR, mf.format(new String[] { "-srx" }));
            return;
        }
        if (srcLang.isEmpty()) {
            MessageFormat mf = new MessageFormat(Messages.getString("Resegmenter.5"));
            logger.log(Level.ERROR, mf.format(new String[] { "-srcLang" }));
            return;
        }
        if (catalogFile.isEmpty()) {
            String home = System.getenv("OpenXLIFF_HOME");
            if (home == null) {
                home = System.getProperty("user.dir");
            }
            File catalogFolder = new File(new File(home), "catalog");
            if (!catalogFolder.exists()) {
                logger.log(Level.ERROR, Messages.getString("Resegmenter.6"));
                return;
            }
            catalogFile = new File(catalogFolder, "catalog.xml").getAbsolutePath();
        }

        File catalog = new File(catalogFile);
        if (!catalog.exists()) {
            logger.log(Level.ERROR, Messages.getString("Resegmenter.7"));
            return;
        }
        if (!catalog.isAbsolute()) {
            catalogFile = catalog.getAbsoluteFile().getAbsolutePath();
        }
        int maxThreads;
        if (!maxThreadsParam.isEmpty()) {
            try {
                maxThreads = Integer.parseInt(maxThreadsParam);
                if (maxThreads < 1) {
                    maxThreads = 1;
                }
            } catch (NumberFormatException _) {
                // Use default if invalid
                maxThreads = Runtime.getRuntime().availableProcessors();
            }
        } else {
            maxThreads = Runtime.getRuntime().availableProcessors();
        }
        try {
            Catalog instance = CatalogBuilder.getCatalog(catalogFile);
            run(xliffFile, srxFile, srcLang, instance, maxThreads);
        } catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
            logger.log(Level.ERROR, e);
        }
    }

    private static void run(String jsonFile) {
        try {
            JSONObject json = Utils.readJSON(jsonFile);
            String xliffFile = json.optString("xliff", "");
            String srxFile = json.optString("srx", "");
            String srcLang = json.optString("srclang", "");
            String catalogFile = json.optString("catalog", "");
            String maxThreadsParam = json.optString("maxThreads", "");
            if (xliffFile.isEmpty()) {
                MessageFormat mf = new MessageFormat(Messages.getString("Resegmenter.5"));
                logger.log(Level.ERROR, mf.format(new String[] { "-xliff" }));
                return;
            }
            if (srxFile.isEmpty()) {
                MessageFormat mf = new MessageFormat(Messages.getString("Resegmenter.5"));
                logger.log(Level.ERROR, mf.format(new String[] { "-srx" }));
                return;
            }
            if (srcLang.isEmpty()) {
                MessageFormat mf = new MessageFormat(Messages.getString("Resegmenter.5"));
                logger.log(Level.ERROR, mf.format(new String[] { "-srcLang" }));
                return;
            }
            if (catalogFile.isEmpty()) {
                String home = System.getenv("OpenXLIFF_HOME");
                if (home == null) {
                    home = System.getProperty("user.dir");
                }
                File catalogFolder = new File(new File(home), "catalog");
                if (!catalogFolder.exists()) {
                    logger.log(Level.ERROR, Messages.getString("Resegmenter.6"));
                    return;
                }
                catalogFile = new File(catalogFolder, "catalog.xml").getAbsolutePath();
            }

            File catalog = new File(catalogFile);
            if (!catalog.exists()) {
                logger.log(Level.ERROR, Messages.getString("Resegmenter.7"));
                return;
            }
            if (!catalog.isAbsolute()) {
                catalogFile = catalog.getAbsoluteFile().getAbsolutePath();
            }
            int maxThreads;
            if (!maxThreadsParam.isEmpty()) {
                try {
                    maxThreads = Integer.parseInt(maxThreadsParam);
                    if (maxThreads < 1) {
                        maxThreads = 1;
                    }
                } catch (NumberFormatException _) {
                    // Use default if invalid
                    maxThreads = Runtime.getRuntime().availableProcessors();
                }
            } else {
                maxThreads = Runtime.getRuntime().availableProcessors();
            }
            Catalog instance = CatalogBuilder.getCatalog(catalogFile);
            run(xliffFile, srxFile, srcLang, instance, maxThreads);
        } catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
            logger.log(Level.ERROR, e);
        }
    }

    public static List<String> run(String xliff, String srx, String srcLang, Catalog catalog, int maxThreads) {
        List<String> result = new ArrayList<>();
        try {
            SAXBuilder builder = new SAXBuilder();
            builder.setEntityResolver(catalog);
            Document doc = builder.build(xliff);
            Element root = doc.getRootElement();

            // Get all <file> elements for parallel processing
            List<Element> fileElements = root.getChildren("file");

            if (fileElements.isEmpty()) {
                // No file elements, just recurse normally
                Context ctx = new Context();
                ctx.segmenter = SegmenterPool.getSegmenter(srx, srcLang, catalog);
                recurse(ctx, root);
            } else {
                // Process file elements in parallel
                try (ExecutorService executor = Executors.newFixedThreadPool(maxThreads)) {
                    List<Future<Void>> futures = new ArrayList<>();

                    for (Element fileElement : fileElements) {
                        Callable<Void> task = () -> {
                            Context ctx = new Context();
                            ctx.segmenter = SegmenterPool.getSegmenter(srx, srcLang, catalog);
                            recurse(ctx, fileElement);
                            return null;
                        };
                        futures.add(executor.submit(task));
                    }

                    // Wait for all tasks to complete
                    for (Future<Void> future : futures) {
                        future.get();
                    }

                    executor.shutdown();
                }
            }

            try (FileOutputStream out = new FileOutputStream(new File(xliff))) {
                XMLOutputter outputter = new XMLOutputter();
                outputter.preserveSpace(true);
                Indenter.indent(root, 2);
                outputter.output(doc, out);
            }
            result.add(Constants.SUCCESS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            result.add(Constants.ERROR);
            result.add(ie.getMessage());
        } catch (Exception e) {
            Logger logger = System.getLogger(Resegmenter.class.getName());
            logger.log(Level.ERROR, Messages.getString("Resegmenter.1"), e);
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }

    private static boolean startsWithTag(Element e) {
        return e.getChildren().size() == 1 && e.getContent().get(0).getNodeType() == XMLNode.ELEMENT_NODE;
    }

    private static boolean endsWithTag(Element e) {
        return e.getChildren().size() == 1
                && e.getContent().get(e.getContent().size() - 1).getNodeType() == XMLNode.ELEMENT_NODE;
    }

    private static boolean surroundedWithTags(Element e) {
        return e.getChildren().size() == 2 && e.getContent().get(0).getNodeType() == XMLNode.ELEMENT_NODE
                && e.getContent().get(e.getContent().size() - 1).getNodeType() == XMLNode.ELEMENT_NODE;
    }

    private static void recurse(Context ctx, Element root)
            throws SAXException, IOException, ParserConfigurationException {
        if ("file".equals(root.getName())) {
            ctx.canResegment = "yes".equals(root.getAttributeValue("canResegment", "yes"));
            ctx.translate = "yes".equals(root.getAttributeValue("translate", "yes"));
        } else if (root.hasAttribute("canResegment")) {
            ctx.canResegment = "yes"
                    .equals(root.getAttributeValue("canResegment", ctx.canResegment ? "yes" : "no"));
            ctx.translate = "yes"
                    .equals(root.getAttributeValue("translate", ctx.translate ? "yes" : "no"));
        }
        if ("unit".equals(root.getName())) {
            boolean hasMatches = !root.getChildren("mtc:matches").isEmpty();
            if (ctx.translate && ctx.canResegment && !hasMatches && root.getChildren("segment").size() == 1) {
                Element segment = root.getChild("segment");
                String originalId = segment.getAttributeValue("id");
                String unitId = root.getAttributeValue("id");
                Element source = segment.getChild("source");
                Element target = segment.getChild("target");
                boolean isSourceCopy = target != null && source.getContent().equals(target.getContent());
                boolean isEmpty = target != null && target.getContent().isEmpty();
                if (target == null || isSourceCopy || isEmpty) {
                    Element segSource = ctx.segmenter.segment(source);
                    int newSegments = segSource.getChildren("mrk").size();
                    int id = 0;
                    root.removeChild(segment);
                    List<XMLNode> content = segSource.getContent();
                    for (XMLNode n : content) {
                        if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
                            Element e = (Element) n;
                            if ("mrk".equals(e.getName()) && "seg".equals(e.getAttributeValue("mtype"))) {
                                boolean surrounded = surroundedWithTags(e);
                                if (surrounded || startsWithTag(e)) {
                                    // starts with tag
                                    Element firstTag = e.getChildren().get(0);
                                    if (!hasText(firstTag)) {
                                        Element ignorable = new Element("ignorable");
                                        Element ignorableSource = new Element("source");
                                        ignorableSource.setAttribute("xml:space", "preserve");
                                        ignorable.addContent(ignorableSource);
                                        ignorableSource.addContent(firstTag);
                                        e.removeChild(firstTag);
                                        root.addContent(ignorable);
                                    }
                                }
                                Element lastIgnorable = null;
                                if (surrounded || endsWithTag(e)) {
                                    // ends with tag
                                    List<Element> tags = e.getChildren();
                                    Element lastTag = tags.get(tags.size() - 1);
                                    if (!hasText(lastTag)) {
                                        lastIgnorable = new Element("ignorable");
                                        Element ignorableSource = new Element("source");
                                        ignorableSource.setAttribute("xml:space", "preserve");
                                        lastIgnorable.addContent(ignorableSource);
                                        ignorableSource.addContent(lastTag);
                                        e.removeChild(lastTag);
                                    }
                                }
                                Element newSeg = new Element("segment");
                                if (!hasText(e)) {
                                    newSeg = new Element("ignorable");
                                }
                                newSeg.setAttribute("id", newSegments == 1 ? originalId : unitId + '-' + id++);
                                root.addContent(newSeg);
                                Element newSource = new Element("source");
                                newSource.setAttribute("xml:space", source.getAttributeValue("xml:space", "default"));
                                if ("ignorable".equals(newSeg.getName())) {
                                    newSource.setAttribute("xml:space", "preserve");
                                }
                                newSeg.addContent(newSource);
                                newSource.addContent(e.getContent());
                                if (isSourceCopy) {
                                    Element newTarget = new Element("target");
                                    newTarget.setAttribute("xml:space",
                                            source.getAttributeValue("xml:space", "default"));
                                    newSeg.addContent(newTarget);
                                    newTarget.addContent(e.getContent());
                                }
                                if (lastIgnorable != null) {
                                    root.addContent(lastIgnorable);
                                }
                            } else {
                                MessageFormat mf = new MessageFormat(Messages.getString("Resegmenter.2"));
                                throw new SAXException(mf.format(new String[] { e.toString() }));
                            }
                        }
                    }
                }
            }
        } else {
            List<Element> children = root.getChildren();
            for (Element child : children) {
                recurse(ctx, child);
            }
        }
    }

    private static boolean hasText(Element e) {
        List<XMLNode> content = e.getContent();
        for (XMLNode node : content) {
            if (node.getNodeType() == XMLNode.TEXT_NODE) {
                TextNode t = (TextNode) node;
                if (!t.getText().isBlank()) {
                    return true;
                }
            }
            if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
                Element child = (Element) node;
                if (hasText(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void help() {
        MessageFormat mf = new MessageFormat(Messages.getString("Resegmenter.help"));
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
        String help = mf.format(new String[] { isWindows ? "resegment.cmd" : "resegment.sh" });
        System.out.println(help);
    }
}