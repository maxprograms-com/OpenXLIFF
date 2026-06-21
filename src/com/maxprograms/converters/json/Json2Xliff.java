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

package com.maxprograms.converters.json;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.segmenter.Segmenter;
import com.maxprograms.segmenter.SegmenterPool;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.DTDParser;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.EntityDecl;
import com.maxprograms.xml.Grammar;
import com.maxprograms.xml.XMLUtils;

public class Json2Xliff {

    private static final class Context {
        boolean paragraphSegmentation;
        Segmenter segmenter;
        Segmenter targetSegmenter;
        String tgtLang = "";
        int id;
        final List<Element> segments = new ArrayList<>();
        final Set<String> ids = new HashSet<>();
        List<String[]> entities = new ArrayList<>();
        final Map<String, String> entitiesFound = new HashMap<>();
        boolean trimTags = true;
        boolean mergeTags = true;
        boolean rawSegmentation;
        List<String> htmlIgnore = new ArrayList<>();
        boolean exportHTML;
    }

    private Json2Xliff() {
        // do not instantiate this class
        // use run method instead
    }

    public static List<String> run(Map<String, String> params) {
        Context ctx = new Context();
        List<String> result = new ArrayList<>();

        String inputFile = params.get("source");
        String xliffFile = params.get("xliff");
        String skeletonFile = params.get("skeleton");
        String sourceLanguage = params.get("srcLang");
        String targetLanguage = params.get("tgtLang");
        String encoding = params.get("srcEncoding");
        ctx.paragraphSegmentation = "yes".equals(params.get("paragraph"));
        String initSegmenter = params.get("srxFile");
        String catalogFile = params.get("catalog");
        if (targetLanguage != null) {
            ctx.tgtLang = "\" target-language=\"" + targetLanguage;
        }
        try {
            Catalog catalog = CatalogBuilder.getCatalog(catalogFile);
            Object json = loadFile(ctx, inputFile, encoding);
            if (!ctx.paragraphSegmentation) {
                ctx.segmenter = SegmenterPool.getSegmenter(initSegmenter, sourceLanguage, catalog);
                if (targetLanguage != null) {
                    ctx.targetSegmenter = SegmenterPool.getSegmenter(initSegmenter, targetLanguage, catalog);
                }
            }
            String configFile = params.get("config");
            if (configFile != null) {
                JsonConfig config = JsonConfig.parseFile(configFile);
                ctx.trimTags = config.getTrimTags();
                ctx.mergeTags = config.getMergeTags();
                ctx.rawSegmentation = config.getRawSegmentation();
                ctx.exportHTML = config.getExportHTML();
                ctx.htmlIgnore = new ArrayList<>(config.getHtmlIgnore());
                if (config.getParseEntities()) {
                    ctx.entities = loadEntities(catalog);
                    ctx.entities.add(new String[] { "&lt;", "<" });
                    ctx.entities.add(new String[] { "&amp;", "&" });
                }
                if (json instanceof JSONObject obj) {
                    parseJson(ctx, obj, config);
                } else {
                    parseArray(ctx, (JSONArray) json, config);
                }
            } else {
                if (json instanceof JSONObject obj) {
                    parseJson(ctx, obj);
                } else {
                    parseArray(ctx, (JSONArray) json);
                }
            }

            if (ctx.segments.isEmpty()) {
                result.add(Constants.ERROR);
                result.add(Messages.getString("Json2Xliff.1"));
                return result;
            }

            try (FileOutputStream out = new FileOutputStream(skeletonFile)) {
                if (json instanceof JSONObject obj) {
                    out.write(obj.toString(2).getBytes(StandardCharsets.UTF_8));
                } else {
                    out.write(((JSONArray) json).toString(2).getBytes(StandardCharsets.UTF_8));
                }
            }

            try (FileOutputStream out = new FileOutputStream(xliffFile)) {
                writeString(out, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                writeString(out, "<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");
                writeString(out, "<file original=\"" + inputFile + "\" source-language=\"" + sourceLanguage
                        + ctx.tgtLang + "\" tool-id=\"" + Constants.TOOLID + "\" datatype=\"x-json\">\n");
                writeString(out, "<header>\n");
                writeString(out, "   <skl>\n");
                writeString(out, "      <external-file href=\"" + XMLUtils.cleanText(skeletonFile) + "\"/>\n");
                writeString(out, "   </skl>\n");
                writeString(out, "   <tool tool-version=\"" + Constants.VERSION + " " + Constants.BUILD
                        + "\" tool-id=\"" + Constants.TOOLID + "\" tool-name=\"" + Constants.TOOLNAME + "\"/>\n");
                writeString(out, "</header>\n");
                if (!ctx.entities.isEmpty() && !ctx.exportHTML) {
                    writeString(out, "<?escaped yes?>\n");
                }
                if (ctx.exportHTML) {
                    writeString(out, "<?exportHTML yes?>\n");
                    if (!ctx.entitiesFound.isEmpty()) {
                        StringBuilder sb = new StringBuilder("<?entities ");
                        Iterator<String> it = ctx.entitiesFound.keySet().iterator();
                        while (it.hasNext()) {
                            String key = it.next();
                            String value = ctx.entitiesFound.get(key);
                            sb.append(key);
                            sb.append("|");
                            sb.append(value);
                            if (it.hasNext()) {
                                sb.append(",");
                            }
                        }
                        sb.append("?>\n");
                        writeString(out, sb.toString());
                    }
                }
                writeString(out, "<?encoding " + encoding + "?>\n");
                writeString(out, "<body>\n");

                for (Element segment : ctx.segments) {
                    writeString(out, "  " + segment + "\n");
                }

                writeString(out, "</body>\n");
                writeString(out, "</file>\n");
                writeString(out, "</xliff>");
            }
            result.add(Constants.SUCCESS);
        } catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
            Logger logger = System.getLogger(Json2Xliff.class.getName());
            logger.log(Level.ERROR, e);
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }

    protected static List<String[]> loadEntities(Catalog catalog)
            throws SAXException, IOException, NumberFormatException {
        List<String[]> result = new ArrayList<>();

        Pattern pattern = Pattern.compile("&#[\\d]+;");

        DTDParser parser = new DTDParser();
        String latin = catalog.matchPublic("-//W3C//ENTITIES Latin 1 for XHTML//EN");
        Grammar grammar = parser.parse(new File(latin));
        List<EntityDecl> declarations = grammar.getEntities();
        for (EntityDecl e : declarations) {
            String value = e.getValue();
            Matcher matcher = pattern.matcher(value);
            if (matcher.matches()) {
                value = toUnicode(value);
                result.add(new String[] { "&" + e.getName() + ";", value });
            }
        }

        String special = catalog.matchPublic("-//W3C//ENTITIES Special for XHTML//EN");
        grammar = parser.parse(new File(special));
        declarations = grammar.getEntities();
        for (EntityDecl e : declarations) {
            String value = e.getValue();
            Matcher matcher = pattern.matcher(value);
            if (matcher.matches()) {
                value = toUnicode(value);
                result.add(new String[] { "&" + e.getName() + ";", value });
            }
        }

        String symbols = catalog.matchPublic("-//W3C//ENTITIES Symbols for XHTML//EN");
        grammar = parser.parse(new File(symbols));
        declarations = grammar.getEntities();
        for (EntityDecl e : declarations) {
            String value = e.getValue();
            Matcher matcher = pattern.matcher(value);
            if (matcher.matches()) {
                value = toUnicode(value);
                result.add(new String[] { "&" + e.getName() + ";", value });
            }
        }
        return result;
    }

    private static String toUnicode(String value) throws NumberFormatException {
        String code = value.substring(2, value.length() - 1);
        return "" + (char) Integer.parseUnsignedInt(code);
    }

    private static void writeString(FileOutputStream out, String string) throws IOException {
        out.write(string.getBytes(StandardCharsets.UTF_8));
    }

    protected static Object loadFile(Context ctx, String file, String charset) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (FileReader stream = new FileReader(new File(file), Charset.forName(charset))) {
            try (BufferedReader reader = new BufferedReader(stream)) {
                String line = "";
                while ((line = reader.readLine()) != null) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(line);
                }
            }
        }
        String content = builder.toString();
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }
        int index = 0;
        while (index < content.length() && Character.isWhitespace(content.charAt(index))) {
            index++;
        }
        if (index >= content.length()) {
            throw new IOException(Messages.getString("Json2Xliff.2"));
        }
        char first = content.charAt(index);
        if (first == '[') {
            return new JSONArray(content);
        }
        if (first == '{') {
            return new JSONObject(content);
        }
        throw new IOException(Messages.getString("Json2Xliff.2"));
    }

    protected static Object loadFile(String file, String charset) throws IOException {
        return loadFile(new Context(), file, charset);
    }

    private static void parseJson(Context ctx, JSONObject json) {
        Iterator<String> it = json.keys();
        while (it.hasNext()) {
            String key = it.next();
            Object obj = json.get(key);
            if (obj instanceof JSONObject js) {
                parseJson(ctx, js);
            } else if (obj instanceof String string) {
                json.put(key, parseText(ctx, string, false));
            } else if (obj instanceof JSONArray array) {
                parseArray(ctx, array);
            }
        }
    }

    private static void parseJson(Context ctx, JSONObject json, JsonConfig config)
            throws IOException, SAXException, ParserConfigurationException {
        List<String> translatableKeys = config.getSourceKeys();
        List<String> ignorable = config.getIgnorableKeys();
        Set<String> parsedKeys = new HashSet<>();
        for (int i = 0; i < translatableKeys.size(); i++) {
            String sourceKey = translatableKeys.get(i);
            if (json.has(sourceKey) && json.get(sourceKey) instanceof String && !json.getString(sourceKey).isEmpty()) {
                JSONObject configuration = config.getConfiguration(sourceKey);
                if (configuration == null) {
                    MessageFormat mf = new MessageFormat(Messages.getString("Json2Xliff.3"));
                    throw new IOException(mf.format(new String[] { sourceKey }));
                }
                String sourceText = json.get(sourceKey) instanceof String ? json.getString(sourceKey) : "";
                if (!ctx.entities.isEmpty()) {
                    sourceText = replaceEntities(ctx, sourceText);
                }
                String targetKey = configuration.has(JsonConfig.TARGETKEY)
                        ? configuration.getString(JsonConfig.TARGETKEY)
                        : "";
                String targetText = json.has(targetKey) && json.get(targetKey) instanceof String
                        ? json.getString(targetKey)
                        : "";
                if (!ctx.entities.isEmpty()) {
                    targetText = replaceEntities(ctx, targetText);
                }
                String idKey = configuration.has(JsonConfig.IDKEY) ? configuration.getString(JsonConfig.IDKEY) : "";
                String idString = "";
                if (json.has(idKey)) {
                    Object obj = json.get(idKey);
                    if (obj instanceof String string) {
                        idString = string;
                    }
                    if (obj instanceof Integer j) {
                        idString = "" + j;
                    }
                }
                if (!idString.isEmpty()) {
                    validateId(idString);
                }
                boolean approved = false;
                if (configuration.has(JsonConfig.APPROVEDKEY)) {
                    Object obj = json.get(configuration.getString(JsonConfig.APPROVEDKEY));
                    if (obj instanceof Boolean b) {
                        approved = b;
                    }
                    if (obj instanceof String string) {
                        approved = "yes".equalsIgnoreCase(string);
                    }
                    parsedKeys.add(configuration.getString(JsonConfig.APPROVEDKEY));
                }
                String resnameKey = configuration.has(JsonConfig.RESNAMEKEY)
                        ? configuration.getString(JsonConfig.RESNAMEKEY)
                        : "";
                String resnameText = json.has(resnameKey) ? json.getString(resnameKey) : "";
                String noteKey = configuration.has(JsonConfig.NOTEKEY) ? configuration.getString(JsonConfig.NOTEKEY)
                        : "";
                List<String> notes = json.has(noteKey) ? harvestNotes(ctx, json.get(noteKey))
                        : new ArrayList<>();
                boolean replicate = false;
                if (configuration.has(JsonConfig.REPLICATE)) {
                    replicate = configuration.getBoolean(JsonConfig.REPLICATE);
                }
                parsedKeys.add(sourceKey);
                if (!targetKey.isEmpty() && !ctx.tgtLang.isEmpty()) {
                    parsedKeys.add(targetKey);
                }
                if (!idKey.isEmpty()) {
                    parsedKeys.add(idKey);
                }
                if (!noteKey.isEmpty()) {
                    parsedKeys.add(noteKey);
                }
                if (!resnameText.isEmpty()) {
                    parsedKeys.add(resnameKey);
                }
                ElementHolder sourceHolder = ElementBuilder.buildElement("source", sourceText, ctx.trimTags,
                        ctx.mergeTags, ctx.htmlIgnore, config.getPreserveSpaces());
                ElementHolder targetHolder = ElementBuilder.buildElement("target", targetText, ctx.trimTags,
                        ctx.mergeTags, ctx.htmlIgnore, config.getPreserveSpaces());

                Element fullSource = sortTags(sourceHolder.getElement());
                Element fullTarget = matchTags(fullSource, targetHolder.getElement());

                List<Element> sourceList = new ArrayList<>();
                if (ctx.segmenter != null) {
                    Element segmentedSource = ctx.segmenter.segment(fullSource);
                    sourceList.addAll(splitSegmented("source", segmentedSource));
                } else {
                    sourceList.add(fullSource);
                }
                List<Element> targetList = new ArrayList<>();
                if (ctx.targetSegmenter != null) {
                    Element segmentedTarget = ctx.targetSegmenter.segment(fullTarget);
                    targetList.addAll(splitSegmented("target", segmentedTarget));
                } else {
                    targetList.add(fullTarget);
                }

                if (sourceList.size() != targetList.size()) {
                    sourceList.clear();
                    sourceList.add(fullSource);
                    targetList.clear();
                    targetList.add(fullTarget);
                }

                StringBuilder sb = new StringBuilder();
                for (int h = 0; h < sourceList.size(); h++) {
                    Element transUnit = new Element("trans-unit");
                    if (!resnameText.isEmpty()) {
                        transUnit.setAttribute("resname", resnameText);
                    }
                    String suffix = sourceList.size() > 1 ? "-" + (h + 1) : "";
                    transUnit.setAttribute("id", idString.isEmpty() ? "" + ctx.id : idString + suffix);
                    if (ctx.ids.contains(transUnit.getAttributeValue("id"))) {
                        MessageFormat mf = new MessageFormat(Messages.getString("Json2Xliff.4"));
                        throw new IOException(mf.format(new String[] { transUnit.getAttributeValue("id") }));
                    }
                    ctx.ids.add(transUnit.getAttributeValue("id"));
                    transUnit.addContent("\n    ");

                    Element source = sourceList.get(h);
                    transUnit.addContent(source);
                    if (transUnit.getChild("source").getChildren().isEmpty() || config.getPreserveSpaces()) {
                        transUnit.setAttribute("xml:space", "preserve");
                    }
                    boolean hasTarget = false;
                    if (ctx.tgtLang.isEmpty() || targetText.isEmpty()) {
                        if (h == 0) {
                            sb.append(sourceHolder.getStart());
                        }
                        sb.append("%%%");
                        sb.append(idString.isEmpty() ? "" + ctx.id++ : transUnit.getAttributeValue("id"));
                        sb.append("%%%");
                        if (h == sourceList.size() - 1) {
                            sb.append(sourceHolder.getEnd());
                        }
                        json.put(sourceKey, sb.toString());
                    } else {
                        Element target = targetList.get(h);
                        transUnit.addContent("\n    ");
                        transUnit.addContent(target);
                        hasTarget = true;
                        if (h == 0) {
                            sb.append(targetHolder.getStart());
                        }
                        sb.append("%%%");
                        sb.append(idString.isEmpty() ? "" + ctx.id++ : transUnit.getAttributeValue("id"));
                        sb.append("%%%");
                        if (h == sourceList.size() - 1) {
                            sb.append(targetHolder.getEnd());
                        }
                        json.put(targetKey, sb.toString());
                    }
                    if (approved && hasTarget) {
                        transUnit.setAttribute("approved", "yes");
                    }
                    boolean hasCrlf = hasTarget ? transUnit.getChild("target").toString().indexOf("\r\n") != -1
                            : source.toString().indexOf("\r\n") != -1;
                    if (hasCrlf) {
                        transUnit.setAttribute("ts", "crlf");
                    }
                    if (!notes.isEmpty() && (replicate || h == 0)) {
                        Iterator<String> noteIt = notes.iterator();
                        while (noteIt.hasNext()) {
                            Element note = new Element("note");
                            note.setText(noteIt.next());
                            transUnit.addContent("\n    ");
                            transUnit.addContent(note);
                        }
                    }
                    transUnit.addContent("\n  ");
                    if (transUnit.getChild("source").getText().isBlank()) {
                        String idRef = "%%%" + transUnit.getAttributeValue("id") + "%%%";
                        Element target = transUnit.getChild("target");
                        if (target != null) {
                            json.put(targetKey, json.getString(targetKey).replace(idRef, target.getText()));
                        } else {
                            json.put(sourceKey,
                                    json.getString(sourceKey).replace(idRef, transUnit.getChild("source").getText()));
                        }
                    } else {
                        ctx.segments.add(transUnit);
                    }
                }
            }
        }
        Iterator<String> it = json.keys();
        while (it.hasNext()) {
            String key = it.next();
            if (!parsedKeys.contains(key) && !ignorable.contains(key)) {
                Object object = json.get(key);
                if (object instanceof JSONObject jsobj) {
                    parseJson(ctx, jsobj, config);
                } else if (object instanceof String string) {
                    if (!ctx.entities.isEmpty()) {
                        string = replaceEntities(ctx, string);
                    }
                    json.put(key, parseText(ctx, string, config.getPreserveSpaces()));
                } else if (object instanceof JSONArray array) {
                    parseArray(ctx, array, config);
                }
            }
        }
    }

    private static List<Element> splitSegmented(String name, Element segmented) {
        List<Element> result = new ArrayList<>();
        List<Element> marks = segmented.getChildren("mrk");
        for (int i = 0; i < marks.size(); i++) {
            Element mrk = marks.get(i);
            Element e = new Element(name);
            e.setContent(mrk.getContent());
            result.add(e);
        }
        return result;
    }

    private static Element sortTags(Element source) {
        List<Element> sourceTags = source.getChildren();
        Iterator<Element> it = sourceTags.iterator();
        int count = 0;
        while (it.hasNext()) {
            it.next().setAttribute("id", "" + count++);
        }
        return source;
    }

    private static Element matchTags(Element source, Element target) {
        List<Element> sourceTags = source.getChildren();
        int extra = sourceTags.size();
        Set<String> usedIds = new TreeSet<>();
        List<Element> targetTags = target.getChildren();
        Iterator<Element> it = targetTags.iterator();
        while (it.hasNext()) {
            Element tag = it.next();
            String text = tag.getText();
            boolean found = false;
            for (int i = 0; i < sourceTags.size(); i++) {
                Element sourceTag = sourceTags.get(i);
                String id = sourceTag.getAttributeValue("id");
                if (text.equals(sourceTag.getText()) && !usedIds.contains(id)) {
                    tag.setAttribute("id", id);
                    usedIds.add(id);
                    found = true;
                    break;
                }
            }
            if (!found) {
                tag.setAttribute("id", "" + extra++);
            }
        }
        return target;
    }

    private static String replaceEntities(Context ctx, String string) {
        if (string.isEmpty() || ctx.entities.isEmpty()) {
            return string;
        }
        String result = string;
        for (String[] entry : ctx.entities) {
            String key = entry[0];
            String value = entry[1];
            int index = result.indexOf(key);
            while (index != -1) {
                if (!"&gt;".equals(key) && !"&lt;".equals(key) && !"&amp;".equals(key) && !"&quote;".equals(key)) {
                    ctx.entitiesFound.put(key, value);
                }
                String start = result.substring(0, index);
                String end = result.substring(index + key.length());
                result = start + value + end;
                index = result.indexOf(key);
            }
        }
        return result;
    }

    private static void validateId(String id) throws IOException {
        String[] nameStart = new String[] { ":", "[A-Z]", "_", "[a-z]", "[\\u00C0-\\u00D6]", "[\\u00D8-\\u00F6]",
                "[\\u00F8-\\u02FF]", "[\\u0370-\\u037D]", "[\\u037F-\\u1FFF]", "[\\u200C-\\u200D]", "[\\u2070-\\u218F]",
                "[\\u2C00-\\u2FEF]", "[\\u3001-\\uD7FF]", "[\\uF900-\\uFDCF]", "[\\uFDF0-\\uFFFD]",
                "[\\u10000-\\uEFFFF]" };
        String[] nameChar = new String[] { ":", "[A-Z]", "_", "[a-z]", "[-]", "[.]", "[0-9]", "\u00B7",
                "[\\u00C0-\\u00D6]", "[\\u00D8-\\u00F6]", "[\\u00F8-\\u02FF]", "[\\u0370-\\u037D]", "[\\u037F-\\u1FFF]",
                "[\\u200C-\\u200D]", "[\\u2070-\\u218F]", "[\\u2C00-\\u2FEF]", "[\\u3001-\\uD7FF]", "[\\uF900-\\uFDCF]",
                "[\\uFDF0-\\uFFFD]", "[\\u10000-\\uEFFFF]", "[\\u0300-\\u036F]", "[\\u203F-\\u2040]" };
        boolean first = false;
        String firstChar = "" + id.charAt(0);
        for (int i = 0; i < nameStart.length; i++) {
            if (firstChar.matches(nameStart[i])) {
                first = true;
                break;
            }
        }
        if (!first) {
            MessageFormat mf = new MessageFormat(Messages.getString("Json2Xliff.5"));
            throw new IOException(mf.format(new String[] { "" + id.charAt(0) }));
        }
        for (int i = 1; i < id.length(); i++) {
            boolean rest = false;
            String nextChar = "" + id.charAt(i);
            for (int j = 0; j < nameStart.length; j++) {
                String expr = nameChar[j];
                if (nextChar.matches(expr)) {
                    rest = true;
                    break;
                }
            }
            if (!rest) {
                MessageFormat mf = new MessageFormat(Messages.getString("Json2Xliff.6"));
                throw new IOException(mf.format(new String[] { "" + id.charAt(i) }));
            }
        }
    }

    private static List<String> harvestNotes(Context ctx, Object object) {
        List<String> result = new ArrayList<>();
        if (object instanceof JSONObject json) {
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                result.addAll(harvestNotes(ctx, json.get(keys.next())));
            }
        }
        if (object instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                result.addAll(harvestNotes(ctx, array.get(i)));
            }
        }
        if (object instanceof String string) {
            result.add(replaceEntities(ctx, string));
        }
        return result;
    }

    private static String parseText(Context ctx, String string, boolean preserveSpaces) {
        if (!ctx.paragraphSegmentation && ctx.segmenter != null) {
            String[] segs = ctx.rawSegmentation ? ctx.segmenter.segmentRawString(string) : ctx.segmenter.segment(string);
            StringBuilder result = new StringBuilder();
            for (String seg : segs) {
                result.append(addSegment(ctx, seg, preserveSpaces));
            }
            return result.toString();
        }
        return addSegment(ctx, string, preserveSpaces);
    }

    private static String addSegment(Context ctx, String string, boolean preserveSpaces) {
        Element segment = new Element("trans-unit");
        segment.setAttribute("id", "" + ctx.id);
        segment.addContent("\n    ");
        ElementHolder holder = ElementBuilder.buildElement("source", string, ctx.trimTags, ctx.mergeTags,
                ctx.htmlIgnore, preserveSpaces);
        segment.addContent(holder.getElement());
        segment.addContent("\n  ");
        if (holder.getElement().getChildren().isEmpty() || preserveSpaces) {
            segment.setAttribute("xml:space", "preserve");
        }
        ctx.segments.add(segment);
        return holder.getStart() + "%%%" + ctx.id++ + "%%%" + holder.getEnd();
    }

    private static void parseArray(Context ctx, JSONArray array) {
        for (int i = 0; i < array.length(); i++) {
            Object obj = array.get(i);
            if (obj instanceof String string) {
                array.put(i, parseText(ctx, string, false));
            } else if (obj instanceof JSONArray arr) {
                parseArray(ctx, arr);
            } else if (obj instanceof JSONObject json) {
                parseJson(ctx, json);
            }
        }
    }

    private static void parseArray(Context ctx, JSONArray array, JsonConfig config)
            throws JSONException, IOException, SAXException, ParserConfigurationException {
        for (int i = 0; i < array.length(); i++) {
            Object obj = array.get(i);
            if (obj instanceof String string) {
                array.put(i, parseText(ctx, string, config.getPreserveSpaces()));
            } else if (obj instanceof JSONArray arr) {
                parseArray(ctx, arr, config);
            } else if (obj instanceof JSONObject json) {
                parseJson(ctx, json, config);
            }
        }
    }

}
