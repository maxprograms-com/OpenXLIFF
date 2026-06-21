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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
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
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;

public class Xliff2Txml {

	private static String xliffFile;
	private static String catalog;
	private static Map<String, Element> segments;

	private Xliff2Txml() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		List<String> result = new ArrayList<>();

		String sklFile = params.get("skeleton");
		xliffFile = params.get("xliff");
		catalog = params.get("catalog");
		String encoding = params.get("encoding");
		String outputFile = params.get("backfile");

		try {
			loadSegments();
			SAXBuilder builder = new SAXBuilder();
			Document doc = builder.build(sklFile);
			Element root = doc.getRootElement();
			replaceTargets(root);
			XMLOutputter outputter = new XMLOutputter();
			outputter.preserveSpace(true);
			outputter.setEncoding(Charset.forName(encoding));
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
				outputter.output(doc, output);
			}
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
			Logger logger = System.getLogger(Xliff2Txml.class.getName());
			logger.log(Level.ERROR, Messages.getString("Xliff2Txml.1"), e);
			result.add(Constants.ERROR);
			if (e.getMessage() != null) {
				result.add(e.getMessage());
			} else {
				result.add(Messages.getString("Xliff2Txml.2"));
			}
		}

		return result;
	}

	private static void replaceTargets(Element e) throws SAXException, IOException, ParserConfigurationException {
		if (e.getName().equals("revisions")) {
			return;
		}
		if (e.getName().equals("target")) {
			String id = e.getText().substring(3);
			id = id.substring(0, id.length() - 3);
			Element seg = segments.get(id);
			e.setText("");
			List<XMLNode> content = seg.getChild("target").getContent();
			for (XMLNode n : content) {
				if (n.getNodeType() == XMLNode.TEXT_NODE) {
					e.addContent(((TextNode) n).getText().replaceAll("[\\n\\r]", ""));
				}
				if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
					Element tag = (Element) n;
					e.addContent(buildTag(tag.getText().replaceAll("[\\n\\r]", "")));
				}
			}
		} else {
			List<Element> children = e.getChildren();
			for (Element child : children) {
				replaceTargets(child);
			}
		}
	}

	private static Element buildTag(String text) throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		Document sdoc = builder.build(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
		Element sroot = sdoc.getRootElement();
		Element tag = new Element(sroot.getName());
		tag.clone(sroot);
		return tag;
	}

	private static void loadSegments()
			throws SAXException, IOException, ParserConfigurationException, URISyntaxException {

		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));

		Document sdoc = builder.build(xliffFile);
		Element root = sdoc.getRootElement();
		Element body = root.getChild("file").getChild("body");
		List<Element> units = body.getChildren("trans-unit");

		segments = new HashMap<>();

		for (Element unit : units) {
			segments.put(unit.getAttributeValue("id"), unit);
		}
	}
}
