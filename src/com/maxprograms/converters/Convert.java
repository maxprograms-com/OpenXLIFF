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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.maxprograms.converters.ditamap.DitaMap2Xliff;
import com.maxprograms.converters.html.Html2Xliff;
import com.maxprograms.converters.idml.Idml2Xliff;
import com.maxprograms.converters.idml.Story2Xliff;
import com.maxprograms.converters.javaproperties.Properties2Xliff;
import com.maxprograms.converters.javascript.Jscript2xliff;
import com.maxprograms.converters.json.Json2Xliff;
import com.maxprograms.converters.mif.Mif2Xliff;
import com.maxprograms.converters.office.Office2Xliff;
import com.maxprograms.converters.php.Php2Xliff;
import com.maxprograms.converters.plaintext.Text2Xliff;
import com.maxprograms.converters.po.Po2Xliff;
import com.maxprograms.converters.qti.Qtip2Xliff;
import com.maxprograms.converters.rc.Rc2Xliff;
import com.maxprograms.converters.resx.Resx2Xliff;
import com.maxprograms.converters.sdlppx.Sdlppx2Xliff;
import com.maxprograms.converters.sdlxliff.Sdl2Xliff;
import com.maxprograms.converters.srt.Srt2Xliff;
import com.maxprograms.converters.ts.Ts2Xliff;
import com.maxprograms.converters.txlf.Txlf2Xliff;
import com.maxprograms.converters.txml.Txml2Xliff;
import com.maxprograms.converters.wpml.Wpml2Xliff;
import com.maxprograms.converters.xliff.ToOpenXliff;
import com.maxprograms.converters.xml.Xml2Xliff;
import com.maxprograms.xliff2.Resegmenter;
import com.maxprograms.xliff2.ToXliff2;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLOutputter;

public class Convert {

	private static Logger logger = System.getLogger(Convert.class.getName());

