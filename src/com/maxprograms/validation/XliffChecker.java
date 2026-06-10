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

package com.maxprograms.validation;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Utils;
import com.maxprograms.languages.RegistryParser;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;

public class XliffChecker {

	private static Logger logger = System.getLogger(XliffChecker.class.getName());
	private String reason;
	private String version;

	private RegistryParser registry;

	private HashSet<String> xliffNamespaces;
	private Map<String, Set<String>> attributesTable;
	private Map<String, String> altSrcItTable;
	private Map<String, String> altTgtItTable;
	private Map<String, String> tgtItTable;
	private Map<String, String> phasesTable;
	private Map<String, String> xids;
	private Map<String, String> groupIds;
	private Map<String, String> srcItTable;
	private Map<String, String> toolsTable;
	private Map<String, String> ids;
	private Map<String, String> midTable;
	private Map<String, String> bxTable;
	private Map<String, String> exTable;
	private Map<String, String> bptTable;
	private Map<String, String> eptTable;
	private String sourceLanguage;
	private String targetLanguage;
	private boolean inSegSource;
	private boolean inSource;
	private boolean inAltTrans;

	public static void main(String[] args) {

		String[] fixedArgs = Utils.fixPath(args);

		String xliff = "";
		String catalog = "";
		for (int i = 0; i < fixedArgs.length; i++) {
			String arg = fixedArgs[i];
			if (arg.equals("-help")) {
				help();
				return;
			}
			if (arg.equals("-lang") && (i + 1) < fixedArgs.length) {
				Locale.setDefault(Locale.forLanguageTag(fixedArgs[i + 1]));
			}
			if (arg.equals("-xliff") && (i + 1) < fixedArgs.length) {
				xliff = fixedArgs[i + 1];
			}
			if (arg.equals("-catalog") && (i + 1) < fixedArgs.length) {
				catalog = fixedArgs[i + 1];
			}
		}
		if (fixedArgs.length < 2) {
			help();
			return;
		}
		if (xliff.isEmpty()) {
			logger.log(Level.ERROR, Messages.getString("XliffChecker.1"));
			return;
		}
		File xliffFile = new File(xliff);
		if (!xliffFile.isAbsolute()) {
			xliff = xliffFile.getAbsoluteFile().getAbsolutePath();
		}
		if (catalog.isEmpty()) {
			String home = System.getenv("OpenXLIFF_HOME");
			if (home == null) {
				home = System.getProperty("user.dir");
			}
			File catalogFolder = new File(new File(home), "catalog");
			if (!catalogFolder.exists()) {
				logger.log(Level.ERROR, Messages.getString("XliffChecker.2"));
				return;
			}
			catalog = new File(catalogFolder, "catalog.xml").getAbsolutePath();
		}
		File catalogFile = new File(catalog);
		if (!catalogFile.exists()) {
			logger.log(Level.ERROR, Messages.getString("XliffChecker.3"));
			return;
		}
		if (!catalogFile.isAbsolute()) {
			catalog = catalogFile.getAbsoluteFile().getAbsolutePath();
		}
		try {
			XliffChecker instance = new XliffChecker();
			if (!instance.validate(xliff, catalog)) {
				MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.4"));
				logger.log(Level.ERROR, mf.format(new String[] { instance.getReason() }));
				return;
			}
			MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.5"));
			logger.log(Level.INFO, mf.format(new String[] { instance.getVersion() }));
		} catch (IOException e) {
			logger.log(Level.ERROR, Messages.getString("XliffChecker.6"), e);
		}
	}

	private static void help() {
		MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.help"));
		boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
		String help = mf.format(new String[] { isWindows ? "xliffchecker.cmd" : "xliffchecker.sh" });
		System.out.println(help);
	}

	public String getVersion() {
		return version;
	}

	public XliffChecker() throws IOException {
		version = "";
		reason = "";
		registry = new RegistryParser();
		xliffNamespaces = new HashSet<>();
		xliffNamespaces.add("urn:oasis:names:tc:xliff:document:1.1");
		xliffNamespaces.add("urn:oasis:names:tc:xliff:document:1.2");
	}

