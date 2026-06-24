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
package com.maxprograms.converters.xml;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.converters.Utils;
import com.maxprograms.converters.qti.QtiCheck;
import com.maxprograms.segmenter.Segmenter;
import com.maxprograms.segmenter.SegmenterPool;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.CData;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Comment;
import com.maxprograms.xml.DTDParser;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Grammar;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.SilentErrorHandler;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLUtils;

public class Xml2Xliff {

	private static final Logger logger = System.getLogger(Xml2Xliff.class.getName());

	static final String DOUBLEPRIME = "\u2033";
	static final String MATHLT = "\u2039";
	static final String MATHGT = "\u200B\u203A";
	static final String GAMP = "\u200B\u203A";

	private static Catalog catalog;
	private static String currentCatalog;
	private static Document ditaCache;

	private String inputFile;
	private String skeletonFile;
	private String sourceLanguage;
	private String srcEncoding;
	private FileOutputStream output;
	private FileOutputStream skeleton;
	private int segId;
	private int tagId;
	private List<String> segments;
	private Map<String, String> startsSegment;
	private Map<String, List<String>> translatableAttributes;
	private Map<String, String> inline;
	private Map<String, String> ctypes;
	private Map<String, String> keepFormating;
	private boolean segByElement;
	private String rootElement;
	private Map<String, String> entities;
	private String entitiesMap;
	private Element root;
	private String text;
	private Stack<String> stack;
	private String translatable = "";
	private boolean inDesign = false;
	private Map<String, String> ignore;
	private boolean resx;
	private String startText;
	private String endText;
	private boolean ditaBased;
	private boolean qtiBased;
	private String targetLanguage;
	private boolean inCData;
	private boolean translateComments;
	private boolean containsText;
	private boolean ignoreTC = false;
	private boolean arbortextDita = false;

	public static List<String> run(Map<String, String> params) {
		Xml2Xliff instance = new Xml2Xliff();
		return instance.runConversion(params);
	}