	public static void main(String[] args) {

		String[] arguments = Utils.fixPath(args);

		String source = "";
		String type = "";
		String enc = "";
		String srcLang = "";
		String tgtLang = "";
		String srx = "";
		String skl = "";
		String xliff = "";
		String catalog = "";
		String ditaval = "";
		String config = "";
		String xmlfilter = "";
		String jsonFile = "";
		String maxThreads = "";
		boolean embed = false;
		boolean paragraph = false;
		boolean ignoretc = false;
		boolean ignoresvg = false;
		boolean xliff20 = false;
		boolean xliff21 = false;
		boolean xliff22 = false;
		boolean strict = false;
		boolean resegment = false;

		for (int i = 0; i < arguments.length; i++) {
			String arg = arguments[i];
			if (arg.equals("-version")) {
				MessageFormat mf = new MessageFormat(Messages.getString("Convert.02"));
				logger.log(Level.INFO, mf.format(new String[] { Constants.VERSION, Constants.BUILD }));
				return;
			}
			if (arg.equals("-help")) {
				help();
				return;
			}
			if (arg.equals("-charsets")) {
				listCharsets();
				return;
			}
			if (arg.equals("-lang") && (i + 1) < arguments.length) {
				Locale.setDefault(Locale.forLanguageTag(arguments[i + 1]));
			}
			if (arg.equals("-json") && (i + 1) < arguments.length) {
				jsonFile = arguments[i + 1];
				try {
					convert(jsonFile);
				} catch (IOException | JSONException e) {
					MessageFormat mf = new MessageFormat(Messages.getString("Convert.13"));
					logger.log(Level.ERROR, mf.format(new String[] { e.getMessage() }));
				}
				return;
			}
			if (arg.equals("-types")) {
				System.out.println(Messages.getString("Convert.types"));
				return;
			}
			if (arg.equals("-file") && (i + 1) < arguments.length) {
				source = arguments[i + 1];
			}
			if (arg.equals("-type") && (i + 1) < arguments.length) {
				type = arguments[i + 1];
			}
			if (arg.equals("-enc") && (i + 1) < arguments.length) {
				enc = arguments[i + 1];
			}
			if (arg.equals("-srcLang") && (i + 1) < arguments.length) {
				srcLang = arguments[i + 1];
			}
			if (arg.equals("-tgtLang") && (i + 1) < arguments.length) {
				tgtLang = arguments[i + 1];
			}
			if (arg.equals("-srx") && (i + 1) < arguments.length) {
				srx = arguments[i + 1];
			}
			if (arg.equals("-skl") && (i + 1) < arguments.length) {
				skl = arguments[i + 1];
			}
			if (arg.equals("-xliff") && (i + 1) < arguments.length) {
				xliff = arguments[i + 1];
			}
			if (arg.equals("-catalog") && (i + 1) < arguments.length) {
				catalog = arguments[i + 1];
			}
			if (arg.equals("-ditaval") && (i + 1) < arguments.length) {
				ditaval = arguments[i + 1];
			}
			if (arg.equals("-config") && (i + 1) < arguments.length) {
				config = arguments[i + 1];
			}
			if (arg.equals("-embed")) {
				embed = true;
			}
			if (arg.equals("-paragraph")) {
				paragraph = true;
			}
			if (arg.equals("-ignoretc")) {
				ignoretc = true;
			}
			if (arg.equals("-ignoresvg")) {
				ignoresvg = true;
			}
			if (arg.equals("-xmlfilter") && (i + 1) < arguments.length) {
				xmlfilter = arguments[i + 1];
			}
			if (arg.equals("-2.0")) {
				xliff20 = true;
			}
			if (arg.equals("-2.1")) {
				xliff21 = true;
			}
			if (arg.equals("-2.2")) {
				xliff22 = true;
			}
			if (arg.equals("-strict")) {
				strict = true;
			}
			if (arg.equals("-maxThreads") && (i + 1) < arguments.length) {
				maxThreads = arguments[i + 1];
			}
		}
		if (arguments.length < 4) {
			help();
			return;
		}
		if (source.isEmpty()) {
			logger.log(Level.ERROR, Messages.getString("Convert.03"));
			return;
		}
		File sourceFile = new File(source);
		if (!sourceFile.exists()) {
			logger.log(Level.ERROR, Messages.getString("Convert.04"));
			return;
		}
		if (!sourceFile.isAbsolute()) {
			source = sourceFile.getAbsoluteFile().getAbsolutePath();
		}
		if (type.isEmpty()) {
			String detected = FileFormats.detectFormat(source);
			if (detected != null) {
				type = FileFormats.getShortName(detected);
				MessageFormat mf = new MessageFormat(Messages.getString("Convert.05"));
				logger.log(Level.INFO, mf.format(new String[] { type }));
			} else {
				logger.log(Level.ERROR, Messages.getString("Convert.06"));
				return;
			}
		}
		type = FileFormats.getFullName(type);
		if (type == null) {
			logger.log(Level.ERROR, Messages.getString("Convert.07"));
			return;
		}
		if (enc.isEmpty()) {
			Charset charset = EncodingResolver.getEncoding(source, type);
			if (charset != null) {
				enc = charset.name();
				MessageFormat mf = new MessageFormat(Messages.getString("Convert.08"));
				logger.log(Level.INFO, mf.format(new String[] { enc }));
			} else {
				logger.log(Level.ERROR, Messages.getString("Convert.09"));
				return;
			}
		}
		String[] encodings = EncodingResolver.getPageCodes();
		if (!Arrays.asList(encodings).contains(enc)) {
			logger.log(Level.ERROR, Messages.getString("Convert.10"));
			return;
		}
		if (srcLang.isEmpty()) {
			logger.log(Level.ERROR, Messages.getString("Convert.11"));
			return;
		}
		try {
			if (!Utils.isValidLanguage(srcLang)) {
				MessageFormat mf = new MessageFormat(Messages.getString("Convert.12"));
				logger.log(Level.WARNING, mf.format(new String[] { srcLang }));
			}
			if (!tgtLang.isEmpty() && !Utils.isValidLanguage(tgtLang)) {
				MessageFormat mf = new MessageFormat(Messages.getString("Convert.12"));
				logger.log(Level.WARNING, mf.format(new String[] { tgtLang }));
			}
		} catch (IOException | SAXException | ParserConfigurationException e) {
			logger.log(Level.ERROR, Messages.getString("Convert.14"), e);
			return;
		}
		if (srx.isEmpty()) {
			srx = defaultSRX();
		}
		File srxFile = new File(srx);
		if (!srxFile.exists()) {
			MessageFormat mf = new MessageFormat(Messages.getString("Convert.15"));
			logger.log(Level.ERROR, mf.format(new String[] { srxFile.getAbsolutePath() }));
			return;
		}
		if (!srxFile.isAbsolute()) {
			srx = srxFile.getAbsoluteFile().getAbsolutePath();
		}
		if (xmlfilter.isEmpty()) {
			xmlfilter = defaultFilterFolder();
		}
		if (catalog.isEmpty()) {
			try {
				catalog = defaultCatalog();
			} catch (IOException e) {
				logger.log(Level.ERROR, e.getMessage());
				return;
			}
		}
		File catalogFile = new File(catalog);
		if (!catalogFile.exists()) {
			logger.log(Level.ERROR, Messages.getString("Convert.17"));
			return;
		}
		if (!catalogFile.isAbsolute()) {
			catalog = catalogFile.getAbsoluteFile().getAbsolutePath();
		}
		if (skl.isEmpty()) {
			skl = sourceFile.getAbsoluteFile().getAbsolutePath() + ".skl";
		}
		if (xliff.isEmpty()) {
			xliff = sourceFile.getAbsoluteFile().getAbsolutePath() + ".xlf";
		}
		if ((xliff20 && xliff21) || (xliff20 && xliff22) || (xliff21 && xliff22)) {
			logger.log(Level.ERROR, Messages.getString("Convert.21"));
			return;
		}
		if (!paragraph && (xliff20 || xliff21 || xliff22)) {
			paragraph = true;
			resegment = true;
		}
		Map<String, String> params = new HashMap<>();
		params.put("source", source);
		params.put("xliff", xliff);
		params.put("skeleton", skl);
		params.put("format", type);
		params.put("catalog", catalog);
		params.put("srcEncoding", enc);
		params.put("paragraph", paragraph ? "yes" : "no");
		params.put("ignoretc", ignoretc ? "yes" : "no");
		params.put("ignoresvg", ignoresvg ? "yes" : "no");
		params.put("srxFile", srx);
		params.put("srcLang", srcLang);
		params.put("xmlfilter", xmlfilter);
		if (!tgtLang.isEmpty()) {
			params.put("tgtLang", tgtLang);
		}
		if (type.equals(FileFormats.getShortName(FileFormats.DITA)) && !ditaval.isEmpty()) {
			params.put("ditaval", ditaval);
		}
		if (type.equals(FileFormats.getShortName(FileFormats.JSON)) && !config.isEmpty()) {
			File configFile = new File(config);
			if (!configFile.isAbsolute()) {
				config = configFile.getAbsoluteFile().getAbsolutePath();
			}
			params.put("config", config);
		}
		if (embed) {
			params.put("embed", "yes");
		}
		if (xliff20) {
			params.put("xliff20", "yes");
		}
		if (xliff21) {
			params.put("xliff21", "yes");
		}
		if (xliff22) {
			params.put("xliff22", "yes");
		}
		if (strict) {
			params.put("strict", "yes");
		}
		if (!maxThreads.isEmpty()) {
			params.put("maxThreads", maxThreads);
		}
		List<String> result = run(params);

		if (resegment && Constants.SUCCESS.equals(result.get(0))) {
			try {
				int threads = Runtime.getRuntime().availableProcessors();
				if (!maxThreads.isEmpty()) {
					try {
						threads = Integer.parseInt(maxThreads);
					} catch (NumberFormatException e) {
						MessageFormat mf = new MessageFormat(Messages.getString("Convert.22"));
						logger.log(Level.WARNING, mf.format(new String[] { maxThreads }));
					}
				}
				result = Resegmenter.run(xliff, srx, srcLang, CatalogBuilder.getCatalog(catalog), threads);
			} catch (Exception e) {
				// do nothing
			}
		}
		if (!Constants.SUCCESS.equals(result.get(0))) {
			logger.log(Level.ERROR, Messages.getString("Convert.18"), result.get(1));
		}
	}

