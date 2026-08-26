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
package com.maxprograms.converters.xliff2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.converters.FileFormats;
import com.maxprograms.converters.xliff.XliffUtils;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;
import com.maxprograms.xml.XMLUtils;

public class ToOpenXliff2 {

    boolean usesMatches = false;
    boolean usesGlossary = false;
    int fileIndex = 0;
    int unitCounter = 1;
    int tagCounter = 1;
    String targetVersion = "";

    private ToOpenXliff2() {
        usesMatches = false;
        usesGlossary = false;
        fileIndex = 0;
        unitCounter = 1;
        tagCounter = 1;
    }

    public static List<String> run(Map<String, String> params) {
        ToOpenXliff2 instance = new ToOpenXliff2();
        return instance.convert(params);
    }

    private List<String> convert(Map<String, String> params) {
        List<String> result = new Vector<>();
        try {
            String inputFile = params.get("source");
            String xliffFile = params.get("xliff");
            String skeletonFile = params.get("skeleton");
            String catalog = params.get("catalog");

            String version = "";
            if ("yes".equals(params.get("xliff20"))) {
                version = "2.0";
            }
            if ("yes".equals(params.get("xliff21"))) {
                version = "2.1";
            }
            if ("yes".equals(params.get("xliff22"))) {
                version = "2.2";
            }
            if (version.isEmpty()) {
                result.add(Constants.ERROR);
                result.add(Messages.getString("ToOpenXliff2.4"));
                return result;
            }
            targetVersion = version;

            SAXBuilder builder = new SAXBuilder();
            builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));
            Document sourceDoc = builder.build(inputFile);
            Element sourceRoot = sourceDoc.getRootElement();

            String sourceVersion = sourceRoot.getAttributeValue("version", "");
            if (!sourceVersion.startsWith("2.")) {
                result.add(Constants.ERROR);
                result.add(Messages.getString("ToOpenXliff2.1"));
                return result;
            }

            Document outputDoc = new Document(null, "xliff", null, null);
            Element outputRoot = outputDoc.getRootElement();
            outputRoot.setAttribute("version", version);
            outputRoot.setAttribute("srcLang", sourceRoot.getAttributeValue("srcLang"));
            String trgLang = sourceRoot.getAttributeValue("trgLang");
            if (!trgLang.isEmpty()) {
                outputRoot.setAttribute("trgLang", trgLang);
            }
            if ("2.2".equals(version)) {
                outputRoot.setAttribute("xmlns", "urn:oasis:names:tc:xliff:document:2.2");
            } else {
                outputRoot.setAttribute("xmlns", "urn:oasis:names:tc:xliff:document:2.0");
            }
            outputRoot.setAttribute("xmlns:mda", "urn:oasis:names:tc:xliff:metadata:2.0");

            List<Element> sourceFiles = sourceRoot.getChildren("file");
            if (sourceFiles.isEmpty()) {
                result.add(Constants.ERROR);
                result.add(Messages.getString("ToOpenXliff2.2"));
                return result;
            }

            if (usesMatches) {
                outputRoot.setAttribute("xmlns:mtc", "urn:oasis:names:tc:xliff:matches:2.0");
            }
            if (usesGlossary) {
                outputRoot.setAttribute("xmlns:gls", "urn:oasis:names:tc:xliff:glossary:2.0");
            }

            Element sourceRootNotes = sourceRoot.getChild("notes");
            if (sourceRootNotes != null && "2.2".equals(version)) {
                Element rootNotesCopy = new Element("notes");
                rootNotesCopy.clone(sourceRootNotes);
                outputRoot.addContent(rootNotesCopy);
            }

            Element sourceRootMetadata = sourceRoot.getChild("mda:metadata");
            if (sourceRootMetadata != null && "2.2".equals(version)) {
                Element rootMetadataCopy = new Element("mda:metadata");
                rootMetadataCopy.clone(sourceRootMetadata);
                outputRoot.addContent(rootMetadataCopy);
            }