	private List<String> runConversion(Map<String, String> params) {
		List<String> result = new ArrayList<>();
		segId = 1;
		stack = new Stack<>();
		containsText = false;

		inputFile = params.get("source");
		String xliffFile = params.get("xliff");
		skeletonFile = params.get("skeleton");
		sourceLanguage = params.get("srcLang");
		targetLanguage = params.get("tgtLang");
		srcEncoding = params.get("srcEncoding");
		String catalogFile = params.get("catalog");
		String xmlfilter = params.get("xmlfilter");
		String elementSegmentation = params.get("paragraph");
		String initSegmenter = params.get("srxFile");
		String isInDesign = params.get("InDesign");
		inDesign = isInDesign != null;
		String isResx = params.get("resx");
		resx = isResx != null;
		ditaBased = "yes".equals(params.get("dita_based"));
		qtiBased = "yes".equals(params.get("qti"));
		Segmenter segmenter = null;

		String ignoreTrackedChanges = params.get("ignoretc");
		if (ignoreTrackedChanges != null) {
			ignoreTC = ignoreTrackedChanges.equals("yes");
		}
		boolean generic = false;
		String isGeneric = params.get("generic");
		if (isGeneric != null && isGeneric.equals("yes")) {
			generic = true;
		}

		String comments = params.get("translateComments");
		if (comments != null) {
			translateComments = comments.equalsIgnoreCase("yes");
		}

		try {
			synchronized (Xml2Xliff.class) {
				if (catalog == null || !catalogFile.equals(currentCatalog)) {
					catalog = CatalogBuilder.getCatalog(catalogFile);
					currentCatalog = catalogFile;
				}
			}
			boolean autoConfiguration = false;
			String iniFile = getIniFile(xmlfilter, inputFile);
			if (generic) {
				File temp = File.createTempFile("config_", ".xml");
				iniFile = temp.getAbsolutePath();
				AutoConfiguration.run(inputFile, iniFile, catalog);
				autoConfiguration = true;
			}
			File f = new File(iniFile);
			if (!f.exists()) {
				MessageFormat mf = new MessageFormat(Messages.getString("Xml2Xliff.0"));
				throw new IOException(mf.format(new Object[] { f.getName() }));
			}
			if (qtiBased && "yes".equals(params.get("strict"))) {
				List<String> list = QtiCheck.validateFile(inputFile, catalog);
				if (!Constants.SUCCESS.equals(list.get(0))) {
					return list;
				}
			}
			if (elementSegmentation == null) {
				segByElement = false;
			} else {
				if (elementSegmentation.equals("yes")) {
					segByElement = true;
				} else {
					segByElement = false;
				}
			}

			if (!segByElement) {
				segmenter = SegmenterPool.getSegmenter(initSegmenter, sourceLanguage, catalog);
			}

			String detected = getEncoding(inputFile);
			if (!srcEncoding.equals(detected)) {
				srcEncoding = detected;
			}

			try (FileInputStream input = new FileInputStream(inputFile)) {
				skeleton = new FileOutputStream(skeletonFile);
				output = new FileOutputStream(xliffFile);
				writeHeader();

				int size = input.available();
				byte[] array = new byte[size];
				if (size != input.read(array)) {
					result.add(Constants.ERROR);
					result.add(Messages.getString("Xml2Xliff.1"));
					return result;
				}
				String file = new String(array, srcEncoding);

				// remove xml declaration and doctype
				String target = '<' + rootElement;
				int balance = 0;
				for (int i = 0; i < file.length(); i++) {
					if (Utils.lookingAt(target, file, i) && balance == 0) {
						writeSkeleton(file.substring(0, i));
						break;
					}
					char c = file.charAt(i);
					if (c == '<') {
						balance++;
					}
					if (c == '>') {
						balance--;
					}
				}

				buildTables(iniFile);

				if (autoConfiguration) {
					Files.delete(new File(iniFile).toPath());
				}

				buildList();

				processList(segmenter);

				skeleton.close();
				writeString("</body>\n");
				writeString("</file>\n");
				writeString("</xliff>");
			}
			output.close();
			if (!containsText) {
				Files.deleteIfExists(new File(skeletonFile).toPath());
				Files.deleteIfExists(new File(xliffFile).toPath());
				result.add(Constants.ERROR);
				MessageFormat mf = new MessageFormat(Messages.getString("Xml2Xliff.2"));
				result.add(mf.format(new String[] { inputFile }));
				if (ditaBased || qtiBased || rootElement.equals("svg")) {
					result.add("EMPTY");
				} else {
					logger.log(Level.WARNING, mf.format(new String[] { inputFile }));
				}
				return result;
			}
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | URISyntaxException
				| IllegalArgumentException e) {
			logger.log(Level.ERROR, Messages.getString("Xml2Xliff.4"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	private String getIniFile(String filtersFolder, String fileName)
			throws SAXException, IOException, ParserConfigurationException {
		File folder = new File(filtersFolder);
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(catalog);
		builder.setValidating(false);
		builder.setErrorHandler(new SilentErrorHandler());
		Document doc = builder.build(fileName);
		entities = new HashMap<>();

		Map<String, String> map = doc.getEntities();

		entitiesMap = "";
		if (map != null) {
			Set<String> en = entities.keySet();
			for (String key : en) {
				entitiesMap = entitiesMap + "      <prop prop-type=\"" + key.substring(1, key.length() - 1) + "\">"
						+ cleanEntity(entities.get(key)) + "</prop>\n";
			}
		}

		// Add predefined standard entities
		entities.put("&gt;", ">");
		entities.put("&lt;", "<");
		entities.put("&amp;", "&");
		entities.put("&apos;", "'");
		entities.put("&quot;", "\"");

		root = doc.getRootElement();
		rootElement = root.getName();
		if (qtiBased) {
			File base = new File(folder, "config_qti.xml");
			return base.getAbsolutePath();
		}
		if (ditaBased && rootElement.equals("svg")) {
			ditaBased = false;
		}
		List<Attribute> attributes = root.getAttributes();
		for (Attribute att : attributes) {
			if (att.getValue().indexOf("arbortext:dita") != -1) {
				arbortextDita = true;
				File base = new File(folder, "config_dita.xml");
				return base.getAbsolutePath();
			}
		}
		String publicId = doc.getPublicId();
		if (publicId != null && !arbortextDita && publicId.indexOf(" DITA ") != -1) {
			ditaBased = true;
		}
		if (root.hasAttribute("class") && !arbortextDita
				&& root.getAttributeValue("class").indexOf("topic/topic") != -1) {
			ditaBased = true;
		}
		if (ditaBased) {
			File base = new File(folder, "config_dita.xml");
			synchronized (Xml2Xliff.class) {
				if (ditaCache == null) {
					ditaCache = builder.build(base);
				}
			}
			List<Element> list = ditaCache.getRootElement().getChildren();
			for (Element item : list) {
				if (rootElement.equals(item.getText().trim())) {
					return base.getAbsolutePath();
				}
			}
			String cls = root.getAttributeValue("class");
			String[] parts = cls.split("\\s");
			for (int h = 0; h < parts.length; h++) {
				String part = parts[h];
				if (part.indexOf('/') == -1) {
					continue;
				}
				String code = part.substring(part.indexOf('/') + 1).trim();
				for (Element item : list) {
					if (code.equals(item.getText().trim())) {
						return base.getAbsolutePath();
					}
				}
			}
			MessageFormat mf = new MessageFormat(Messages.getString("Xml2Xliff.5"));
			throw new IOException(mf.format(new Object[] { cls }));
		}

		// check for ResX before anything else
		// this requires a fixed ini name
		if (root.getName().equals("root")) {
			List<Element> dataElements = root.getChildren("data");
			if (!dataElements.isEmpty()) {
				boolean isResx = false;
				for (int i = 0; i < dataElements.size(); i++) {
					Element g = (dataElements.get(i)).getChild("translate");
					if (g != null) {
						isResx = true;
						break;
					}
				}
				if (isResx) {
					return new File(folder, "config_resx.xml").getAbsolutePath();
				}
			}
		}
		String pub = doc.getPublicId();
		if (pub != null && !pub.isEmpty()) {
			String location = catalog.matchPublic(pub);
			String s = getRootElement(location);
			if (s != null) {
				return new File(folder, "config_" + s + ".xml").getAbsolutePath();
			}
		}
		String sys = doc.getSystemId();
		if (sys != null) {
			// remove path from systemId
			if (sys.indexOf('/') != -1 && sys.lastIndexOf('/') < sys.length()) {
				sys = sys.substring(sys.lastIndexOf('/') + 1);
			}
			if (sys.indexOf('\\') != -1 && sys.lastIndexOf('/') < sys.length()) {
				sys = sys.substring(sys.lastIndexOf('\\') + 1);
			}
			String location = catalog.matchSystem("", sys);
			if (location == null) {
				location = catalog.getDTD(sys);
			}
			String s = getRootElement(location);
			if (s != null) {
				File config = new File(folder, "config_" + s + ".xml");
				if (config.exists()) {
					return config.getAbsolutePath();
				}
			}
		}
		if (rootElement.indexOf(':') != -1) {
			return new File(folder, "config_" + rootElement.substring(0, rootElement.indexOf(':')) + ".xml")
					.getAbsolutePath();
		}
		return new File(folder, "config_" + rootElement + ".xml").getAbsolutePath();
	}

	private String cleanEntity(String string) {
		String result = string;
		int control = result.indexOf('&');
		while (control != -1) {
			int sc = result.indexOf(';', control);
			if (sc == -1) {
				// no semicolon, it's not an entity
				result = result.substring(0, control) + "&amp;" + result.substring(control + 1);
			} else {
				// may be an entity
				String candidate = result.substring(control, sc) + ";";
				if (!candidate.equals("&amp;")) {
					String entity = entities.get(candidate);
					if (entity != null) {
						result = result.substring(0, control) + entity + result.substring(sc + 1);
					} else if (candidate.startsWith("&#x")) {
						// it's a character in hexadecimal format
						int c = Integer.parseInt(candidate.substring(3, candidate.length() - 1), 16);
						result = result.substring(0, control) + (char) c + result.substring(sc + 1);
					} else if (candidate.startsWith("&#")) {
						// it's a character
						int c = Integer.parseInt(candidate.substring(2, candidate.length() - 1));
						result = result.substring(0, control) + (char) c + result.substring(sc + 1);
					} else {
						result = result.substring(0, control) + "&amp;" + result.substring(control + 1);
					}
				}
			}
			if (control < result.length()) {
				control++;
			}
			control = result.indexOf('&', control);
		}

		control = result.indexOf('<');
		while (control != -1) {
			result = result.substring(0, control) + "&lt;" + result.substring(control + 1);
			if (control < result.length()) {
				control++;
			}
			control = result.indexOf('<', control);
		}

		control = result.indexOf('>');
		while (control != -1) {
			result = result.substring(0, control) + "&gt;" + result.substring(control + 1);
			if (control < result.length()) {
				control++;
			}
			control = result.indexOf('>', control);
		}
		return result;
	}

	private String getRootElement(String file) {
		if (file == null) {
			return null;
		}
		String result = null;
		File dtd = new File(file);
		try {
			DTDParser parser = new DTDParser();
			Grammar grammar = parser.parse(dtd);
			String root = grammar.getRootElement();
			if (!root.isEmpty()) {
				result = root;
			}
		} catch (Exception _) {
			// do nothing
		}
		return result;
	}

	private void writeHeader() throws IOException {
		String tgtLang = "";
		if (targetLanguage != null) {
			tgtLang = "\" target-language=\"" + targetLanguage;
		}

		String format = "xml";
		if (inDesign) {
			format = "x-inx";
		} else if (resx) {
			format = "resx";
		}
		writeString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		writeString("<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" "
				+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
				+ "xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");

		writeString("<file original=\"" + cleanString(inputFile) + "\" source-language=\"" + sourceLanguage + tgtLang
				+ "\" datatype=\"" + format + "\" tool-id=\"" + Constants.TOOLID + "\">\n");
		writeString("<header>\n");
		writeString("   <skl>\n");
		writeString("      <external-file href=\"" + XMLUtils.cleanText(skeletonFile) + "\"/>\n");
		writeString("   </skl>\n");
		if (!entitiesMap.isEmpty()) {
			writeString("   <prop-group name=\"entities\">\n" + entitiesMap + "   </prop-group>\n");
		}
		writeString("   <tool tool-version=\"" + Constants.VERSION + " " + Constants.BUILD + "\" tool-id=\""
				+ Constants.TOOLID + "\" tool-name=\"" + Constants.TOOLNAME + "\"/>\n");
		writeString("</header>\n");
		if (arbortextDita) {
			writeString("<?arbortext-dita ?>\n");
		}
		if (qtiBased) {
			writeString("<?qti-based ?>\n");
		}
		writeString("<?encoding " + srcEncoding + "?>\n");
		writeString("<body>\n");
	}

	private void processList(Segmenter segmenter)
			throws IOException, SAXException, ParserConfigurationException {
		for (int i = 0; i < segments.size(); i++) {
			String txt = segments.get(i);
			if (txt.startsWith("" + '\u007F' + "" + '\u007F')) {
				// send directly to skeleton
				writeSkeleton(txt.substring(2));
				continue;
			}
			if (txt.startsWith("" + '\u0081')) {
				inCData = true;
				txt = txt.substring(1);
			} else {
				inCData = false;
			}
			if (inDesign && !txt.trim().isEmpty()) {
				if (txt.startsWith("c_") && !txt.substring(2).trim().isEmpty()) {
					writeSkeleton("c_");
					txt = txt.substring(2);
					txt = txt.replace("~sep~", "_");
				} else {
					writeSkeleton(txt);
					continue;
				}
			}
			tagId = 0;
			txt = addTags(txt);
			if (segmenter != null) {
				txt = txt.replace(Segmenter.STARTIGNORE, "");
				String[] segs = segmenter.segment(txt);
				for (int h = 0; h < segs.length; h++) {
					String seg = segs[h];
					while (seg.startsWith("" + '\u2029')) {
						writeSkeleton("" + '\u2029');
						seg = seg.substring(1);
					}
					writeSegment(seg);
				}
			} else {
				String seg = txt.replace(Segmenter.STARTIGNORE, "");
				seg = seg.replace(Segmenter.ENDIGNORE, "");
				writeSegment(seg);
			}
		}
	}

	private void writeSegment(String tagged) throws IOException, SAXException, ParserConfigurationException {
		String restype = "";
		if (!containsText(tagged)) {
			String untagged = removeTags(tagged);
			writeSkeleton(untagged);
			return;
		}
		if (inCData) {
			restype = " restype=\"x-cdata\"";
		}
		String seg = "   <trans-unit id=\"" + segId + "\" xml:space=\"preserve\" approved=\"no\" " + restype + ">\n"
				+ "      <source>" + tagged + "</source>\n   </trans-unit>\n";

		String clean = tidy(seg);
		String dirt = startText + "%%%" + segId++ + "%%%" + endText;
		writeString(clean);
		writeSkeleton(dirt);
		containsText = true;
	}

	private String removeTags(String tagged) throws IOException, SAXException, ParserConfigurationException {
		String source = "<skeleton>" + tagged + "</skeleton>";
		SAXBuilder b = new SAXBuilder();
		Document d = null;
		try {
			d = b.build(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
		} catch (SAXException sax) {
			MessageFormat mf = new MessageFormat(Messages.getString("Xml2Xliff.6"));
			logger.log(Level.ERROR, mf.format(new String[] { source }));
			throw sax;
		}
		Element r = d.getRootElement();
		return extractText(r);
	}

	private String extractText(Element element) throws SAXException {
		if ("ph".equals(element.getName())) {
			return Xliff2Xml.fixEntities(element);
		}
		if (ditaBased && "mrk".equals(element.getName())) {
			return cleanMrk(element);
		}

		StringBuilder result = new StringBuilder();
		List<XMLNode> content = element.getContent();
		for (XMLNode n : content) {
			switch (n.getNodeType()) {
				case XMLNode.ELEMENT_NODE:
					Element e = (Element) n;
					if ("ph".equals(e.getName())) {
						result.append(extractText(e));
					} else if ("mrk".equals(e.getName())) {
						result.append(cleanMrk(e));
					} else {
						throw new SAXException(Messages.getString("Xml2Xliff.7"));
					}
					break;
				case XMLNode.TEXT_NODE:
					if (inCData) {
						result.append(((TextNode) n).getText());
					} else {
						result.append(XMLUtils.cleanText(((TextNode) n).getText()));
					}
					break;
				default:
					// ignore
					break;
			}
		}
		return result.toString();
	}

	private String cleanMrk(Element element) throws SAXException {
		String ts = element.getAttributeValue("ts");
		if (ts.isEmpty()) {
			throw new SAXException(Messages.getString("Xml2Xliff.8"));
		}
		ts = restoreChars(ts).trim();
		StringBuilder name = new StringBuilder();
		for (int i = 1; i < ts.length(); i++) {
			if (Character.isSpaceChar(ts.charAt(i))) {
				break;
			}
			name.append(ts.charAt(i));
		}
		StringBuilder content = new StringBuilder();
		List<XMLNode> nodes = element.getContent();
		for (XMLNode n : nodes) {
			switch (n.getNodeType()) {
				case XMLNode.ELEMENT_NODE:
					Element e = (Element) n;
					String ph = extractText(e);
					content.append(ph);
					break;
				case XMLNode.TEXT_NODE:
					content.append(XMLUtils.cleanText(((TextNode) n).getText()));
					break;
				default:
					// ignore
					break;
			}
		}
		StringBuilder result = new StringBuilder();
		result.append(ts);
		result.append(content.toString());
		result.append("</");
		result.append(name.toString());
		result.append('>');
		return result.toString();
	}

	private String restoreChars(String string) {
		String result = string.replace(Xml2Xliff.MATHLT, "<");
		result = result.replace(Xml2Xliff.MATHGT, ">");
		result = result.replace(Xml2Xliff.DOUBLEPRIME, "\"");
		result = result.replace(Xml2Xliff.GAMP, "&");
		return result;
	}

	private String tidy(String seg) throws SAXException, IOException, ParserConfigurationException {
		startText = "";
		endText = "";
		SAXBuilder b = new SAXBuilder();
		Document d = b.build(new ByteArrayInputStream(seg.getBytes(StandardCharsets.UTF_8)));
		Element r = d.getRootElement();
		Element s = r.getChild("source");
		if (s.getChildren().isEmpty()) {
			return seg;
		}
		List<XMLNode> start = new ArrayList<>();
		List<XMLNode> end = new ArrayList<>();
		List<XMLNode> txt = new ArrayList<>();

		List<XMLNode> content = s.getContent();

		List<XMLNode> startTags = new ArrayList<>();
		List<XMLNode> endTags = new ArrayList<>();

		for (int i = 0; i < content.size(); i++) {
			XMLNode n = content.get(i);
			if (n.getNodeType() == XMLNode.TEXT_NODE && !n.toString().trim().isEmpty()) {
				break;
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				if (!e.getName().equals("ph")) {
					break;
				}
				startTags.add(e);
			}
			start.add(n);
		}

		if (startTags.isEmpty()) {
			start.clear();
		}
		for (int i = content.size() - 1; i >= 0; i--) {
			XMLNode n = content.get(i);
			if (n.getNodeType() == XMLNode.TEXT_NODE && !n.toString().trim().isEmpty()) {
				break;
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				if (!e.getName().equals("ph")) {
					break;
				}
				endTags.add(0, e);
			}
			end.add(0, n);
		}
		if (endTags.isEmpty()) {
			end.clear();
		}

		int trimmed = 0;

		if (!startTags.isEmpty() && !endTags.isEmpty()) {
			for (int i = 0; i < startTags.size() && i < endTags.size(); i++) {
				Element f = (Element) startTags.get(i);
				Element l = (Element) endTags.get(endTags.size() - 1 - i);
				if ((l.getText().startsWith("</") && l.getText().endsWith(">"))
						&& (!(f.getText().startsWith("</") || f.getText().endsWith("/>")))) {
					String endTag = l.getText().substring(2);
					endTag = endTag.substring(0, endTag.length() - 1);
					if (f.getText().startsWith("<" + endTag)) {
						// matched
						trimmed++;
					}
				}
			}
		}

		if (trimmed > 0) {
			List<XMLNode> start2 = new ArrayList<>();
			List<XMLNode> end2 = new ArrayList<>();

			int count = 0;
			for (int h = 0; h < start.size(); h++) {
				XMLNode n = start.get(h);
				start2.add(n);
				if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
					count++;
					if (count == trimmed) {
						break;
					}
				}
			}
			start = start2;
			count = 0;
			for (int h = end.size() - 1; h >= 0; h--) {
				XMLNode n = end.get(h);
				end2.add(0, n);
				if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
					count++;
					if (count == trimmed) {
						break;
					}
				}
			}
			end = end2;
		} else {
			if (startTags.size() == 1 && s.getChildren().size() == 1) {
				// send initial tag to skeleton, keep end spaces
				end.clear();
			} else if (s.getChildren().size() == 1 && endTags.size() == 1) {
				// set ending tag to skeleton, keep initial spaces
				start.clear();
			} else {
				start.clear();
				end.clear();
			}
		}

		for (int i = start.size(); i < content.size() - end.size(); i++) {
			txt.add(content.get(i));
		}

		for (int i = 0; i < start.size(); i++) {
			XMLNode n = start.get(i);
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				startText += ((TextNode) n).getText();
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				startText += ((Element) n).getText();
			}
		}
		for (int i = 0; i < end.size(); i++) {
			XMLNode n = end.get(i);
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				endText += ((TextNode) n).getText();
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				endText += ((Element) n).getText();
			}
		}
		s.setContent(txt);
		List<Element> children = s.getChildren("ph");
		for (int id = 0; id < children.size(); id++) {
			Element child = children.get(id);
			child.setAttribute("id", "" + id);
		}

		return r.toString();
	}

	private boolean containsText(String string) throws IOException, ParserConfigurationException, SAXException {
		if (string.strip().isEmpty()) {
			return false;
		}
		String source = "<holder>" + string.strip() + "</holder>";
		SAXBuilder b = new SAXBuilder();
		Document d = null;
		try {
			d = b.build(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
		} catch (SAXException sax) {
			MessageFormat mf = new MessageFormat(Messages.getString("Xml2Xliff.9"));
			logger.log(Level.ERROR, mf.format(new String[] { string }));
			throw sax;
		}
		return containsText(d.getRootElement());
	}

	private boolean containsText(Element e) {
		if ("ph".equals(e.getName()) || "bpt".equals(e.getName()) || "ept".equals(e.getName())
				|| "it".equals(e.getName())) {
			return false;
		}
		if ("mrk".equals(e.getName()) && "protected".equals(e.getAttributeValue("mtype"))) {
			return false;
		}
		if ("no".equals(e.getAttributeValue("translate"))) {
			return false;
		}
		List<XMLNode> content = e.getContent();
		for (XMLNode node : content) {
			if (node.getNodeType() == XMLNode.TEXT_NODE) {
				String nodeText = ((TextNode) node).getText().strip();
				for (int i = 0; i < nodeText.length(); i++) {
					int c = nodeText.charAt(i);
					if (" \u00A0\r\n\f\t\u2028\u2029,.;\":<>()[]{}=/*\u00AB\u00BB\u201C\u201D\u201E\uFF00"
							.indexOf(c) == -1) {
						return true;
					}
				}
			}
			if (node.getNodeType() == XMLNode.ELEMENT_NODE && containsText((Element) node)) {
				return true;
			}
		}
		return false;
	}

	private String normalize(String string) {
		String result = string.replace('\n', ' ');
		result = result.replace('\t', ' ');
		result = result.replace('\r', ' ');
		result = result.replace('\f', ' ');
		String rs = "";
		int length = result.length();
		for (int i = 0; i < length; i++) {
			char ch = result.charAt(i);
			if (ch != ' ') {
				rs = rs + ch;
			} else {
				rs = rs + ch;
				while (i < (length - 1) && result.charAt(i + 1) == ' ') {
					i++;
				}
			}
		}
		return rs;
	}

	private String addTags(String string) {
		String src = string;
		StringBuilder result = new StringBuilder();
		int start = src.indexOf('<');
		int end = src.indexOf('>');

		while (start != -1) {
			if (start > 0) {
				result.append(cleanString(src.substring(0, start)));
				src = src.substring(start);
				start = src.indexOf('<');
				end = src.indexOf('>');
			}
			String element = src.substring(start, end + 1);
			src = src.substring(end + 1);
			if (ditaBased) {
				if (!(element.startsWith("<mrk ") || element.equals("</mrk>"))) {
					result.append(tag(element));
				} else {
					result.append(element);
				}
			} else {
				result.append(tag(element));
			}
			start = src.indexOf('<');
			end = src.indexOf('>');
		}
		result.append(cleanString(src));
		return result.toString();
	}

	private String clean(String string) {
		String result = string.replace("<", MATHLT);
		result = result.replace(">", MATHGT);
		result = result.replace("\"", DOUBLEPRIME);
		return replaceAmp(result);
	}

	private String replaceAmp(String value) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '&') {
				result.append(GAMP);
			} else {
				result.append(c);
			}
		}
		return result.toString();
	}

	private String tag(String element) {
		String result = "";
		String type = getType(element);
		if (translatableAttributes.containsKey(type)) {
			result = extractAttributes(type, element);
		} else {
			String ctype = "";
			if (ctypes.containsKey(type)) {
				ctype = " ctype=\"" + ctypes.get(type) + "\"";
			}
			result = "<ph id=\"" + tagId++ + "\"" + ctype + ">" + cleanString(element) + "</ph>";
		}
		return result;
	}

	private String cleanString(String string) {
		String s = string;
		int control = s.indexOf('&');
		while (control != -1) {
			int sc = s.indexOf(";", control);
			if (sc == -1) {
				// no semicolon, it's not an entity
				s = s.substring(0, control) + "&amp;" + s.substring(control + 1);
			} else {
				// may be an entity
				String candidate = s.substring(control, sc) + ";";
				if (validEntitiy(candidate)) {
					if (!candidate.equals("&amp;") && !candidate.equals("&quot;")) {
						String entity = entities.get(candidate);
						if (entity != null) {
							s = s.substring(0, control) + entity + s.substring(sc + 1);
						} else {
							s = s.substring(0, control) + "%%%ph id=\"" + tagId++ + "\"%%%&amp;"
									+ candidate.substring(1) + "%%%/ph%%%" + s.substring(sc + 1);
						}
					} else {
						// it is an "&amp;"
						s = s.substring(0, control) + "&amp;" + s.substring(control + 1);
					}
				} else {
					// treat as an "&amp;"
					s = s.substring(0, control) + "&amp;" + s.substring(control + 1);
				}
			}
			if (control < s.length()) {
				control++;
			}
			control = s.indexOf('&', control);
		}

		control = s.indexOf('<');
		while (control != -1) {
			s = s.substring(0, control) + "&lt;" + s.substring(control + 1);
			if (control < s.length()) {
				control++;
			}
			control = s.indexOf('<', control);
		}

		control = s.indexOf('>');
		while (control != -1) {
			s = s.substring(0, control) + "&gt;" + s.substring(control + 1);
			if (control < s.length()) {
				control++;
			}
			control = s.indexOf('>', control);
		}
		s = s.replace("%%%/ph%%%", "</ph>");
		s = s.replace("%%%ph", "<ph");
		s = s.replace("\"%%%&amp;", "\">&amp;");
		return XMLUtils.validChars(s);
	}

	private boolean validEntitiy(String candidate) {
		if (candidate.length() < 3) {
			return false;
		}
		char nameStart = candidate.charAt(1);
		// ":" | [A-Z] | "_" | [a-z] | [#xC0-#xD6] | [#xD8-#xF6] | [#xF8-#x2FF] |
		// [#x370-#x37D] | [#x37F-#x1FFF] | [#x200C-#x200D] | [#x2070-#x218F] |
		// [#x2C00-#x2FEF] | [#x3001-#xD7FF] | [#xF900-#xFDCF] | [#xFDF0-#xFFFD] |
		// [#x10000-#xEFFFF]
		if (nameStart == ':' || (nameStart >= 'A' && nameStart <= 'Z') || (nameStart >= 'a' && nameStart <= 'z')
				|| (nameStart >= '\u00C0' && nameStart <= '\u00D6') || (nameStart >= '\u00D8' && nameStart <= '\u00F6')
				|| (nameStart >= '\u00F8' && nameStart <= '\u02FF') || (nameStart >= '\u0370' && nameStart <= '\u037D')
				|| (nameStart >= '\u037F' && nameStart <= '\u1FFF') || (nameStart >= '\u200C' && nameStart <= '\u200D')
				|| (nameStart >= '\u2017' && nameStart <= '\u218F') || (nameStart >= '\u2C00' && nameStart <= '\u2FEF')
				|| (nameStart >= '\u3001' && nameStart <= '\uD7FF') || (nameStart >= '\uF900' && nameStart <= '\uFDCF')
				|| (nameStart >= '\uFDF0' && nameStart <= '\uFFFD')) // not considered [#x10000-#xEFFFF]
		{
			// its OK
		} else {
			return false;
		}
		for (int i = 2; i < candidate.length() - 1; i++) {
			// valid = nameStart | "-" | "." | [0-9] | #xB7 | [#x0300-#x036F] |
			// [#x203F-#x2040]
			char nameChar = candidate.charAt(i);
			if (nameChar == ':' || (nameChar >= 'A' && nameChar <= 'Z') || (nameChar >= 'a' && nameChar <= 'z')
					|| (nameChar >= '\u00C0' && nameChar <= '\u00D6') || (nameChar >= '\u00D8' && nameChar <= '\u00F6')
					|| (nameChar >= '\u00F8' && nameChar <= '\u02FF') || (nameChar >= '\u0370' && nameChar <= '\u037D')
					|| (nameChar >= '\u037F' && nameChar <= '\u1FFF') || (nameChar >= '\u200C' && nameChar <= '\u200D')
					|| (nameChar >= '\u2017' && nameChar <= '\u218F') || (nameChar >= '\u2C00' && nameChar <= '\u2FEF')
					|| (nameChar >= '\u3001' && nameChar <= '\uD7FF') || (nameChar >= '\uF900' && nameChar <= '\uFDCF')
					|| (nameChar >= '\uFDF0' && nameChar <= '\uFFFD') || nameChar == '-'
					|| (nameChar >= '0' && nameChar <= '9') || (nameChar >= '\u0300' && nameChar <= '\u036F')
					|| (nameChar >= '\u203F' && nameChar <= '\u2040')) {
				// its OK
			} else {
				return false;
			}
		}
		return true;
	}

	private void writeSkeleton(String string) throws IOException {
		skeleton.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private void writeString(String string) throws IOException {
		output.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private String extractAttributes(String type, String element) {
		String ctype = "";
		if (ctypes.containsKey(type)) {
			ctype = " ctype=\"" + ctypes.get(type) + "\"";
		}
		String result = "<ph id=\"" + tagId++ + "\"" + ctype + ">";
		String clean = cleanString(element);

		List<String> v = translatableAttributes.get(type);

		StringTokenizer tokenizer = new StringTokenizer(clean, "&= \t\n\r\f/", true);

		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();
			if (!v.contains(token)) {
				result = result + token;
			} else {
				result = result + token;
				String s = tokenizer.nextToken();
				while (s.equals("=") || s.equals(" ")) {
					result = result + s;
					s = tokenizer.nextToken();
				}
				// s contains the first word of the attribute
				s = s.replace("amp;amp;", "%%AMP%%");
				if (((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))
						&& s.length() > 1) {
					// the value is one word and it is quoted
					result = result + s.substring(0, 1) + "</ph>" + s.substring(1, s.length() - 1) + "<ph id=\""
							+ tagId++ + "\">" + s.substring(s.length() - 1);
				} else {
					if (s.startsWith("\"") || s.startsWith("'")) {
						// attribute value is quoted, but it has more than one
						// word
						String quote = s.substring(0, 1);
						result = result + s.substring(0, 1) + "</ph>" + s.substring(1);
						s = tokenizer.nextToken().replace("amp;amp;", "%%AMP%%");
						do {
							result = result + s;
							if (tokenizer.hasMoreElements()) {
								s = tokenizer.nextToken().replace("amp;amp;", "%%AMP%%");
							}
						} while (s.indexOf(quote) == -1);
						String left = s.substring(0, s.indexOf(quote));
						String right = s.substring(s.indexOf(quote));
						result = result + left + "<ph id=\"" + tagId++ + "\">" + right;
					} else {
						// attribute is not quoted, it can only be one word
						result = result + "</ph>" + s + "<ph id=\"" + tagId++ + "\">";
					}
				}
				result = result.replace("%%AMP%%", "amp;");
			}
		}
		return result + "</ph>";
	}

	private void buildTables(String iniFile) throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(catalog);
		Document doc = builder.build(iniFile);
		Element rt = doc.getRootElement();
		List<Element> tags = rt.getChildren("tag");

		startsSegment = new HashMap<>();
		translatableAttributes = new HashMap<>();
		ignore = new HashMap<>();
		ctypes = new HashMap<>();
		keepFormating = new HashMap<>();
		inline = new HashMap<>();

		for (Element t : tags) {
			if (t.getAttributeValue("hard-break", "inline").equals("yes")
					|| t.getAttributeValue("hard-break", "inline").equals("segment")) {
				startsSegment.put(t.getText(), "yes");
			} else if (t.getAttributeValue("hard-break", "inline").equals("no")
					|| t.getAttributeValue("hard-break", "inline").equals("inline")) {
				inline.put(t.getText(), "yes");
			} else {
				ignore.put(t.getText(), "yes");
			}
			if (t.getAttributeValue("keep-format", "no").equals("yes")) {
				keepFormating.put(t.getText(), "yes");
			}
			String attributes = t.getAttributeValue("attributes");
			if (!attributes.isEmpty()) {
				StringTokenizer tokenizer = new StringTokenizer(attributes, ";");
				int count = tokenizer.countTokens();
				List<String> v = new ArrayList<>(count);
				for (int j = 0; j < count; j++) {
					v.add(tokenizer.nextToken());
				}
				translatableAttributes.put(t.getText(), v);
			}
			String ctype = t.getAttributeValue("ctype");
			if (!ctype.isEmpty()) {
				ctypes.put(t.getText(), ctype);
			}
		}
	}

	private void buildList() throws SAXException, IOException {
		segments = new ArrayList<>();
		text = "";
		parseNode(root);
		segments.add(text);
	}

	private void parseNode(XMLNode n) throws SAXException, IOException {
		switch (n.getNodeType()) {
			case XMLNode.ATTRIBUTE_NODE:
				throw new SAXException(Messages.getString("Xml2Xliff.10") + n);
			case XMLNode.CDATA_SECTION_NODE:
				String name = stack.peek();
				if (startsSegment.containsKey(name)) {
					segments.add(text);
					segments.add("" + '\u007F' + '\u007F' + "<![CDATA[");
					CData data = (CData) n;
					segments.add("" + '\u0081' + data.getData());
					segments.add("" + '\u007F' + '\u007F' + "]]>");
				} else {
					segments.add(text);
					segments.add("" + '\u007F' + '\u007F' + n.toString());
				}
				translatable = "";
				text = "";
				break;
			case XMLNode.COMMENT_NODE:
				segments.add(text);
				if (translateComments) {
					segments.add("" + '\u007F' + '\u007F' + "<!--");
					segments.add(((Comment) n).getText());
					segments.add("" + '\u007F' + '\u007F' + "-->");
				} else {
					segments.add("" + '\u007F' + '\u007F' + n.toString());
				}
				translatable = "";
				text = "";
				break;
			case XMLNode.ELEMENT_NODE:
				Element e = (Element) n;
				if (ditaBased && !isKnownElement(e.getName())) {
					configureElement(e);
				}
				if (ditaBased && "no".equals(e.getAttributeValue("translate"))) {

					if (startsSegment.containsKey(e.getName())) {
						// treat as element to ignore, send to skeleton
						segments.add(text);
						if (e.getAttributeValue("removeTranslate", "no").equals("yes")) {
							e.removeAttribute("translate");
						}
						segments.add("" + '\u007F' + "" + '\u007F' + e.toString());
						text = "";
						translatable = "";
						stack = null;
						stack = new Stack<>();
						return;
					}

					removeComments(e);

					text = text + Segmenter.STARTIGNORE + parseElement(e) + Segmenter.ENDIGNORE;
					return;
				}
				if (ditaBased && e.getAttributeValue("fluentaIgnore", "no").equals("yes")) {
					e.removeAttribute("fluentaIgnore");
					segments.add(text);
					segments.add("" + '\u007F' + "" + '\u007F' + e.toString());
					text = "";
					translatable = "";
					stack = null;
					stack = new Stack<>();
					return;
				}
				if (startsSegment.containsKey(e.getName())) {
					segments.add(text);
					text = "";
					translatable = "";
					stack = null;
					stack = new Stack<>();
					stack.push(e.getName());
					if (!keepFormating.containsKey(e.getName())
							&& !e.getAttributeValue("xml:space", "default").equals("preserve")) {
						normalizeElement(e);
					}
				}
				if (ignore.containsKey(e.getName())) {
					segments.add(text);
					segments.add("" + '\u007F' + "" + '\u007F' + e.toString());
					text = "";
					translatable = "";
					stack = null;
					stack = new Stack<>();
					return;
				}
				if (stack.isEmpty() && e.getChildren().isEmpty() && !translatableAttributes.containsKey(e.getName())) {
					if (inline.containsKey(e.getName()) && !e.getText().isEmpty()) {
						if (text.startsWith('\u007F' + "" + '\u007F')) {
							segments.add(text);
							text = "";
							translatable = "";
							stack = null;
							stack = new Stack<>();
						}
						stack.push(e.getName());
						if (!keepFormating.containsKey(e.getName())
								&& !e.getAttributeValue("xml:space", "default").equals("preserve")) {
							normalizeElement(e);
						}
					} else {
						segments.add(text);
						text = "";
						translatable = "";
						segments.add('\u007F' + "" + '\u007F' + e.toString());
						break;
					}
				}
				if (!stack.isEmpty() && !startsSegment.containsKey(e.getName())) {
					stack.push(e.getName());
				}
				List<Attribute> attributes = e.getAttributes();
				text = text + "<" + e.getName();
				if (!attributes.isEmpty()) {
					for (int i = 0; i < attributes.size(); i++) {
						Attribute a = attributes.get(i);
						text = text + " " + a.getName() + "=\"" + cleanAttribute(a.getValue()) + "\"";
					}
				}
				List<XMLNode> content = e.getContent();
				if (content.isEmpty()) {
					if (text.isEmpty()) {
						text = "" + '\u007F' + '\u007F' + "/>";
					} else {
						text = text + "/>";
					}
				} else {
					if (!inline.containsKey(e.getName())) {
						if (!text.isEmpty()) {
							segments.add(text + ">");
							text = "";
						} else {
							segments.add("" + '\u007F' + '\u007F' + ">");
						}
						translatable = "";
					} else {
						if (!text.isEmpty()) {
							text = text + ">";
						} else {
							segments.add("" + '\u007F' + '\u007F' + ">");
							translatable = "";
						}
					}
					for (int i = 0; i < content.size(); i++) {
						parseNode(content.get(i));
					}
					if (startsSegment.containsKey(e.getName())) {
						segments.add(text);
						text = "";
						translatable = "";
					}
					if (!text.isEmpty()) {
						text = text + "</" + e.getName() + ">";
					} else {
						segments.add("" + '\u007F' + '\u007F' + "</" + e.getName() + ">");
					}
				}
				if (!stack.isEmpty()) {
					stack.pop();
				}
				break;
			case XMLNode.PROCESSING_INSTRUCTION_NODE:
				PI pi = (PI) n;
				if (ignoreTC && pi.getTarget().startsWith("oxy_")) {
					break;
				}
				if (arbortextDita && pi.getTarget().startsWith("Pub") && pi.getData().indexOf("text=\"") != -1) {
					segments.add(text);
					String data = pi.getData();
					int start = data.indexOf("text=\"") + 6;
					int end = data.indexOf("\"", start);
					String translate = data.substring(start, end);
					segments.add("" + '\u007F' + '\u007F' + "<?" + pi.getTarget() + " " + data.substring(0, start));
					segments.add(translate);
					segments.add("" + '\u007F' + '\u007F' + data.substring(end) + "?>");
					text = "";
					translatable = "";
					break;
				}
				if (inDesign && !translatable.trim().isEmpty()) {
					text = text + n.toString();
				} else {
					segments.add(text);
					segments.add("" + '\u007F' + '\u007F' + n.toString());
					text = "";
					translatable = "";
				}
				break;
			case XMLNode.TEXT_NODE:
				String value = ((TextNode) n).getText();
				//
				// Don't enable replacement of "&". Replacement of "<" and ">" is needed because
				// otherwise tag handling will fail (it searches for initial "<" and closing ">"
				//
				value = value.replace("<", "&lt;");
				value = value.replace(">", "&gt;");

				text = text + value;
				if (!value.trim().isEmpty()) {
					translatable = translatable + value;
				}
				if (!value.trim().isEmpty() && text.startsWith("" + '\u007F' + '\u007F')) {
					for (int j = 0; j < stack.size(); j++) {
						if (startsSegment.containsKey(stack.get(j))) {
							text = text.substring(2);
							break;
						}
					}
				}

				break;
			default:
				// ignore
				break;
		}
	}

	private Element parseElement(Element e) {
		if (!isKnownElement(e.getName())) {
			configureElement(e);
		}
		StringBuilder sb = new StringBuilder("<");
		sb.append(e.getName());
		List<Attribute> atts = e.getAttributes();
		for (Attribute at : atts) {
			sb.append(' ');
			sb.append(at.toString());
		}
		sb.append(">");
		Element mrk = new Element("mrk");
		mrk.setAttribute("ts", clean(sb.toString()));
		String type = "protected";
		if (ditaBased && !"no".equals(e.getAttributeValue("translate"))) {
			type = "x-" + e.getName();
		}
		mrk.setAttribute("mtype", type);
		List<XMLNode> content = e.getContent();
		for (XMLNode node : content) {
			if (node.getNodeType() == XMLNode.TEXT_NODE) {
				mrk.addContent(node);
			}
			if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
				mrk.addContent(parseElement((Element) node));
			}
		}
		return mrk;
	}

	private void configureElement(Element e) {
		String cls = e.getAttributeValue("class");
		String[] parts = cls.split("\\s");
		for (int h = 0; h < parts.length; h++) {
			String part = parts[h];
			if (part.indexOf('/') == -1) {
				continue;
			}
			String ancestor = part.substring(part.indexOf('/') + 1).trim();
			if (isKnownElement(ancestor)) {
				if (startsSegment.containsKey(ancestor)) {
					startsSegment.put(e.getName(), startsSegment.get(ancestor));
				}
				if (inline.containsKey(ancestor)) {
					inline.put(e.getName(), inline.get(ancestor));
				}
				if (ignore.containsKey(ancestor)) {
					ignore.put(e.getName(), ignore.get(ancestor));
				}
				if (keepFormating.containsKey(ancestor)) {
					keepFormating.put(e.getName(), keepFormating.get(ancestor));
				}
				if (translatableAttributes.containsKey(ancestor)) {
					translatableAttributes.put(e.getName(), translatableAttributes.get(ancestor));
				}
				if (ctypes.containsKey(ancestor)) {
					ctypes.put(e.getName(), ctypes.get(ancestor));
				}
				return;
			}
		}
		MessageFormat mf = new MessageFormat(Messages.getString("Xml2Xliff.11"));
		logger.log(Level.WARNING, mf.format(new String[] { e.getName() }));
	}

	private boolean isKnownElement(String name) {
		if (startsSegment.containsKey(name)) {
			return true;
		}
		if (inline.containsKey(name)) {
			return true;
		}
		return ignore.containsKey(name);
	}

	private void removeComments(Element e) {
		List<XMLNode> content = new ArrayList<>();
		List<XMLNode> list = e.getContent();
		for (XMLNode n : list) {
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				content.add(n);
			}
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				content.add(n);
			}
		}
		e.setContent(content);
	}

	private void normalizeElement(Element e) {
		List<XMLNode> l = e.getContent();
		List<XMLNode> normal = new ArrayList<>();
		for (XMLNode n : l) {
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				String value = ((TextNode) n).getText();
				value = normalize(value);
				((TextNode) n).setText(value);
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e1 = (Element) n;
				if (!keepFormating.containsKey(e1.getName())
						&& !e1.getAttributeValue("xml:space", "default").equals("preserve")) {
					normalizeElement((Element) n);
				}
			}
			normal.add(n);
		}
		e.setContent(normal);
	}

	private String cleanAttribute(String value) {
		String result = value;
		if (stack.size() > 1 && !text.startsWith("" + '\u007F' + '\u007F')) {
			// this is an inline element and will be placed in <ph>
			result = result.replace("&", "###AMP###");
		} else {
			result = result.replace("&", "&amp;");
		}
		result = result.replace(">", "&gt;");
		result = result.replace("<", "&lt;");
		result = result.replace("\"", "&quot;");
		return result;
	}

	private String getType(String string) {
		String result = "";
		if (string.startsWith("<![CDATA[")) {
			return "![CDATA[";
		}

		if (string.startsWith("<!--")) {
			return "!--";
		}

		if (string.startsWith("<?")) {
			return "?";
		}

		// skip initial "<"
		for (int i = 1; i < string.length(); i++) {
			char c = string.charAt(i);
			if (c == ' ' || c == '\n' || c == '\f' || c == '\t' || c == '\r' || c == '>') {
				break;
			}
			result = result + c;
		}
		if (result.endsWith("/") && result.length() > 1) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	public static String getEncoding(String fileName) throws IOException {
		// check if there is a BOM (byte order mark)
		// at the start of the document
		byte[] array = new byte[2];
		try (FileInputStream inputStream = new FileInputStream(fileName)) {
			if (inputStream.read(array) == -1) {
				throw new IOException(Messages.getString("Xml2Xliff.12"));
			}
		}
		byte[] lt = "<".getBytes();
		byte[] feff = { -1, -2 };
		byte[] fffe = { -2, -1 };
		if (array[0] != lt[0]) {
			// there is a BOM, now check the order
			if (array[0] == fffe[0] && array[1] == fffe[1]) {
				return StandardCharsets.UTF_16BE.name();
			}
			if (array[0] == feff[0] && array[1] == feff[1]) {
				return StandardCharsets.UTF_16LE.name();
			}
		}
		// check declared encoding
		// return UTF-8 as default
		String result = StandardCharsets.UTF_8.name();
		String line = "";
		try (FileReader in = new FileReader(fileName)) {
			BufferedReader buffer = new BufferedReader(in);
			line = buffer.readLine();
		}
		if (line.startsWith("<?")) {
			line = line.substring(2, line.indexOf("?>"));
			line = line.replace("\'", "\"");
			StringTokenizer tokenizer = new StringTokenizer(line);
			while (tokenizer.hasMoreTokens()) {
				String token = tokenizer.nextToken();
				if (token.startsWith("encoding")) {
					result = token.substring(token.indexOf('\"') + 1, token.lastIndexOf('\"'));
				}
			}
		}
		return result;
	}
}
