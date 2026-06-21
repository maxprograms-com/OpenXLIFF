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
package com.maxprograms.converters.ts;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;
import com.maxprograms.xml.XMLUtils;

public class Ts2Xliff {

	private static int segId;
	private static FileOutputStream output;

	private Ts2Xliff() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		List<String> result = new ArrayList<>();
		segId = 0;
		String inputFile = params.get("source");
		String xliffFile = params.get("xliff");
		String skeletonFile = params.get("skeleton");
		String sourceLanguage = params.get("srcLang");
		String targetLanguage = params.get("tgtLang");
		String srcEncoding = params.get("srcEncoding");
		String tgtLang = "";
		if (targetLanguage != null) {
			tgtLang = "\" target-language=\"" + targetLanguage;
		}

		try {
			output = new FileOutputStream(xliffFile);
			writeString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			writeString("<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" "
					+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
					+ "xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");

			writeString("<file original=\"" + inputFile + "\" source-language=\"" + sourceLanguage + tgtLang
					+ "\" tool-id=\"" + Constants.TOOLID + "\" datatype=\"x-ts\">\n");
			writeString("<header>\n");
			writeString("   <skl>\n");
			writeString("      <external-file href=\"" + skeletonFile + "\"/>\n");
			writeString("   </skl>\n");
			writeString("   <tool tool-version=\"" + Constants.VERSION + " " + Constants.BUILD + "\" tool-id=\""
					+ Constants.TOOLID + "\" tool-name=\"" + Constants.TOOLNAME + "\"/>\n");
			writeString("</header>\n");
			writeString("<?encoding " + srcEncoding + "?>\n");
			writeString("<body>\n");

			SAXBuilder builder = new SAXBuilder();
			Document doc = builder.build(inputFile);
			recurse(doc.getRootElement());

			try (FileOutputStream skl = new FileOutputStream(skeletonFile)) {
				XMLOutputter outputter = new XMLOutputter();
				outputter.preserveSpace(true);
				outputter.output(doc, skl);
			}

			writeString("</body>\n");
			writeString("</file>\n");
			writeString("</xliff>");
			output.close();

			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException e) {
			Logger logger = System.getLogger(Ts2Xliff.class.getName());
			logger.log(Level.ERROR, Messages.getString("Ts2Xliff.1"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}

		return result;
	}

	private static void recurse(Element e) throws IOException {
		if (e.getName().equals("message")) {
			Element source = e.getChild("source");
			Element target = e.getChild("translation");
			target.setAttribute("id", "" + segId);
			String targetText = getTarget(target);
			Element comment = e.getChild("comment");
			String approved = (target.getAttributeValue("type").isEmpty() && !targetText.trim().isEmpty()) ? "yes"
					: "no";
			writeString("<trans-unit id=\"" + segId++ + "\" approved=\"" + approved + "\" xml:space=\"preserve\">\n");
			writeString("<source>" + getText(source) + "</source>\n");
			writeString("<target>" + targetText + "</target>\n");
			if (comment != null) {
				writeString("<note>" + getText(comment) + "</note>\n");
			}
			writeString("</trans-unit>\n");
		} else {
			List<Element> children = e.getChildren();
			for (Element child : children) {
				recurse(child);
			}
		}
	}

	private static String getTarget(Element target) {
		List<Element> numerusforms = target.getChildren("numerusform");
		if (!numerusforms.isEmpty()) {
			return getText(numerusforms.get(0));
		}
		return getText(target);
	}

	private static String getText(Element e) {
		String result = "";
		int id = 0;
		List<XMLNode> nodes = e.getContent();
		for (XMLNode n : nodes) {
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				result = result + n.toString();
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element by = (Element) n;
				result = result + "<ph id=\"" + id++ + "\">" + XMLUtils.cleanText(by.toString()) + "</ph>";
			}
		}
		return result;
	}

	private static void writeString(String string) throws IOException {
		output.write(string.getBytes(StandardCharsets.UTF_8));
	}

}
