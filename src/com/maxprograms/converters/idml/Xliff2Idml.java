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
package com.maxprograms.converters.idml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.converters.xml.Xliff2Xml;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLOutputter;

public class Xliff2Idml {

	private static Map<String, String> filesTable;
	private static boolean isEmbedded;

	private Xliff2Idml() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		List<String> result = new ArrayList<>();
		String xliffFile = params.get("xliff");
		String outputFile = params.get("backfile");
		filesTable = new HashMap<>();
		try {
			SAXBuilder builder = new SAXBuilder();
			Document doc = builder.build(xliffFile);
			Element root = doc.getRootElement();
			List<Element> files = root.getChildren("file");
			for (Element file : files) {
				saveFile(file, xliffFile);
			}
			String skeleton = params.get("skeleton");
			if (isEmbedded) {
				File t = new File(skeleton);
				t.deleteOnExit();
			}

			try (ZipInputStream in = new ZipInputStream(new FileInputStream(skeleton))) {
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
				try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f))) {
					ZipEntry entry = null;
					while ((entry = in.getNextEntry()) != null) {
						if (entry.getName().matches(".*Story_.*\\.xml\\.skl")) {
							String name = entry.getName().substring(0, entry.getName().lastIndexOf(".skl"));
							File tmp = new File(filesTable.get(name) + ".skl");
							try (FileOutputStream output = new FileOutputStream(tmp.getAbsolutePath())) {
								byte[] buf = new byte[1024];
								int len;
								while ((len = in.read(buf)) > 0) {
									output.write(buf, 0, len);
								}
							}
							Map<String, String> table = new HashMap<>();
							table.put("xliff", filesTable.get(name));
							table.put("backfile", filesTable.get(name) + ".xml");
							table.put("catalog", params.get("catalog"));
							table.put("skeleton", filesTable.get(name) + ".skl");
							table.put("encoding", params.get("encoding"));
							table.put("IDML", "true");
							List<String> res = Xliff2Xml.run(table);
							if (!Constants.SUCCESS.equals(res.get(0))) {
								return res;
							}
							ZipEntry content = new ZipEntry(name);
							content.setMethod(ZipEntry.DEFLATED);
							out.putNextEntry(content);
							try (FileInputStream input = new FileInputStream(filesTable.get(name) + ".xml")) {
								byte[] buf = new byte[1024];
								int len;
								while ((len = input.read(buf)) > 0) {
									out.write(buf, 0, len);
								}
								out.closeEntry();
							}
							File xml = new File(filesTable.get(name) + ".xml");
							Files.delete(Paths.get(xml.toURI()));
							File xlf = new File(filesTable.get(name));
							Files.delete(Paths.get(xlf.toURI()));
							tmp.deleteOnExit();
						} else {
							File tmp = File.createTempFile("entry", ".tmp");
							try (FileOutputStream output = new FileOutputStream(tmp.getAbsolutePath())) {
								byte[] buf = new byte[1024];
								int len;
								while ((len = in.read(buf)) > 0) {
									output.write(buf, 0, len);
								}
							}

							ZipEntry content = new ZipEntry(entry.getName());
							content.setMethod(ZipEntry.DEFLATED);
							out.putNextEntry(content);
							try (FileInputStream input = new FileInputStream(tmp.getAbsolutePath())) {
								byte[] buf = new byte[1024];
								int len;
								while ((len = input.read(buf)) > 0) {
									out.write(buf, 0, len);
								}
								out.closeEntry();
							}
							tmp.deleteOnExit();
						}
					}
				}
			}
			if (isEmbedded) {
				File f1 = new File(skeleton);
				Files.delete(Paths.get(f1.toURI()));
			}
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException e) {
			Logger logger = System.getLogger(Xliff2Idml.class.getName());
			logger.log(Level.ERROR, Messages.getString("Xliff2Idml.1"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	private static void saveFile(Element element, String xliffFile) throws IOException {
		Document doc = new Document(null, "xliff", null, null);
		Element root = doc.getRootElement();
		root.setAttribute("version", "1.2");
		Element file = new Element("file");
		file.clone(element);
		root.addContent(file);
		File xliff = File.createTempFile("tmp", ".xlf", new File(xliffFile).getParentFile());
		List<Element> groups = file.getChild("header").getChildren("prop-group");
		for (Element group : groups) {
			if (group.getAttributeValue("name").equals("document")) {
				filesTable.put(group.getChild("prop").getText(), xliff.getAbsolutePath());
			}
		}
		if (file.getChild("header").getChild("skl").getChild("external-file") == null) {
			// embedded skeleton
			file.getChild("header").getChild("skl").addContent(new Element("external-file"));
			isEmbedded = true;
		}
		file.getChild("header").getChild("skl").getChild("external-file").setAttribute("href",
				xliff.getAbsolutePath() + ".skl");
		XMLOutputter outputter = new XMLOutputter();
		try (FileOutputStream output = new FileOutputStream(xliff.getAbsolutePath())) {
			outputter.output(doc, output);
		}
	}
}
