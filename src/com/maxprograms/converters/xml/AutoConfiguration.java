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

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;

public class AutoConfiguration {

	private static Map<String, String> segment;

	private AutoConfiguration() {
		// do not instantiate this class
		// use run method instead
	}

	public static void run(String input, String out, Catalog catalog)
			throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(catalog);
		Document d = builder.build(input);
		Element r = d.getRootElement();
		segment = new HashMap<>();
		recurse(r);

		Document doc = new Document(null, "ini-file", "-//Maxprograms//Converters 2.0.0//EN", "configuration.dtd");
		Element root = doc.getRootElement();
		Set<String> keys = segment.keySet();
		for (String key : keys) {
			Element e = new Element("tag");
			e.setText(key);
			e.setAttribute("hard-break", "segment");
			root.addContent(e);
		}

		XMLOutputter outputter = new XMLOutputter();
		Indenter.indent(root, 2);
		try (FileOutputStream output = new FileOutputStream(out)) {
			outputter.output(doc, output);
		}
	}

	private static void recurse(Element r) {
		StringBuilder text = new StringBuilder();
		List<XMLNode> content = r.getContent();
		for (XMLNode n : content) {
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				text.append(((TextNode) n).getText().trim());
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				recurse(e);
			}
		}
		if (!text.isEmpty() && !segment.containsKey(r.getName())) {
			segment.put(r.getName(), r.getName());
		}
	}
}
