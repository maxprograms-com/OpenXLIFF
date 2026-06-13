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

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.segmenter.Segmenter;
import com.maxprograms.segmenter.SegmenterPool;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;
import com.maxprograms.xml.XMLUtils;

public class XliffModel {

	private Document doc;
	private Element root;
	private Charset encoding;
	private String version;
	private List<String> ids;
	private Map<String, Element> sources;
	private Map<String, Element> targets;
	private Map<String, List<Element>> matches;
	private Map<String, List<Element>> notes;
	private Map<String, Boolean> translatable;
	private Map<String, Boolean> approved;
	private Segmenter segmenter;
	private FileOutputStream out;
	private String original;
	private String srclang;
	private boolean hasSegSource;
	private boolean modified;
	private ArrayList<Element> mrks;
	private Set<String> namespaces;

	public XliffModel(String url, String srx, Catalog catalog)
			throws SAXException, IOException, ParserConfigurationException {
		namespaces = new TreeSet<>();
		original = url;
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(catalog);
		doc = builder.build(url);
		root = doc.getRootElement();
		encoding = doc.getEncoding();
		version = root.getAttributeValue("version");
		srclang = root.getChild("file").getAttributeValue("source-language");
		if (version.equals("1.2") && srx != null) {
			segmenter = SegmenterPool.getSegmenter(srx, srclang, catalog);
		}
		ids = new ArrayList<>();
		sources = new HashMap<>();
		targets = new HashMap<>();
		matches = new HashMap<>();
		notes = new HashMap<>();
		translatable = new HashMap<>();
		approved = new HashMap<>();
		List<Attribute> atts = root.getAttributes();
		for (Attribute a : atts) {
			if (a.getName().startsWith("xmlns:")) {
				String ns = a.getName().substring("xmlns:".length());
				if (!ns.equals("xml")) {
					namespaces.add(ns);
				}
			}
		}
		recurse(root);
		if (segmenter != null) {
			segmenter = null;
		}
	}

