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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Utils;
import com.maxprograms.languages.LanguageUtils;
import com.maxprograms.xliff2.FromXliff2;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLUtils;

public class RepetitionAnalysis {

	private static Logger logger = System.getLogger(RepetitionAnalysis.class.getName());

	private String srcLang;
	private Map<String, List<Element>> segments;
	private List<Element> sources;
	private List<String> files;

	public static void main(String[] args) {

		String[] fixedArgs = Utils.fixPath(args);

		String file = "";
		String catalog = "";
		for (int i = 0; i < fixedArgs.length; i++) {
			String arg = fixedArgs[i];
			if (arg.equals("-help")) {
				help();
				return;
			}
			if (arg.equals("-xliff") && (i + 1) < fixedArgs.length) {
				file = fixedArgs[i + 1];
			}
			if (arg.equals("-catalog") && (i + 1) < fixedArgs.length) {
				catalog = fixedArgs[i + 1];
			}
			if (arg.equals("-lang") && (i + 1) < fixedArgs.length) {
				Locale.setDefault(Locale.forLanguageTag(fixedArgs[i + 1]));
			}
		}
		if (fixedArgs.length < 2) {
			help();
			return;
		}
		if (file.isEmpty()) {
			logger.log(Level.ERROR, Messages.getString("RepetitionAnalysis.1"));
			return;
		}
		if (catalog.isEmpty()) {
			String home = System.getenv("OpenXLIFF_HOME");
			if (home == null) {
				home = System.getProperty("user.dir");
			}
			File catalogFolder = new File(new File(home), "catalog");
			catalog = new File(catalogFolder, "catalog.xml").getAbsolutePath();
		}
		File catalogFile = new File(catalog);
		if (!catalogFile.exists()) {
			logger.log(Level.ERROR, Messages.getString("RepetitionAnalysis.2"));
			return;
		}
		if (!catalogFile.isAbsolute()) {
			catalog = catalogFile.getAbsoluteFile().getAbsolutePath();
		}
		File xliffFile = new File(file);
		if (!xliffFile.isAbsolute()) {
			file = xliffFile.getAbsoluteFile().getAbsolutePath();
		}
		try {
			RepetitionAnalysis instance = new RepetitionAnalysis();
			instance.analyse(file, catalog);
		} catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
			logger.log(Level.ERROR, Messages.getString("RepetitionAnalysis.3"), e);
		}
	}

	private static void help() {
		MessageFormat mf = new MessageFormat(Messages.getString("RepetitionAnalysis.help"));
		boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
		String help = mf.format(new String[] { isWindows ? "analysis.cmd" : "analysis.sh" });
		System.out.println(help);
	}

	private void createList(Element root) {
		List<Element> elements = root.getChildren();
		Iterator<Element> it = elements.iterator();
		while (it.hasNext()) {
			Element el = it.next();
			if (el.getName().equals("file")) {
				String originalFile = el.getAttributeValue("original");
				srcLang = el.getAttributeValue("source-language");
				if (!segments.containsKey(originalFile)) {
					sources = new ArrayList<>();
					segments.put(originalFile, sources);
					files.add(originalFile);
				} else {
					sources = segments.get(originalFile);
				}
			}
			if (el.getName().equals("trans-unit")) {
				Element src = el.getChild("source");
				if (src.getContent().isEmpty()) {
					continue;
				}
				String approved = el.getAttributeValue("approved", "no");
				src = removeTags(src);
				Element target = el.getChild("target");
				String translated = "no";
				if (target != null && !target.getText().isEmpty()) {
					translated = "yes";
				}
				String type = "";
				int[] count = getCount(el);
				if (approved.equalsIgnoreCase("yes") && target != null
						&& target.getAttributeValue("state-qualifier").equals("leveraged-inherited")) {
					type = "ice";
				} else {
					type = getMatch(el);
				}
				if (el.getAttributeValue("translate", "yes").equals("yes")) {
					src.setAttribute("words", "" + count[0]);
					src.setAttribute("untranslatable", "" + count[1]);
				} else {
					src.setAttribute("words", "0");
					src.setAttribute("untranslatable", "" + (count[0] + count[1]));
				}
				src.setAttribute("type", type);
				src.setAttribute("approved", approved);
				src.setAttribute("translated", translated);
				src.setAttribute("translatableChars", "" + count[2]);
				src.setAttribute("protectedChars", "" + count[3]);
				src.setAttribute("spaces", "" + count[4]);
				src.setAttribute("protectedSpaces", "" + count[5]);
				sources.add(src);
			} else {
				createList(el);
			}
		}
	}

	public void analyse(String fileName, String catalog)
			throws SAXException, IOException, ParserConfigurationException, URISyntaxException {

		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));
		Iterator<Element> it = null;
		String shortName = XMLUtils.cleanText(new File(fileName).getName());
		Document doc = builder.build(fileName);
		Element root = doc.getRootElement();

		SvgStats svgStats = new SvgStats();
		if (root.getAttributeValue("version").startsWith("2.")) {
			File temp = File.createTempFile("temp", ".xlf");
			temp.deleteOnExit();
			FromXliff2.run(fileName, temp.getAbsolutePath(), catalog);
			doc = builder.build(temp);
			root = doc.getRootElement();
		}
		svgStats.analyse(fileName, catalog);
		segments = new HashMap<>();
		files = new ArrayList<>();

		createList(root);

		Collections.sort(files, (o1, o2) -> o1.compareToIgnoreCase(o2));

		String title = Messages.getString("RepetitionAnalysis.4");

		// List of files ready, process individually
		MessageFormat mf = new MessageFormat(Messages.getString("RepetitionAnalysis.5"));

		//
		// all segments are in, now check repeated
		//
		for (int i = 0; i < files.size(); i++) {
			List<Element> currFile = segments.get(files.get(i));
			for (int j = 0; j < currFile.size(); j++) {
				Element src = currFile.get(j);
				// check segments without matches from TM only
				if (src.getAttributeValue("type").equals("new")) {
					String currText = src.toString();
					// check in current file
					// start with next segment
					for (int k = j + 1; k < currFile.size(); k++) {
						Element other = currFile.get(k);
						if (currText.equals(other.toString())) {
							other.setAttribute("type", "rep-int");
						}
					}
					// check in other files
					// start with following one
					for (int k = i + 1; k < files.size(); k++) {
						List<Element> otherFile = segments.get(files.get(k));
						for (int m = 0; m < otherFile.size(); m++) {
							Element other = otherFile.get(m);
							if (currText.equals(other.toString())) {
								other.setAttribute("type", "rep-ext");
							}
						}
					}
				}
			}
		}

		//
		// publish results
		//
		int newSegsTotal = 0;
		int iceSegsTotal = 0;
		int untrSegsTotal = 0;
		int matchesTotal = 0;
		int repeatedTotal = 0;
		int repIntTotal = 0;
		int repExtTotal = 0;
		int matches95Total = 0;
		int matches85Total = 0;
		int matches75Total = 0;
		int matches50Total = 0;

		String css = "";
		try (InputStream is = RepetitionAnalysis.class.getResourceAsStream("styles.css")) {
			StringBuffer sb = new StringBuffer();
			try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
				try (BufferedReader br = new BufferedReader(reader)) {
					String line;
					while ((line = br.readLine()) != null) {
						if (!sb.isEmpty()) {
							sb.append("\n");
						}
						sb.append(line);
					}
				}
			}
			css = sb.toString();
		}

		try (FileOutputStream out = new FileOutputStream(fileName + ".log.html")) {
			writeString(out, "<!DOCTYPE html>\n");
			writeString(out, "<html>\n");
			writeString(out, "<head>\n");
			writeString(out, "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n");
			writeString(out, "  <title>" + Messages.getString("RepetitionAnalysis.6") + "</title>\n");
			writeString(out, "  <style type=\"text/css\">\n");
			writeString(out, css);
			writeString(out, "  </style>\n");
			writeString(out, "</head>\n");
			writeString(out, "<body>\n");

			//
			// Segment based analysis
			//
			mf = new MessageFormat(title);
			Object[] args = { shortName };
			writeString(out, "<h1>" + mf.format(args) + "</h1>\n");
			writeString(out, "<h2>" + Messages.getString("RepetitionAnalysis.7") + "</h2>\n");
			writeString(out, "<table class=\"wordCount\" width=\"100%\">\n");

			writeString(out,
					"<tr><th>#</th><th>" + Messages.getString("RepetitionAnalysis.8") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.9") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.10") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.11") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.12") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.13") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.14")
							+ "</th></tr>\n");
			for (int i = 0; i < files.size(); i++) {
				List<Element> content = segments.get(files.get(i));
				it = content.iterator();
				int newSegs = 0;
				int iceSegs = 0;
				int matches = 0;
				int repInt = 0;
				int repExt = 0;
				while (it.hasNext()) {
					Element e = it.next();
					String type = e.getAttributeValue("type", "new");
					if (type.equals("new")) {
						newSegs++;
					}
					if (type.equals("ice")) {
						iceSegs++;
					}
					if (type.equals("exact") || type.equals("95") || type.equals("85") || type.equals("75")
							|| type.equals("50")) {
						matches++;
					}
					if (type.equals("rep-int")) {
						repInt++;
					}
					if (type.equals("rep-ext")) {
						repExt++;
					}
				}
				writeString(out, "<tr>");
				writeString(out,
						"<td class=\"center\">" + (i + 1) + "</td>" + "<td class=\"left\">" + files.get(i)
								+ "</td><td class=\"right\">" + newSegs + "</td><td class=\"right\">" + iceSegs
								+ "</td><td class=\"right\">" + matches + "</td><td class=\"right\">" + repInt
								+ "</td><td class=\"right\">" + repExt + "</td><td class=\"right\">"
								+ (newSegs + iceSegs + matches + repInt + repExt) + "</td>");
				writeString(out, "</tr>\n");
				newSegsTotal += newSegs;
				iceSegsTotal += iceSegs;
				matchesTotal += matches;
				repIntTotal += repInt;
				repExtTotal += repExt;
			}

			writeString(out, "<tr>");
			writeString(out,
					"<td bgcolor=\"#ededed\" style=\"border-right:1px #adbfbe solid;border-bottom:1px #adbfbe solid;\">&nbsp;</td><td align=\"center\" bgcolor=\"#ededed\"><b>"
							+ Messages.getString("RepetitionAnalysis.14")
							+ "</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>" + newSegsTotal
							+ "</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>" + iceSegsTotal
							+ "</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>" + matchesTotal
							+ "</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>" + repIntTotal
							+ "</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>" + repExtTotal
							+ "</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>"
							+ (newSegsTotal + matchesTotal + repIntTotal + repExtTotal) + "</b></td>");
			writeString(out, "</tr>\n");
			writeString(out, "</table>\n");

			//
			// Words based analysis
			//

			iceSegsTotal = 0;
			matchesTotal = 0;
			repeatedTotal = 0;
			newSegsTotal = 0;
			writeString(out, "<h2>" + Messages.getString("RepetitionAnalysis.16") + "</h2>\n");
			writeString(out, "<table class=\"wordCount\" width=\"100%\">\n");
			writeString(out,
					"<tr><th>#</th><th>" + Messages.getString("RepetitionAnalysis.8") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.9") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.19") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.20") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.21") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.22") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.23") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.24") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.25") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.26")
							+ "</th><th>" + Messages.getString("RepetitionAnalysis.14") + "</th></tr>\n");
			for (int i = 0; i < files.size(); i++) {
				List<Element> content = segments.get(files.get(i));
				it = content.iterator();
				int newSegs = 0;
				int iceSegs = 0;
				int untrSegs = 0;
				int matches = 0;
				int repeated = 0;
				int matches95 = 0;
				int matches85 = 0;
				int matches75 = 0;
				int matches50 = 0;

				while (it.hasNext()) {
					Element e = it.next();
					String type = e.getAttributeValue("type", "new");
					if (type.equals("new")) {
						newSegs += Integer.parseInt(e.getAttributeValue("words"));
					}
					if (type.equals("ice")) {
						iceSegs += Integer.parseInt(e.getAttributeValue("words"));
					}
					if (type.equals("exact")) {
						matches += Integer.parseInt(e.getAttributeValue("words"));
					}
					if (type.equals("95")) {
						matches95 += Integer.parseInt(e.getAttributeValue("words"));
					}
					if (type.equals("85")) {
						matches85 += Integer.parseInt(e.getAttributeValue("words"));
					}
					if (type.equals("75")) {
						matches75 += Integer.parseInt(e.getAttributeValue("words"));
					}
					if (type.equals("50")) {
						matches50 += Integer.parseInt(e.getAttributeValue("words"));
					}
					if (type.startsWith("rep")) {
						repeated += Integer.parseInt(e.getAttributeValue("words"));
					}
					untrSegs += Integer.parseInt(e.getAttributeValue("untranslatable"));
				}
				writeString(out, "<tr>");
				writeString(out, "<td class=\"center\">" + (i + 1) + "</td>" + "<td class=\"left\">" + files.get(i)
						+ "</td><td class=\"right\">" + newSegs + "</td><td class=\"right\">" + iceSegs
						+ "</td><td class=\"right\">" + untrSegs + "</td><td class=\"right\">" + matches
						+ "</td><td class=\"right\">" + repeated + "</td><td class=\"right\">" + matches95
						+ "</td><td class=\"right\">" + matches85 + "</td><td class=\"right\">" + matches75
						+ "</td><td class=\"right\">" + matches50 + "</td><td class=\"right\">" + (newSegs + iceSegs
								+ matches + untrSegs + repeated + matches95 + matches85 + matches75 + matches50)
						+ "</td>");
				writeString(out, "</tr>\n");
				newSegsTotal += newSegs;
				iceSegsTotal += iceSegs;
				untrSegsTotal += untrSegs;
				matchesTotal += matches;
				repeatedTotal += repeated;
				matches95Total += matches95;
				matches85Total += matches85;
				matches75Total += matches75;
				matches50Total += matches50;

				newSegs = 0;
				iceSegs = 0;
				matches = 0;
				matches95 = 0;
				matches85 = 0;
				matches75 = 0;
				matches50 = 0;
				repeated = 0;
			}

			writeString(out, "<tr>");
			writeString(out,
					"<td bgcolor=\"#ededed\" style=\"border-right:1px #adbfbe solid;border-bottom:1px #adbfbe solid;\">&nbsp;</td><td align=\"center\" bgcolor=\"#ededed\"><b>"
							+ Messages.getString("RepetitionAnalysis.14"));
			writeString(out,
					"</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>" + newSegsTotal
							+ "</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>" + iceSegsTotal
							+ "</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>" + untrSegsTotal
							+ "</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>" + matchesTotal
							+ "</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>" + repeatedTotal
							+ "</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>" + matches95Total
							+ "</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>" + matches85Total
							+ "</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>" + matches75Total
							+ "</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>" + matches50Total
							+ "</b></td><td align=\"right\" bgcolor=\"#ededed\"><b>"
							+ (newSegsTotal + iceSegsTotal + untrSegsTotal + matchesTotal + repeatedTotal
									+ matches95Total + matches85Total + matches75Total + matches50Total)
							+ "</b></td>");
			writeString(out, "</tr>\n");
			writeString(out, "</table>\n");

			writeString(out, "<h2>" + Messages.getString("RepetitionAnalysis.29") + "</h2>\n");

			//
			// Translation status by segments
			//

			writeString(out, "<h3>" + Messages.getString("RepetitionAnalysis.30") + "</h3>\n");

			writeString(out, "<table class=\"wordCount\" width=\"100%\">\n");
			writeString(out,
					"<tr><th>#</th><th>" + Messages.getString("RepetitionAnalysis.8") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.32") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.33")
							+ "</th><th>" + Messages.getString("RepetitionAnalysis.34") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.35") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.14")
							+ "</th></tr>\n");

			int allnumapproved = 0;
			int allnumtranslated = 0;
			int allnotapproved = 0;
			int allnottranslated = 0;

			for (int i = 0; i < files.size(); i++) {
				List<Element> content = segments.get(files.get(i));
				it = content.iterator();
				int numapproved = 0;
				int numtranslated = 0;
				int nottranslated = 0;
				int notapproved = 0;

				while (it.hasNext()) {
					Element e = it.next();
					String approved = e.getAttributeValue("approved");
					String translated = e.getAttributeValue("translated");
					if (approved.equals("yes")) {
						numapproved++;
					} else {
						notapproved++;
					}
					if (translated.equals("yes")) {
						numtranslated++;
					} else {
						nottranslated++;
					}
				}
				allnumapproved = allnumapproved + numapproved;
				allnumtranslated = allnumtranslated + numtranslated;
				allnotapproved = allnotapproved + notapproved;
				allnottranslated = allnottranslated + nottranslated;
				writeString(out, "<tr><td class=\"center\">" + (i + 1) + "</td>" + "<td class=\"left\">" + files.get(i)
						+ "</td><td class=\"right\">" + nottranslated + "</td><td class=\"right\">" + numtranslated
						+ "</td><td class=\"right\">" + numapproved + "</td><td class=\"right\">" + notapproved
						+ "</td><td class=\"right\">" + (numapproved + notapproved) + "</td></tr>\n");
			}

			writeString(out,
					"<tr><td  bgcolor=\"#ededed\" style=\"border-right:1px #adbfbe solid;border-bottom:1px #adbfbe solid;\">&nbsp;</td><td align=\"center\" bgcolor=\"#ededed\"><b>"
							+ Messages.getString("RepetitionAnalysis.14") + "</b>");
			writeString(out,
					"</td><td bgcolor=\"#ededed\" align=\"right\"><b>" + allnottranslated
							+ "</b></td><td bgcolor=\"#ededed\" align=\"right\"><b>" + allnumtranslated
							+ "</b></td><td bgcolor=\"#ededed\" align=\"right\"><b>" + allnumapproved
							+ "</b></td><td bgcolor=\"#ededed\" align=\"right\"><b>" + allnotapproved
							+ "</b></td><td bgcolor=\"#ededed\" align=\"right\"><b>" + (allnumapproved + allnotapproved)
							+ "</b></td></tr>\n");
			writeString(out, "</table>\n");

			Element matchesSvg = svgStats.generateMatchesSvg();
			Element translatedSvg = svgStats.generateTranslatedSvg();
			Element approvedSvg = svgStats.generateApprovedSvg();

			writeString(out, "<div style=\"padding-left:50px;\">\n");
			writeString(out, "<h3>" + Messages.getString("RepetitionAnalysis.38") + "</h3>\n");
			writeString(out, translatedSvg.toString());
			writeString(out, "\n<br>\n");

			writeString(out, "<h3>" + Messages.getString("RepetitionAnalysis.39") + "</h3>\n");
			writeString(out, approvedSvg.toString());
			writeString(out, "\n<br>\n");

			writeString(out, "<h3>" + Messages.getString("RepetitionAnalysis.40") + "</h3>\n");
			writeString(out, matchesSvg.toString());
			writeString(out, "\n<br>\n");
			writeString(out, "</div>\n");

			//
			// Translation status by words
			//

			writeString(out, "<h3>" + Messages.getString("RepetitionAnalysis.41") + "</h3>\n");

			writeString(out, "<table class=\"wordCount\" width=\"100%\">\n");
			writeString(out,
					"<tr><th>#</th><th>" + Messages.getString("RepetitionAnalysis.8") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.32") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.33")
							+ "</th><th>" + Messages.getString("RepetitionAnalysis.20") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.34") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.35")
							+ "</th><th>" + Messages.getString("RepetitionAnalysis.14") + "</th></tr>\n");

			allnumapproved = 0;
			allnumtranslated = 0;
			int alluntranslatable = 0;
			allnotapproved = 0;
			allnottranslated = 0;

			for (int i = 0; i < files.size(); i++) {
				List<Element> content = segments.get(files.get(i));
				it = content.iterator();
				int numapproved = 0;
				int numtranslated = 0;
				int untranslatable = 0;
				int notapproved = 0;
				int nottranslated = 0;

				while (it.hasNext()) {
					Element e = it.next();
					String approved = e.getAttributeValue("approved");
					String translated = e.getAttributeValue("translated");
					int words = Integer.parseInt(e.getAttributeValue("words"));
					if (approved.equals("yes")) {
						numapproved += words;
					} else {
						notapproved += words;
					}
					if (translated.equals("yes")) {
						numtranslated += words;
					} else {
						nottranslated += words;
					}
					untranslatable += Integer.parseInt(e.getAttributeValue("untranslatable"));
				}
				allnumapproved = allnumapproved + numapproved;
				allnumtranslated = allnumtranslated + numtranslated;
				allnotapproved = allnotapproved + notapproved;
				allnottranslated = allnottranslated + nottranslated;
				alluntranslatable = alluntranslatable + untranslatable;

				writeString(out, "<tr><td class=\"center\">" + (i + 1) + "</td>" + "<td class=\"left\">" + files.get(i)
						+ "</td><td class=\"right\">" + nottranslated + "</td><td class=\"right\">" + numtranslated
						+ "</td><td class=\"right\">" + untranslatable + "</td><td class=\"right\">" + numapproved
						+ "</td><td class=\"right\">" + notapproved + "</td><td class=\"right\">"
						+ (notapproved + numapproved + untranslatable) + "</td></tr>\n");
			}

			writeString(out,
					"<tr><td bgcolor=\"#ededed\" style=\"border-right:1px #adbfbe solid;border-bottom:1px #adbfbe solid;\">&nbsp;</td><td align=\"center\" bgcolor=\"#ededed\"><b>"
							+ Messages.getString("RepetitionAnalysis.14") + "</b>");
			writeString(out,
					"</td><td bgcolor=\"#ededed\" align=\"right\"><b>" + allnottranslated
							+ "</b></td><td bgcolor=\"#ededed\" align=\"right\"><b>" + allnumtranslated
							+ "</b></td><td bgcolor=\"#ededed\" align=\"right\"><b>" + alluntranslatable
							+ "</b></td><td bgcolor=\"#ededed\" align=\"right\"><b>" + allnumapproved
							+ "</b></td><td bgcolor=\"#ededed\" align=\"right\"><b>" + allnotapproved
							+ "</b></td><td bgcolor=\"#ededed\" align=\"right\"><b>"
							+ (allnumapproved + allnotapproved + alluntranslatable) + "</b></td></tr>\n");
			writeString(out, "</table>\n");

			//
			// Translation status by characters
			//

			writeString(out, "<h3>" + Messages.getString("RepetitionAnalysis.42") + "</h3>\n");

			writeString(out, "<table class=\"wordCount\" width=\"100%\">\n");
			writeString(out,
					"<tr><th>#</th><th>" + Messages.getString("RepetitionAnalysis.8") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.32") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.33") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.20") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.34") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.35") + "</th><th>"
							+ Messages.getString("RepetitionAnalysis.14") + "</th></tr>\n");

			allnumapproved = 0;
			allnumtranslated = 0;
			alluntranslatable = 0;
			allnotapproved = 0;
			allnottranslated = 0;

			for (int i = 0; i < files.size(); i++) {
				List<Element> content = segments.get(files.get(i));
				it = content.iterator();
				int numapproved = 0;
				int numtranslated = 0;
				int untranslatable = 0;
				int notapproved = 0;
				int nottranslated = 0;

				while (it.hasNext()) {
					Element e = it.next();
					String approved = e.getAttributeValue("approved");
					String translated = e.getAttributeValue("translated");
					int chars = Integer.parseInt(e.getAttributeValue("translatableChars"))
							- Integer.parseInt(e.getAttributeValue("spaces"));
					if (approved.equals("yes")) {
						numapproved += chars;
					} else {
						notapproved += chars;
					}
					if (translated.equals("yes")) {
						numtranslated += chars;
					} else {
						nottranslated += chars;
					}
					untranslatable += (Integer.parseInt(e.getAttributeValue("protectedChars"))
							- Integer.parseInt(e.getAttributeValue("protectedSpaces")));
				}
				allnumapproved = allnumapproved + numapproved;
				allnumtranslated = allnumtranslated + numtranslated;
				allnotapproved = allnotapproved + notapproved;
				allnottranslated = allnottranslated + nottranslated;
				alluntranslatable = alluntranslatable + untranslatable;

				writeString(out, "<tr><td class=\"center\">" + (i + 1) + "</td>" + "<td class=\"left\">" + files.get(i)
						+ "</td><td class=\"right\">" + nottranslated + "</td><td class=\"right\">" + numtranslated
						+ "</td><td class=\"right\">" + untranslatable + "</td><td class=\"right\">" + numapproved
						+ "</td><td class=\"right\">" + notapproved + "</td><td class=\"right\">"
						+ (notapproved + numapproved + untranslatable) + "</td></tr>\n");
			}

			writeString(out,
					"<tr><td bgcolor=\"#ededed\" style=\"border-right:1px #adbfbe solid;border-bottom:1px #adbfbe solid;\">&nbsp;</td><td align=\"center\" bgcolor=\"#ededed\"><b>"
							+ Messages.getString("RepetitionAnalysis.14") + "</b>");
			writeString(out,
					"</td><td bgcolor=\"#ededed\" align=\"right\"><b>" + allnottranslated
							+ "</b></td><td bgcolor=\"#ededed\" align=\"right\"><b>" + allnumtranslated
							+ "</b></td><td bgcolor=\"#ededed\" align=\"right\"><b>" + alluntranslatable
							+ "</b></td><td bgcolor=\"#ededed\" align=\"right\"><b>" + allnumapproved
							+ "</b></td><td bgcolor=\"#ededed\" align=\"right\"><b>" + allnotapproved
							+ "</b></td><td bgcolor=\"#ededed\" align=\"right\"><b>"
							+ (allnumapproved + allnotapproved + alluntranslatable) + "</b></td></tr>\n");
			writeString(out, "</table>\n");

			writeString(out, "<p><b><u>" + Messages.getString("RepetitionAnalysis.50") + "</u></b><br />");
			writeString(out, "<b>" + Messages.getString("RepetitionAnalysis.51") + "</b> "
					+ Messages.getString("RepetitionAnalysis.52") + "<br />");
			writeString(out, "<b>" + Messages.getString("RepetitionAnalysis.53") + "</b> "
					+ Messages.getString("RepetitionAnalysis.54") + "</p>");
			writeString(out, "</body>\n");
			writeString(out, "</html>\n");
		}
	}

	private static String getMatch(Element e) {
		List<Element> altTrans = e.getChildren("alt-trans");
		int max = 0;
		String type = "";
		Iterator<Element> i = altTrans.iterator();
		while (i.hasNext()) {
			Element trans = i.next();
			String quality = trans.getAttributeValue("match-quality");
			try {
				Integer value = Integer.parseInt(quality);
				if (value > max) {
					max = value;
				}
			} catch (Exception _) {
				// do nothing here
			}
		}
		if (max == 100) {
			type = "exact";
		} else if (max >= 95 && max < 100) {
			type = "95";
		} else if (max >= 85 && max < 95) {
			type = "85";
		} else if (max >= 75 && max < 85) {
			type = "75";
		} else if (max >= 50 && max < 75) {
			type = "50";
		} else {
			type = "new";
		}
		return type;
	}

	public static String pureText(Element src, String space, String translate) {
		if (src == null) {
			return "";
		}
		StringBuilder text = new StringBuilder();
		List<XMLNode> content = src.getContent();
		Iterator<XMLNode> it = content.iterator();
		while (it.hasNext()) {
			XMLNode node = it.next();
			if (node.getNodeType() == XMLNode.TEXT_NODE
					&& src.getAttributeValue("mtype", "translatable").equals(translate)) {
				if (space.equals("default")) {
					if (text.length() > 0) {
						text.append(' ');
					}
					text.append(normalise(node.toString(), true));
				} else {
					text.append(node.toString());
				}
			} else if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element el = (Element) node;
				String type = el.getName();
				// discard all inline elements
				// except <g>, <mrk>, <hi> and <sub>
				if (!type.equals("bpt") && !type.equals("ept") && !type.equals("it") && !type.equals("ph")
						&& !type.equals("ut") && !type.equals("x")) {
					text.append(' ');
					text.append(pureText(el, space, translate));
				}
			}
		}
		return text.toString();
	}

	private int[] getCount(Element e) {
		if (e == null) {
			return new int[] { 0, 0 };
		}
		Element source = e.getChild("source");
		String translatableText = pureText(source, e.getAttributeValue("xml:space", "default"), "translatable");
		int res1 = wordCount(translatableText, srcLang);
		String protectedText = pureText(source, e.getAttributeValue("xml:space", "default"), "protected");
		int res2 = wordCount(protectedText, srcLang);
		return new int[] { res1, res2, translatableText.length(), protectedText.length(), countSpaces(translatableText),
				countSpaces(protectedText) };
	}

	private int countSpaces(String text) {
		int count = 0;
		for (int i = 0; i < text.length(); i++) {
			if (Character.isWhitespace(text.charAt(i))) {
				count++;
			}
		}
		return count;
	}

	private static void writeString(FileOutputStream out, String text) throws IOException {
		out.write(text.getBytes(StandardCharsets.UTF_8));
	}

	public static int wordCount(String str, String lang) {
		if (LanguageUtils.isCJK(lang)) {
			return chineseCount(str);
		}
		return europeanCount(str);
	}

	private static int chineseCount(String str) {
		// basic idea is that we need to remove unicode that higher than 255
		// and then we count by europeanCount
		// after that remove 0-255 unicode value and just count character
		StringBuffer european = new StringBuffer();
		int chineseCount = 0;
		char[] chars = str.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			char chr = chars[i];
			if (chr <= 255 || chr == '\u00A0' || chr == '\u3001' || chr == '\u3002' || chr == '\uff1a'
					|| chr == '\uff01' || chr == '\uff1f' || chr == '\u4ecb') {
				european.append(chr);
			} else {
				chineseCount++;
			}
		}
		int euroCount = europeanCount(european.toString());
		return euroCount + chineseCount;
	}

	private static int europeanCount(String source) {
		int wordnum = 0;
		StringTokenizer tok = new StringTokenizer(source, " \t\r\n()?\u00A0\u3001\u3002\uff1a\uff01\uff1f\u4ecb");
		String charsInNumber = ".,-/<>";
		while (tok.hasMoreTokens()) {
			String str = tok.nextToken();
			if (!containsSeparator(str, charsInNumber)) {
				wordnum++;
			} else {
				if (!isFormatNumber(str)) {
					// concatenated words
					StringTokenizer tok2 = new StringTokenizer(str, charsInNumber);
					while (tok2.hasMoreTokens()) {
						tok2.nextToken();
						wordnum++;
					}
				} else {
					// single number
					wordnum++;
				}
			}
		}
		return wordnum;
	}

	private static boolean containsSeparator(String str, String seps) {
		for (int i = 0; i < seps.length(); i++) {
			if (str.indexOf(seps.charAt(i)) != -1) {
				return true;
			}
		}
		return false;
	}

	public static boolean isFormatNumber(String str) {
		char[] chars = str.toCharArray();
		boolean hasDigit = false;
		for (int i = 0; i < chars.length; i++) {
			if (Character.isDigit(chars[i])) {
				hasDigit = true;
			}
		}
		return hasDigit;
	}

	private static Element removeTags(Element src) {
		src.removeChild("ph");
		src.removeChild("bpt");
		src.removeChild("ept");
		return src;
	}

	public int[] analyseWords(Document doc) {
		Iterator<Element> it = null;
		Element rootClone = new Element();
		rootClone.clone(doc.getRootElement());
		srcLang = rootClone.getChild("file").getAttributeValue("source-language");

		List<Element> segs = new ArrayList<>();
		createList(rootClone, segs);

		it = segs.iterator();
		List<Element> srcs = new ArrayList<>();
		while (it.hasNext()) {
			Element e = it.next();
			String approved = e.getAttributeValue("approved", "no");
			Element src = e.getChild("source");
			src = removeTags(src);

			Element target = e.getChild("target");
			String translated = "no";
			if (target != null && !target.getText().isEmpty()) {
				translated = "yes";
			}

			String type = "";
			int[] count = getCount(e);
			if (approved.equalsIgnoreCase("yes") && target != null
					&& target.getAttributeValue("state-qualifier").equals("leveraged-inherited")) {
				type = "ice";
			} else {
				type = getMatch(e);
			}
			src.setAttribute("words", "" + count[0]);
			src.setAttribute("untranslatable", "" + count[1]);
			src.setAttribute("type", type);
			src.setAttribute("approved", approved);
			src.setAttribute("translated", translated);
			srcs.add(src);
		}
		//
		// all segments are in, now check repeated
		//

		for (int j = 0; j < srcs.size(); j++) {
			Element src = srcs.get(j);
			// check segments without matches from TM only
			if (src.getAttributeValue("type").equals("new")) {
				String currText = src.toString();
				// check in current file
				// start with next segment
				for (int k = j + 1; k < srcs.size(); k++) {
					Element other = srcs.get(k);
					if (currText.equals(other.toString())) {
						other.setAttribute("type", "rep-int");
					}
				}
			}
		}

		//
		// Words based analysis
		//

		it = srcs.iterator();
		int iceSegs = 0;
		int newSegs = 0;
		int matches = 0;
		int repeated = 0;
		int matches95 = 0;
		int matches85 = 0;
		int matches75 = 0;
		int matches50 = 0;

		while (it.hasNext()) {
			Element e = it.next();
			String type = e.getAttributeValue("type", "new");
			if (type.equals("ice")) {
				iceSegs += Integer.parseInt(e.getAttributeValue("words"));
			}
			if (type.equals("new")) {
				newSegs += Integer.parseInt(e.getAttributeValue("words"));
			}
			if (type.equals("exact")) {
				matches += Integer.parseInt(e.getAttributeValue("words"));
			}
			if (type.equals("95")) {
				matches95 += Integer.parseInt(e.getAttributeValue("words"));
			}
			if (type.equals("85")) {
				matches85 += Integer.parseInt(e.getAttributeValue("words"));
			}
			if (type.equals("75")) {
				matches75 += Integer.parseInt(e.getAttributeValue("words"));
			}
			if (type.equals("50")) {
				matches50 += Integer.parseInt(e.getAttributeValue("words"));
			}
			if (type.startsWith("rep")) {
				repeated += Integer.parseInt(e.getAttributeValue("words"));
			}
		}

		int numapproved = 0;
		int numtranslated = 0;
		int allwords = 0;

		it = srcs.iterator();
		while (it.hasNext()) {
			Element e = it.next();
			String approved = e.getAttributeValue("approved");
			String translated = e.getAttributeValue("translated");
			if (approved.equals("yes")) {
				numapproved += Integer.parseInt(e.getAttributeValue("words"));
			}
			if (translated.equals("yes")) {
				numtranslated += Integer.parseInt(e.getAttributeValue("words"));
			}
			allwords += Integer.parseInt(e.getAttributeValue("words"));
		}

		int[] result = new int[11];

		result[0] = newSegs;
		result[1] = matches;
		result[2] = repeated;
		result[3] = matches95;
		result[4] = matches85;
		result[5] = matches75;
		result[6] = matches50;
		result[7] = allwords;
		result[8] = numapproved;
		result[9] = numtranslated;
		result[10] = iceSegs;

		return result;
	}

	private static void createList(Element root, List<Element> segs) {
		List<Element> elements = root.getChildren();
		Iterator<Element> it = elements.iterator();
		while (it.hasNext()) {
			Element el = it.next();
			if (el.getName().equals("trans-unit")) {
				if (el.getAttributeValue("translate", "yes").equalsIgnoreCase("yes")) {
					segs.add(el);
				}
			} else {
				createList(el, segs);
			}
		}

	}

	public static void generateStatusHistoryView(Status[] status, String filename) throws IOException {
		try (FileOutputStream out = new FileOutputStream(filename + ".status.html")) {
			writeString(out, "<!DOCTYPE html>\n");
			writeString(out, "<html>\n");
			writeString(out, "<head>\n");
			writeString(out, "<title>" + Messages.getString("RepetitionAnalysis.55") + "</title>\n");
			writeString(out, "  <style>\n");
			writeString(out, "   h2 {\n");
			writeString(out, "       font-family: Arial,Helvetica,sans-serif;\n");
			writeString(out, "   }\n");
			writeString(out, "   table {\n");
			writeString(out, "       border-collapse: collapse;\n");
			writeString(out, "   }\n");
			writeString(out, "  </style>\n");
			writeString(out, "</head>\n");
			writeString(out, "<body>\n");
			writeString(out, "<h2>" + Messages.getString("RepetitionAnalysis.55") + "</h2>\n");
			MessageFormat mf = new MessageFormat(Messages.getString("RepetitionAnalysis.56"));
			Object[] args = { filename };
			writeString(out, "<h2>" + mf.format(args) + "</h2>\n");
			writeString(out, "<table width=\"100%\">\n");
			writeString(out,
					"<tr><td bgcolor=\"#ededed\">" + Messages.getString("RepetitionAnalysis.57")
							+ "</th><td bgcolor=\"#ededed\">" + Messages.getString("RepetitionAnalysis.58")
							+ "</th><td bgcolor=\"#ededed\">" + Messages.getString("RepetitionAnalysis.9")
							+ "</th><td bgcolor=\"#ededed\">" + Messages.getString("RepetitionAnalysis.60")
							+ "</th><td bgcolor=\"#ededed\">" + Messages.getString("RepetitionAnalysis.61")
							+ "</th><td bgcolor=\"#ededed\">" + Messages.getString("RepetitionAnalysis.62")
							+ "</th><td bgcolor=\"#ededed\">" + Messages.getString("RepetitionAnalysis.23")
							+ "</th><td bgcolor=\"#ededed\">" + Messages.getString("RepetitionAnalysis.24")
							+ "</th><td bgcolor=\"#ededed\">" + Messages.getString("RepetitionAnalysis.25")
							+ "</th><td bgcolor=\"#ededed\">" + Messages.getString("RepetitionAnalysis.26")
							+ "</th><td bgcolor=\"#ededed\">" + Messages.getString("RepetitionAnalysis.14")
							+ "</th><td bgcolor=\"#ededed\">" + Messages.getString("RepetitionAnalysis.34")
							+ "</th><td bgcolor=\"#ededed\">" + Messages.getString("RepetitionAnalysis.35")
							+ "</th><td bgcolor=\"#ededed\">"
							+ Messages.getString("RepetitionAnalysis.33") + "</th><td bgcolor=\"#ededed\">"
							+ Messages.getString("RepetitionAnalysis.32")
							+ "</th><td bgcolor=\"#ededed\">" + Messages.getString("RepetitionAnalysis.14")
							+ "</th></tr>\n");

			for (int i = 0; i < status.length; i++) {
				Status stat = status[i];
				writeString(out, "<tr>\n");
				writeString(out,
						"<td align=\"center\">" + stat.getDescription() + "</td><td align=\"center\">" + stat.getDate()
								+ "</td><td align=\"right\">" + stat.getNewWords() + "</td><td align=\"right\">"
								+ stat.getIceWords() + "</td><td align=\"right\">" + stat.getRange0Count()
								+ "</td><td align=\"right\">" + stat.getRepeated() + "</td><td align=\"right\">"
								+ stat.getRange1Count() + "</td><td align=\"right\">" + stat.getRange2Count()
								+ "</td><td align=\"right\">" + stat.getRange3Count() + "</td><td align=\"right\">"
								+ stat.getRange4Count() + "</td><td align=\"right\">" + stat.getTotalWords()
								+ "</td><td align=\"right\">" + stat.getApproved() + "</td><td align=\"right\">"
								+ (stat.getTotalWords() - stat.getApproved()) + "</td><td align=\"right\">"
								+ stat.getTranslated() + "</td><td align=\"right\">"
								+ (stat.getTotalWords() - stat.getTranslated()) + "</td><td align=\"right\">"
								+ stat.getTotalWords() + "</td>");
				writeString(out, "</tr>\n");
			}

			writeString(out, "</table>\n");

			writeString(out, "</body>\n");
			writeString(out, "</html>\n");
		}
	}

	public int getCount(Element e, String language) {
		srcLang = language;
		int[] count = getCount(e);
		return count[0] + count[1];
	}

	public static String normalise(String string, boolean trim) {
		boolean repeat = false;
		StringBuilder result = new StringBuilder();
		int length = string.length();
		for (int i = 0; i < length; i++) {
			char ch = string.charAt(i);
			if (!Character.isSpaceChar(ch)) {
				if (ch != '\n') {
					result.append(ch);
				} else {
					result.append(' ');
					repeat = true;
				}
			} else {
				result.append(' ');
				while (i < length - 1 && Character.isSpaceChar(string.charAt(i + 1))) {
					i++;
				}
			}
		}
		if (repeat) {
			return normalise(result.toString(), trim);
		}
		if (trim) {
			return result.toString().trim();
		}
		return result.toString();
	}
}