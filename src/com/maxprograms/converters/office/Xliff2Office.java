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
package com.maxprograms.converters.office;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
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
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLOutputter;

public class Xliff2Office {

	private static Logger logger = System.getLogger(Xliff2Office.class.getName());

	private Map<String, String> filesTable;
	private boolean isEmbedded = false;

	private Xliff2Office() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		Xliff2Office converter = new Xliff2Office();
		return converter.convert(params);
	}

	private List<String> convert(Map<String, String> params) {
		List<String> result = new ArrayList<>();
		String xliffFile = params.get("xliff");
		String outputFile = params.get("backfile");
		String catalog = params.get("catalog");
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
			try (ZipInputStream in = new ZipInputStream(new FileInputStream(skeleton))) {
				try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f))) {
					ZipEntry entry = null;
					while ((entry = in.getNextEntry()) != null) {
						if (entry.getName().matches(".*\\.[xX][mM][lL]\\.skl")) {
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
							String s = filesTable.get(name);
							if (s == null) {
								MessageFormat mf = new MessageFormat(Messages.getString("Xliff2Office.1"));
								logger.log(Level.WARNING, mf.format(new String[] { name }));
								continue;
							}
							table.put("xliff", s);
							table.put("backfile", filesTable.get(name) + ".xml");
							table.put("catalog", params.get("catalog"));
							table.put("skeleton", filesTable.get(name) + ".skl");
							table.put("encoding", params.get("encoding"));
							List<String> res = Xliff2Xml.run(table);
							if (!Constants.SUCCESS.equals(res.get(0))) {
								return res;
							}
							// adjust the spaces in the file
							fixSpaces(filesTable.get(name) + ".xml", catalog);
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
							tmp.deleteOnExit();
							File xml = new File(filesTable.get(name) + ".xml");
							Files.delete(Paths.get(xml.toURI()));
							File xlf = new File(filesTable.get(name));
							Files.delete(Paths.get(xlf.toURI()));
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
							Files.delete(Paths.get(tmp.toURI()));
						}
					}
				}
			}
			if (isEmbedded) {
				File f1 = new File(skeleton);
				Files.delete(Paths.get(f1.toURI()));
			}
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
			logger.log(Level.ERROR, Messages.getString("Xliff2Office.2"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	private static void fixSpaces(String file, String catalog)
			throws SAXException, IOException, ParserConfigurationException, URISyntaxException {
		SAXBuilder builder = new SAXBuilder();
		builder.setValidating(false);
		Document doc = builder.build(file);
		builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));
		addPreserveSpace(doc.getRootElement());
		XMLOutputter outputter = new XMLOutputter();
		outputter.preserveSpace(true);
		try (FileOutputStream output = new FileOutputStream(file)) {
			outputter.output(doc, output);
		}
	}

	private static void addPreserveSpace(Element e) {
		// a:t is used in PowerPoint and should not be modified
		// <t> is a simple type, does not support attributes
		if (e.getName().matches("[w-z]:t")) {
			e.setAttribute("xml:space", "preserve");
		}
		List<Element> children = e.getChildren();
		for (Element child : children) {
			addPreserveSpace(child);
		}
	}

	private void saveFile(Element element, String xliffFile) throws IOException {
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
		outputter.preserveSpace(true);
		try (FileOutputStream output = new FileOutputStream(xliff.getAbsolutePath())) {
			outputter.output(doc, output);
		}
	}

}
