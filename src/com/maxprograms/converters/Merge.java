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
package com.maxprograms.converters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.ditamap.Xliff2DitaMap;
import com.maxprograms.converters.html.Xliff2Html;
import com.maxprograms.converters.idml.Xliff2Idml;
import com.maxprograms.converters.javaproperties.Xliff2Properties;
import com.maxprograms.converters.javascript.Xliff2jscript;
import com.maxprograms.converters.json.Xliff2json;
import com.maxprograms.converters.mif.Xliff2Mif;
import com.maxprograms.converters.office.Xliff2Office;
import com.maxprograms.converters.php.Xliff2Php;
import com.maxprograms.converters.plaintext.Xliff2Text;
import com.maxprograms.converters.po.Xliff2Po;
import com.maxprograms.converters.qti.Xliff2Qtip;
import com.maxprograms.converters.rc.Xliff2Rc;
import com.maxprograms.converters.resx.Xliff2Resx;
import com.maxprograms.converters.sdlppx.Xliff2Sdlrpx;
import com.maxprograms.converters.sdlxliff.Xliff2Sdl;
import com.maxprograms.converters.srt.Xliff2Srt;
import com.maxprograms.converters.ts.Xliff2Ts;
import com.maxprograms.converters.txlf.Xliff2Txlf;
import com.maxprograms.converters.txml.Xliff2Txml;
import com.maxprograms.converters.wpml.Xliff2Wpml;
import com.maxprograms.converters.xliff.FromOpenXliff;
import com.maxprograms.converters.xml.Xliff2Xml;
import com.maxprograms.xliff2.FromXliff2;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLNode;

public class Merge {

	private static Logger logger = System.getLogger(Merge.class.getName());

	private static List<Element> segments;
	protected static HashSet<String> fileSet;

	private static Document doc;
	private static Element root;

	public static void main(String[] args) {
		String xliff = "";
		String target = "";
		String catalog = "";
		boolean unapproved = false;
		boolean exportTMX = false;
		boolean getTarget = false;

		String[] arguments = Utils.fixPath(args);
		for (int i = 0; i < arguments.length; i++) {
			String arg = arguments[i];
			if (arg.equals("-version")) {
				MessageFormat mf = new MessageFormat(Messages.getString("Merge.01"));
				logger.log(Level.INFO, mf.format(new String[] { Constants.VERSION, Constants.BUILD }));
				return;
			}
			if (arg.equals("-help")) {
				help();
				return;
			}
			if (arg.equals("-xliff") && (i + 1) < arguments.length) {
				xliff = arguments[i + 1];
			}
			if (arg.equals("-target") && (i + 1) < arguments.length) {
				target = arguments[i + 1];
			}
			if (arg.equals("-catalog") && (i + 1) < arguments.length) {
				catalog = arguments[i + 1];
			}
			if (arg.equals("-unapproved")) {
				unapproved = true;
			}
			if (arg.equals("-export")) {
				exportTMX = true;
			}
			if (arg.equals("-getTarget")) {
				getTarget = true;
			}
			if (arg.equals("-lang") && (i + 1) < arguments.length) {
				Locale.setDefault(Locale.forLanguageTag(arguments[i + 1]));
			}
		}
		if (arguments.length < 2) {
			help();
			return;
		}
		if (xliff.isEmpty()) {
			logger.log(Level.ERROR, Messages.getString("Merge.02"));
			return;
		}
		File xliffFile = new File(xliff);
		if (!xliffFile.isAbsolute()) {
			xliff = xliffFile.getAbsoluteFile().getAbsolutePath();
		}
		if (getTarget) {
			try {
				target = getTargetFile(xliff);
				System.out.println(target);
				return;
			} catch (IOException | SAXException | ParserConfigurationException e) {
				logger.log(Level.ERROR, Messages.getString("Merge.03"), e);
				return;
			}
		}
		if (target.isEmpty()) {
			try {
				target = getTargetFile(xliff);
			} catch (IOException | SAXException | ParserConfigurationException e) {
				logger.log(Level.ERROR, Messages.getString("Merge.03"), e);
				return;
			}
		} else {
			File targetFile = new File(target);
			if (!targetFile.isAbsolute()) {
				target = targetFile.getAbsoluteFile().getAbsolutePath();
			}
		}
		if (target.isEmpty()) {
			logger.log(Level.ERROR, Messages.getString("Merge.04"));
			return;
		}
		if (catalog.isEmpty()) {
			String home = System.getenv("OpenXLIFF_HOME");
			if (home == null) {
				home = System.getProperty("user.dir");
			}
			File catalogFolder = new File(new File(home), "catalog");
			if (!catalogFolder.exists()) {
				MessageFormat mf = new MessageFormat(Messages.getString("Merge.05"));
				logger.log(Level.ERROR, mf.format(new String[] { catalogFolder.getAbsolutePath() }));
				return;
			}
			catalog = new File(catalogFolder, "catalog.xml").getAbsolutePath();
		}
		File catalogFile = new File(catalog);
		if (!catalogFile.exists()) {
			logger.log(Level.ERROR, Messages.getString("Merge.06"));
			return;
		}
		if (!catalogFile.isAbsolute()) {
			catalog = catalogFile.getAbsoluteFile().getAbsolutePath();
		}
		List<String> result = merge(xliff, target, catalog, unapproved);
		if (exportTMX && Constants.SUCCESS.equals(result.get(0))) {
			String tmx = "";
			if (xliff.toLowerCase().endsWith(".xlf")) {
				tmx = xliff.substring(0, xliff.lastIndexOf('.')) + ".tmx";
			} else {
				tmx = xliff + ".tmx";
			}
			result = TmxExporter.export(xliff, tmx, catalog);
		}
		if (!Constants.SUCCESS.equals(result.get(0))) {
			MessageFormat mf = new MessageFormat(Messages.getString("Merge.07"));
			logger.log(Level.ERROR, mf.format(new String[] { result.get(1) }));
		}
	}