	private static void help() {
		MessageFormat mf = new MessageFormat(Messages.getString("Convert.help"));
		boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
		String help = mf.format(new String[] { isWindows ? "convert.cmd" : "convert.sh" });
		System.out.println(help);
	}

	public static List<String> addSkeleton(String fileName, String catalog) {
		List<String> result = new ArrayList<>();
		try {
			SAXBuilder builder = new SAXBuilder();
			builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));
			Document doc = builder.build(fileName);
			Element root = doc.getRootElement();
			List<Element> files = root.getChildren("file");
			Iterator<Element> it = files.iterator();
			Set<String> deleted = new HashSet<>();
			while (it.hasNext()) {
				Element file = it.next();
				Element header = file.getChild("header");
				Element skl = header.getChild("skl");
				Element external = skl.getChild("external-file");
				String sklName = external.getAttributeValue("href");
				sklName = sklName.replace("&amp;", "&");
				sklName = sklName.replace("&lt;", "<");
				sklName = sklName.replace("&gt;", ">");
				sklName = sklName.replace("&apos;", "\'");
				sklName = sklName.replace("&quot;", "\"");
				if (!deleted.contains(sklName)) {
					File skeleton = new File(sklName);
					Element internal = new Element("internal-file");
					internal.setAttribute("form", "base64");
					internal.addContent(Utils.encodeFromFile(skeleton.getAbsolutePath()));
					skl.setContent(new ArrayList<>());
					skl.addContent(internal);
					Files.delete(Paths.get(skeleton.toURI()));
					deleted.add(sklName);
				}
			}
			XMLOutputter outputter = new XMLOutputter();
			Indenter.indent(root, 2);
			outputter.preserveSpace(true);
			try (FileOutputStream out = new FileOutputStream(fileName)) {
				outputter.output(doc, out);
			}
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
			logger.log(Level.ERROR, Messages.getString("Convert.19"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	public static List<String> run(Map<String, String> params) {
		List<String> result = new ArrayList<>();
		try {
			String format = params.get("format");
			if (format.equals(FileFormats.INX)) {
				params.put("InDesign", "yes");
				result = Xml2Xliff.run(params);
			} else if (format.equals(FileFormats.ICML)) {
				params.put("from", "x-icml");
				result = Story2Xliff.run(params);
			} else if (format.equals(FileFormats.IDML)) {
				result = Idml2Xliff.run(params);
			} else if (format.equals(FileFormats.DITA)) {
				result = DitaMap2Xliff.run(params);
			} else if (format.equals(FileFormats.HTML)) {
				result = Html2Xliff.run(params);
			} else if (format.equals(FileFormats.JS)) {
				result = Jscript2xliff.run(params);
			} else if (format.equals(FileFormats.JSON)) {
				result = Json2Xliff.run(params);
			} else if (format.equals(FileFormats.JAVA)) {
				result = Properties2Xliff.run(params);
			} else if (format.equals(FileFormats.MIF)) {
				result = Mif2Xliff.run(params);
			} else if (format.equals(FileFormats.OO) || format.equals(FileFormats.OFF)) {
				result = Office2Xliff.run(params);
			} else if (format.equals(FileFormats.PHPA)) {
				result = Php2Xliff.run(params);
			} else if (format.equals(FileFormats.PO)) {
				result = Po2Xliff.run(params);
			} else if (format.equals(FileFormats.QTI)) {
				params.put("qti", "yes");
				result = Xml2Xliff.run(params);
			} else if (format.equals(FileFormats.QTIP)) {
				result = Qtip2Xliff.run(params);
			} else if (format.equals(FileFormats.RC)) {
				result = Rc2Xliff.run(params);
			} else if (format.equals(FileFormats.RESX)) {
				result = Resx2Xliff.run(params);
			} else if (format.equals(FileFormats.SDLPPX)) {
				result = Sdlppx2Xliff.run(params);
			} else if (format.equals(FileFormats.SDLXLIFF)) {
				result = Sdl2Xliff.run(params);
			} else if (format.equals(FileFormats.SRT)) {
				result = Srt2Xliff.run(params);
			} else if (format.equals(FileFormats.TEXT)) {
				result = Text2Xliff.run(params);
			} else if (format.equals(FileFormats.TS)) {
				result = Ts2Xliff.run(params);
			} else if (format.equals(FileFormats.TXML)) {
				result = Txml2Xliff.run(params);
			} else if (format.equals(FileFormats.TXLF)) {
				result = Txlf2Xliff.run(params);
			} else if (format.equals(FileFormats.WPML)) {
				result = Wpml2Xliff.run(params);
			} else if (format.equals(FileFormats.XML)) {
				result = Xml2Xliff.run(params);
			} else if (format.equals(FileFormats.XMLG)) {
				params.put("generic", "yes");
				result = Xml2Xliff.run(params);
			} else if (format.equals(FileFormats.XLIFF)) {
				result = ToOpenXliff.run(params);
			} else {
				result.add(Constants.ERROR);
				result.add(Messages.getString("Convert.20"));
			}
			if ("yes".equals(params.get("embed")) && Constants.SUCCESS.equals(result.get(0))) {
				result = addSkeleton(params.get("xliff"), params.get("catalog"));
			}
			boolean xliff20 = "yes".equals(params.get("xliff20"));
			boolean xliff21 = "yes".equals(params.get("xliff21"));
			boolean xliff22 = "yes".equals(params.get("xliff22"));
			if ((xliff20 || xliff21 || xliff22) && Constants.SUCCESS.equals(result.get(0))) {
				String version = "2.0";
				if (xliff21) {
					version = "2.1";
				}
				if (xliff22) {
					version = "2.2";
				}
				result = ToXliff2.run(new File(params.get("xliff")), params.get("catalog"), version);
			}
		} catch (Exception e) {
			result.add(0, Constants.ERROR);
			result.add(1, e.getMessage());
		}
		return result;
	}

	private static void listCharsets() {
		SortedMap<String, Charset> available = Charset.availableCharsets();
		Set<String> keySet = available.keySet();
		Iterator<String> it = keySet.iterator();
		while (it.hasNext()) {
			Charset charset = available.get(it.next());
			System.out.println(charset.displayName());
		}
	}

	private static void convert(String jsonFile) throws IOException, JSONException {
		JSONObject json = Utils.readJSON(jsonFile);
		JSONArray files = json.getJSONArray("files");
		for (int i = 0; i < files.length(); i++) {
			JSONObject file = files.getJSONObject(i);
			String source = file.getString("source");
			String xliff = file.has("xliff") ? file.getString("xliff") : source + ".xlf";
			String skl = file.has("skl") ? file.getString("skl") : source + ".skl";
			String srcLang = file.getString("srcLang");
			String tgtLang = file.has("tgtLang") ? file.getString("tgtLang") : "";
			String catalog = file.has("catalog") ? file.getString("catalog") : defaultCatalog();
			String srx = file.has("srx") ? file.getString("srx") : defaultSRX();
			String type = file.has("type") ? file.getString("type") : FileFormats.detectFormat(source);
			String enc = file.has("enc") ? file.getString("enc") : "";
			String xmlfilter = file.has("xmlfilter") ? file.getString("xmlfilter") : defaultFilterFolder();
			type = FileFormats.getFullName(type);
			if (enc.isEmpty()) {
				Charset charset = EncodingResolver.getEncoding(source, type);
				if (charset != null) {
					enc = charset.name();
					MessageFormat mf = new MessageFormat(Messages.getString("Convert.08"));
					logger.log(Level.INFO, mf.format(new String[] { enc }));
				} else {
					throw new IOException(Messages.getString("Convert.09"));
				}
			}

			File sklFile = new File(skl).getParentFile();
			if (!sklFile.exists()) {
				sklFile.mkdirs();
			}

			Map<String, String> params = new HashMap<>();
			params.put("source", source);
			params.put("xliff", xliff);
			params.put("skeleton", skl);
			params.put("format", type);
			params.put("catalog", catalog);
			params.put("srcEncoding", enc);
			if (file.has("paragraph")) {
				params.put("paragraph", "yes");
			}
			if (file.has("ignoretc")) {
				params.put("ignoretc", "yes");
			}
			if (file.has("ignoresvg")) {
				params.put("ignoresvg", "yes");
			}
			params.put("srxFile", srx);
			params.put("srcLang", srcLang);
			if (!tgtLang.isEmpty()) {
				params.put("tgtLang", tgtLang);
			}
			if (file.has("embed")) {
				params.put("embed", "yes");
			}
			if (file.has("is20")) {
				params.put("xliff20", file.getBoolean("is20") ? "yes" : "no");
			}
			if (file.has("is21")) {
				params.put("xliff21", file.getBoolean("is21") ? "yes" : "no");
			}
			if (file.has("is22")) {
				params.put("xliff22", file.getBoolean("is22") ? "yes" : "no");
			}
			if (file.has("ditaval")) {
				params.put("ditaval", file.getString("ditaval"));
			}
			if (file.has("config")) {
				params.put("config", file.getString("config"));
			}
			if (file.has("ignoresvg")) {
				params.put("ignoresvg", "yes");
			}
			params.put("xmlfilter", xmlfilter);
			List<String> result = run(params);
			if (!Constants.SUCCESS.equals(result.get(0))) {
				throw new IOException(result.get(1));
			}
		}
	}

	private static String defaultSRX() {
		String home = System.getenv("OpenXLIFF_HOME");
		if (home == null) {
			home = System.getProperty("user.dir");
		}
		File srxFolder = new File(new File(home), "srx");
		return new File(srxFolder, "default.srx").getAbsolutePath();
	}

	public static String defaultCatalog() throws IOException {
		String home = System.getenv("OpenXLIFF_HOME");
		if (home == null) {
			home = System.getProperty("user.dir");
		}
		File catalogFolder = new File(new File(home), "catalog");
		if (!catalogFolder.exists()) {
			throw new IOException(Messages.getString("Convert.16"));
		}
		File catalogFile = new File(catalogFolder, "catalog.xml");
		if (!catalogFile.exists()) {
			throw new IOException(Messages.getString("Convert.17"));
		}
		return catalogFile.getAbsolutePath();
	}

	private static String defaultFilterFolder() {
		String home = System.getenv("OpenXLIFF_HOME");
		if (home == null) {
			home = System.getProperty("user.dir");
		}
		File filtersFolder = new File(new File(home), "xmlfilter");
		return filtersFolder.getAbsolutePath();
	}
}
