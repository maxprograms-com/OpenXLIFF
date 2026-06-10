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
package com.maxprograms.stats;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.SAXBuilder;

public class SvgStats {

	private List<SegmentStatus> segmentsList;
	private Map<String, Float> segmentMatches;
	private int groupSize;
	private int barWidth;
	private int maxBars;
	private int xLimit;
	private int offset;
	private String stroke;

	public void analyse(String file, String catalog)
			throws SAXException, IOException, ParserConfigurationException, URISyntaxException {
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));
		Document document = builder.build(file);
		Element root = document.getRootElement();
		if (!"xliff".equals(root.getLocalName())) {
			throw new IOException(Messages.getString("SvgStats.1"));
		}
		segmentsList = new ArrayList<>();
		if (root.getAttributeValue("version").startsWith("1.")) {
			parseXliff(root);
		} else {
			parseXliff2(root);
		}
		if (segmentsList.isEmpty()) {
			throw new IOException(Messages.getString("SvgStats.2"));
		}
		int listSize = segmentsList.size();
		if (listSize <= 10) {
			groupSize = 1;
			barWidth = 80;
			offset = 40;
			stroke = "stroke-width:78;";
			maxBars = 10;
			xLimit = 10;
		} else if (listSize > 10 && listSize <= 50) {
			groupSize = 1;
			barWidth = 16;
			offset = 8;
			stroke = "stroke-width:14;";
			maxBars = 50;
			xLimit = 50;
		} else if (listSize > 50 && listSize <= 100) {
			groupSize = 1;
			barWidth = 8;
			offset = 4;
			stroke = "stroke-width:6;";
			maxBars = 100;
			xLimit = 100;
		} else if (listSize > 100 && listSize <= 200) {
			groupSize = 1;
			barWidth = 4;
			offset = 1;
			stroke = "stroke-width:1;";
			maxBars = 200;
			xLimit = 200;
		} else {
			barWidth = 2;
			offset = 0;
			stroke = "stroke-width:1;";
			xLimit = (int) (Math.ceil(listSize / 100.0) * 100);
			maxBars = 400 * listSize / xLimit;
			groupSize = listSize / maxBars;
			if (groupSize < 1) {
				groupSize = 1;
			}
		}
	}

	private Element basicSvg() {
		Element svg = new Element("svg");
		svg.setAttribute("xmlns", "http://www.w3.org/2000/svg");
		svg.setAttribute("width", "840px");
		svg.setAttribute("height", "130px");

		Element rect = new Element("rect");
		rect.setAttribute("width", "840px");
		rect.setAttribute("height", "130px");
		rect.setAttribute("style", "fill:#eeeeee;");
		svg.addContent(rect);

		Element xAxe = new Element("line");
		xAxe.setAttribute("x1", "5");
		xAxe.setAttribute("y1", "110");
		xAxe.setAttribute("x2", "815");
		xAxe.setAttribute("y2", "110");
		xAxe.setAttribute("style", "stroke:rgb(0,0,0);stroke-width:2;");
		svg.addContent(xAxe);

		for (int x = 0; x <= 20; x++) {
			Element pipe = new Element("line");
			pipe.setAttribute("x1", "" + (10 + x * 40));
			pipe.setAttribute("x2", "" + (10 + x * 40));
			pipe.setAttribute("y1", "110");
			pipe.setAttribute("y2", "114");
			pipe.setAttribute("style", "stroke:rgb(0,0,0);stroke-width:1;");
			svg.addContent(pipe);
		}
		for (int x = 0; x < 10; x++) {
			Element text = new Element("text");
			text.setText((x + 1) * (xLimit / 10) + "");
			text.setAttribute("style", "font-family:sans-serif;font-size:10px;");
			text.setAttribute("x", "" + (84 + x * 80));
			text.setAttribute("y", "122");
			svg.addContent(text);
		}

		Element yAxe = new Element("line");
		yAxe.setAttribute("x1", "9");
		yAxe.setAttribute("y1", "5");
		yAxe.setAttribute("x2", "9");
		yAxe.setAttribute("y2", "115");
		yAxe.setAttribute("style", "stroke:rgb(0,0,0);stroke-width:2;");
		svg.addContent(yAxe);

		Element yTop = new Element("line");
		yTop.setAttribute("x1", "10");
		yTop.setAttribute("y1", "10");
		yTop.setAttribute("x2", "810");
		yTop.setAttribute("y2", "10");
		yTop.setAttribute("style", "stroke:grey;stroke-width:1;stroke-dasharray:5,5;");
		svg.addContent(yTop);

		return svg;
	}

	public Element generateMatchesSvg() {
		Element svg = basicSvg();

		Element text100 = new Element("text");
		text100.setText("100%");
		text100.setAttribute("style", "font-family:sans-serif;font-size:10px;");
		text100.setAttribute("x", "812");
		text100.setAttribute("y", "15");
		svg.addContent(text100);

		Element yCenter = new Element("line");
		yCenter.setAttribute("x1", "10");
		yCenter.setAttribute("y1", "60");
		yCenter.setAttribute("x2", "810");
		yCenter.setAttribute("y2", "60");
		yCenter.setAttribute("style", "stroke:grey;stroke-width:1;stroke-dasharray:5,5;");
		svg.addContent(yCenter);

		Element text50 = new Element("text");
		text50.setText("50%");
		text50.setAttribute("style", "font-family:sans-serif;font-size:10px;");
		text50.setAttribute("x", "812");
		text50.setAttribute("y", "65");
		svg.addContent(text50);

		for (int x = 0; x < maxBars; x++) {
			float y = matchAverage(x);
			if (y < 1) {
				continue;
			}
			Element line = new Element("line");
			line.setAttribute("x1", "" + (10 + offset + barWidth * x));
			line.setAttribute("y1", "" + (110 - y));
			line.setAttribute("x2", "" + (10 + offset + barWidth * x));
			line.setAttribute("y2", "109");
			line.setAttribute("style", "stroke:blue;" + stroke);
			svg.addContent(line);
		}
		Indenter.indent(svg, 2);
		return svg;
	}

	public Element generateTranslatedSvg() {
		Element svg = basicSvg();
		for (int x = 0; x < maxBars; x++) {
			float y = translatedAverage(x);
			if (y < 1) {
				continue;
			}
			Element line = new Element("line");
			line.setAttribute("x1", "" + (10 + offset + barWidth * x));
			line.setAttribute("y1", "" + (110 - y));
			line.setAttribute("x2", "" + (10 + offset + barWidth * x));
			line.setAttribute("y2", "109");
			line.setAttribute("style", "stroke:green;" + stroke);
			svg.addContent(line);
		}
		Indenter.indent(svg, 2);
		return svg;
	}

	public Element generateApprovedSvg() {
		Element svg = basicSvg();
		for (int x = 0; x < maxBars; x++) {
			float y = approvedAverage(x);
			if (y < 1) {
				continue;
			}
			Element line = new Element("line");
			line.setAttribute("x1", "" + (10 + offset + barWidth * x));
			line.setAttribute("y1", "" + (110 - y));
			line.setAttribute("x2", "" + (10 + offset + barWidth * x));
			line.setAttribute("y2", "109");
			line.setAttribute("style", "stroke:red;" + stroke);
			svg.addContent(line);
		}
		Indenter.indent(svg, 2);
		return svg;
	}

	private float translatedAverage(int x) {
		if (groupSize == 1) {
			if (x < segmentsList.size()) {
				return segmentsList.get(x).isTranslated() ? 100 : 0;
			}
			return 0;
		}
		float total = 0;
		int counted = 0;
		for (int count = 0; count < groupSize; count++) {
			if (x * groupSize + count < segmentsList.size()) {
				total += segmentsList.get(x * groupSize + count).isTranslated() ? 1 : 0;
				counted++;
			}
		}
		if (counted == 0) {
			return 0;
		}
		return total > counted / 2 ? 100 : 0;
	}

	private float approvedAverage(int x) {
		if (groupSize == 1) {
			if (x < segmentsList.size()) {
				return segmentsList.get(x).isApproved() ? 100 : 0;
			}
			return 0;
		}
		float total = 0;
		int counted = 0;
		for (int count = 0; count < groupSize; count++) {
			if (x * groupSize + count < segmentsList.size()) {
				total += segmentsList.get(x * groupSize + count).isApproved() ? 1 : 0;
				counted++;
			}
		}
		if (counted == 0) {
			return 0;
		}
		return total > counted / 2 ? 100 : 0;
	}

	private float matchAverage(int x) {
		if (groupSize == 1) {
			if (x < segmentsList.size()) {
				return segmentsList.get(x).getMatch();
			}
			return 0;
		}
		float total = 0;
		int counted = 0;
		for (int count = 0; count < groupSize; count++) {
			if (x * groupSize + count < segmentsList.size()) {
				total += segmentsList.get(x * groupSize + count).getMatch();
				counted++;
			}
		}
		if (counted == 0) {
			return 0;
		}
		return total / counted;
	}

	private void parseXliff(Element e) {
		if ("trans-unit".equals(e.getName())) {
			Element src = e.getChild("source");
			if (src.getContent().isEmpty()) {
				return;
			}
			SegmentStatus status = new SegmentStatus();
			status.setApproved(e.getAttributeValue("approved", "no").equalsIgnoreCase("yes"));
			Element target = e.getChild("target");
			if (target == null) {
				status.setTranslated(false);
			} else {
				status.setTranslated(!target.getContent().isEmpty());
			}
			List<Element> matches = e.getChildren("alt-trans");
			if (!matches.isEmpty()) {
				float max = 0;
				Iterator<Element> it = matches.iterator();
				while (it.hasNext()) {
					Element match = it.next();
					try {
						float quality = Float.parseFloat(match.getAttributeValue("match-quality", "0"));
						if (quality > max) {
							max = quality;
						}
					} catch (NumberFormatException _) {
						// ignore
					}
				}
				if (max > 100) {
					max = 100;
				}
				status.setMatch(max);
			}
			segmentsList.add(status);
		}
		List<Element> children = e.getChildren();
		Iterator<Element> it = children.iterator();
		while (it.hasNext()) {
			parseXliff(it.next());
		}
	}

	private void parseXliff2(Element e) {
		if ("unit".equals(e.getName())) {
			segmentMatches = new HashMap<>();
			Element matches = e.getChild("mtc:matches");
			if (matches != null) {
				List<Element> matchesList = matches.getChildren("mtc:match");
				Iterator<Element> it = matchesList.iterator();
				while (it.hasNext()) {
					Element match = it.next();
					String ref = match.getAttributeValue("ref");
					if (ref.startsWith("#")) {
						ref = ref.substring(1);
					}
					float quality = Float.parseFloat(match.getAttributeValue("matchQuality", "0"));
					if (!segmentMatches.containsKey(ref)) {
						segmentMatches.put(ref, quality);
					} else {
						float max = segmentMatches.get(ref);
						if (quality > max) {
							segmentMatches.put(ref, quality);
						}
					}
				}
			}
		}
		if ("segment".equals(e.getName())) {
			Element src = e.getChild("source");
			if (src.getContent().isEmpty()) {
				return;
			}
			SegmentStatus status = new SegmentStatus();
			String state = e.getAttributeValue("state", "initial");
			status.setApproved("final".equals(state));
			status.setTranslated(!"initial".equals(state));
			String id = e.getAttributeValue("id");
			if (segmentMatches.containsKey(id)) {
				status.setMatch(segmentMatches.get(id));
			}
			segmentsList.add(status);
		}
		List<Element> children = e.getChildren();
		Iterator<Element> it = children.iterator();
		while (it.hasNext()) {
			parseXliff2(it.next());
		}
	}
}