	public static List<String> merge(String xliff, String target, String catalog, boolean acceptUnaproved) {
		List<String> result = new ArrayList<>();
		try {
			loadXliff(xliff, catalog);
			boolean unapproved = acceptUnaproved;
			if (root.getAttributeValue("version").startsWith("2.")) {
				File tmpXliff = File.createTempFile("temp", ".xlf", new File(xliff).getParentFile());
				FromXliff2.run(xliff, tmpXliff.getAbsolutePath(), catalog);
				loadXliff(tmpXliff.getAbsolutePath(), catalog);
				Files.delete(Paths.get(tmpXliff.toURI()));
			}
			if (unapproved) {
				approveAll(root);
			}

			segments = new ArrayList<>();
			removeAltTrans(root);

			List<Element> files = root.getChildren("file");
			fileSet = new HashSet<>();
			Iterator<Element> it = files.iterator();
			while (it.hasNext()) {
				Element file = it.next();
				fileSet.add(file.getAttributeValue("original"));
			}

			if (fileSet.size() != 1) {
				File f = new File(target);
				if (f.exists()) {
					if (!f.isDirectory()) {
						MessageFormat mf = new MessageFormat(Messages.getString("Merge.08"));
						String error = mf.format(new String[] { f.getAbsolutePath() });
						logger.log(Level.ERROR, error);
						result.add(Constants.ERROR);
						result.add(error);
						return result;
					}
				} else {
					Files.createDirectories(f.toPath());
				}
			}
			Iterator<String> ft = fileSet.iterator();
			List<Map<String, String>> paramsList = new ArrayList<>();
			while (ft.hasNext()) {
				String file = ft.next();
				File xliffFile = File.createTempFile("temp", ".xlf");
				String[] pair = saveXliff(file, xliffFile);
				String encoding = pair[0];
				if (encoding.isEmpty()) {
					List<PI> pis = root.getPI();
					if (pis != null) {
						Iterator<PI> pt = pis.iterator();
						while (pt.hasNext()) {
							PI pi = pt.next();
							if (pi.getTarget().equals("encoding")) {
								encoding = pi.getData();
							}
						}
					}
				}
				Map<String, String> params = new HashMap<>();
				params.put("xliff", xliffFile.getAbsolutePath());
				if (fileSet.size() == 1) {
					params.put("backfile", target);
				} else {
					params.put("backfile", Utils.getAbsolutePath(target, file));
				}
				params.put("encoding", encoding);
				params.put("catalog", catalog);
				params.put("format", pair[1]);
				paramsList.add(params);
			}
			for (int i = 0; i < paramsList.size(); i++) {
				List<String> res = run(paramsList.get(i));
				File f = new File(paramsList.get(i).get("xliff"));
				Files.deleteIfExists(Paths.get(f.toURI()));
				if (!Constants.SUCCESS.equals(res.get(0))) {
					logger.log(Level.ERROR, res.get(1));
					return res;
				}
			}
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | URISyntaxException ex) {
			logger.log(Level.ERROR, ex);
			result.add(Constants.ERROR);
			result.add(ex.getMessage());
		}
		return result;
	}

