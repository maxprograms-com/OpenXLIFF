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
package com.maxprograms.converters.mif;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.converters.UnexistentSegmentException;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;

public class Xliff2Mif {

	private static String xliffFile;
	private static Map<String, Element> segments;
	private static FileOutputStream output;
	private static Map<String, String> charmap;
	private static String catalog;
	private static boolean useUnicode;

	private Xliff2Mif() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		List<String> result = new ArrayList<>();

		String sklFile = params.get("skeleton");
		xliffFile = params.get("xliff");
		catalog = params.get("catalog");

		try {
			File f = new File(params.get("backfile"));
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
			output = new FileOutputStream(f);
			loadSegments();
			loadCharMap();

			try (InputStreamReader input = new InputStreamReader(new FileInputStream(sklFile),
					StandardCharsets.UTF_8)) {
				BufferedReader buffer = new BufferedReader(input);
				String line = buffer.readLine();
				if (line.indexOf("<MIFFile 2015>") != -1) {
					useUnicode = true;
				}
				while (line != null) {
					if (line.startsWith("%%%")) {
						//
						// translatable text
						//
						String code = line.substring(3, line.length() - 3);
						Element segment = segments.get(code);
						if (segment != null) {
							if (segment.getAttributeValue("approved", "no").equals("yes")) {
								Element target = segment.getChild("target");
								if (target != null) {
									process(target);
								} else {
									MessageFormat mf = new MessageFormat(Messages.getString("Xliff2Mif.1"));
									throw new UnexistentSegmentException(mf.format(new String[] { code }));
								}
							} else {
								// process source
								Element source = segment.getChild("source");
								process(source);
							}
						} else {
							MessageFormat mf = new MessageFormat(Messages.getString("Xliff2Mif.2"));
							throw new UnexistentSegmentException(mf.format(new String[] { code }));
						}
					} else {
						//
						// non translatable portion
						//
						writeString(line + "\n");
					}
					line = buffer.readLine();
				}
			}
			output.close();
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | UnexistentSegmentException
				| URISyntaxException e) {
			Logger logger = System.getLogger(Xliff2Mif.class.getName());
			logger.log(Level.ERROR, Messages.getString("Xliff2Mif.3"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	private static void loadCharMap() throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder cbuilder = new SAXBuilder();
		Document cdoc = cbuilder.build(Xliff2Mif.class.getResource("init_mif.xml"));
		charmap = new HashMap<>();
		Element croot = cdoc.getRootElement();
		List<Element> codes = croot.getChildren("char");
		for (Element e : codes) {
			charmap.put(e.getText(), e.getAttributeValue("code"));
		}
	}

	private static void process(Element e) throws IOException {
		String result = "";
		List<XMLNode> content = e.getContent();
		for (XMLNode n : content) {
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				result += "   <String `" + cleanString(((TextNode) n).getText()) + "'>\n";
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element el = (Element) n;
				if (el.getName().equals("ph")) {
					result += el.getText() + "\n";
				}
			}
		}
		writeString(result);
	}

	private static void loadSegments()
			throws SAXException, IOException, ParserConfigurationException, URISyntaxException {
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));

		Document doc = builder.build(xliffFile);
		Element root = doc.getRootElement();
		Element body = root.getChild("file").getChild("body");
		List<Element> units = body.getChildren("trans-unit");

		segments = new HashMap<>();

		for (Element unit : units) {
			segments.put(unit.getAttributeValue("id"), unit);
		}
	}

	private static String cleanString(String string) {
		int length = string.length();
		StringBuilder buff = new StringBuilder();
		for (int i = 0; i < length; i++) {
			buff.append(getCleanChar(string.charAt(i)));
		}
		return buff.toString();
	}

	private static String getCleanChar(char c) {
		switch (c) {
			case '\u0009':
				return "\\t";
			case '\'':
				return "\\q";
			case '`':
				return "\\Q";
			case '\\':
				return "\\\\";
			case '>':
				return "\\>";
		}
		if (useUnicode) {
			return "" + c;
		}
		String s = "" + c;
		if (c > '\u007f' && charmap.containsKey(s)) {
			return charmap.get(s) + " ";
		}
		return "" + c;
	}

	private static void writeString(String string) throws IOException {
		output.write(string.getBytes());
	}

}
