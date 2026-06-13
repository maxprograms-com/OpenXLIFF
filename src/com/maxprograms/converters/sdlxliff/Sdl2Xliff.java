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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;
import com.maxprograms.xml.XMLUtils;

public class Sdl2Xliff {

	private static final List<String> CONTEXT_TYPES = List.of("database", "element", "elementtitle", "linenumber",
			"numparams", "paramnotes", "record", "recordtitle", "sourceFile");

	private Sdl2Xliff() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		List<String> result = new ArrayList<>();

		try {
			String original = params.get("source");
			String output = params.get("xliff");
			String skeletonFile = params.get("skeleton");
			String sourceLanguage = params.get("srcLang");
			String targetLanguage = params.get("tgtLang");
			String srxRules = params.get("srxFile");
			String catalogFile = params.get("catalog");
			String tgtLang = "";
			if (targetLanguage != null) {
				tgtLang = "\" target-language=\"" + targetLanguage;
			}

			Catalog catalog = CatalogBuilder.getCatalog(catalogFile);
			XliffModel model = new XliffModel(original, srxRules, catalog);
			if (model.wasModified()) {
				File f = File.createTempFile("temp", ".sdlxliff");
				f.deleteOnExit();
				model.save(f.getAbsolutePath());
				original = f.getAbsolutePath();
			}
			model = null;

			Map<String, String[]> contextMap = new Hashtable<>();

			SAXBuilder builder = new SAXBuilder();
			builder.setEntityResolver(catalog);
			Document doc = builder.build(original);

			FileOutputStream out = new FileOutputStream(output);
			writeStr(out, "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
			writeStr(out, "<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" "
					+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
					+ "xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");
			writeStr(out, "<file datatype=\"x-sdlxliff\" original=\"" + XMLUtils.cleanText(params.get("source"))
					+ "\" tool-id=\"" + Constants.TOOLID + "\" source-language=\"" + sourceLanguage + tgtLang + "\" "
					+ "xmlns:sdl=\"http://sdl.com/FileTypes/SdlXliff/1.0\" " + ">\n");
			writeStr(out, "<?encoding " + doc.getEncoding() + "?>\n");

			writeStr(out, "<header>\n");
			writeStr(out, "  <skl>\n");
			writeStr(out, "    <external-file href=\"" + XMLUtils.cleanText(skeletonFile) + "\"/>\n");
			writeStr(out, "  </skl>\n");
			writeStr(out, "  <tool tool-version=\"" + Constants.VERSION + " " + Constants.BUILD + "\" tool-id=\""
					+ Constants.TOOLID + "\" tool-name=\"" + Constants.TOOLNAME + "\"/>\n");
			writeStr(out, "</header>\n");
			writeStr(out, "<body>\n");

			acceptTrackedChanges(doc.getRootElement());
			recurse(doc.getRootElement(), out, contextMap);

			writeStr(out, "</body>\n");
			writeStr(out, "</file>\n");
			writeStr(out, "</xliff>\n");
			out.close();

			XMLOutputter outputter = new XMLOutputter();
			outputter.preserveSpace(true);
			outputter.setSkipLinefeed(true);
			outputter.writeBOM(true);
			try (FileOutputStream skl = new FileOutputStream(skeletonFile)) {
				outputter.output(doc, skl);
			}
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
			Logger logger = System.getLogger(Sdl2Xliff.class.getName());
			logger.log(Level.ERROR, Messages.getString("Sdl2Xliff.1"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	private static void recurse(Element root, FileOutputStream out, Map<String, String[]> contextMap)
			throws IOException {
		if ("cxt-defs".equals(root.getName())) {
			List<Element> defs = root.getChildren("cxt-def");
			for (Element def : defs) {
				if (def.hasAttribute("id") && def.hasAttribute("name") && def.hasAttribute("descr")) {
					String[] pair = new String[] { def.getAttributeValue("name"), def.getAttributeValue("descr") };
					contextMap.put(def.getAttributeValue("id"), pair);
				}
			}
		} else if ("group".equals(root.getName())) {
			List<String[]> contextList = new Vector<>();
			Element contextInfo = root.getChild("sdl:cxts");
			if (contextInfo != null) {
				List<Element> cxts = contextInfo.getChildren("sdl:cxt");
				for (Element cxt : cxts) {
					String id = cxt.getAttributeValue("id");
					if (contextMap.containsKey(id)) {
						String[] pair = contextMap.get(id);
						contextList.add(pair);
					}
				}
			}
			for (Element child : root.getChildren()) {
				if ("trans-unit".equals(child.getName())) {
					processUnit(child, out, contextMap, contextList);
				} else {
					recurse(child, out, contextMap);
				}
			}
		} else if ("trans-unit".equals(root.getName())) {
			processUnit(root, out, contextMap, new Vector<>());
		} else {
			List<Element> children = root.getChildren();
			for (Element child : children) {
				recurse(child, out, contextMap);
			}
		}
	}

	private static void processUnit(Element unit, FileOutputStream out, Map<String, String[]> contextMap,
			List<String[]> contextList) throws IOException {
		if (unit.getAttributeValue("translate", "yes").equals("no")
				&& !(unit.getAttributeValue("sdl:locktype").equals("Manual")
						&& containsSrcText(unit.getChild("source")))) {
			return;
		}
		Element segSource = unit.getChild("seg-source");
		Element target = unit.getChild("target");

		Map<String, Boolean> lockedMap = new HashMap<>();
		Map<String, String> statusMap = new HashMap<>();

		Element segDefs = unit.getChild("sdl:seg-defs");
		if (segDefs != null) {
			List<Element> children = segDefs.getChildren("sdl:seg");
			for (Element sdlSeg : children) {
				String id = sdlSeg.getAttributeValue("id");
				if ("true".equals(sdlSeg.getAttributeValue("locked", "false"))) {
					lockedMap.put(id, true);
				}
				if ("Draft".equals(sdlSeg.getAttributeValue("conf"))) {
					statusMap.put(id, "new");
				}
				if ("Translated".equals(sdlSeg.getAttributeValue("conf"))) {
					statusMap.put(id, "translated");
				}
				if ("RejectedTranslation".equals(sdlSeg.getAttributeValue("conf"))) {
					statusMap.put(id, "needs-review-translation");
				}
				if ("ApprovedSignOff".equals(sdlSeg.getAttributeValue("conf"))) {
					statusMap.put(id, "signed-off");
				}
			}
		}
		if (segSource != null) {
			if (containsText(segSource)) {
				Map<String, Element> targets = new HashMap<>();
				if (target != null) {
					List<Element> tmarks = getSegments(target);
					for (Element mrk : tmarks) {
						targets.put(mrk.getAttributeValue("mid"), mrk);
					}
				}
				List<Element> mrks = getSegments(segSource);
				if (!mrks.isEmpty()) {
					for (Element mrk : mrks) {
						String id = mrk.getAttributeValue("mid");
						String lockedString = "";
						if (lockedMap.containsKey(id)) {
							lockedString = "\" ts=\"locked";
						}
						writeStr(out, "  <trans-unit id=\"" + unit.getAttributeValue("id") + ':'
								+ mrk.getAttributeValue("mid") + lockedString + "\" xml:space=\"preserve\">\n");
						// write new source
						writeStr(out, "    <source>");
						recurseSource(mrk, out);
						writeStr(out, "</source>\n");
						if (targets.containsKey(mrk.getAttributeValue("mid"))) {
							// write new target
							String stateString = "";
							if (statusMap.containsKey(id)) {
								stateString = " state=\"" + statusMap.get(id) + "\"";
							}
							Element tmrk = targets.get(mrk.getAttributeValue("mid"));
							writeStr(out, "    <target " + stateString + ">");
							recurseTarget(tmrk, out);
							writeStr(out, "</target>\n");
						}
						if (!contextList.isEmpty()) {
							writeStr(out, "    <context-group>\n");
							for (String[] pair : contextList) {
								String type = pair[0];
								if (!CONTEXT_TYPES.contains(type)) {
									type = "x-sdl-" + type;
								}
								type = type.replaceAll("\\s+", "_sdl:spc_");
								writeStr(out,
										"      <context context-type=\"" + type + "\">" + XMLUtils.cleanText(pair[1])
												+ "</context>\n");
							}
							writeStr(out, "    </context-group>\n");
						}
						writeStr(out, "  </trans-unit>\n");
					}
				}
			}
		} else {
			// <seg-source> is null
			if (unit.getAttributeValue("translate", "yes").equals("no")) {
				writeStr(out, unit.toString());
			} else {
				throw new IOException(Messages.getString("Sdl2Xliff.2"));
			}
		}
	}

	private static boolean containsSrcText(Element child) {
		List<XMLNode> content = child.getContent();
		for (XMLNode n : content) {
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				if (!n.toString().trim().isEmpty()) {
					return true;
				}
			} else if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				if (e.getName().equals("g") && containsSrcText(e)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void recurseTarget(Element mrk, FileOutputStream out) throws IOException {
		List<XMLNode> tnodes = mrk.getContent();
		for (XMLNode n : tnodes) {
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				writeStr(out, n.toString());
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				if (e.getName().equals("mrk")) {
					recurseTarget(e, out);
				} else {
					writeStr(out, e.toString());
				}
			}
		}
	}

	private static void recurseSource(Element mrk, FileOutputStream out) throws IOException {
		List<XMLNode> nodes = mrk.getContent();
		for (XMLNode n : nodes) {
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				writeStr(out, n.toString());
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				if (e.getName().equals("mrk")) {
					recurseSource(e, out);
				} else {
					writeStr(out, e.toString());
				}
			}
		}

	}

	private static List<Element> getSegments(Element e) {
		List<Element> result = new ArrayList<>();
		List<Element> children = e.getChildren();
		for (Element child : children) {
			if (child.getName().equals("mrk") && child.getAttributeValue("mtype").equals("seg")) {
				result.add(child);
			} else {
				result.addAll(getSegments(child));
			}
		}
		return result;
	}

	private static boolean containsText(Element source) {
		List<XMLNode> nodes = source.getContent();
		for (XMLNode n : nodes) {
			if (n.getNodeType() == XMLNode.TEXT_NODE && !n.toString().trim().isEmpty()) {
				return true;
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				if (e.getName().equals("mrk") && containsText(e)) {
					return true;
				}
				if (e.getName().equals("g") && containsText(e)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void writeStr(FileOutputStream out, String string) throws IOException {
		out.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private static void acceptTrackedChanges(Element e) {
		List<Element> children = e.getChildren("mrk");
		if (!children.isEmpty()) {
			List<XMLNode> content = e.getContent();
			List<XMLNode> newContent = new ArrayList<>();
			for (XMLNode n : content) {
				if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
					Element child = (Element) n;
					if (child.getName().equals("mrk")) {
						if (child.getAttributeValue("mtype").equals("x-sdl-added")) {
							acceptTrackedChanges(child);
							newContent.addAll(child.getContent());
						} else if (child.getAttributeValue("mtype").equals("x-sdl-deleted")) {
							// do nothing
						} else {
							newContent.add(child);
						}
					} else {
						newContent.add(child);
					}
				} else {
					newContent.add(n);
				}
			}
			e.setContent(newContent);
		}
		children = e.getChildren();
		for (Element child : children) {
			acceptTrackedChanges(child);
		}
	}
}
