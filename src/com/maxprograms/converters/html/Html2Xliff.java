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
package com.maxprograms.converters.html;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.segmenter.Segmenter;
import com.maxprograms.segmenter.SegmenterPool;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLUtils;

public class Html2Xliff {

	private String inputFile;
	private String skeletonFile;
	private String sourceLanguage;
	private String srcEncoding;

	private FileOutputStream output;
	private FileOutputStream skeleton;

	private int segId;
	private int tagId;

	private List<String> segments;
	private static final List<String> startsSegment = List.of("address", "article", "aside", "blockquote", "br",
			"details", "dialog", "dd", "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form", "h1",
			"h2", "h3", "h4", "h5", "h6", "header", "hgroup", "hr", "html", "label", "li", "main", "meta", "nav", "ol",
			"p", "pre", "script", "section", "table", "td", "tr", "ul");
	private Map<String, List<String>> translatableAttributes;
	private Map<String, String> entities;
	private Map<String, String> ctypes;

	private boolean segByElement;
	private boolean keepFormat;

	private String first;
	private String last;
	private String targetLanguage;

	private SAXBuilder builder;

	private Html2Xliff() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		return new Html2Xliff().convert(params);
	}

	private List<String> convert(Map<String, String> params) {
		List<String> result = new ArrayList<>();

		inputFile = params.get("source");
		String xliffFile = params.get("xliff");
		skeletonFile = params.get("skeleton");
		sourceLanguage = params.get("srcLang");
		targetLanguage = params.get("tgtLang");
		srcEncoding = params.get("srcEncoding");
		String catalog = params.get("catalog");
		String paragraphSegmentation = params.get("paragraph");
		Segmenter segmenter = null;
		if (paragraphSegmentation == null) {
			segByElement = false;
		} else {
			if (paragraphSegmentation.equals("yes")) {
				segByElement = true;
			} else {
				segByElement = false;
			}
		}
		try {
			builder = new SAXBuilder();
			if (!segByElement) {
				String initSegmenter = params.get("srxFile");
				segmenter = SegmenterPool.getSegmenter(initSegmenter, sourceLanguage,
						CatalogBuilder.getCatalog(catalog));
			}
			try (FileInputStream input = new FileInputStream(inputFile)) {
				skeleton = new FileOutputStream(skeletonFile);
				output = new FileOutputStream(xliffFile);
				writeHeader();

				int size = input.available();
				byte[] array = new byte[size];
				if (size != input.read(array)) {
					result.add(Constants.ERROR);
					result.add(Messages.getString("Html2Xliff.1"));
					return result;
				}
				String file = new String(array, srcEncoding);
				buildTables();
				buildList(file);
				processList(segmenter);

				skeleton.close();
				writeString("</body>\n");
				writeString("</file>\n");
				writeString("</xliff>");
				output.close();
			}
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | URISyntaxException
				| StringIndexOutOfBoundsException e) {
			Logger logger = System.getLogger(Html2Xliff.class.getName());
			logger.log(Level.ERROR, Messages.getString("Html2Xliff.2"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	private void writeHeader() throws IOException {
		String tgtLang = "";
		if (targetLanguage != null) {
			tgtLang = "\" target-language=\"" + targetLanguage;
		}
		writeString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		writeString("<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" "
				+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
				+ "xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");
		writeString("<file original=\"" + cleanString(inputFile) + "\" source-language=\"" + sourceLanguage + tgtLang
				+ "\" tool-id=\"" + Constants.TOOLID + "\" datatype=\"html\">\n");

		writeString("<header>\n");
		writeString("   <skl>\n");
		writeString("      <external-file href=\"" + XMLUtils.cleanText(skeletonFile) + "\"/>\n");
		writeString("   </skl>\n");
		writeString("   <tool tool-version=\"" + Constants.VERSION + " " + Constants.BUILD + "\" tool-id=\""
				+ Constants.TOOLID + "\" tool-name=\"" + Constants.TOOLNAME + "\"/>\n");
		writeString("</header>\n");
		writeString("<?encoding " + srcEncoding + "?>\n");
		writeString("<body>\n");
	}

	private void processList(Segmenter segmenter)
			throws IOException, SAXException, ParserConfigurationException {
		for (int i = 0; i < segments.size(); i++) {
			String text = segments.get(i);
			if (isTranslateable(text)) {
				extractSegment(segmenter, text);
			} else {
				// send directly to skeleton
				writeSkeleton(text);
			}
		}
	}

	private void extractSegment(Segmenter segmenter, String seg)
			throws IOException, SAXException, ParserConfigurationException {

		// start by making a smaller list

		List<String> miniList = new ArrayList<>();

		int start = seg.indexOf('<');
		int end = seg.indexOf('>');
		String text = "";

		while (start != -1) {
			if (start > 0) {
				// add initial text
				miniList.add(seg.substring(0, start));
				seg = seg.substring(start);
				start = seg.indexOf('<');
				end = seg.indexOf('>');
			}
			// add the tag
			if (end < seg.length()) {
				miniList.add(seg.substring(start, end + 1));
				seg = seg.substring(end + 1);
				start = seg.indexOf('<');
				end = seg.indexOf('>');
			} else {
				miniList.add(seg);
				start = -1;
			}
		}
		if (!seg.isEmpty()) {
			// add trailing characters
			miniList.add(seg);
		}

		int size = miniList.size();
		int i;
		// separate initial text
		String initial = "";
		for (i = 0; i < size; i++) {
			text = miniList.get(i);
			if (!isTranslateable(text)) {
				initial = initial + text;
			} else {
				break;
			}
		}

		// get translatable
		String translatable = text;
		for (i++; i < size; i++) {
			translatable = translatable + miniList.get(i);
		}

		// get trailing text
		String trail = "";
		int j;
		for (j = size - 1; j > 0; j--) {
			String t = miniList.get(j);
			if (!isTranslateable(t)) {
				trail = t + trail;
			} else {
				break;
			}
		}

		// remove trailing from translatable
		start = translatable.lastIndexOf(trail);
		if (start != -1) {
			translatable = translatable.substring(0, start);
		}

		writeSkeleton(initial);
		String tagged = addTags(translatable);
		if (containsText(tagged)) {
			translatable = tagged;
			if (segByElement) {
				String[] frags = translatable.split("\u2029");
				for (int m = 0; m < frags.length; m++) {
					writeSegment(frags[m]);
				}
			} else {
				String[] frags = translatable.split("\u2029");
				for (int m = 0; m < frags.length; m++) {
					String[] segs = segmenter.segment(frags[m]);
					for (int h = 0; h < segs.length; h++) {
						writeSegment(segs[h]);
					}
				}
			}
		} else {
			writeSkeleton(translatable);
		}
		writeSkeleton(trail);
	}

	private void writeSegment(String segment) throws IOException, SAXException, ParserConfigurationException {
		segment = segment.replace("\u2029", "");
		String pure = removePH(segment);
		if (pure.trim().isEmpty()) {
			writeSkeleton(phContent(segment));
			return;
		}
		if (segment.trim().isEmpty()) {
			writeSkeleton(segment);
			return;
		}

		first = "";
		last = "";

		segment = segmentCleanup(segment);

		writeSkeleton(first);
		tagId = 0;
		writeString("   <trans-unit id=\"" + segId + "\" xml:space=\"preserve\" approved=\"no\">\n"
				+ "      <source>");
		if (keepFormat) {
			writeString(segment);
		} else {
			writeString(normalize(segment));
		}
		writeString("</source>\n   </trans-unit>\n");

		writeSkeleton("%%%" + segId++ + "%%%\n" + last);
	}

	private String segmentCleanup(String segment)
			throws SAXException, IOException, ParserConfigurationException {
		ByteArrayInputStream stream = new ByteArrayInputStream(
				("<x>" + segment + "</x>").getBytes(StandardCharsets.UTF_8));
		Document d = builder.build(stream);
		Element e = d.getRootElement();
		List<XMLNode> content = e.getContent();
		int count = 0;
		for (XMLNode n : content) {
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				count++;
			}
		}

		if (count == 1) {
			XMLNode n = content.get(0);
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				first = phContent(n.toString());
				content.remove(0);
			} else {
				n = content.get(content.size() - 1);
				if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
					last = phContent(n.toString());
					content.remove(content.size() - 1);
				}
			}
		}

		if (count == 2) {
			XMLNode n = content.get(0);
			XMLNode s = content.get(content.size() - 1);
			if (n.getNodeType() == XMLNode.ELEMENT_NODE && s.getNodeType() == XMLNode.ELEMENT_NODE) {
				first = ((Element) n).getText();
				content.remove(n);
				last = ((Element) s).getText();
				content.remove(s);
			}
		}
		e.setContent(content);
		String es = e.toString();
		return es.substring(3, es.length() - 4);
	}

	private String phContent(String segment) throws SAXException, IOException, ParserConfigurationException {
		ByteArrayInputStream stream = new ByteArrayInputStream(
				("<x>" + segment + "</x>").getBytes(StandardCharsets.UTF_8));
		Document d = builder.build(stream);
		Element e = d.getRootElement();
		List<XMLNode> content = e.getContent();
		String result = "";
		for (XMLNode n : content) {
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				result = result + ((Element) n).getText();
			}
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				result = result + ((TextNode) n).getText();
			}
		}
		return result;
	}

	private String removePH(String segment) throws SAXException, IOException, ParserConfigurationException {
		ByteArrayInputStream stream = new ByteArrayInputStream(
				("<x>" + segment + "</x>").getBytes(StandardCharsets.UTF_8));
		Document d = builder.build(stream);
		Element e = d.getRootElement();
		List<XMLNode> content = e.getContent();
		String result = "";
		for (XMLNode n : content) {
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				result = result + ((TextNode) n).getText();
			}
		}
		return result;
	}

	private static String normalize(String string) {
		string = string.replace('\n', ' ');
		string = string.replace('\t', ' ');
		string = string.replace('\r', ' ');
		string = string.replace('\f', ' ');
		String rs = "";
		int length = string.length();
		for (int i = 0; i < length; i++) {
			char ch = string.charAt(i);
			if (ch != ' ') {
				rs = rs + ch;
			} else {
				rs = rs + ch;
				while (i < (length - 1) && string.charAt(i + 1) == ' ') {
					i++;
				}
			}
		}
		return rs;
	}

	private String addTags(String src) {
		String result = "";
		int start = src.indexOf('<');
		int end = src.indexOf('>');

		while (start != -1) {
			if (start > 0) {
				result = result + cleanString(src.substring(0, start));
				src = src.substring(start);
				start = src.indexOf('<');
				end = src.indexOf('>');
			}
			String element = src.substring(start, end + 1);
			// check if we don't fall in a trap
			// from Adobe GoLive
			int errors = element.indexOf('<', 1);
			if (errors != -1) {
				// there is a "<" inside quotes
				// must move manually until the end
				// of the element avoiding angle brackets
				// within quotes
				boolean exit = false;
				boolean inQuotes = false;
				StringBuilder buffer = new StringBuilder();
				buffer.append("<");
				int k = 0;
				while (!exit) {
					k++;
					char c = src.charAt(start + k);
					if (c == '\"') {
						inQuotes = !inQuotes;
					}
					buffer.append(c);
					if (!inQuotes && c == '>') {
						exit = true;
					}
				}
				element = buffer.toString();
				end = start + buffer.toString().length();
			}

			String tagged = tag(element);
			result = result + tagged;

			src = src.substring(end + 1);

			start = src.indexOf('<');
			end = src.indexOf('>');
		}
		result = result + cleanString(src);
		return result;
	}

	private String tag(String element) {
		String result = "";
		String type = getType(element);

		if (translatableAttributes.containsKey(type)) {
			result = extractAttributes(type, element);
			if (result.indexOf("\u2029") == -1) {
				String ctype = "";
				if (ctypes.containsKey(type)) {
					ctype = " ctype=\"" + ctypes.get(type) + "\"";
				}
				result = "<ph id=\"" + tagId++ + "\"" + ctype + ">" + cleanString(element) + "</ph>";
			}
		} else {
			String ctype = "";
			if (ctypes.containsKey(type)) {
				ctype = " ctype=\"" + ctypes.get(type) + "\"";
			}
			result = "<ph id=\"" + tagId++ + "\"" + ctype + ">" + cleanString(element) + "</ph>";
		}
		return result;
	}

	private String cleanString(String s) {
		int control = s.indexOf('&');
		while (control != -1) {
			int sc = s.indexOf(';', control);
			if (sc == -1) {
				// no semicolon, it's not an entity
				s = s.substring(0, control) + "&amp;" + s.substring(control + 1);
			} else {
				// may be an entity
				String candidate = s.substring(control, sc) + ";";
				if (!(candidate.equals("&amp;") || candidate.equals("&gt;") || candidate.equals("&lt;"))) {
					String entity = entities.get(candidate);
					if (entity != null) {
						s = s.substring(0, control) + entity + s.substring(sc + 1);
					} else {
						if (candidate.startsWith("&#x")) {
							// numeric entity in hex, get its value
							int ch = Integer.parseInt(candidate.substring(3, candidate.indexOf(';')), 16);
							s = s.substring(0, control) + (char) ch + s.substring(sc + 1);
						} else if (candidate.startsWith("&#")) {
							// numeric entity in decimal
							int ch = Integer.parseInt(candidate.substring(2, candidate.indexOf(';')));
							s = s.substring(0, control) + (char) ch + s.substring(sc + 1);
						} else if (candidate.equals("&nbsp;")) {
							s = s.substring(0, control) + "\u00A0" + s.substring(sc + 1);
						} else if (candidate.equals("&copy;")) {
							s = s.substring(0, control) + "\u00A9" + s.substring(sc + 1);
						} else {
							// ugly, this should never happen
							s = s.substring(0, control) + "%%%ph id=\"" + tagId++ + "\"%%%&amp;"
									+ candidate.substring(1) + "%%%/ph%%%" + s.substring(sc + 1);
						}
					}
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
		return s;
	}

	private void writeSkeleton(String string) throws IOException {
		skeleton.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private void writeString(String string) throws IOException {
		output.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private boolean isTranslateable(String string) {

		keepFormat = false;

		//
		// remove CDATA sections
		//
		int startComment = string.indexOf("<![CDATA[");
		int endComment = string.indexOf("]]>");
		while (startComment != -1 || endComment != -1) {
			if (startComment != -1) {
				if (endComment == -1) {
					string = string.substring(0, startComment);
				} else {
					if (startComment < endComment) {
						string = string.substring(0, startComment) + string.substring(endComment + 3);
					} else {
						string = string.substring(endComment + 3, startComment);
					}
				}
			} else {
				if (endComment != -1) {
					string = string.substring(endComment + 3);
				}
			}
			startComment = string.indexOf("<![CDATA[");
			endComment = string.indexOf("]]>");
		}
		//
		// remove IGNORE sections
		//
		startComment = string.indexOf("<![IGNORE[");
		endComment = string.indexOf("]]>");
		while (startComment != -1 || endComment != -1) {
			if (startComment != -1) {
				if (endComment == -1) {
					string = string.substring(0, startComment);
				} else {
					if (startComment < endComment) {
						string = string.substring(0, startComment) + string.substring(endComment + 3);
					} else {
						string = string.substring(endComment + 3, startComment);
					}
				}
			} else {
				if (endComment != -1) {
					string = string.substring(endComment + 3);
				}
			}
			startComment = string.indexOf("<![IGNORE[");
			endComment = string.indexOf("]]>");
		}
		//
		// remove IGNORE sections
		//
		startComment = string.indexOf("<![INCLUDE[");
		endComment = string.indexOf("]]>");
		while (startComment != -1 || endComment != -1) {
			if (startComment != -1) {
				if (endComment == -1) {
					string = string.substring(0, startComment);
				} else {
					if (startComment < endComment) {
						string = string.substring(0, startComment) + string.substring(endComment + 3);
					} else {
						string = string.substring(endComment + 3, startComment);
					}
				}
			} else {
				if (endComment != -1) {
					string = string.substring(endComment + 3);
				}
			}
			startComment = string.indexOf("<![INCLUDE[");
			endComment = string.indexOf("]]>");
		}
		//
		// remove XML style comments
		//
		startComment = string.indexOf("<!--");
		endComment = string.indexOf("-->");
		while (startComment != -1 || endComment != -1) {
			if (startComment != -1) {
				if (endComment == -1) {
					string = string.substring(0, startComment);
				} else {
					if (startComment < endComment) {
						string = string.substring(0, startComment) + string.substring(endComment + 3);
					} else {
						string = string.substring(endComment + 3, startComment);
					}
				}
			} else {
				if (endComment != -1) {
					string = string.substring(endComment + 3);
				}
			}
			startComment = string.indexOf("<!--");
			endComment = string.indexOf("-->");
		}
		//
		// remove Processing Instruction
		//
		startComment = string.indexOf("<?");
		endComment = string.indexOf("?>");
		while (startComment != -1 || endComment != -1) {
			if (startComment != -1) {
				if (endComment == -1) {
					string = string.substring(0, startComment);
				} else {
					string = string.substring(0, startComment) + string.substring(endComment + 2);
				}
			} else {
				if (endComment != -1) {
					string = string.substring(endComment + 2);
				}
			}
			startComment = string.indexOf("<?");
			endComment = string.indexOf("?>");
		}
		//
		// remove C style comments
		//
		startComment = string.indexOf("/*");
		endComment = string.indexOf("*/");
		while (startComment != -1 || endComment != -1) {
			if (startComment != -1) {
				if (endComment == -1) {
					string = string.substring(0, startComment);
				} else {
					if (startComment < endComment) {
						string = string.substring(0, startComment) + string.substring(endComment + 2);
					} else {
						string = string.substring(endComment + 2, startComment);
					}
				}
			} else {
				if (endComment != -1) {
					string = string.substring(endComment + 2);
				}
			}
			startComment = string.indexOf("/*");
			endComment = string.indexOf("*/");
		}
		//
		// Start checking
		//
		int start = string.indexOf('<');
		int end = string.indexOf('>');

		String type;
		String text = "";

		while (start != -1) {
			if (start > 0) {
				text = text + cleanString(string.substring(0, start));
				string = string.substring(start);
				start = string.indexOf('<');
				end = string.indexOf('>');
			}
			String fragment = string.substring(start, end);
			type = getType(fragment);
			keepFormat = "pre".equalsIgnoreCase(type) || "code".equalsIgnoreCase(type);

			if (fragment.startsWith("<") && !fragment.startsWith("</")) {
				updateAttributes(type, fragment + ">");
			}

			if (type.equals("script") || type.equals("style")) {
				return false;
			}

			// check for translatable attributes
			if (translatableAttributes.containsKey(type)) {
				return true;
			}
			if (type.startsWith("/") && translatableAttributes.containsKey(type.substring(1))) {
				return true;
			}
			if (end < string.length()) {
				string = string.substring(end + 1);
			} else {
				string = string.substring(end);
			}
			start = string.indexOf('<');
			end = string.indexOf('>');
		}

		text = text + cleanString(string);

		// look for non-white space

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == ' ' || c == '\n' || c == '\f' || c == '\t' || c == '\r' || c == '\u00A0' || c == '\u2007'
					|| c == '\u202F' || c == '\uFEFF' || c == '>') {
				continue;
			}
			return true;
		}

		return false;
	}

	private void updateAttributes(String type, String fragment) {
		Map<String, Attribute> atts = attributesMap(fragment);
		if ("meta".equalsIgnoreCase(type)) {
			if (atts.containsKey("name")) {
				Attribute name = atts.get("name");
				if ("application-name".equalsIgnoreCase(name.getValue()) ||
						"author".equalsIgnoreCase(name.getValue())
						|| "description".equalsIgnoreCase(name.getValue())
						|| "keywords".equalsIgnoreCase(name.getValue())) {
					translatableAttributes.put("meta", List.of("content"));
				} else {
					translatableAttributes.remove("meta");
				}
			} else {
				translatableAttributes.remove("meta");
			}
		} else {
			List<String> translatables = new ArrayList<>();
			if (("button".equalsIgnoreCase(type) || "input".equalsIgnoreCase(type)) && atts.containsKey("value")) {
				translatables.add("value");
			}
			if (atts.containsKey("alt")) {
				translatables.add("alt");
			}
			if (atts.containsKey("content")) {
				translatables.add("content");
			}
			if (atts.containsKey("label")) {
				translatables.add("label");
			}
			if (atts.containsKey("placeholder")) {
				translatables.add("placeholder");
			}
			if (atts.containsKey("lang") && "html".equalsIgnoreCase(type)) {
				translatables.add("lang");
			}
			if (!translatables.isEmpty()) {
				translatableAttributes.put(type.toLowerCase(), translatables);
			} else {
				translatableAttributes.remove(type.toLowerCase());
			}
		}
	}

	private static Map<String, Attribute> attributesMap(String element) {
		Map<String, Attribute> atts = new HashMap<>();
		StringTokenizer tokenizer = new StringTokenizer(element, "=<> \t\n\r\f/", true);
		String name = "";
		String key = "";
		StringBuilder sb = new StringBuilder();
		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();
			if ("=".equals(token) && !name.isEmpty()) {
				key = name.toLowerCase();
				continue;
			}
			if (!token.isBlank()) {
				name = token;
			}
			if (token.startsWith("\"") || token.startsWith("'")) {
				String quote = token.substring(0, 1);
				if (token.endsWith(quote) && token.length() > 1) {
					// value is one word
					atts.put(key, new Attribute(key, token.substring(1, token.length() - 1)));
				} else {
					// attribute value is quoted, but it has more than one word
					sb.append(token.substring(1));
					token = tokenizer.nextToken();
					do {
						sb.append(token);
						token = tokenizer.nextToken();
					} while (token.indexOf(quote) == -1);
					sb.append(token.substring(0, token.indexOf(quote)));
					atts.put(key, new Attribute(key, sb.toString()));
				}
			}
		}
		return atts;
	}

	private String extractAttributes(String type, String element) {
		String ctype = "";
		if (ctypes.containsKey(type)) {
			ctype = " ctype=\"" + ctypes.get(type) + "\"";
		}
		String result = "<ph id=\"" + tagId++ + "\"" + ctype + ">";
		element = cleanString(element);

		List<String> v = translatableAttributes.get(type);

		StringTokenizer tokenizer = new StringTokenizer(element, "&= \t\n\r\f/", true);
		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();
			if (!v.contains(token.toLowerCase())) {
				result = result + token;
			} else {
				result = result + token;
				String s = tokenizer.nextToken();
				while (s.equals("=") || s.equals(" ")) {
					result = result + s;
					s = tokenizer.nextToken();
				}
				// s contains the first word of the atttribute
				if ((s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))
						&& s.length() > 1) {
					// the value is one word and it is quoted
					result = result + s.substring(0, 1) + "</ph>\u2029" + s.substring(1, s.length() - 1)
							+ "\u2029<ph id=\"" + tagId++ + "\">" + s.substring(s.length() - 1);
				} else {
					if (s.startsWith("\"") || s.startsWith("'")) {
						// attribute value is quoted, but it has more than one word
						String quote = s.substring(0, 1);
						result = result + s.substring(0, 1) + "</ph>\u2029" + s.substring(1);
						s = tokenizer.nextToken();
						do {
							result = result + s;
							s = tokenizer.nextToken();
						} while (s.indexOf(quote) == -1);
						String left = s.substring(0, s.indexOf(quote));
						String right = s.substring(s.indexOf(quote));
						result = result + left + "\u2029<ph id=\"" + tagId++ + "\">" + right;
					} else {
						// attribute is not quoted, it can only be one word
						result = result + "</ph>\u2029" + s + "\u2029<ph id=\"" + tagId++ + "\">";
					}
				}
			}
		}
		result = result + "</ph>";
		return result;
	}

	private void buildTables() throws SAXException, IOException, ParserConfigurationException {
		translatableAttributes = new HashMap<>();
		entities = new HashMap<>();
		ctypes = new HashMap<>();
		ctypes.put("img", "image");
		ctypes.put("b", "x-bold");
		ctypes.put("strong", "x-bold");
		ctypes.put("i", "x-italic");
		ctypes.put("u", "x-underlined");
		Document doc = builder.build(Html2Xliff.class.getResource("entities.xml"));
		Element root = doc.getRootElement();
		List<Element> ents = root.getChildren("entity");
		for (Element e : ents) {
			entities.put("&" + e.getAttributeValue("name") + ";", e.getText());
		}
	}

	private void buildList(String file) throws IOException {
		segments = new ArrayList<>();
		int start = file.indexOf('<');
		int end = file.indexOf('>');
		String type;
		String text = "";

		if (start > 0) {
			segments.add(file.substring(0, start));
			file = file.substring(start);
			start = file.indexOf('<');
			end = file.indexOf('>');
		}
		while (start != -1) {
			if (file.substring(start, start + 3).equals("<![")) {
				end = file.indexOf("]]>") + 2;
			}
			if (end < start || end < 0 || start < 0) {
				String fragment = "";
				if (start > 5 && start + 5 < file.length()) {
					fragment = file.substring(start - 5, start + 5);
				}
				if (fragment.isEmpty()) {
					throw new IOException(Messages.getString("Html2Xliff.3" + file.substring(start - 5, start + 5)));
				} else {
					MessageFormat mf = new MessageFormat(Messages.getString("Html2Xliff.4"));
					throw new IOException(mf.format(new Object[] { fragment }));
				}
			}
			String element = file.substring(start, end + 1);

			// check if the element is OK or has strange stuff
			// from Adobe GoLive 5
			int errors = element.indexOf('<', 1);
			if (errors != -1 && !element.startsWith("<![")) {
				// there is a "<" inside quotes
				// must move manually until the end
				// of the element avoiding angle brackets
				// within quotes
				boolean exit = false;
				boolean inQuotes = false;
				StringBuilder buffer = new StringBuilder();
				buffer.append('<');
				int k = 0;
				while (!exit) {
					k++;
					char c = file.charAt(start + k);
					if (c == '\"') {
						inQuotes = !inQuotes;
					}
					buffer.append(c);
					if (!inQuotes && c == '>') {
						exit = true;
					}
				}
				element = buffer.toString();
				end = start + buffer.toString().length();
				start = end;
			}

			type = getType(element);

			if (type.equals("![INCLUDE[") || type.equals("![IGNORE[") || type.equals("![CDATA[")) {
				// it's an SGML section, send it to skeleton
				// and keep looking for segments
				segments.add(text);
				text = "";
				end = file.indexOf("]]>");
				segments.add(file.substring(start, end + 3));
				file = file.substring(end + 3);
				start = file.indexOf('<');
				end = file.indexOf('>');
				if (start != -1) {
					text = file.substring(0, start);
				}
				continue;
			}
			if (startsSegment.contains(type)) {
				segments.add(text);
				file = file.substring(text.length());
				start = start - text.length();
				end = end - text.length();
				text = "";
			}
			if (type.startsWith("/") && startsSegment.contains(type.substring(1))) {
				segments.add(text);
				file = file.substring(text.length());
				start = start - text.length();
				end = end - text.length();
				text = "";
			}
			if (type.equals("script") || type.equals("style")) {
				// send the whole element to the list
				segments.add(text);
				file = file.substring(text.length());
				text = "";
				if (!element.endsWith("/>")) {
					end = file.toLowerCase().indexOf("/" + type + ">");
					String script = file.substring(0, end + 2 + type.length());
					if (script.indexOf("<!--") != -1 && script.indexOf("-->") == -1) {
						// there are nested scripts inside this one
						end = file.toLowerCase().indexOf("/" + type + ">", file.indexOf("-->") + 3);
						script = file.substring(0, end + 2 + type.length());
					}
					segments.add(script);
					file = file.substring(end + 2 + type.length());
				} else {
					segments.add(element);
					file = file.substring(element.length());
				}

				start = file.indexOf('<');
				end = file.indexOf('>');
				if (start != -1) {
					text = file.substring(0, start);
				}
				continue;
			}
			if (type.equals("!--")) {
				// it's a comment, send it to skeleton
				// and keep looking for segments
				segments.add(text);
				file = file.substring(text.length());
				text = "";
				end = file.indexOf("-->");
				String comment = file.substring(0, end + 3);
				segments.add(comment);
				file = file.substring(end + 3);
				start = file.indexOf('<');
				end = file.indexOf('>');
				if (start != -1) {
					text = file.substring(0, start);
				}
				continue;
			}

			text = text + element;

			if (end < file.length()) { // there may be text to extract
				int nextTag = file.indexOf('<', end);
				if (nextTag != -1) {
					text += file.substring(end + 1, nextTag);
				}
			}

			if (start < file.length()) {
				start++;
			}
			if (end < file.length()) {
				end = file.indexOf('>', end + 1);
			} else {
				end = file.length();
			}
			start = file.indexOf('<', start);
		}
		segments.add(text);

	}

	private static String getType(String string) {

		String result = "";
		if (string.startsWith("<![CDATA[")) {
			return "![CDATA[";
		}
		if (string.startsWith("<![IGNORE[")) {
			return "![IGNORE[";
		}
		if (string.startsWith("<![INCLUDE[")) {
			return "![INCLUDE[";
		}
		if (string.startsWith("<!--")) {
			return "!--";
		}
		if (string.startsWith("<?")) {
			return "?";
		}

		if (!string.isEmpty()) {
			// skip initial "<"
			string = string.substring(1);
		}

		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			if (c == ' ' || c == '\n' || c == '\f' || c == '\t' || c == '\r' || c == '>') {
				break;
			}
			result = result + c;
		}
		if (result.endsWith("/") && result.length() > 1) {
			result = result.substring(0, result.length() - 1);
		}
		return result.toLowerCase();
	}

	private static boolean containsText(String string) {
		StringBuilder buffer = new StringBuilder();
		int length = string.length();
		boolean inTag = false;

		for (int i = 0; i < length; i++) {
			char c = string.charAt(i);
			if (string.startsWith("<ph", 1)) {
				inTag = true;
			}
			if (string.startsWith("</ph>", i)) {
				inTag = false;
				i = i + 4;
				continue;
			}
			if (!inTag) {
				buffer.append(c);
			}
		}
		return !buffer.toString().trim().isEmpty();
	}

}