	private String getCatalog(String version) throws IOException {
		String home = System.getenv("OpenXLIFF_HOME");
		if (home == null) {
			home = System.getProperty("user.dir");
		}
		File catalogFolder = new File(new File(home), "catalog");
		if (!catalogFolder.exists()) {
			throw new IOException(Messages.getString("XliffChecker.2"));
		}
		File validationFolder = new File(catalogFolder, "validation");
		if (!validationFolder.exists()) {
			throw new IOException(Messages.getString("XliffChecker.3"));
		}

		File catalog = null;
		switch (version) {
			case "2.0":
				catalog = new File(validationFolder, "validation20.xml");
				break;
			case "2.1":
				catalog = new File(validationFolder, "validation21.xml");
				break;
			case "2.2":
				catalog = new File(validationFolder, "validation22.xml");
				break;
		}
		if (catalog == null) {
			throw new IOException(Messages.getString("XliffChecker.10"));
		}
		return catalog.getAbsolutePath();
	}

	public boolean validate(String file, String catalog) {
		try {
			SAXBuilder builder = new SAXBuilder();
			builder.setValidating(true);
			builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));
			Document document = builder.build(file);
			Element root = document.getRootElement();
			if (!"xliff".equals(root.getLocalName())) {
				reason = Messages.getString("XliffChecker.7");
				return false;
			}
			if (!root.hasAttribute("version")) {
				reason = Messages.getString("XliffChecker.8");
				return false;
			}
			version = root.getAttributeValue("version");
			if ("1.0".equals(version)) {
				// validate with DTD and recurse
				Xliff10 validator = new Xliff10();
				boolean valid = validator.validate(document, catalog);
				if (!valid) {
					reason = validator.getReason();
					return false;
				}
			} else if ("1.1".equals(version)) {
				// validate with schema and recurse
				Xliff11 validator = new Xliff11();
				boolean valid = validator.validate(document, catalog);
				if (!valid) {
					reason = validator.getReason();
					return false;
				}
			} else if ("1.2".equals(version)) {
				// validate against translational only
				Xliff12 validator = new Xliff12();
				boolean valid = validator.validate(document, catalog);
				if (!valid) {
					reason = validator.getReason();
					return false;
				}
			} else if ("2.0".equals(version) || "2.1".equals(version) || "2.2".equals(version)) {
				Xliff20 validator = new Xliff20();
				boolean valid = validator.validate(file, getCatalog(version), version);
				if (!valid) {
					reason = validator.getReason();
					return false;
				}
				return true;
			} else {
				reason = Messages.getString("XliffChecker.10");
				return false;
			}

			// There is a bug in XLIFF 1.1 schema, duplicated in XLIFF 1.2 Transitional
			// schema,
			// they allow any attribute in XLIFF elements.
			// Load a table of attributes for validating details that the parser ignored.

