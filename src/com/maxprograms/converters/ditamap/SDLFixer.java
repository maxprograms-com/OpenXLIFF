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
package com.maxprograms.converters.ditamap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
import com.maxprograms.xml.XMLOutputter;

public class SDLFixer {

	private static String baseURL;
	private static Map<String, String> table;
	private static boolean changes;

	private SDLFixer() {
		// do not instantiate this class
		// use fix() method instead
	}

	public static void fix(File folder, Catalog catalog)
			throws IOException, SAXException, ParserConfigurationException {
		baseURL = folder.toURI().toURL().toString();
		String[] files = folder.list();
		table = new HashMap<>();
		for (int i = 0; i < files.length; i++) {
			String file = files[i];
			if (file.endsWith(".xlf") || file.endsWith(".tmx") || file.endsWith(".autosave")) {
				continue;
			}
			int index = file.indexOf("GUID");
			if (index != -1) {
				int end = file.indexOf('=', index + 1);
				String guid = file.substring(index, end);
				table.put(guid, file);
			}
		}
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(catalog);
		builder.setValidating(false);

		XMLOutputter outputter = new XMLOutputter();
		outputter.preserveSpace(true);

		Set<String> keys = table.keySet();
		for (String key : keys) {
			changes = false;
			File f = new File(folder, table.get(key));
			Document doc = builder.build(f);
			Element root = doc.getRootElement();
			recurse(root, folder);
			if (changes) {
				Indenter.indent(root, 2);
				try (FileOutputStream output = new FileOutputStream(f)) {
					outputter.output(doc, output);
				}
			}
		}
	}

	private static void recurse(Element root, File folder) throws MalformedURLException {
		String href = root.getAttributeValue("href");
		if (!href.isEmpty()) {
			href = URLDecoder.decode(href, StandardCharsets.UTF_8);
			String file = table.get(href);
			if (file != null) {
				File f = new File(folder, file);
				String url = f.toURI().toURL().toString();
				root.setAttribute("href", url.substring(baseURL.length()));
				changes = true;
			}
		}
		String conref = root.getAttributeValue("conref");
		int index = conref.indexOf('=');
		if (!conref.isEmpty() && conref.startsWith("GUID-") && index != -1) {
			String guid = conref.substring(0, index);
			index = conref.indexOf('#');
			if (index != -1) {
				String file = table.get(guid);
				if (file != null) {
					String rest = conref.substring(index);
					root.setAttribute("conref", file + rest);
					changes = true;
				}
			} else {
				String file = table.get(guid);
				if (file != null) {
					root.setAttribute("conref", file);
					changes = true;
				}
			}
		}

		String lang = root.getAttributeValue("xml:lang");
		if ((!lang.isEmpty())) {
			lang = lang.replace("_", "-");
			root.setAttribute("xml:lang", lang);
		}
		root.removeAttribute("class");
		root.removeAttribute("ditaarch:DITAArchVersion");
		root.removeAttribute("domains");
		List<Element> children = root.getChildren();
		for (int i = 0; i < children.size(); i++) {
			recurse(children.get(i), folder);
		}
	}
}
