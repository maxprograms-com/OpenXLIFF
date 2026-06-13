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

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.segmenter.Segmenter;
import com.maxprograms.segmenter.SegmenterPool;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLUtils;

public class Story2Xliff {

	private String inputFile;
	private String skeletonFile;
	private String sourceLanguage;
	private String targetLanguage;
	private String srcEncoding;
	private FileOutputStream output;
	private FileOutputStream skeleton;
	private int id = 1;

	public static List<String> run(Map<String, String> params) {
		Story2Xliff converter = new Story2Xliff();
		return converter.process(params);
	}

	private List<String> process(Map<String, String> params) {
		List<String> result = new ArrayList<>();
		id = 1;
		inputFile = params.get("source");
		String xliffFile = params.get("xliff");
		skeletonFile = params.get("skeleton");
		sourceLanguage = params.get("srcLang");
		targetLanguage = params.get("tgtLang");
		srcEncoding = params.get("srcEncoding");
		String elementSegmentation = params.get("paragraph");
		String initSegmenter = params.get("srxFile");
		String catalog = params.get("catalog");
		boolean paragraphSegmentation = false;
		Segmenter segmenter = null;

		if (elementSegmentation == null) {
			paragraphSegmentation = false;
		} else {
			paragraphSegmentation = elementSegmentation.equals("yes");
		}

		try {
			if (!paragraphSegmentation) {
				segmenter = SegmenterPool.getSegmenter(initSegmenter, sourceLanguage, CatalogBuilder.getCatalog(catalog));
			}
			skeleton = new FileOutputStream(skeletonFile);
			output = new FileOutputStream(xliffFile);
			writeHeader(params.get("from"));

			SAXBuilder builder = new SAXBuilder();
			Document doc = builder.build(inputFile);
			Element root = doc.getRootElement();

			removeChangeTracking(root);

			writeSkeleton("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
			List<PI> pis = doc.getPI();
			for (int i = 0; i < pis.size(); i++) {
				writeSkeleton(pis.get(i).toString() + "\n");
			}
			writeSkeleton("<" + root.getName());
			List<Attribute> atts = root.getAttributes();
			for (Attribute a : atts) {
				writeSkeleton(
						" " + a.getName() + "=\"" + XMLUtils.cleanText(a.getValue()).replace("\"", "&quote;")
								+ "\"");
			}
			writeSkeleton(">");

			List<XMLNode> content = root.getContent();
			for (XMLNode n : content) {
				if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
					Element e = (Element) n;
					if (e.getName().equals("Story")) {
						writeSkeleton("<" + e.getName());
						atts = e.getAttributes();
						for (Attribute a : atts) {
							writeSkeleton(" " + a.getName() + "=\""
									+ XMLUtils.cleanText(a.getValue()).replaceAll("\"", "&quote;") + "\"");
						}
						writeSkeleton(">");
						processStory(segmenter, e);
						writeSkeleton("</" + e.getName() + ">");
					} else {
						writeSkeleton(e.toString());
					}
				}
				if (n.getNodeType() == XMLNode.TEXT_NODE) {
					writeSkeleton(((TextNode) n).getText());
				}
				if (n.getNodeType() == XMLNode.PROCESSING_INSTRUCTION_NODE) {
					writeSkeleton(n.toString());
				}
				if (n.getNodeType() == XMLNode.CDATA_SECTION_NODE) {
					writeSkeleton(n.toString());
				}
			}

			writeSkeleton("</" + root.getName() + ">");
			skeleton.close();

			writeString("</body>\n");
			writeString("</file>\n");
			writeString("</xliff>");
			output.close();

			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
			Logger logger = System.getLogger(Story2Xliff.class.getName());
			logger.log(Level.ERROR, Messages.getString("Story2Xliff.1"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	private void removeChangeTracking(Element root) {
		List<XMLNode> newContent = new ArrayList<>();
		List<XMLNode> nodes = root.getContent();
		boolean changed = false;
		for (XMLNode n : nodes) {
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				if (e.getName().equals("Change")) {
					if (e.getAttributeValue("ChangeType").equals("DeletedText")) {
						changed = true;
					} else {
						newContent.addAll(e.getContent());
						changed = true;
					}
				} else {
					newContent.add(n);
				}
			} else {
				newContent.add(n);
			}
		}
		if (changed) {
			root.setContent(newContent);
			mergeContent(root);
		}
		List<Element> children = root.getChildren();
		for (int i = 0; i < children.size(); i++) {
			Element child = children.get(i);
			removeChangeTracking(child);
		}
	}

	private void mergeContent(Element e) {
		List<XMLNode> newContent = new ArrayList<>();
		Element current = null;
		List<XMLNode> content = e.getContent();
		for (XMLNode node : content) {
			if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
				if (current == null) {
					current = (Element) node;
					newContent.add(node);
				} else {
					Element next = (Element) node;
					if (current.getName().equals(next.getName()) && validateAttributes(current, next)) {
						List<XMLNode> nodes = next.getContent();
						for (XMLNode n : nodes) {
							current.addContent(n);
						}
					} else {
						newContent.add(next);
						current = next;
					}
				}
			} else {
				newContent.add(node);
			}
		}
		e.setContent(newContent);
		List<Element> children = e.getChildren();
		for (Element child : children) {
			mergeContent(child);
		}
	}

	private boolean validateAttributes(Element current, Element next) {
		List<Attribute> currentList = current.getAttributes();
		List<Attribute> nextList = next.getAttributes();
		if (currentList.size() != nextList.size()) {
			return false;
		}
		for (int i = 0; i < currentList.size(); i++) {
			Attribute a = currentList.get(i);
			if (!a.getValue().equals(next.getAttributeValue(a.getName()))) {
				return false;
			}
		}
		return true;
	}

	private void writeSkeleton(String string) throws IOException {
		skeleton.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private void processStory(Segmenter segmenter, Element root) throws IOException {
		List<XMLNode> content = root.getContent();
		for (XMLNode n : content) {
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				writeSkeleton(n.toString());
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				if (e.getName().equals("ParagraphStyleRange")) {
					if (hasText(e)) {
						processPara(segmenter, e);
					} else {
						writeSkeleton(e.toString());
					}
				} else {
					if (e.getChildren().isEmpty()) {
						writeSkeleton(e.toString());
					} else {
						writeSkeleton("<" + e.getName());
						List<Attribute> atts = e.getAttributes();
						for (Attribute a : atts) {
							writeSkeleton(" " + a.getName() + "=\""
									+ XMLUtils.cleanText(a.getValue()).replace("\"", "&quote;") + "\"");
						}
						writeSkeleton(">");
						processStory(segmenter, e);
						writeSkeleton("</" + e.getName() + ">");
					}
				}
			}
			if (n.getNodeType() == XMLNode.PROCESSING_INSTRUCTION_NODE) {
				writeSkeleton(n.toString());
			}
			if (n.getNodeType() == XMLNode.CDATA_SECTION_NODE) {
				writeSkeleton(n.toString());
			}
		}
	}

	private void processPara(Segmenter segmenter, Element e) throws IOException {
		cleanAttributes(e);
		mergeStyles(e);
		writeSkeleton("<" + e.getName());
		List<Attribute> atts = e.getAttributes();
		for (Attribute a : atts) {
			writeSkeleton(
					" " + a.getName() + "=\"" + XMLUtils.cleanText(a.getValue()).replace("\"", "&quote;") + "\"");
		}
		writeSkeleton(">");
		StringBuilder source = new StringBuilder("<ph>");
		List<XMLNode> content = e.getContent();
		boolean preamble = true;
		for (XMLNode n : content) {
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				source.append(n.toString());
			}
			if (n.getNodeType() == XMLNode.CDATA_SECTION_NODE) {
				source.append(n.toString());
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element el = (Element) n;
				if (hasText(el)) {
					preamble = false;
					source.append(recurseElement(el));
				} else {
					if (preamble) {
						writeSkeleton(el.toString());
					} else {
						source.append(el.toString());
					}
				}
			}
			if (n.getNodeType() == XMLNode.PROCESSING_INSTRUCTION_NODE) {
				source.append(n.toString());
			}
		}
		source.append("</ph>");
		String[] parts = splitCell(source.toString());
		for (int i = 0; i < parts.length; i++) {
			String s = parts[i];
			String first = s.substring(0, s.indexOf("</ph>") + 5);
			writeSkeleton(first.substring("<ph>".length(), first.length() - "</ph>".length()));
			if (!first.equals(s)) {
				String center = s.substring(first.length(), s.lastIndexOf("<ph>"));
				String last = s.substring(s.lastIndexOf("<ph>"));
				String[] segments = null;
				if (segmenter != null) {
					segments = segmenter.segment(fixTags(center));
				} else {
					segments = new String[] { fixTags(center) };
				}
				for (int h = 0; h < segments.length; h++) {
					if (segments[h].trim().isEmpty()) {
						writeSkeleton(segments[h]);
					} else {
						writeSkeleton("%%%" + id + "%%%");
						writeString(
								"<trans-unit id=\"" + id++ + "\" xml:space=\"preserve\">\n<source>" + segments[h]
										+ "</source>\n</trans-unit>\n");
					}
				}
				writeSkeleton(last.substring("<ph>".length(), last.length() - "</ph>".length()));
			}
		}
		writeSkeleton("</" + e.getName() + ">");
	}

	private void mergeStyles(Element e) {
		List<XMLNode> newContent = new ArrayList<>();
		List<XMLNode> content = e.getContent();
		Element current = null;
		Element currentStyle = null;
		for (int i = 0; i < content.size(); i++) {
			XMLNode node = content.get(i);
			if (node.getNodeType() == XMLNode.TEXT_NODE) {
				newContent.add(node);
			}
			if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element child = (Element) node;
				if (child.getName().equals("CharacterStyleRange")) {
					current = child;
					currentStyle = getStyle(current);
					int h = 1;
					int skip = 0;
					while (i + h < content.size()) {
						XMLNode next = content.get(i + h);
						if (next.getNodeType() == XMLNode.ELEMENT_NODE) {
							Element n = (Element) next;
							if (n.getName().equals("CharacterStyleRange")) {
								Element nextStyle = getStyle(n);
								if (currentStyle.equals(nextStyle)) {
									String text = extractContent(n);
									addContent(current, text);
									skip = h;
								} else {
									break;
								}
							} else {
								break;
							}
						}
						h++;
					}
					newContent.add(node);
					i = i + skip;
				} else {
					newContent.add(node);
				}
			}
		}
		e.setContent(newContent);
	}