	private void recurse(Element e) throws SAXException, IOException, ParserConfigurationException {
		if (e.getName().equals("trans-unit")) {

			boolean translate = e.getAttributeValue("translate", "yes").equals("yes");
			boolean appr = e.getAttributeValue("approved", "no").equals("yes");

			if (version.equals("1.2")) {
				Element segSource = e.getChild("seg-source");
				if (segSource == null && translate && segmenter != null) {
					segSource = segmenter.segment(e.getChild("source"));
					// insert segSource in the unit
					List<XMLNode> content = e.getContent();
					List<XMLNode> newContent = new ArrayList<>();
					for (int i = 0; i < content.size(); i++) {
						XMLNode n = content.get(i);
						if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
							newContent.add(n);
							Element child = (Element) n;
							if (n.getNodeType() == XMLNode.ELEMENT_NODE && child.getName().equals("source")) {
								newContent.add(new TextNode("\n"));
								newContent.add(segSource);
								modified = true;
							}
						}
					}
					e.setContent(newContent);
				}
				if (segSource != null) {
					String id = e.getAttributeValue("id");
					List<Element> segments = getMrks(segSource);
					if (segments.isEmpty() && !segSource.getText().trim().isEmpty()) {
						// no <mrk> elements but there is translatable text
						Element mrk = new Element("mrk");
						mrk.setAttribute("mid", "0");
						mrk.setAttribute("mtype", "seg");
						mrk.setText(segSource.getText());
						segSource.setContent(new ArrayList<>());
						segSource.addContent(mrk);
						segments.add(mrk);
						modified = true;
					}
					for (int i = 0; i < segments.size(); i++) {
						Element mrk = segments.get(i);
						ids.add(id + ":" + mrk.getAttributeValue("mid"));
						sources.put(id + ":" + mrk.getAttributeValue("mid"), mrk);
					}
					Element target = e.getChild("target");
					if (target != null) {
						List<Element> translations = getMrks(target);
						if (translations.isEmpty() && !target.getText().trim().isEmpty()) {
							// no <mrk> elements but there is translatable text
							Element mrk = new Element("mrk");
							mrk.setAttribute("mid", "0");
							mrk.setAttribute("mtype", "seg");
							mrk.setText(target.getText());
							target.setContent(new ArrayList<>());
							target.addContent(mrk);
							translations.add(mrk);
							modified = true;
						} else {
							for (int h = 0; h < translations.size(); h++) {
								Element mrk = translations.get(h);
								targets.put(id + ":" + mrk.getAttributeValue("mid"), mrk);
							}
						}
					}
					List<Element> alttrans = e.getChildren("alt-trans");
					for (int i = 0; i < alttrans.size(); i++) {
						Element alt = alttrans.get(i);
						String mid = id + ":" + alt.getAttributeValue("mid");
						if (matches.containsKey(mid)) {
							matches.get(mid).add(alt);
						} else {
							List<Element> list = new ArrayList<>();
							list.add(alt);
							matches.put(mid, list);
						}
					}
					List<Element> nts = e.getChildren("note");
					for (int i = 0; i < nts.size(); i++) {
						Element note = nts.get(i);
						List<PI> nids = note.getPI("mid");
						String nid = "1";
						if (!nids.isEmpty()) {
							nid = nids.get(0).getData();
						}
						String mid = id + ":" + nid;
						if (notes.containsKey(mid)) {
							notes.get(mid).add(note);
						} else {
							List<Element> list = new ArrayList<>();
							list.add(note);
							notes.put(mid, list);
						}
					}
				} else {
					// use source as is
					String id = e.getAttributeValue("id");
					ids.add(id);
					sources.put(id, e.getChild("source"));
					Element target = e.getChild("target");
					if (target != null) {
						targets.put(id, target);
					}
					matches.put(id, e.getChildren("alt-trans"));
					notes.put(id, e.getChildren("note"));
					translatable.put(id, translate);
					approved.put(id, appr);
				}
			} else {
				String id = e.getAttributeValue("id");
				sources.put(id, e.getChild("source"));
				Element t = e.getChild("target");
				if (t != null) {
					targets.put(id, t);
				}
				matches.put(id, e.getChildren("alt-trans"));
				notes.put(id, e.getChildren("note"));
			}
		} else {
			List<Element> children = e.getChildren();
			for (Element child : children) {
				recurse(child);
			}
		}
	}

	private List<Element> getMrks(Element element) {
		mrks = new ArrayList<>();
		recurseMrks(element);
		return mrks;
	}

	private void recurseMrks(Element element) {
		if (element.getName().equals("mrk")) {
			mrks.add(element);
		} else {
			List<Element> children = element.getChildren();
			for (Element child : children) {
				recurseMrks(child);
			}
		}
	}

	public void save(String url) throws IOException {
		try (FileOutputStream output = new FileOutputStream(url)) {
			XMLOutputter outputer = new XMLOutputter();
			outputer.setEncoding(encoding);
			outputer.preserveSpace(true);
			outputer.output(doc, output);
		}
	}

	public boolean isSegmented() {
		hasSegSource = false;
		recurseSegments(root);
		return hasSegSource;
	}

	private void recurseSegments(Element e) {
		if (e.getName().equals("trans-unit")) {
			Element segSource = e.getChild("seg-source");
			if (segSource != null) {
				hasSegSource = true;
			}
			return;
		}
		List<Element> children = e.getChildren();
		for (int i = 0; i < children.size(); i++) {
			recurseSegments(children.get(i));
		}
	}

	List<String> getIds() {
		return ids;
	}

	private void writeStr(String string) throws IOException {
		out.write(string.getBytes(StandardCharsets.UTF_8));
	}

	public void normalize(String output, String skeletonFile) throws IOException {
		String targetLanguage = root.getChild("file").getAttributeValue("target-language");
		if (!targetLanguage.isEmpty()) {
			targetLanguage = "\" target-language=\"" + targetLanguage;
		}

		out = new FileOutputStream(output);
		writeStr("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
		writeStr("<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" "
				+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
				+ "xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");
		writeStr("<file datatype=\"x-unknown\" original=\"" + XMLUtils.cleanText(original) + "\" source-language=\""
				+ srclang + targetLanguage + "\">\n");
		writeStr("<?encoding " + doc.getEncoding() + "?>\n");

		writeStr("<header>\n");
		writeStr("<skl>\n");
		writeStr("<external-file href=\"" + XMLUtils.cleanText(skeletonFile) + "\"/>\n");
		writeStr("</skl>\n");
		writeStr("</header>\n");
		writeStr("<body>\n");

		normalize(doc.getRootElement());

		writeStr("</body>\n");
		writeStr("</file>\n");
		writeStr("</xliff>\n");
		out.close();

		XMLOutputter outputter = new XMLOutputter();
		outputter.preserveSpace(true);
		outputter.setSkipLinefeed(true);
		outputter.writeBOM(true);
		try (FileOutputStream skl = new FileOutputStream(skeletonFile)) {
			outputter.output(doc, skl);
		}
	}

	private void normalize(Element root1) throws IOException {
		if (root1.getName().equals("trans-unit")) {
			if (root1.getAttributeValue("translate", "yes").equals("no")) {
				return;
			}
			Element segSource = root1.getChild("seg-source");
			Element target = root1.getChild("target");
			if (segSource != null) {
				if (containsText(segSource)) {
					Map<String, Element> targets1 = new HashMap<>();
					if (target != null) {
						List<Element> tmarks = getSegments(target);
						for (Element mrk : tmarks) {
							targets1.put(mrk.getAttributeValue("mid"), mrk);
						}
					}
					List<Element> mrks1 = getSegments(segSource);
					if (!mrks1.isEmpty()) {
						for (Element mrk : mrks1) {
							writeStr("<trans-unit id=\"" + root1.getAttributeValue("id") + ':'
									+ mrk.getAttributeValue("mid") + "\" xml:space=\"preserve\">\n");
							// write new source
							writeStr("<source>");
							recurseSource(mrk);
							writeStr("</source>\n");
							if (targets1.containsKey(mrk.getAttributeValue("mid"))) {
								// write new target
								Element tmrk = targets1.get(mrk.getAttributeValue("mid"));
								writeStr("<target>");
								recurseTarget(tmrk);
								writeStr("</target>\n");
							}
							writeStr("</trans-unit>\n");
						}
					}
				}
			} else {
				// <seg-source> is null, write <trans-unit> as is
				writeStr(root1.toString());
			}
		} else {
			// not in <trans-unit>
			List<Element> children = root1.getChildren();
			for (Element child : children) {
				normalize(child);
			}
		}

	}

	private boolean containsText(Element source) {
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

	private List<Element> getSegments(Element e) {
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

	private void recurseSource(Element mrk) throws IOException {
		List<XMLNode> nodes = mrk.getContent();
		for (XMLNode n : nodes) {
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				writeStr(n.toString());
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				if (e.getName().equals("mrk")) {
					recurseSource(e);
				} else {
					writeStr(e.toString());
				}
			}
		}
	}

	private void recurseTarget(Element mrk) throws IOException {
		List<XMLNode> tnodes = mrk.getContent();
		for (XMLNode n : tnodes) {
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				writeStr(n.toString());
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				if (e.getName().equals("mrk")) {
					recurseTarget(e);
				} else {
					writeStr(e.toString());
				}
			}
		}
	}

	public boolean wasModified() {
		return modified;
	}

	public boolean hasNamespaces() {
		return !namespaces.isEmpty();
	}

	public Set<String> getNamespaces() {
		return namespaces;
	}

}
