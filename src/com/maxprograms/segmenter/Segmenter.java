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
package com.maxprograms.segmenter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLUtils;

public class Segmenter {

	public static final String STARTIGNORE = "@#$%~";
	public static final String ENDIGNORE = "~%$#@";

	private Element root;
	private boolean cascade;
	private List<Element> rules;

	public Segmenter(String srxFile, String srcLanguage, Catalog catalog)
			throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(catalog);
		Document doc = builder.build(srxFile);
		root = doc.getRootElement();
		if (!root.getName().equals("srx")) {
			throw new IOException(Messages.getString("Segmenter.1"));
		}
		if (!root.getAttributeValue("version").equals("2.0")) {
			throw new IOException(Messages.getString("Segmenter.2"));
		}
		cascade = isCascading();
		buildRulesList(srcLanguage);
	}

	public Segmenter(Document doc, String srcLanguage) throws IOException {
		root = doc.getRootElement();
		if (!root.getName().equals("srx")) {
			throw new IOException(Messages.getString("Segmenter.1"));
		}
		cascade = isCascading();
		buildRulesList(srcLanguage);
	}

	public String[] segmentRawString(String string) {
		return segment(string, false);
	}

	public String[] segment(String string) {
		return segment(string, true);
	}

	private String[] segment(String string, boolean prepare) {
		if (string == null || string.isEmpty()) {
			return new String[] {};
		}
		Map<String, String> tags = new HashMap<>();
		String pureText = prepare ? prepareString(string, tags) : string;
		List<String> parts = new ArrayList<>();
		for (int pos = 0; pos < pureText.length(); pos++) {
			String left = hideTags(pureText.substring(0, pos), tags);
			String right = hideTags(pureText.substring(pos), tags);
			if (left.isEmpty()) {
				continue;
			}
			for (int i = 0; i < rules.size(); i++) {
				Element rule = rules.get(i);
				boolean breaks = rule.getAttributeValue("break", "yes").equals("yes");
				Element before = rule.getChild("beforebreak");
				Element after = rule.getChild("afterbreak");
				String beforexp = "";
				if (before != null) {
					beforexp = before.getText();
				}
				String afterxp = "";
				if (after != null) {
					afterxp = after.getText();
				}
				if (!beforexp.isEmpty() && !afterxp.isEmpty()) {
					// match left and right
					if (endsWith(left, beforexp) && startsWith(right, afterxp)) {
						if (breaks) {
							parts.add(pureText.substring(0, pos));
							pureText = pureText.substring(pos);
							pos = -1;
						}
						break;
					}
				} else if (!beforexp.isEmpty()) {
					// match left side only
					if (endsWith(left, beforexp)) {
						if (breaks) {
							parts.add(pureText.substring(0, pos));
							pureText = pureText.substring(pos);
							pos = -1;
						}
						break;
					}
				} else {
					// match right side only
					if (startsWith(right, afterxp)) {
						if (breaks) {
							parts.add(pureText.substring(0, pos));
							pureText = pureText.substring(pos);
							pos = -1;
						}
						break;
					}
				}
			}
		}
		parts.add(pureText);
		String[] result = new String[parts.size()];
		for (int i = 0; i < parts.size(); i++) {
			result[i] = cleanup(parts.get(i), tags);
		}
		return result;
	}

	private String hideTags(String string, Map<String, String> tags) {
		String result = string;
		Set<String> keys = tags.keySet();
		for (String key : keys) {
			int index = result.indexOf(key);
			if (index != -1) {
				result = result.substring(0, index) + result.substring(index + 1);
			}
		}
		return result;
	}

	private static boolean endsWith(String string, String exp) {
		Pattern p = Pattern.compile(exp);
		String[] parts = p.split(string);
		if (parts.length > 0) {
			if (!string.endsWith(parts[parts.length - 1])) {
				String ends = string
						.substring(string.lastIndexOf(parts[parts.length - 1]) + parts[parts.length - 1].length());
				Matcher m = p.matcher(ends);
				return m.lookingAt();
			}
			Matcher m = p.matcher(parts[parts.length - 1]);
			return m.lookingAt();
		}
		// split() did not return any part (everything was removed from the returned
		// result).
		// perhaps the whole text matches
		Matcher m = p.matcher(string);
		return m.matches();
	}

	private static boolean startsWith(String string, String exp) {
		Pattern p = Pattern.compile(exp);
		Matcher m = p.matcher(string);
		return m.lookingAt();
	}

	private String prepareString(String raw, Map<String, String> tags) {
		String string = raw;
		int k = 0;

		int start = string.indexOf(STARTIGNORE);
		int end = string.indexOf(ENDIGNORE);

		while (start != -1 && end != -1) {
			if (start > end) {
				break;
			}
			String tag = string.substring(start + STARTIGNORE.length(), end);
			string = string.substring(0, start) + (char) ('\uE000' + k) + string.substring(end + ENDIGNORE.length());
			tags.put("" + (char) ('\uE000' + k), tag);
			k++;
			start = string.indexOf(STARTIGNORE);
			end = string.indexOf(ENDIGNORE);
		}

		string = string.replace(STARTIGNORE, "");
		string = string.replace(ENDIGNORE, "");

		start = string.indexOf("<mrk ");
		end = string.indexOf("</mrk>");
		// check nested <mrk>
		int e = string.indexOf("<mrk ", string.indexOf('>', start));
		while (e != -1 && e < end) {
			end = string.indexOf("</mrk>", end + 1);
			e = string.indexOf("<mrk ", string.indexOf('>', e + 1));
		}

		while (start != -1 && end != -1) {
			if (start > end) {
				break;
			}
			String tag = string.substring(start, end + 6);
			string = string.substring(0, start) + (char) ('\uE000' + k) + string.substring(end + 6);
			tags.put("" + (char) ('\uE000' + k), tag);
			k++;
			start = string.indexOf("<mrk ");
			end = string.indexOf("</mrk>");
		}

		start = string.indexOf("<ph");
		end = string.indexOf("</ph>");

		while (start != -1 && end != -1) {
			if (start > end) {
				break;
			}
			String tag = string.substring(start, end + 5);
			string = string.substring(0, start) + (char) ('\uE000' + k) + string.substring(end + 5);
			tags.put("" + (char) ('\uE000' + k), tag);
			k++;
			start = string.indexOf("<ph");
			end = string.indexOf("</ph>");
		}

		StringBuffer buffer = new StringBuffer();
		StringBuffer element = new StringBuffer();
		int length = string.length();
		boolean inElement = false;
		for (int i = 0; i < length; i++) {
			char c = string.charAt(i);
			if (c == '<' && string.indexOf('>', i) != -1) {
				inElement = true;
				int a = string.indexOf('<', i + 1);
				int b = string.indexOf('>', i + 1);
				if (a != -1 && a < b) {
					inElement = false;
				}
				if (i < length - 1 && !Character.isLetter(string.charAt(i + 1)) && string.charAt(i + 1) != '/') {
					inElement = false;
				}
			}
			if (inElement) {
				element.append(c);
			} else {
				buffer.append(c);
			}
			if (c == '>' && inElement) {
				inElement = false;
				tags.put("" + (char) ('\uE000' + k), element.toString());
				buffer.append((char) ('\uE000' + k));
				element = new StringBuffer();
				k++;
			}
		}
		return buffer.toString();
	}

	private String cleanup(String string, Map<String, String> tags) {
		String result = string;
		Set<String> keys = tags.keySet();
		for (String key : keys) {
			int index = result.indexOf(key);
			if (index != -1) {
				result = result.substring(0, index) + tags.get(key) + result.substring(index + 1);
			}
		}
		return result;
	}

	private void buildRulesList(String srcLanguage) {
		List<String> maps = new ArrayList<>();
		List<Element> allMaps = root.getChild("body").getChild("maprules").getChildren("languagemap");
		for (Element map : allMaps) {
			if (srcLanguage.matches(map.getAttributeValue("languagepattern"))) {
				maps.add(map.getAttributeValue("languagerulename"));
				if (!cascade) {
					break;
				}
			}
		}
		rules = new ArrayList<>();
		List<Element> languageRules = root.getChild("body").getChild("languagerules").getChildren("languagerule");
		for (Element languagerule : languageRules) {
			String name = languagerule.getAttributeValue("languagerulename");
			if (maps.contains(name)) {
				List<Element> ruleset = languagerule.getChildren("rule");
				for (Element rule : ruleset) {
					rules.add(rule);
				}
			}
		}
	}

	private boolean isCascading() {
		return root.getChild("header").getAttributeValue("cascade").equals("yes");
	}

	public Element segment(Element source) throws SAXException, IOException, ParserConfigurationException {
		Map<String, String> tags = new HashMap<>();
		int[] tagId = new int[1];
		String pureText = pureText(source, tags, tagId);
		List<String> parts = new ArrayList<>();
		for (int pos = 0; pos < pureText.length(); pos++) {
			String left = hideTags(pureText.substring(0, pos), tags);
			String right = hideTags(pureText.substring(pos), tags);
			if (left.isEmpty()) {
				continue;
			}
			for (int i = 0; i < rules.size(); i++) {
				Element rule = rules.get(i);
				boolean breaks = rule.getAttributeValue("break", "yes").equals("yes");
				Element before = rule.getChild("beforebreak");
				Element after = rule.getChild("afterbreak");
				String beforexp = "";
				if (before != null) {
					beforexp = before.getText();
				}
				String afterxp = "";
				if (after != null) {
					afterxp = after.getText();
				}
				if (!beforexp.isEmpty() && !afterxp.isEmpty()) {
					// match left and right
					if (endsWith(left, beforexp) && startsWith(right, afterxp)) {
						if (breaks) {
							parts.add(pureText.substring(0, pos));
							pureText = pureText.substring(pos);
							pos = -1;
						}
						break;
					}
				} else if (!beforexp.isEmpty()) {
					// match left side only
					if (endsWith(left, beforexp)) {
						if (breaks) {
							parts.add(pureText.substring(0, pos));
							pureText = pureText.substring(pos);
							pos = -1;
						}
						break;
					}
				} else {
					// match right side only
					if (startsWith(right, afterxp)) {
						if (breaks) {
							parts.add(pureText.substring(0, pos));
							pureText = pureText.substring(pos);
							pos = -1;
						}
						break;
					}
				}
			}
		}
		parts.add(pureText);
		String[] result = new String[parts.size()];
		for (int i = 0; i < parts.size(); i++) {
			result[i] = cleanup(XMLUtils.cleanText(parts.get(i)), tags);
		}
		if (result.length == 1) {
			// return a <seg-source> with the content of source
			Element res = new Element("seg-source");
			Element mrk = new Element("mrk");
			mrk.setAttribute("mtype", "seg");
			mrk.setAttribute("mid", "1");
			res.addContent(mrk);
			mrk.addContent(source.getContent());
			return res;
		}
		// generate segments
		Element res = new Element("seg-source");
		SAXBuilder b = new SAXBuilder();
		for (int i = 0; i < result.length; i++) {
			String seg = "<mrk mtype=\"seg\" mid=\"" + (i + 1) + "\">" + result[i] + "</mrk>";
			Document docu = b.build(new ByteArrayInputStream(seg.getBytes(StandardCharsets.UTF_8)));
			res.addContent(docu.getRootElement());
		}
		return res;
	}

	private String pureText(Element e, Map<String, String> tags, int[] tagId) {
		StringBuilder result = new StringBuilder();
		List<XMLNode> nodes = e.getContent();
		for (XMLNode n : nodes) {
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				result.append(((TextNode) n).getText());
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element tag = (Element) n;
				tags.put("" + (char) ('\uE000' + tagId[0]), tag.toString());
				result.append((char) ('\uE000' + tagId[0]));
				tagId[0]++;
			}
		}
		return result.toString();
	}
}
