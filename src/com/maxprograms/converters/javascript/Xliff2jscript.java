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
package com.maxprograms.converters.javascript;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
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

public class Xliff2jscript {

	private static String xliffFile;
	private static Map<String, Element> segments;
	private static FileOutputStream output;
	private static String catalog;
	private static String encoding;

	private Xliff2jscript() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		List<String> result = new ArrayList<>();

		String sklFile = params.get("skeleton");
		xliffFile = params.get("xliff");
		encoding = params.get("encoding");
		catalog = params.get("catalog");

		try {
			String outputFile = params.get("backfile");
			output = new FileOutputStream(outputFile);
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
							line = line.substring(line.indexOf("%%%") + 3);
							Element segment = segments.get(code);
							if (segment != null) {

								if (segment.getAttributeValue("approved", "no").equalsIgnoreCase("Yes")) {
									Element target = segment.getChild("target");
									if (target != null) {
										writeString(target.getText());
									} else {
										MessageFormat mf = new MessageFormat(Messages.getString("Xliff2jscript.1"));
										throw new UnexistentSegmentException(mf.format(new String[] { code }));
									}
								} else {
									// process source
									Element source = segment.getChild("source");
									writeString(source.getText());
								}
							} else {
								MessageFormat mf = new MessageFormat(Messages.getString("Xliff2jscript.2"));
								throw new UnexistentSegmentException(mf.format(new String[] { code }));
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
			}
			output.close();
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | UnexistentSegmentException
				| URISyntaxException e) {
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}

		return result;
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

	private static void writeString(String string) throws IOException {
		output.write(string.getBytes(encoding));
	}

}
