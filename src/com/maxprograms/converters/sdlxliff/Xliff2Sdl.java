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
package com.maxprograms.converters.sdlxliff;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.converters.UnexistentSegmentException;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;

public class Xliff2Sdl {

	private static String sklFile;
	private static String xliffFile;
	private static Map<String, Element> segments;
	private static Document doc;
	private static Element root;
	private static Catalog catalog;

	private Xliff2Sdl() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		List<String> result = new ArrayList<>();

		sklFile = params.get("skeleton");
		xliffFile = params.get("xliff");

		String outputFile = params.get("backfile");
		String type = params.get("type");
		if (type == null) {
			type = "sdlxliff";
		}

		try {
			catalog = CatalogBuilder.getCatalog(params.get("catalog"));

			loadSegments();
			loadSkeleton();

			Set<String> keys = segments.keySet();
			for (String key : keys) {
				Element unit = segments.get(key);
				if (type.equals("sdlxliff")) {
					if (!unit.getAttributeValue("translate", "yes").equals("no")) {
						String fullid = unit.getAttributeValue("id");
						char separator = fullid.indexOf('|') != -1 ? '|' : ':';
						String id = fullid.substring(0, fullid.lastIndexOf(separator));
						String mrk = fullid.substring(fullid.lastIndexOf(separator) + 1);
						replaceTarget(id, mrk, unit.getChild("target"),
								unit.getAttributeValue("approved", "no").equals("yes"));
					}
				} else {
					String fullid = unit.getAttributeValue("id");
					if (fullid.indexOf('|') != -1 || fullid.indexOf(':') != -1) {
						char separator = fullid.indexOf('|') != -1 ? '|' : ':';
						String id = fullid.substring(0, fullid.lastIndexOf(separator));
						String mrk = fullid.substring(fullid.lastIndexOf(separator) + 1);
						replaceTarget(id, mrk, unit.getChild("target"), false);
					}
				}
			}
			XMLOutputter outputter = new XMLOutputter();
			outputter.preserveSpace(true);
			if (type.equals("sdlxliff")) {
				outputter.setSkipLinefeed(true);
				outputter.writeBOM(true);
			}
			File f = new File(outputFile);
			File p = f.getParentFile();
			if (p == null) {
				p = new File(System.getProperty("user.dir"));
			}
			if (Files.notExists(p.toPath())) {
				Files.createDirectories(p.toPath());
			}
			try (FileOutputStream out = new FileOutputStream(f)) {
				outputter.output(doc, out);
			}
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | UnexistentSegmentException
				| URISyntaxException e) {
			Logger logger = System.getLogger(Xliff2Sdl.class.getName());
			logger.log(Level.ERROR, Messages.getString("Xliff2Sdl.1"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	private static void replaceTarget(String id, String mrkId, Element translated, boolean approved)
			throws UnexistentSegmentException {
		if (translated == null) {
			return;
		}
		Element unit = locateUnit(root, id);
		if (unit == null) {
			MessageFormat mf = new MessageFormat(Messages.getString("Xliff2Sdl.2"));
			throw new UnexistentSegmentException(mf.format(new String[] { id }));
		}
		Element target = unit.getChild("target");
		if (target == null) {
			target = new Element("target");
			addTarget(unit, target);
		}
		Element mrk = locateMrk(target, mrkId);
		if (mrk == null) {
			mrk = new Element("mrk");
			mrk.setAttribute("mtype", "seg");
			mrk.setAttribute("mid", mrkId);
			target.addContent(mrk);
		}
		mrk.setContent(new ArrayList<>());
		List<XMLNode> content = translated.getContent();
		for (int i = 0; i < content.size(); i++) {
			XMLNode n = content.get(i);
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				mrk.addContent(n);
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = new Element();
				e.clone((Element) n);
				if (e.getName().equals("x") && e.getAttributeValue("id").startsWith("locked")) {
					String lockedTU = e.getAttributeValue("xid");
					Element tu = segments.get(lockedTU);
					Element referenced = tu.getChild("source").getChild("g");
					Element g = new Element("g");
					g.setAttribute("id", referenced.getAttributeValue("id"));
					mrk.addContent(g);
				} else {
					mrk.addContent(e);
				}
			}
		}
		if (approved) {
			Element defs = unit.getChild("sdl:seg-defs");
			if (defs != null) {
				List<Element> children = defs.getChildren("sdl:seg");
				for (Element seg : children) {
					if (seg.getAttributeValue("id").equals(mrkId)) {
						seg.setAttribute("conf", "Translated");
					}
				}
			}
		}
	}

	private static void addTarget(Element unit, Element target) {
		List<XMLNode> content = unit.getContent();
		List<XMLNode> newContent = new ArrayList<>();
		for (XMLNode node : content) {
			newContent.add(node);
			if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element n = (Element) node;
				if (n.getName().equals("seg-source")) {
					newContent.add(target);
				}
			}
		}
		unit.setContent(newContent);
	}

	private static Element locateMrk(Element e, String mrkId) {
		if (e.getName().equals("mrk") && e.getAttributeValue("mid").equals(mrkId)) {
			return e;
		}
		List<Element> children = e.getChildren();
		for (Element child : children) {
			Element res = locateMrk(child, mrkId);
			if (res != null) {
				return res;
			}
		}
		return null;
	}

	private static Element locateUnit(Element e, String id) {
		if (e.getName().equals("trans-unit") && e.getAttributeValue("id").equals(id)) {
			return e;
		}
		List<Element> children = e.getChildren();
		for (Element child : children) {
			Element res = locateUnit(child, id);
			if (res != null) {
				return res;
			}
		}
		return null;
	}

	private static void loadSkeleton() throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(catalog);
		doc = builder.build(sklFile);
		root = doc.getRootElement();
	}

	private static void loadSegments() throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(catalog);

		Document xdoc = builder.build(xliffFile);
		Element xroot = xdoc.getRootElement();
		Element body = xroot.getChild("file").getChild("body");
		List<Element> units = body.getChildren("trans-unit");

		segments = new HashMap<>();

		for (Element unit : units) {
			segments.put(unit.getAttributeValue("id"), unit);
		}
	}

}