	private static void help() {
		MessageFormat mf = new MessageFormat(Messages.getString("Merge.help"));
		boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
		String help = mf.format(new String[] { isWindows ? "merge.cmd" : "merge.sh" });
		System.out.println(help);
	}

	private static void approveAll(Element e) {
		if (e.getName().equals("trans-unit")) {
			Element target = e.getChild("target");
			if (target != null) {
				e.setAttribute("approved", "yes");
			}
			return;
		}
		List<Element> children = e.getChildren();
		for (int i = 0; i < children.size(); i++) {
			approveAll(children.get(i));
		}
	}

	protected static void loadXliff(String fileName, String catalog)
			throws SAXException, IOException, ParserConfigurationException, URISyntaxException {
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));
		doc = builder.build(fileName);
		root = doc.getRootElement();
		if (!root.getName().equals("xliff")) {
			throw new IOException(Messages.getString("Merge.09"));
		}
	}

	private static void removeAltTrans(Element e) {
		if (e.getName().equals("trans-unit")) {
			e.removeChild("alt-trans");
			segments.add(e);
		}
		List<Element> children = e.getChildren();
		for (Element child : children) {
			removeAltTrans(child);
		}
	}

	private static String[] saveXliff(String fileName, File xliff) throws IOException {
		String encoding = "";
		String dataType = "";
		try (FileOutputStream out = new FileOutputStream(xliff)) {
			writeStr(out, "<xliff version=\"1.2\">\n");
			List<Element> files = root.getChildren("file");
			Iterator<Element> it = files.iterator();
			while (it.hasNext()) {
				Element file = it.next();
				if (file.getAttributeValue("original").equals(fileName)) {
					dataType = file.getAttributeValue("datatype");
					List<PI> pis = file.getPI();
					if (pis != null) {
						Iterator<PI> pt = pis.iterator();
						while (pt.hasNext()) {
							PI pi = pt.next();
							if (pi.getTarget().equals("encoding")) {
								encoding = pi.getData();
							}
						}
					}
					file.writeBytes(out, StandardCharsets.UTF_8);
				}
			}
			writeStr(out, "</xliff>\n");
		}
		return new String[] { encoding, dataType };
	}

	private static void writeStr(FileOutputStream out, String string) throws IOException {
		out.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private static List<String> run(Map<String, String> params) {
		List<String> result = new ArrayList<>();
		File temporary = null;
		try {
			String dataType = params.get("format");
			loadXliff(params.get("xliff"), params.get("catalog"));
			String skl = getSkeleton();
			params.put("skeleton", skl);
			if (checkGroups(root)) {
				temporary = File.createTempFile("group", ".xlf");
				removeGroups(root, doc);
				try (FileOutputStream out = new FileOutputStream(temporary.getAbsolutePath())) {
					doc.writeBytes(out, doc.getEncoding());
				}
				params.put("xliff", temporary.getAbsolutePath());
			}

			if (dataType.equals(FileFormats.INX) || dataType.equals("x-inx")) {
				params.put("InDesign", "yes");
				result = Xliff2Xml.run(params);
			} else if (dataType.equals(FileFormats.ICML) || dataType.equals("x-icml")) {
				params.put("IDML", "true");
				result = Xliff2Xml.run(params);
			} else if (dataType.equals(FileFormats.IDML) || dataType.equals("x-idml")) {
				result = Xliff2Idml.run(params);
			} else if (dataType.equals(FileFormats.DITA) || dataType.equals("x-ditamap")) {
				result = Xliff2DitaMap.run(params);
			} else if (dataType.equals(FileFormats.HTML) || dataType.equals("html")) {
				String home = System.getenv("OpenXLIFF_HOME");
				if (home == null) {
					home = System.getProperty("user.dir");
				}
				File folder = new File(home, "xmlfilter");
				params.put("iniFile", new File(folder, "init_html.xml").getAbsolutePath());
				result = Xliff2Html.run(params);
			} else if (dataType.equals(FileFormats.JS) || dataType.equals("javascript")) {
				result = Xliff2jscript.run(params);
			} else if (dataType.equals(FileFormats.JSON) || dataType.endsWith("json")) {
				result = Xliff2json.run(params);
			} else if (dataType.equals(FileFormats.JAVA) || dataType.equals("javapropertyresourcebundle")
					|| dataType.equals("javalistresourcebundle")) {
				result = Xliff2Properties.run(params);
			} else if (dataType.equals(FileFormats.MIF) || dataType.equals("mif")) {
				result = Xliff2Mif.run(params);
			} else if (dataType.equals(FileFormats.OFF) || dataType.equals("x-office")) {
				result = Xliff2Office.run(params);
			} else if (dataType.equals(FileFormats.PO) || dataType.equals("po")) {
				result = Xliff2Po.run(params);
			} else if (dataType.equals(FileFormats.PHPA) || dataType.equals("x-phparray")) {
				result = Xliff2Php.run(params);
			} else if (dataType.equals(FileFormats.QTIP) || dataType.equals("x-qtipackage")) {
				result = Xliff2Qtip.run(params);
			} else if (dataType.equals(FileFormats.RC) || dataType.equals("winres")) {
				result = Xliff2Rc.run(params);
			} else if (dataType.equals(FileFormats.RESX) || dataType.equals("resx")) {
				result = Xliff2Resx.run(params);
			} else if (dataType.equals(FileFormats.SDLPPX) || dataType.equals("x-sdlpackage")) {
				result = Xliff2Sdlrpx.run(params);
			} else if (dataType.equals(FileFormats.SDLXLIFF) || dataType.equals("x-sdlxliff")) {
				result = Xliff2Sdl.run(params);
			} else if (dataType.equals(FileFormats.SRT) || dataType.equals("x-srt")) {
				result = Xliff2Srt.run(params);
			} else if (dataType.equals(FileFormats.TEXT) || dataType.equals("plaintext")) {
				result = Xliff2Text.run(params);
			} else if (dataType.equals(FileFormats.TS) || dataType.equals("x-ts")) {
				result = Xliff2Ts.run(params);
			} else if (dataType.equals(FileFormats.TXML) || dataType.equals("x-txml")) {
				result = Xliff2Txml.run(params);
			} else if (dataType.equals(FileFormats.TXLF) || dataType.equals("x-txlf")) {
				result = Xliff2Txlf.run(params);
			} else if (dataType.equals(FileFormats.WPML) || dataType.equals("x-wpmlxliff")) {
				result = Xliff2Wpml.run(params);
			} else if (dataType.equals(FileFormats.XML) || dataType.equals("xml")) {
				result = Xliff2Xml.run(params);
			} else if (dataType.equals(FileFormats.XLIFF) || dataType.equals("x-xliff")) {
				result = FromOpenXliff.run(params);
			} else {
				result.add(Constants.ERROR);
				result.add(Messages.getString("Merge.10"));
			}
			if (temporary != null) {
				Files.delete(Paths.get(temporary.toURI()));
			}
		} catch (Exception e) {
			logger.log(Level.ERROR, Messages.getString("Merge.11"), e);
			result = new ArrayList<>();
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	private static String getSkeleton() throws IOException {
		String result = "";
		Element file = root.getChild("file");
		Element header = null;
		if (file != null) {
			header = file.getChild("header");
			if (header != null) {
				Element mskl = header.getChild("skl");
				if (mskl != null) {
					Element external = mskl.getChild("external-file");
					if (external != null) {
						result = external.getAttributeValue("href");
						result = result.replace("&amp;", "&");
						result = result.replace("&lt;", "<");
						result = result.replace("&gt;", ">");
						result = result.replace("&apos;", "\'");
						result = result.replace("&quot;", "\"");
					} else {
						Element internal = mskl.getChild("internal-file");
						if (internal != null) {
							File tmp = File.createTempFile("internal", ".skl");
							tmp.deleteOnExit();
							Utils.decodeToFile(internal.getText(), tmp.getAbsolutePath());
							return tmp.getAbsolutePath();
						}
						return result;
					}
				} else {
					return result;
				}
			} else {
				return result;
			}
		} else {
			return result;
		}
		return result;
	}

	private static boolean checkGroups(Element e) {
		if (e.getName().equals("group") && e.getAttributeValue("ts").equals("hs-split")) {
			return true;
		}
		List<Element> children = e.getChildren();
		Iterator<Element> i = children.iterator();
		while (i.hasNext()) {
			Element child = i.next();
			if (checkGroups(child)) {
				return true;
			}
		}
		return false;
	}

	public static void removeGroups(Element e, Document d) {
		List<XMLNode> children = e.getContent();
		for (int i = 0; i < children.size(); i++) {
			XMLNode n = children.get(i);
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element child = (Element) n;
				if (child.getName().equals("group") && child.getAttributeValue("ts").equals("hs-split")) {
					child = joinGroup(child);
					Element tu = new Element("trans-unit");
					tu.clone(child);
					children.set(i, tu);
					e.setContent(children);
				} else {
					removeGroups(child, d);
				}
			}
		}
	}

	private static Element joinGroup(Element child) {
		List<Element> pair = child.getChildren();
		Element left = pair.get(0);
		if (left.getName().equals("group")) {
			left = joinGroup(left);
		}
		Element right = pair.get(1);
		if (right.getName().equals("group")) {
			right = joinGroup(right);
		}
		List<XMLNode> srcContent = right.getChild("source").getContent();
		for (int k = 0; k < srcContent.size(); k++) {
			XMLNode n = srcContent.get(k);
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				left.getChild("source").addContent(n);
			}
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				left.getChild("source").addContent(n);
			}
		}
		List<XMLNode> tgtContent = right.getChild("target").getContent();
		for (int k = 0; k < tgtContent.size(); k++) {
			XMLNode n = tgtContent.get(k);
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				left.getChild("target").addContent(n);
			}
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				left.getChild("target").addContent(n);
			}
		}
		left.setAttribute("id", child.getAttributeValue("id"));
		if (left.getAttributeValue("approved").equalsIgnoreCase("yes")
				&& right.getAttributeValue("approved").equalsIgnoreCase("yes")) {
			left.setAttribute("approved", "yes");
		} else {
			left.setAttribute("approved", "no");
		}
		return left;
	}

	public static String getTargetFile(String file) throws IOException, SAXException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		Element r = builder.build(file).getRootElement();
		if (!r.getName().equals("xliff")) {
			throw new IOException(Messages.getString("Merge.12"));
		}
		List<Element> files = r.getChildren("file");
		if (files.isEmpty()) {
			throw new IOException(Messages.getString("Merge.13"));
		}
		String version = r.getAttributeValue("version");
		String tgtLanguage = "";
		if (version.equals("1.2")) {
			tgtLanguage = files.get(0).getAttributeValue("target-language");
		} else {
			tgtLanguage = r.getAttributeValue("trgLang");
		}
		if (tgtLanguage.isEmpty()) {
			throw new IOException(Messages.getString("Merge.14"));
		}
		String target = "";
		TreeSet<String> originals = new TreeSet<>();
		Iterator<Element> it = files.iterator();
		while (it.hasNext()) {
			originals.add(it.next().getAttributeValue("original"));
		}
		if (originals.size() == 1) {
			if (file.endsWith(".xlf")) {
				target = file.substring(0, file.length() - ".xlf".length());
				if (target.indexOf('.') != -1) {
					target = target.substring(0, target.lastIndexOf('.'))
							+ "_" + tgtLanguage + target.substring(target.lastIndexOf('.'));
				} else {
					String original = originals.first();
					if (original.indexOf('.') != -1) {
						target = original.substring(0, original.lastIndexOf('.'))
								+ "_" + tgtLanguage + original.substring(original.lastIndexOf('.'));
					} else {
						target = original + "_" + tgtLanguage;
					}
				}
			} else {
				if (target.indexOf('.') != -1) {
					target = target.substring(0, target.lastIndexOf('.'))
							+ "_" + tgtLanguage + target.substring(target.lastIndexOf('.'));
				}
			}
			if (target.endsWith(".sdlppx")) {
				target = target.substring(0, target.length() - ".sdlppx".length()) + ".sdlrpx";
			}
		} else {
			File parent = new File(file).getParentFile();
			if (parent == null) {
				parent = new File(System.getProperty("user.dir"));
			}
			target = new File(parent, tgtLanguage).getAbsolutePath();
		}
		return target;
	}

}