	private void addContent(Element e, String text) {
		Element content = e.getChild("Content");
		if (content != null) {
			content.setText(content.getText() + text);
		}
	}

	private String extractContent(Element e) {
		Element content = e.getChild("Content");
		if (content != null) {
			return content.getText();
		}
		return "";
	}

	private Element getStyle(Element e) {
		Element result = new Element(e.getName());
		List<Attribute> atts = e.getAttributes();
		for (int i = 0; i < atts.size(); i++) {
			Attribute a = atts.get(i);
			result.setAttribute(a.getName(), a.getValue());
		}
		List<XMLNode> content = e.getContent();
		for (XMLNode node : content) {
			if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element child = (Element) node;
				if (!child.getName().equals("Content")) {
					result.addContent(getStyle(child));
				}
			} else if (node.getNodeType() == XMLNode.TEXT_NODE) {
				result.addContent(node);
			}
		}
		return result;
	}

	private void cleanAttributes(Element e) {
		Attribute a = e.getAttribute("CharacterDirection");
		if (a != null && a.getValue().equals("DefaultDirection")) {
			e.removeAttribute("CharacterDirection");
		}
		a = e.getAttribute("DiacriticPosition");
		if (a != null && a.getValue().equals("DefaultPosition")) {
			e.removeAttribute("DiacriticPosition");
		}
		a = e.getAttribute("DigitsType");
		if (a != null && a.getValue().equals("DefaultDigits")) {
			e.removeAttribute("DigitsType");
		}
		List<Element> children = e.getChildren();
		for (Element child : children) {
			cleanAttributes(child);
		}
	}

	private String[] splitCell(String string) {
		int index = string.indexOf("<Cell");
		if (index == -1 && string.indexOf("<Br") == -1) {
			return new String[] { string };
		}
		List<String> v = new ArrayList<>();
		while (index != -1) {
			v.add(string.substring(0, index) + "</ph>");
			string = "<ph>" + string.substring(index);
			index = string.indexOf("<Cell", 5);
		}
		v.add(string);
		// split at <Br
		List<String> v2 = new ArrayList<>();
		for (int i = 0; i < v.size(); i++) {
			String s = v.get(i);
			int index2 = s.indexOf("<Br");
			if (index2 == -1) {
				v2.add(s);
			} else {
				while (index2 != -1) {
					String str = s.substring(0, index2) + "</ph>";
					v2.add(str);
					s = "<ph>" + s.substring(index2);
					index2 = s.indexOf("<Br", 7);
				}
				v2.add(s);
			}
		}
		return v2.toArray(new String[v2.size()]);
	}

	private String fixTags(String string) {
		StringBuilder result = new StringBuilder();
		int id1 = 1;
		int start = string.indexOf("<ph>");
		while (start != -1) {
			result.append(string.substring(0, start));
			string = string.substring(start);
			int end = string.indexOf("</ph>");
			String clean = XMLUtils.cleanText(string.substring("<ph>".length(), end));
			result.append("<ph id=\"");
			result.append(id1++);
			result.append("\">");
			result.append(clean);
			result.append("</ph>");
			if (clean.indexOf('\u2028') != -1) {
				result.append("\n");
			}
			string = string.substring(end + "</ph>".length());
			start = string.indexOf("<ph>");
		}
		result.append(string);
		return result.toString();
	}

	private String recurseElement(Element e) {
		String result = "";
		if (e.getName().equals("Content")) {
			result = result + "<Content></ph>" + XMLUtils.cleanText(e.getText()) + "<ph></Content>";
		} else {
			result = result + "<" + e.getName();
			List<Attribute> atts = e.getAttributes();
			for (Attribute a : atts) {
				result = result + " " + a.getName() + "=\""
						+ XMLUtils.cleanText(a.getValue()).replace("\"", "&quote;") + "\"";
			}
			if (e.getContent().isEmpty()) {
				result = result + " />";
			} else {
				result = result + ">";
				List<XMLNode> content = e.getContent();
				for (XMLNode n : content) {
					if (n.getNodeType() == XMLNode.TEXT_NODE) {
						result = result + n.toString();
					}
					if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
						result = result + recurseElement((Element) n);
					}
				}
				result = result + "</" + e.getName() + ">";
			}
		}
		return result;
	}

	private boolean hasText(Element e) {
		if (e.getName().equals("Content") && !e.getText().isEmpty()) {
			return true;
		}
		List<Element> children = e.getChildren();
		for (Element child : children) {
			if (hasText(child)) {
				return true;
			}
		}
		return false;
	}

	private void writeHeader(String format) throws IOException {
		String tgtLang = "";
		if (targetLanguage != null) {
			tgtLang = "\" target-language=\"" + targetLanguage;
		}
		writeString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		writeString("<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" "
				+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
				+ "xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");

		writeString("<file original=\"" + XMLUtils.cleanText(inputFile) + "\" source-language=\"" + sourceLanguage
				+ tgtLang + "\" datatype=\"" + format + "\">\n");
		writeString("<header>\n");
		writeString("   <skl>\n");
		writeString("      <external-file href=\"" + XMLUtils.cleanText(skeletonFile) + "\"/>\n");
		writeString("   </skl>\n");
		writeString("</header>\n");
		writeString("<?encoding " + srcEncoding + "?>\n");
		writeString("<body>\n");
	}

	private void writeString(String string) throws IOException {
		output.write(string.getBytes(StandardCharsets.UTF_8));
	}
}
