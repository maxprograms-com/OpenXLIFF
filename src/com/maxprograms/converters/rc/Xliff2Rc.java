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
package com.maxprograms.converters.rc;

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

public class Xliff2Rc {

	private static String sklFile;
	private static String xliffFile;

	private static Map<String, Element> segments;
	private static FileOutputStream output;
	private static String catalog;
	private static Map<String, Object> dlgText;
	private static String destTemp;
	private static String encoding;

	private Xliff2Rc() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		List<String> result = new ArrayList<>();

		dlgText = new HashMap<>();

		sklFile = params.get("skeleton");
		xliffFile = params.get("xliff");
		catalog = params.get("catalog");
		encoding = params.get("encoding");

		try {

			File tempFile = File.createTempFile("tempRC", ".temp");
			destTemp = tempFile.getAbsolutePath();
			output = new FileOutputStream(destTemp);
			loadSegments();

			dlgInitExists(params);

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
										writeString(converDlgInit(target.getText(), code));
									} else {
										throw new UnexistentSegmentException();
									}
								} else {
									// process source
									Element source = segment.getChild("source");
									writeString(converDlgInit(source.getText(), code));
								}
							} else {
								throw new UnexistentSegmentException();
							}

							index = line.indexOf("%%%");
							if (index == -1) {
								writeString(line);
							}
						} // end while

					} else {
						writeString(line);
					}
				}
			}
			output.close();
			dlgInitLengths(params);
			Files.delete(Paths.get(tempFile.toURI()));
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | UnexistentSegmentException
				| URISyntaxException e) {
			Logger logger = System.getLogger(Xliff2Rc.class.getName());
			logger.log(Level.ERROR, Messages.getString("Xliff2Rc.1"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	private static String converDlgInit(String word, String code) {
		if (dlgText.containsKey(code)) {
			return decode(word, code);
		}
		return "\"" + word + "\"";
	}

	private static String decode(String word, String code) {
		dlgText.remove(code);
		byte[] bytes = word.getBytes(StandardCharsets.UTF_8);
		String byteWord = "";
		Byte par = Byte.valueOf("00");
		int length = bytes.length;

		for (int i = 0; i < length; i = i + 1) {
			if (i % 2 == 0) {
				par = Byte.valueOf(bytes[i]);
			} else {
				Byte impar = Byte.valueOf(bytes[i]);
				byteWord = byteWord + " 0x" + Integer.toHexString(impar.intValue())
						+ Integer.toHexString(par.intValue()) + ", ";
			}
		}

		if (bytes.length % 2 == 0) {
			byteWord = byteWord + "\"\\000\" ";
		} else {
			if (length > 0) {
				byteWord = byteWord + " 0x00" + Integer.toHexString(par.intValue()) + ", ";
			}
			byteWord = byteWord + " ";
		}
		length++;
		dlgText.put(code, Integer.toString(length));
		return byteWord;
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

	private static void writeStringEncoded(String string) throws IOException {
		output.write(string.getBytes(encoding));
	}

	private static void writeString(String string) throws IOException {
		output.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private static void dlgInitExists(Map<String, String> params) throws IOException, UnexistentSegmentException {
		sklFile = params.get("skeleton");
		xliffFile = params.get("xliff");
		catalog = params.get("catalog");
		try (InputStreamReader input = new InputStreamReader(new FileInputStream(sklFile), StandardCharsets.UTF_8)) {
			BufferedReader buffer = new BufferedReader(input);
			String line;
			while ((line = buffer.readLine()) != null) {
				if (line.indexOf("###") != -1) {
					// contains dlginit length
					int index = line.indexOf("###");
					while (index != -1) {
						line = line.substring(index + 3);
						String code = line.substring(0, line.indexOf("###"));
						line = line.substring(line.indexOf("###") + 3);
						Element segment = segments.get(code);
						if (segment != null) {
							dlgText.put(code, Integer.valueOf(0));
						} else {
							MessageFormat mf = new MessageFormat(Messages.getString("Xliff2Rc.2"));
							throw new UnexistentSegmentException(mf.format(new String[] { code }));
						}
						index = line.indexOf("###");
					} // end while
				}
			}
		}
	}

	private static void dlgInitLengths(Map<String, String> params) throws IOException {
		sklFile = params.get("skeleton");
		xliffFile = params.get("xliff");
		catalog = params.get("catalog");

		String outputFile = params.get("backfile");
		output = new FileOutputStream(outputFile);
		try (InputStreamReader input = new InputStreamReader(new FileInputStream(destTemp), StandardCharsets.UTF_8)) {
			BufferedReader buffer = new BufferedReader(input);
			String line;
			while ((line = buffer.readLine()) != null) {
				line = line + "\n";

				if (line.indexOf("###") != -1) {
					// contains dlginit length
					int index = line.indexOf("###");
					while (index != -1) {
						String start = line.substring(0, index);
						writeStringEncoded(start);
						line = line.substring(index + 3);
						String code = line.substring(0, line.indexOf("###"));
						line = line.substring(line.indexOf("###") + 3);
						writeStringEncoded((String) dlgText.get(code));
						index = line.indexOf("###");
						if (index == -1) {
							writeStringEncoded(line);
						}
					} // end while

				} else {
					writeStringEncoded(line);
				}
			}
		}
	}
}
