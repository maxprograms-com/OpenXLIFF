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
package com.maxprograms.converters.txml;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;
import com.maxprograms.xml.XMLUtils;

public class Txml2Xliff {

	private static String sourceLanguage;
	private static String targetLanguage;
	private static FileOutputStream output;
	private static int tagId;
	private static int segNum;
	private static String srcEncoding;

	private Txml2Xliff() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		List<String> result = new ArrayList<>();
		String inputFile = params.get("source");
		String xliffFile = params.get("xliff");
		String skeletonFile = params.get("skeleton");
		sourceLanguage = params.get("srcLang");
		targetLanguage = params.get("tgtLang");
		String catalog = params.get("catalog");
		srcEncoding = params.get("srcEncoding");

		try {
			SAXBuilder builder = new SAXBuilder();
			builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));
			Document doc = builder.build(inputFile);
			Element root = doc.getRootElement();

			output = new FileOutputStream(xliffFile);
			writeHeader(inputFile, skeletonFile);
			recurse(root);
			writeEnd();
			output.close();

			try (FileOutputStream skl = new FileOutputStream(skeletonFile)) {
				XMLOutputter outputter = new XMLOutputter();
				outputter.output(doc, skl);
			}

			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
			Logger logger = System.getLogger(Txml2Xliff.class.getName());
			logger.log(Level.ERROR, Messages.getString("Txml2Xliff.1"), e);
			result.add(Constants.ERROR);
			if (e.getMessage() != null) {
				result.add(e.getMessage());
			} else {
				result.add(Messages.getString("Txml2Xliff.2"));
			}
		}
		return result;
	}

	private static void writeHeader(String source, String skeleton) throws IOException {
		String tgtLang = "";
		if (targetLanguage != null) {
			tgtLang = "\" target-language=\"" + targetLanguage;
		}

		writeStr("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
		writeStr(
				"<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");
		writeStr("<file datatype=\"x-txml\" original=\"" + XMLUtils.cleanText(source) + "\" tool-id=\""
				+ Constants.TOOLID + "\" source-language=\"" + sourceLanguage + tgtLang + "\">\n");
		writeStr("<header>\n");
		writeStr("  <skl>\n");
		writeStr("    <external-file href=\"" + XMLUtils.cleanText(skeleton) + "\"/>\n");
		writeStr("  </skl>\n");
		writeStr("  <tool tool-version=\"" + Constants.VERSION + " " + Constants.BUILD + "\" tool-id=\""
				+ Constants.TOOLID + "\" tool-name=\"" + Constants.TOOLNAME + "\"/>\n");
		writeStr("</header>\n");
		writeStr("<?encoding " + srcEncoding + "?>\n");
		writeStr("<body>\n");
	}

	private static void recurse(Element root) throws IOException {
		List<Element> children = root.getChildren();
		for (Element child : children) {
			if (child.getName().equals("segment") || child.getName().equals("localizable")) {
				parseSegment(child);
			} else {
				recurse(child);
			}
		}
	}

	private static void parseSegment(Element segment) throws IOException {
		segNum++;
		tagId = 0;
		writeStr("<trans-unit id=\"" + segNum + "\">\n");
		Element src = segment.getChild("source");
		Element tgt = segment.getChild("target");
		writeStr(parseElement(src) + "\n");
		if (tgt == null) {
			tgt = new Element("target");
			segment.addContent(tgt);
		}
		tagId = 0;
		writeStr(parseElement(tgt) + "\n");
		Element comments = segment.getChild("comments");
		if (comments != null) {
			parseComments(comments);
		}
		writeStr("</trans-unit>\n");
	}

	private static String parseElement(Element ele) {
		StringBuilder result = new StringBuilder();
		result.append("<" + ele.getName() + ">");
		List<XMLNode> content = ele.getContent();
		for (XMLNode node : content) {
			if (node.getNodeType() == XMLNode.TEXT_NODE) {
				result.append(node.toString());
			}
			if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element tag = (Element) node;
				result.append(parseTag(tag));
			}
		}
		result.append("</" + ele.getName() + ">");
		if (ele.getName().equals("target")) {
			ele.setContent(new ArrayList<>());
			ele.setText("%%%" + segNum + "%%%");
		}
		return result.toString();
	}

	private static String parseTag(Element tag) {
		tagId++;
		return "<ph id=\"" + tagId + "\">" + XMLUtils.cleanText(tag.toString()) + "</ph>";
	}

	private static void parseComments(Element comments) throws IOException {
		List<Element> list = comments.getChildren("comment");
		for (Element comment : list) {
			writeStr("<note>");
			writeStr(XMLUtils.cleanText(comment.getText()));
			writeStr("</note>\n");
		}
	}

	private static void writeEnd() throws IOException {
		writeStr("</body>\n");
		writeStr("</file>\n");
		writeStr("</xliff>\n");
	}

	private static void writeStr(String string) throws IOException {
		output.write(string.getBytes(StandardCharsets.UTF_8));
	}

}