            for (Element sourceFile : sourceFiles) {
                Element outputFile = processFile(sourceFile, skeletonFile, version);
                outputRoot.addContent(outputFile);
            }

            try (FileOutputStream sklOut = new FileOutputStream(skeletonFile)) {
                XMLOutputter outputter = new XMLOutputter();
                outputter.preserveSpace(true);
                outputter.output(sourceDoc, sklOut);
            }

            Indenter.indent(outputRoot, 2);
            try (FileOutputStream out = new FileOutputStream(new File(xliffFile))) {
                XMLOutputter outputter = new XMLOutputter();
                outputter.preserveSpace(true);
                out.write(XMLUtils.UTF8BOM);
                outputter.output(outputDoc, out);
            }

            result.add(Constants.SUCCESS);
            params.put("resegment", "no");
        } catch (SAXException | IOException | ParserConfigurationException | URISyntaxException e) {
            Logger logger = System.getLogger(ToOpenXliff2.class.getName());
            logger.log(Level.ERROR, Messages.getString("ToOpenXliff2.3"), e);
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }

    private Element processFile(Element sourceFile, String skeletonFile, String version) {
        Element outputFile = new Element("file");

        outputFile.setAttribute("id", "" + ++fileIndex);
        outputFile.setAttribute("original", sourceFile.getAttributeValue("original"));
        outputFile.setAttribute("canResegment", "no");

        Element skeleton = new Element("skeleton");
        skeleton.setAttribute("href", skeletonFile);
        outputFile.addContent(skeleton);

        Element metadata = new Element("mda:metadata");

        Element formatGroup = new Element("mda:metaGroup");
        formatGroup.setAttribute("category", "format");
        Element formatMeta = new Element("mda:meta");
        formatMeta.setAttribute("type", "datatype");
        formatMeta.setText("x-xliff2");
        formatGroup.addContent(formatMeta);
        metadata.addContent(formatGroup);

        Element originGroup = new Element("mda:metaGroup");
        originGroup.setAttribute("category", "origin");
        Element originMeta = new Element("mda:meta");
        originMeta.setAttribute("type", "original-format");
        originMeta.setText(FileFormats.XLIFF2);
        originGroup.addContent(originMeta);
        Element versionMeta = new Element("mda:meta");
        versionMeta.setAttribute("type", "xliff-version");
        versionMeta.setText(version);
        originGroup.addContent(versionMeta);
        metadata.addContent(originGroup);

        Element toolGroup = new Element("mda:metaGroup");
        toolGroup.setAttribute("category", "tool");
        Element toolId = new Element("mda:meta");
        toolId.setAttribute("type", "tool-id");
        toolId.setText(Constants.TOOLID);
        toolGroup.addContent(toolId);
        Element toolName = new Element("mda:meta");
        toolName.setAttribute("type", "tool-name");
        toolName.setText(Constants.TOOLNAME);
        toolGroup.addContent(toolName);
        Element toolVersion = new Element("mda:meta");
        toolVersion.setAttribute("type", "tool-version");
        toolVersion.setText(Constants.VERSION);
        toolGroup.addContent(toolVersion);
        metadata.addContent(toolGroup);

        Element sourceFileMetadata = sourceFile.getChild("mda:metadata");
        if (sourceFileMetadata != null) {
            for (Element metaGroup : sourceFileMetadata.getChildren("mda:metaGroup")) {
                Element metaGroupCopy = new Element("mda:metaGroup");
                metaGroupCopy.clone(metaGroup);
                metadata.addContent(metaGroupCopy);
            }
        }

        outputFile.addContent(metadata);

        Element sourceNotes = sourceFile.getChild("notes");
        if (sourceNotes != null) {
            Element notesCopy = new Element("notes");
            notesCopy.clone(sourceNotes);
            outputFile.addContent(notesCopy);
        }

        unitCounter = 1;
        String fileTranslate = sourceFile.getAttributeValue("translate", "yes");
        appendUnits(sourceFile, outputFile, fileTranslate);
        return outputFile;
    }

    private void appendUnits(Element sourceContainer, Element outputFile, String inheritedTranslate) {
        for (Element child : sourceContainer.getChildren()) {
            String name = child.getName();
            if ("unit".equals(name)) {
                String translate = child.getAttributeValue("translate", inheritedTranslate);
                outputFile.addContent(processUnit(child, unitCounter++, translate));
            } else if ("group".equals(name)) {
                String groupTranslate = child.getAttributeValue("translate", inheritedTranslate);
                appendUnits(child, outputFile, groupTranslate);
            }
        }
    }

    private Element processUnit(Element sourceUnit, int unitIndex, String translate) {
        String unitId = "" + unitIndex;
        Element outputUnit = new Element("unit");
        outputUnit.setAttribute("id", unitId);
        outputUnit.setAttribute("canResegment", "no");
        if ("no".equals(translate)) {
            outputUnit.setAttribute("translate", translate);
        }

        Element matches = sourceUnit.getChild("mtc:matches");
        Element matchesCopy = null;
        if (matches != null) {
            matchesCopy = new Element("mtc:matches");
            matchesCopy.clone(matches);
            this.usesMatches = true;
        }

        Element glossary = sourceUnit.getChild("gls:glossary");
        Element glossaryCopy = null;
        if (glossary != null) {
            glossaryCopy = new Element("gls:glossary");
            glossaryCopy.clone(glossary);
            this.usesGlossary = true;
        }

        Set<String> referencedNoteIds = collectReferencedNoteIds(sourceUnit);
        Set<String> segmentIds = ConcurrentHashMap.newKeySet();
        for (Element child : sourceUnit.getChildren("segment")) {
            String id = child.getAttributeValue("id");
            if (!id.isEmpty()) {
                segmentIds.add(id);
            }
        }
        Element sourceNotes = sourceUnit.getChild("notes");
        Element notesCopy = filterUsefulNotes(sourceNotes, referencedNoteIds, segmentIds);

        Element originalData = new Element("originalData");
        tagCounter = 1;

        List<Element> segments = new Vector<>();
        int segmentIndex = 1;
        for (Element child : sourceUnit.getChildren()) {
            if ("segment".equals(child.getName()) || "ignorable".equals(child.getName())) {
                segments.add(processSegment(child, unitId, segmentIndex++, originalData));
            }
        }

        // Schema order: [##other]*, notes?, originalData?, (segment|ignorable)+
        if (matchesCopy != null) {
            outputUnit.addContent(matchesCopy);
        }
        if (glossaryCopy != null) {
            outputUnit.addContent(glossaryCopy);
        }
        if (notesCopy != null) {
            outputUnit.addContent(notesCopy);
        }
        if (!originalData.getChildren("data").isEmpty()) {
            outputUnit.addContent(originalData);
        }
        for (Element segment : segments) {
            outputUnit.addContent(segment);
        }
        return outputUnit;
    }

    private Element processSegment(Element sourceSegment, String unitId, int segmentIndex,
            Element originalData) {
        String segmentId = "" + segmentIndex;
        Element outputSegment = new Element(sourceSegment.getName());
        outputSegment.setAttribute("id", segmentId);
        if (sourceSegment.hasAttribute("state")) {
            outputSegment.setAttribute("state", sourceSegment.getAttributeValue("state"));
        }
        String piData = fileIndex + "_" + unitId + "_" + segmentId;
        sourceSegment.addContent(new PI(Constants.TOOLID, piData));
        outputSegment.addContent(new PI(Constants.TOOLID, piData));
        for (Element child : sourceSegment.getChildren()) {
            String name = child.getName();
            if ("source".equals(name) || "target".equals(name)) {
                List<XMLNode> normalized = normalizeContent(child.getContent(), originalData);
                if ("target".equals(name) && normalized.isEmpty()) {
                    continue;
                }
                Element copy = new Element(name);
                copy.setContent(normalized);
                String space = child.getAttributeValue("xml:space", "default");
                if ("preserve".equals(space)) {
                    copy.setAttribute("xml:space", "preserve");
                }
                outputSegment.addContent(copy);
            }
        }
        return outputSegment;
    }

    private List<XMLNode> normalizeContent(List<XMLNode> content, Element originalData) {
        List<XMLNode> result = new Vector<>();
        for (XMLNode node : content) {
            if (node.getNodeType() == XMLNode.TEXT_NODE) {
                result.add(node);
            } else if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
                Element e = (Element) node;
                String name = e.getName();
                if ("pc".equals(name)) {
                    result.addAll(normalizePc(e, originalData));
                } else if ("ph".equals(name) || "sc".equals(name) || "ec".equals(name) || "cp".equals(name)
                        || "sm".equals(name) || "em".equals(name)) {
                    result.add(normalizeTag(e, originalData));
                } else {
                    Element copy = new Element(name);
                    copy.clone(e);
                    result.add(copy);
                }
            }
        }
        return result;
    }

    private List<XMLNode> normalizePc(Element e, Element originalData) {
        List<XMLNode> result = new Vector<>();

        String openId = e.getName() + tagCounter++;
        Element openData = new Element("data");
        openData.setAttribute("id", openId);
        openData.setText(XliffUtils.getHead(e));
        originalData.addContent(openData);

        Element openPh = new Element("ph");
        openPh.setAttribute("id", openId);
        result.add(openPh);

        result.addAll(normalizeContent(e.getContent(), originalData));

        String closeId = e.getName() + tagCounter++;
        Element closeData = new Element("data");
        closeData.setAttribute("id", closeId);
        closeData.setText("</" + e.getName() + ">");
        originalData.addContent(closeData);

        Element closePh = new Element("ph");
        closePh.setAttribute("id", closeId);
        result.add(closePh);

        return result;
    }

    private Element normalizeTag(Element e, Element originalData) {
        String id = e.getName() + tagCounter++;
        Element data = new Element("data");
        data.setAttribute("id", id);
        data.setText(e.toString());
        originalData.addContent(data);

        Element ph = new Element("ph");
        ph.setAttribute("id", id);
        return ph;
    }

    private Set<String> collectReferencedNoteIds(Element element) {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        if ("mrk".equals(element.getName()) && "comment".equals(element.getAttributeValue("type"))) {
            String ref = element.getAttributeValue("ref");
            if (ref.startsWith("#n") && ref.indexOf('=') != -1) {
                ids.add(ref.substring(ref.indexOf('=') + 1));
            }
        }
        for (Element child : element.getChildren()) {
            ids.addAll(collectReferencedNoteIds(child));
        }
        return ids;
    }

    private Element filterUsefulNotes(Element sourceNotes, Set<String> referencedNoteIds, Set<String> segmentIds) {
        if (sourceNotes == null) {
            return null;
        }
        boolean refSupported = "2.2".equals(targetVersion);
        List<Element> usefulNotes = new Vector<>();
        for (Element note : sourceNotes.getChildren("note")) {
            String id = note.getAttributeValue("id");
            boolean hasId = !id.isEmpty() && referencedNoteIds.contains(id);
            String ref = note.getAttributeValue("ref");
            boolean hasRef = refSupported && ref.startsWith("#") && segmentIds.contains(ref.substring(1));
            if (hasId || hasRef) {
                usefulNotes.add(note);
            }
        }
        if (usefulNotes.isEmpty()) {
            return null;
        }
        Element notesCopy = new Element("notes");
        for (Element note : usefulNotes) {
            Element copy = new Element("note");
            copy.clone(note);
            if (!refSupported) {
                copy.removeAttribute("ref");
            }
            notesCopy.addContent(copy);
        }
        return notesCopy;
    }
}
