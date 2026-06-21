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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
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

public class Xliff2Ts {

	private static Map<String, Element> segments;
	private static String xliffFile;

	private Xliff2Ts() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		List<String> result = new ArrayList<>();
		try {
			xliffFile = params.get("xliff");
			loadSegments();

			String sklFile = params.get("skeleton");
			SAXBuilder builder = new SAXBuilder();
			Document doc = builder.build(sklFile);
			recurseSkl(doc.getRootElement());

			String outputFile = params.get("backfile");
			File f = new File(outputFile);
			File p = f.getParentFile();
			if (p == null) {
				p = new File(System.getProperty("user.dir"));
			}
			if (Files.notExists(p.toPath())) {
				Files.createDirectories(p.toPath());
			}
			if (!f.exists()) {
				Files.createFile(Paths.get(f.toURI()));
			}
			try (FileOutputStream output = new FileOutputStream(f)) {
				XMLOutputter outputter = new XMLOutputter();
				outputter.setEncoding(StandardCharsets.UTF_8);
				outputter.preserveSpace(true);
				outputter.escapeQuotes(true);
				outputter.setEmptyDoctype(true);
				outputter.output(doc, output);
			}
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException e) {
			Logger logger = System.getLogger(Xliff2Ts.class.getName());
			logger.log(Level.ERROR, Messages.getString("Xliff2Ts.1"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	private static void recurseSkl(Element e) throws SAXException, IOException, ParserConfigurationException {
		if (e.getName().equals("message")) {
			Element translation = e.getChild("translation");
			String id = translation.getAttributeValue("id");
			boolean wasObsolete = translation.getAttributeValue("type").equals("obsolete");
			String old = translation.getText();
			Element segment = segments.get(id);
			Element tmp = getTranslation(segment.getChild("target"));
			translation.clone(tmp);
			if (segment.getAttributeValue("approved").equals("yes")) {
				translation.removeAttribute("type");
			} else {
				if (wasObsolete) {
					if (old.equals(tmp.getText())) {
						translation.setAttribute("type", "obsolete");
					} else {
						translation.setAttribute("type", "unfinished");
					}
				} else {
					translation.setAttribute("type", "unfinished");
				}
			}
		} else {
			List<Element> children = e.getChildren();
			for (Element child : children) {
				recurseSkl(child);
			}
		}
	}

	private static Element getTranslation(Element e) throws SAXException, IOException, ParserConfigurationException {
		String result = "";
		List<XMLNode> nodes = e.getContent();
		for (XMLNode n : nodes) {
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				result = result + n.toString();
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element by = (Element) n;
				result = result + by.getText();
			}
		}
		result = "<translation>" + result + "</translation>";
		SAXBuilder b = new SAXBuilder();
		Document d = b.build(new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8)));
		return d.getRootElement();
	}

	private static void loadSegments() throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		Document sdoc = builder.build(xliffFile);
		Element root = sdoc.getRootElement();
		segments = new HashMap<>();
		recurseXliff(root);
	}

	private static void recurseXliff(Element e) {
		List<Element> list = e.getChildren();
		for (Element u : list) {
			if (u.getName().equals("trans-unit")) {
				segments.put(u.getAttributeValue("id"), u);
			} else {
				recurseXliff(u);
			}
		}
	}

}
