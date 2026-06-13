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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;

public class Xliff2Html {

	private static String xliffFile;
	private static Map<String, Element> segments;
	private static FileOutputStream output;
	private static String encoding;
	private static Map<String, String> entities;
	private static Catalog catalog;

	private Xliff2Html() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		List<String> result = new ArrayList<>();

		String sklFile = params.get("skeleton");
		xliffFile = params.get("xliff");
		encoding = params.get("encoding");

		try {
			catalog = CatalogBuilder.getCatalog(params.get("catalog"));
			loadEntities();
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
			output = new FileOutputStream(f);
			loadSegments();

			try (InputStreamReader input = new InputStreamReader(new FileInputStream(sklFile),
					StandardCharsets.UTF_8)) {
				BufferedReader buffer = new BufferedReader(input);
				String line;
				while ((line = buffer.readLine()) != null) {
					line = line + "\n";

					if (line.indexOf("%%%") != -1) {
						//
						// contains translatable text
						//
						int index = line.indexOf("%%%");
						while (index != -1) {
							String start = line.substring(0, index);
							writeString(start);
							line = line.substring(index + 3);
							String code = line.substring(0, line.indexOf("%%%"));
							line = line.substring(line.indexOf("%%%\n") + 4);
							Element segment = segments.get(code);
							if (segment != null) {
								Element target = segment.getChild("target");
								Element source = segment.getChild("source");
								if (target != null) {
									if (segment.getAttributeValue("approved", "no").equals("yes")) {
										writeString(extractText(target));
									} else {
										writeString(extractText(source));
									}
								} else {
									writeString(extractText(source));
								}
							} else {
								result.add(Constants.ERROR);
								result.add("segment " + code + " not found");
								return result;
							}

							index = line.indexOf("%%%");
							if (index == -1) {
								writeString(line);
							}
						} // end while
					} else {
						//
						// non translatable portion
						//
						writeString(line);
					}
				}

				output.close();
			}
			result.add(Constants.SUCCESS);

		} catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
			Logger logger = System.getLogger(Xliff2Html.class.getName());
			logger.log(Level.ERROR, Messages.getString("Xliff2Html.1"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	private static String extractText(Element target) {
		String result = "";
		List<XMLNode> content = target.getContent();
		for (XMLNode n : content) {
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				result = result + extractText(e);
			}
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				result = result + ((TextNode) n).getText();
			}
		}
		return addEntities(result);
	}

	private static void loadEntities() throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		Document doc = builder.build(Xliff2Html.class.getResource("entities.xml"));
		Element root = doc.getRootElement();

		entities = new HashMap<>();

		List<Element> ents = root.getChildren("entity");
		for (Element e : ents) {
			entities.put(e.getText(), "&" + e.getAttributeValue("name") + ";");
		}
	}

	private static String addEntities(String text) {
		StringBuilder result = new StringBuilder();
		boolean inTag = false;
		int start = text.indexOf('<');
		int end = text.indexOf('>');
		if (end != -1) {
			if (start == -1) {
				inTag = true;
			} else {
				if (end < start) {
					inTag = true;
				}
			}
		}
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '<') {
				inTag = true;
			}
			if (c == '>') {
				inTag = false;
			}
			if (!inTag && entities.containsKey("" + c)) {
				if (c == '&' && text.charAt(i + 1) == '#') {
					// check if it is an escaped entity
					// like: &amp;#x2018;
					int scolon = text.indexOf(';', i);
					if (scolon == -1) {
						// not an escaped entity
						result.append(entities.get("" + c));
					} else {
						// check for space before the semicolon
						int space = text.indexOf(' ', i);
						if (space == -1) {
							// no space before the semicolon
							// it is an escaped entity
							result.append(c);
						} else {
							if (space > scolon) {
								// space is after semicolon
								// it is an escaped entity
								result.append(c);
							} else {
								// not an escaped entity
								result.append(entities.get("" + c));
							}
						}
					}
				} else {
					result.append(entities.get("" + c));
				}
			} else {
				result.append(c);
			}
		}
		return result.toString();
	}

	private static void writeString(String string) throws IOException {
		output.write(string.getBytes(encoding));
	}

	private static void loadSegments() throws SAXException, IOException, ParserConfigurationException {

		SAXBuilder builder = new SAXBuilder();
		if (catalog != null) {
			builder.setEntityResolver(catalog);
		}

		Document doc = builder.build(xliffFile);
		Element root = doc.getRootElement();
		Element body = root.getChild("file").getChild("body");
		List<Element> units = body.getChildren("trans-unit");

		segments = new HashMap<>();
		for (Element unit : units) {
			segments.put(unit.getAttributeValue("id"), unit);
		}
	}

}