			createAttributesTable();
			return recurse(root);

		} catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
			reason = e.getMessage();
			return false;
		}
	}

	public String getReason() {
		return reason;
	}

	private void createAttributesTable() {
		attributesTable = new ConcurrentHashMap<>();

		Set<String> xliffSet = new HashSet<>();
		xliffSet.add("version");
		attributesTable.put("xliff", xliffSet);

		Set<String> fileSet = new HashSet<>();
		fileSet.add("original");
		fileSet.add("source-language");
		fileSet.add("datatype");
		fileSet.add("tool");
		fileSet.add("tool-id");
		fileSet.add("date");
		fileSet.add("ts");
		fileSet.add("category");
		fileSet.add("target-language");
		fileSet.add("product-name");
		fileSet.add("product-version");
		fileSet.add("build-num");
		attributesTable.put("file", fileSet);

		attributesTable.put("header", new HashSet<>()); // this element doesn't have attributes
		attributesTable.put("skl", new HashSet<>()); // this element doesn't have attributes

		Set<String> internalFileSet = new HashSet<>();
		internalFileSet.add("form");
		internalFileSet.add("crc");
		attributesTable.put("internal-file", internalFileSet);

		Set<String> externalFileSet = new HashSet<>();
		externalFileSet.add("href");
		externalFileSet.add("uid");
		externalFileSet.add("crc");
		attributesTable.put("external-file", externalFileSet);

		attributesTable.put("glossary", new HashSet<>()); // this element doesn't have attributes
		attributesTable.put("reference", new HashSet<>()); // this element doesn't have attributes

		Set<String> noteSet = new HashSet<>();
		noteSet.add("from");
		noteSet.add("priority");
		noteSet.add("annotates");
		attributesTable.put("note", noteSet);

		attributesTable.put("phase-group", new HashSet<>()); // this element doesn't have attributes

		Set<String> phaseSet = new HashSet<>();
		phaseSet.add("phase-name");
		phaseSet.add("process-name");
		phaseSet.add("company-name");
		phaseSet.add("tool");
		phaseSet.add("tool-id");
		phaseSet.add("date");
		phaseSet.add("job-id");
		phaseSet.add("contact-name");
		phaseSet.add("contact-email");
		phaseSet.add("contact-phone");
		attributesTable.put("phase", phaseSet);

		Set<String> toolSet = new HashSet<>();
		toolSet.add("tool-id");
		toolSet.add("tool-name");
		toolSet.add("tool-version");
		toolSet.add("tool-company");
		attributesTable.put("tool", toolSet);

		Set<String> countGroupSet = new HashSet<>();
		countGroupSet.add("name");
		attributesTable.put("count-group", countGroupSet);

		Set<String> countSet = new HashSet<>();
		countSet.add("count-type");
		countSet.add("phase-name");
		countSet.add("unit");
		attributesTable.put("count", countSet);

		Set<String> contextGroupSet = new HashSet<>();
		contextGroupSet.add("crc");
		contextGroupSet.add("name");
		contextGroupSet.add("purpose");
		attributesTable.put("context-group", contextGroupSet);

		Set<String> contextSet = new HashSet<>();
		contextSet.add("context-type");
		contextSet.add("match-mandatory");
		contextSet.add("crc");
		attributesTable.put("context", contextSet);

		Set<String> propGroupSet = new HashSet<>();
		propGroupSet.add("name");
		attributesTable.put("prop-group", propGroupSet);

		Set<String> propSet = new HashSet<>();
		propSet.add("prop-type");
		attributesTable.put("prop", propSet);

		attributesTable.put("body", new HashSet<>()); // this element doesn't have attributes

		Set<String> groupSet = new HashSet<>();
		groupSet.add("id");
		groupSet.add("datatype");
		groupSet.add("ts");
		groupSet.add("restype");
		groupSet.add("resname");
		groupSet.add("extradata");
		groupSet.add("help-id");
		groupSet.add("menu");
		groupSet.add("menu-option");
		groupSet.add("menu-name");
		groupSet.add("coord");
		groupSet.add("font");
		groupSet.add("css-style");
		groupSet.add("style");
		groupSet.add("exstyle");
		groupSet.add("extype");
		groupSet.add("translate");
		groupSet.add("reformat");
		groupSet.add("maxbytes");
		groupSet.add("minbytes");
		groupSet.add("size-unit");
		groupSet.add("maxheight");
		groupSet.add("minheight");
		groupSet.add("maxwidth");
		groupSet.add("minwidth");
		groupSet.add("charclass");
		if (!version.equals("1.1")) {
			groupSet.add("merged-trans");
		}
		attributesTable.put("group", groupSet);

		Set<String> transUnitSet = new HashSet<>();
		transUnitSet.add("id");
		transUnitSet.add("approved");
		transUnitSet.add("translate");
		transUnitSet.add("reformat");
		transUnitSet.add("datatype");
		transUnitSet.add("ts");
		transUnitSet.add("phase-name");
		transUnitSet.add("restype");
		transUnitSet.add("resname");
		transUnitSet.add("extradata");
		transUnitSet.add("help-id");
		transUnitSet.add("menu");
		transUnitSet.add("menu-option");
		transUnitSet.add("menu-name");
		transUnitSet.add("coord");
		transUnitSet.add("font");
		transUnitSet.add("css-style");
		transUnitSet.add("style");
		transUnitSet.add("exstyle");
		transUnitSet.add("extype");
		transUnitSet.add("maxbytes");
		transUnitSet.add("minbytes");
		transUnitSet.add("size-unit");
		transUnitSet.add("maxheight");
		transUnitSet.add("minheight");
		transUnitSet.add("maxwidth");
		transUnitSet.add("minwidth");
		transUnitSet.add("charclass");
		attributesTable.put("trans-unit", transUnitSet);

		Set<String> sourceSet = new HashSet<>();
		sourceSet.add("ts");
		attributesTable.put("source", sourceSet);

		Set<String> targetSet = new HashSet<>();
		targetSet.add("state");
		targetSet.add("state-qualifier");
		targetSet.add("phase-name");
		targetSet.add("ts");
		targetSet.add("restype");
		targetSet.add("resname");
		targetSet.add("coord");
		targetSet.add("font");
		targetSet.add("css-style");
		targetSet.add("style");
		targetSet.add("exstyle");
		if (!version.equals("1.1")) {
			targetSet.add("equiv-trans");
		}
		attributesTable.put("target", targetSet);

		Set<String> altTransSet = new HashSet<>();
		if (!version.equals("1.1")) {
			altTransSet.add("mid");
		}
		altTransSet.add("match-quality");
		altTransSet.add("tool");
		altTransSet.add("tool-id");
		altTransSet.add("crc");
		altTransSet.add("datatype");
		altTransSet.add("ts");
		altTransSet.add("restype");
		altTransSet.add("resname");
		altTransSet.add("extradata");
		altTransSet.add("help-id");
		altTransSet.add("menu");
		altTransSet.add("menu-option");
		altTransSet.add("menu-name");
		altTransSet.add("coord");
		altTransSet.add("font");
		altTransSet.add("css-style");
		altTransSet.add("style");
		altTransSet.add("exstyle");
		altTransSet.add("extype");
		altTransSet.add("origin");
		if (!version.equals("1.1")) {
			altTransSet.add("phase-name");
			altTransSet.add("alttranstype");
		}
		attributesTable.put("alt-trans", altTransSet);

		Set<String> binUnitSet = new HashSet<>();
		binUnitSet.add("id");
		binUnitSet.add("mime-type");
		binUnitSet.add("approved");
		binUnitSet.add("translate");
		binUnitSet.add("reformat");
		binUnitSet.add("ts");
		binUnitSet.add("phase-name");
		binUnitSet.add("restype");
		binUnitSet.add("resname");
		attributesTable.put("bin-unit", binUnitSet);

		Set<String> binSourceSet = new HashSet<>();
		binSourceSet.add("ts");
		attributesTable.put("bin-source", binSourceSet);

		Set<String> binTargetSet = new HashSet<>();
		binTargetSet.add("mime-type");
		binTargetSet.add("ts");
		binTargetSet.add("state");
		binTargetSet.add("phase-name");
		binTargetSet.add("restype");
		binTargetSet.add("resname");
		if (!version.equals("1.1")) {
			binTargetSet.add("state-qualifier");
		}
		attributesTable.put("bin-target", binTargetSet);

		if (!version.equals("1.1")) {
			Set<String> segSourceSet = new HashSet<>();
			segSourceSet.add("ts");
			attributesTable.put("seg-source", segSourceSet);
		}

		Set<String> gSet = new HashSet<>();
		gSet.add("id");
		gSet.add("ctype");
		gSet.add("ts");
		gSet.add("clone");
		gSet.add("xid");
		if (!version.equals("1.1")) {
			gSet.add("equiv-text");
		}
		attributesTable.put("g", gSet);

		Set<String> xSet = new HashSet<>();
		xSet.add("id");
		xSet.add("ctype");
		xSet.add("ts");
		xSet.add("clone");
		xSet.add("xid");
		if (!version.equals("1.1")) {
			xSet.add("equiv-text");
		}
		attributesTable.put("x", xSet);

		Set<String> bxSet = new HashSet<>();
		bxSet.add("id");
		bxSet.add("rid");
		bxSet.add("ctype");
		bxSet.add("ts");
		bxSet.add("clone");
		bxSet.add("xid");
		if (!version.equals("1.1")) {
			bxSet.add("equiv-text");
		}
		attributesTable.put("bx", bxSet);

		Set<String> exSet = new HashSet<>();
		exSet.add("id");
		exSet.add("rid");
		exSet.add("ts");
		exSet.add("xid");
		if (!version.equals("1.1")) {
			exSet.add("equiv-text");
		}
		attributesTable.put("ex", exSet);

		Set<String> phSet = new HashSet<>();
		phSet.add("id");
		phSet.add("ctype");
		phSet.add("ts");
		phSet.add("crc");
		phSet.add("assoc");
		phSet.add("xid");
		if (!version.equals("1.1")) {
			phSet.add("equiv-text");
		}
		attributesTable.put("ph", phSet);

		Set<String> bptSet = new HashSet<>();
		bptSet.add("id");
		bptSet.add("rid");
		bptSet.add("ctype");
		bptSet.add("ts");
		bptSet.add("crc");
		bptSet.add("xid");
		if (!version.equals("1.1")) {
			bptSet.add("equiv-text");
		}
		attributesTable.put("bpt", bptSet);

		Set<String> eptSet = new HashSet<>();
		eptSet.add("id");
		eptSet.add("rid");
		eptSet.add("ts");
		eptSet.add("crc");
		eptSet.add("xid");
		if (!version.equals("1.1")) {
			eptSet.add("equiv-text");
		}
		attributesTable.put("ept", eptSet);

		Set<String> itSet = new HashSet<>();
		itSet.add("id");
		itSet.add("pos");
		itSet.add("rid");
		itSet.add("ctype");
		itSet.add("ts");
		itSet.add("crc");
		itSet.add("xid");
		if (!version.equals("1.1")) {
			itSet.add("equiv-text");
		}
		attributesTable.put("it", itSet);

		Set<String> subSet = new HashSet<>();
		subSet.add("datatype");
		subSet.add("ctype");
		subSet.add("xid");
		attributesTable.put("sub", subSet);

		Set<String> mrkSet = new HashSet<>();
		mrkSet.add("mtype");
		mrkSet.add("mid");
		mrkSet.add("ts");
		mrkSet.add("comment");
		attributesTable.put("mrk", mrkSet);
	}

	public boolean recurse(Element e) {
		if (!e.getNamespace().isEmpty()) {
			// ignore non-XLIFF elements
			return true;
		}
		String namespace = e.getAttributeValue("xmlns");
		if (!namespace.isEmpty() && !xliffNamespaces.contains(namespace)) {
			// ignore element from another namespace
			return true;
		}

		if (!"1.0".equals(version)) {
			// validate the attributes (the parser can't do it due to bugs in the schemas
			List<Attribute> atts = e.getAttributes();
			for (int i = 0; i < atts.size(); i++) {
				Attribute att = atts.get(i);
				if ("xmlns".equals(att.getLocalName()) || !att.getNamespace().isEmpty()) {
					// attribute from XML standard or from another namespace
					continue;
				}
				Set<String> set = attributesTable.get(e.getLocalName());
				if (set != null && !set.contains(att.getLocalName())) {
					MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.11"));
					reason = mf.format(new Object[] { e.getName(), att.getName() });
					return false;
				}
			}
		}
		// "date" attributes must be in ISO 8601 format
		if (e.getLocalName().equals("file") || e.getLocalName().equals("phase")) {
			String date = e.getAttributeValue("date");
			if (!date.isEmpty() && !checkDate(date)) {
				MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.12"));
				reason = mf.format(new Object[] { date });
				return false;
			}
		}

		// external files should be resolvable resources
		if (e.getLocalName().equals("external-file") && !checkURL(e.getAttributeValue("href"))) {
			MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.13"));
			reason = mf.format(new Object[] { e.getAttribute("href") });
			return false;
		}

		// source file should be resolvable resources
		if (e.getLocalName().equals("file") && !checkURL(e.getAttributeValue("original"))) {
			MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.14"));
			reason = mf.format(new Object[] { e.getAttribute("original") });
			return false;
		}

		// language codes should be valid ISO codes
		if (e.getLocalName().equals("xliff") || e.getLocalName().equals("note") || e.getLocalName().equals("prop")
				|| e.getLocalName().equals("source") || e.getLocalName().equals("target")
				|| e.getLocalName().equals("alt-trans") || e.getLocalName().equals("seg-source")) {
			String lang = e.getAttributeValue("xml:lang");
			if (!lang.isEmpty() && !checkLanguage(lang)) {
				MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.15"));
				reason = mf.format(new Object[] { lang });
				return false;
			}
		}

		if (e.getLocalName().equals("file")) {
			// create tables to make sure that "id" attributes are unique within the <file>
			ids = new ConcurrentHashMap<>();
			groupIds = new ConcurrentHashMap<>();

			// create tables to check that <it> tags have both start and end positions
			srcItTable = new ConcurrentHashMap<>();
			tgtItTable = new ConcurrentHashMap<>();

			// create table to check if "xid" points to valid <trans-unit>
			xids = new ConcurrentHashMap<>();

			// check <phase> and <tool> elements
			phasesTable = new ConcurrentHashMap<>();
			toolsTable = new ConcurrentHashMap<>();

			// check language codes used
			sourceLanguage = e.getAttributeValue("source-language");
			if (!checkLanguage(sourceLanguage)) {
				MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.16"));
				reason = mf.format(new Object[] { sourceLanguage });
				return false;
			}
			targetLanguage = e.getAttributeValue("target-language");
			if (!targetLanguage.isEmpty() && !checkLanguage(targetLanguage)) {
				MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.17"));
				reason = mf.format(new Object[] { targetLanguage });
				return false;
			}
		}

		// store phase name
		if (e.getLocalName().equals("phase")) {
			String name = e.getAttributeValue("phase-name");
			if (!phasesTable.containsKey(name)) {
				phasesTable.put(name, "");
			} else {
				MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.18"));
				reason = mf.format(new Object[] { name });
				return false;
			}
		}

		// store tool-id
		if (e.getLocalName().equals("tool")) {
			String id = e.getAttributeValue("tool-id");
			if (!toolsTable.containsKey(id)) {
				toolsTable.put(id, "");
			} else {
				MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.19"));
				reason = mf.format(new Object[] { id });
				return false;
			}
		}

		if (e.getLocalName().equals("alt-trans")) {
			// language codes in <alt-trans> are independent from those declared in <file>
			inAltTrans = true;

			// check for valid segment reference
			String mid = e.getAttributeValue("mid");
			if (!mid.isEmpty() && !midTable.containsKey(mid)) {
				reason = Messages.getString("XliffChecker.20");
				return false;
			}

			// check for declared <tool>
			String tool = e.getAttributeValue("tool-id");
			if (!tool.isEmpty() && !toolsTable.containsKey(tool)) {
				reason = Messages.getString("XliffChecker.21");
				return false;
			}
			// create tables to check if <it> tags are duplicated
			altSrcItTable = new ConcurrentHashMap<>();
			altTgtItTable = new ConcurrentHashMap<>();
		}

		// validate "phase-name" attribute
		if (e.getLocalName().equals("count") || e.getLocalName().equals("trans-unit")
				|| e.getLocalName().equals("bin-unit") || e.getLocalName().equals("target")
				|| e.getLocalName().equals("bin-target") || e.getLocalName().equals("alt-trans")) {
			String phase = e.getAttributeValue("phase-name");
			if (!phase.isEmpty() && !phasesTable.containsKey(phase)) {
				reason = Messages.getString("XliffChecker.22");
				return false;
			}
		}

		// check language code in <source> and <target>
		if (e.getLocalName().equals("source") && !inAltTrans) {
			String lang = e.getAttributeValue("xml:lang");
			if (!lang.isEmpty() && !lang.equalsIgnoreCase(sourceLanguage)) {
				MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.23"));
				reason = mf.format(new Object[] { lang });
				return false;
			}
		}
		if (e.getLocalName().equals("target") && !inAltTrans) {
			String lang = e.getAttributeValue("xml:lang");
			if (targetLanguage.isEmpty() && lang.isEmpty()) {
				// Missing target language code
				// bad practice, but legal
			}
			if (!targetLanguage.isEmpty() && !lang.isEmpty() && !lang.equalsIgnoreCase(targetLanguage)) {
				MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.24"));
				reason = mf.format(new Object[] { lang });
				return false;
			}
		}

		// check for unique "id" in <trans-unit> and <bin-unit>
		if (version.equals("1.2") && (e.getLocalName().equals("trans-unit") || e.getLocalName().equals("bin-unit"))) {
			String id = e.getAttributeValue("id");
			if (!ids.containsKey(id)) {
				ids.put(id, "");
			} else {
				MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.25"));
				reason = mf.format(new Object[] { id });
				return false;
			}
		}

		// initialize table for checking "mid" attribute in <alt-trans>
		if (e.getLocalName().equals("trans-unit")) {
			midTable = new ConcurrentHashMap<>();
		}

		if (e.getLocalName().equals("seg-source")) {
			// entering <seg-source>
			inSegSource = true;
		}

		// check segment ids
		if (e.getLocalName().equals("mrk") && e.getAttributeValue("mtype").equals("seg")) {
			String mid = e.getAttributeValue("mid");
			if (inSegSource) {
				if (!midTable.containsKey(mid)) {
					midTable.put(mid, "");
				} else {
					MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.26"));
					reason = mf.format(new Object[] { mid });
					return false;
				}
			} else {
				// in <target>
				if (!midTable.containsKey(mid)) {
					reason = Messages.getString("XliffChecker.27");
					return false;
				}
			}
		}

		// check for unique "id" in <group>
		if (e.getLocalName().equals("group") && version.equals("1.2")) {
			String id = e.getAttributeValue("id");
			if (!id.isEmpty()) {
				if (!groupIds.containsKey(id)) {
					groupIds.put(id, "");
				} else {
					MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.28"));
					reason = mf.format(new Object[] { id });
					return false;
				}
			}
		}

		// initialize tables for checking matched pairs of inline elements
		if (e.getLocalName().equals("source") || e.getLocalName().equals("seg-source")
				|| e.getLocalName().equals("target")) {
			bxTable = new ConcurrentHashMap<>();
			exTable = new ConcurrentHashMap<>();
			bptTable = new ConcurrentHashMap<>();
			eptTable = new ConcurrentHashMap<>();
		}

		// check for paired <it> tags in <file>
		if (e.getLocalName().equals("it") && !inAltTrans) {
			String id = e.getAttributeValue("id");
			String pos = e.getAttributeValue("pos");
			if (inSource) {
				if (!srcItTable.containsKey(id)) {
					srcItTable.put(id, pos);
				} else {
					if (!srcItTable.get(id).equals(pos)) {
						// matched
						srcItTable.remove(id);
					} else {
						// duplicated
						reason = Messages.getString("XliffChecker.29");
						return false;
					}
				}
			} else {
				if (!tgtItTable.containsKey(id)) {
					tgtItTable.put(id, pos);
				} else {
					if (!tgtItTable.get(id).equals(pos)) {
						// matched
						tgtItTable.remove(id);
					} else {
						// duplicated
						reason = Messages.getString("XliffChecker.30");
						return false;
					}
				}
			}
		}
		// check for duplicated <it> tags in <alt-trans>
		if (e.getLocalName().equals("it") && inAltTrans) {
			String id = e.getAttributeValue("id");
			String pos = e.getAttributeValue("pos");
			if (inSource) {
				if (!altSrcItTable.containsKey(id)) {
					altSrcItTable.put(id, pos);
				} else {
					if (altSrcItTable.get(id).equals(pos)) {
						// duplicated
						reason = Messages.getString("XliffChecker.31");
						return false;
					}
				}
			} else {
				if (!altTgtItTable.containsKey(id)) {
					altTgtItTable.put(id, pos);
				} else {
					if (altTgtItTable.get(id).equals(pos)) {
						// duplicated
						reason = Messages.getString("XliffChecker.32");
						return false;
					}
				}
			}
		}
		// check if "xid" attribute points to a valid <trans-unit> or <bin-unit>
		if (e.getLocalName().equals("ph") || e.getLocalName().equals("it") || e.getLocalName().equals("sub")
				|| e.getLocalName().equals("bx") || e.getLocalName().equals("ex") || e.getLocalName().equals("bpt")
				|| e.getLocalName().equals("ept") || e.getLocalName().equals("g") || e.getLocalName().equals("x")) {
			String xid = e.getAttributeValue("xid");
			if (!xid.isEmpty()) {
				xids.put(xid, "");
			}
		}

		if (e.getLocalName().equals("bx")) {
			String id = e.getAttributeValue("rid");
			if (id.isEmpty()) {
				id = e.getAttributeValue("id");
			}
			if (id.isEmpty()) {
				reason = Messages.getString("XliffChecker.33");
				return false;
			}
			bxTable.put(id, "");
		}
		if (e.getLocalName().equals("ex")) {
			String id = e.getAttributeValue("rid");
			if (id.isEmpty()) {
				id = e.getAttributeValue("id");
			}
			if (id.isEmpty()) {
				reason = Messages.getString("XliffChecker.34");
				return false;
			}
			exTable.put(id, "");
		}
		if (e.getLocalName().equals("bpt")) {
			String id = e.getAttributeValue("rid");
			if (id.isEmpty()) {
				id = e.getAttributeValue("id");
			}
			if (id.isEmpty()) {
				reason = Messages.getString("XliffChecker.35");
				return false;
			}
			bptTable.put(id, "");
		}
		if (e.getLocalName().equals("ept")) {
			String id = e.getAttributeValue("rid");
			if (id.isEmpty()) {
				id = e.getAttributeValue("id");
			}
			if (id.isEmpty()) {
				reason = Messages.getString("XliffChecker.36");
				return false;
			}
			eptTable.put(id, "");
		}

		// Recurse all children
		List<Element> children = e.getChildren();
		for (int i = 0; i < children.size(); i++) {
			boolean result = recurse(children.get(i));
			if (!result) {
				return false;
			}
		}

		// check if inline tags are paired at <source>, <seg-source> and <target>
		if (e.getLocalName().equals("source") || e.getLocalName().equals("seg-source")
				|| e.getLocalName().equals("target")) {
			Set<String> keys = bxTable.keySet();
			Iterator<String> it = keys.iterator();
			while (it.hasNext()) {
				String key = it.next();
				if (exTable.containsKey(key)) {
					exTable.remove(key);
					bxTable.remove(key);
				}
			}

			if (exTable.size() > 0 || bxTable.size() > 0) {
				MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.37"));
				reason = mf.format(new Object[] { e.getLocalName() });
				return false;
			}
			keys = bptTable.keySet();
			it = keys.iterator();
			while (it.hasNext()) {
				String key = it.next();
				if (eptTable.containsKey(key)) {
					eptTable.remove(key);
					bptTable.remove(key);
				}
			}
			if (eptTable.size() > 0 || bptTable.size() > 0) {
				MessageFormat mf = new MessageFormat(Messages.getString("XliffChecker.38"));
				reason = mf.format(new Object[] { e.getLocalName() });
				return false;
			}

		}

		// check for not paired <it> tags in <file>
		if (e.getLocalName().equals("file") && srcItTable.size() + tgtItTable.size() > 0) {
			reason = Messages.getString("XliffChecker.39");
			return false;
		}

		// check for missing <trans-unit> referenced in <sub>
		if (e.getLocalName().equals("file")) {
			Set<String> keys = xids.keySet();
			Iterator<String> it = keys.iterator();
			while (it.hasNext()) {
				if (!ids.containsKey(it.next())) {
					reason = Messages.getString("XliffChecker.40");
					return false;
				}
			}
		}

		if (e.getLocalName().equals("alt-trans")) {
			// leaving an <alt-trans> element
			inAltTrans = false;
		}

		if (e.getLocalName().equals("seg-source")) {
			// leaving <seg-source>
			inSegSource = false;
		}

		if (e.getLocalName().equals("source")) {
			// leaving <source>
			inSource = false;
		}

		// all seems fine so far
		return true;
	}

	private static boolean checkDate(String date) {
		// YYYY-MM-DDThh:mm:ssZ
		if (date.length() != 20) {
			return false;
		}
		if (date.charAt(4) != '-') {
			return false;
		}
		if (date.charAt(7) != '-') {
			return false;
		}
		if (date.charAt(10) != 'T') {
			return false;
		}
		if (date.charAt(13) != ':') {
			return false;
		}
		if (date.charAt(16) != ':') {
			return false;
		}
		if (date.charAt(19) != 'Z') {
			return false;
		}
		try {
			int year = Integer.parseInt("" + date.charAt(0) + date.charAt(1) + date.charAt(2) + date.charAt(3));
			if (year < 0) {
				return false;
			}
			int month = Integer.parseInt("" + date.charAt(5) + date.charAt(6));
			if (month < 1 || month > 12) {
				return false;
			}
			int day = Integer.parseInt("" + date.charAt(8) + date.charAt(9));
			switch (month) {
				case 1, 3, 5, 7, 8, 10, 12:
					if (day < 1 || day > 31) {
						return false;
					}
					break;
				case 4, 6, 9, 11:
					if (day < 1 || day > 30) {
						return false;
					}
					break;
				case 2:
					// check for leap years
					if (year % 4 == 0) {
						if (year % 100 == 0) {
							// not all centuries are leap years
							if (year % 400 == 0) {
								if (day < 1 || day > 29) {
									return false;
								}
							} else {
								// not leap year
								if (day < 1 || day > 28) {
									return false;
								}
							}
						}
						if (day < 1 || day > 29) {
							return false;
						}
					} else if (day < 1 || day > 28) {
						return false;
					}
					break;
				default:
					// wrong month
					return false;
			}
			int hour = Integer.parseInt("" + date.charAt(11) + date.charAt(12));
			if (hour < 0 || hour > 23) {
				return false;
			}
			int min = Integer.parseInt("" + date.charAt(14) + date.charAt(15));
			if (min < 0 || min > 59) {
				return false;
			}
			int sec = Integer.parseInt("" + date.charAt(17) + date.charAt(18));
			if (sec < 0 || sec > 59) {
				return false;
			}
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	private static boolean checkURL(String string) {
		try {
			new URI(string).toURL();
			return true;
		} catch (Exception e1) {
			try {
				new File(string);
				return true;
			} catch (Exception e2) {
				return false;
			}
		}
	}

	private boolean checkLanguage(String lang) {
		if (lang.isBlank()) {
			return false;
		}
		if (lang.toLowerCase().startsWith("x-") && !lang.endsWith("-")) {
			// custom language code
			return true;
		}
		if (lang.toLowerCase().indexOf("-x-") > 1 && !lang.endsWith("-")) {
			// private use tag, e.g. "en-x-abc"
			return true;
		}
		return lang.equalsIgnoreCase(registry.normalizeCode(lang));
	}

}
