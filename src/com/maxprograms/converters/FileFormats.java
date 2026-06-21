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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.maxprograms.xml.Document;
import com.maxprograms.xml.SAXBuilder;

public class FileFormats {

	private static Logger logger = System.getLogger(FileFormats.class.getName());

	private FileFormats() {
		// do not instantiate this class
	}

	public static final String INX = "Adobe InDesign Interchange";
	public static final String ICML = "Adobe InCopy ICML";
	public static final String IDML = "Adobe InDesign IDML";
	public static final String DITA = "DITA Map";
	public static final String HTML = "HTML Page";
	public static final String JS = "JavaScript";
	public static final String JSON = "JSON";
	public static final String JAVA = "Java Properties";
	public static final String MIF = "MIF (Maker Interchange Format)";
	public static final String OFF = "Microsoft Office 2007 Document";
	public static final String OO = "OpenOffice Document";
	public static final String TEXT = "Plain Text";
	public static final String PHPA = "PHP Array";
	public static final String PO = "PO (Portable Objects)";
	public static final String QTI = "QTI (IMS Question and Test Interoperability)";
	public static final String QTIP = "QTI Package";
	public static final String RC = "RC (Windows C/C++ Resources)";
	public static final String RESX = "ResX (Windows .NET Resources)";
	public static final String SDLPPX = "Trados Studio Package";
	public static final String SDLXLIFF = "SDLXLIFF Document";
	public static final String SRT = "SRT Subtitle";
	public static final String TS = "TS (Qt Linguist translation source)";
	public static final String TXLF = "Wordfast/GlobalLink XLIFF";
	public static final String TXML = "TXML Document";
	public static final String WPML = "WPML XLIFF";
	public static final String XLIFF = "XLIFF Document";
	public static final String XML = "XML Document";
	public static final String XMLG = "XML (Generic)";

	protected static final String[] formats = { INX, ICML, IDML, DITA, HTML, JS, JSON, JAVA, MIF, OFF, OO, TEXT, PHPA,
			PO, QTI, QTIP, RC, RESX, SDLPPX, SDLXLIFF, SRT, TS, TXML, TXLF, WPML, XLIFF, XML, XMLG };

	public static boolean isBilingual(String type) {
		return List.of(PO, SDLPPX, SDLXLIFF, TS, TXML, TXLF, WPML, XLIFF).contains(type);
	}

	public static void main(String[] args) {
		String[] arguments = Utils.fixPath(args);
		String file = "";
		boolean list = false;
		for (int i = 0; i < arguments.length; i++) {
			String arg = arguments[i];
			if (arg.equals("-file") && (i + 1) < arguments.length) {
				file = arguments[i + 1];
			}
			if (arg.equals("-lang") && (i + 1) < arguments.length) {
				Locale.setDefault(Locale.forLanguageTag(arguments[i + 1]));
			}
			if (arg.equals("-list")) {
				list = true;
			}
		}
		if (list) {
			JSONArray result = new JSONArray();
			for (String format : formats) {
				JSONObject obj = new JSONObject();
				obj.put("type", getShortName(format));
				obj.put("description", getLocalizedName(format));
				result.put(obj);
			}
			System.out.println(result.toString());
			return;
		}
		if (file.isEmpty()) {
			logger.log(Level.ERROR, Messages.getString("FileFormats.3"));
			return;
		}
		File sourceFile = new File(file);
		if (!sourceFile.exists()) {
			logger.log(Level.ERROR, Messages.getString("FileFormats.4"));
			return;
		}
		if (!sourceFile.isAbsolute()) {
			file = sourceFile.getAbsoluteFile().getAbsolutePath();
		}
		String format = detectFormat(file);
		JSONObject result = new JSONObject();
		if (format != null) {
			result.put("format", getShortName(format));
		} else {
			result.put("format", "Unknown");
		}
		System.out.println(result.toString());
	}

	public static String detectFormat(String fileName) {
		File file = new File(fileName);
		if (!file.exists()) {
			return null;
		}
		try {
			byte[] array = new byte[40960];
			try (FileInputStream input = new FileInputStream(file)) {
				if (input.read(array) == -1) {
					throw new IOException(Messages.getString("FileFormats.1"));
				}
			}
			String string = "";

			Charset bom = EncodingResolver.getBOM(fileName);
			if (bom != null) {
				byte[] efbbbf = { -17, -69, -65 }; // UTF-8
				String utf8 = new String(efbbbf);
				string = new String(array, bom);
				if (string.startsWith("\uFFFE")) {
					string = string.substring("\uFFFE".length());
				} else if (string.startsWith("\uFEFF")) {
					string = string.substring("\uFEFF".length());
				} else if (string.startsWith(utf8)) {
					string = string.substring(utf8.length());
				}
			} else {
				string = new String(array);
			}

			if (string.startsWith("<MIFFile")) {
				return MIF;
			}
			if (string.indexOf("=\"http://www.imsglobal.org") != -1 && string.indexOf("<xliff ") == -1) {
				return QTI;
			}
			if (string.indexOf("<xliff ") != -1 && string.indexOf("xmlns:sdl") != -1) {
				return SDLXLIFF;
			}
			if (string.indexOf("<xliff ") != -1 && string.indexOf("xmlns:gs4tr") != -1) {
				return TXLF;
			}
			if (string.indexOf("<xliff ") != -1 && string.indexOf("<![CDATA[") != -1) {
				return WPML;
			}
			if (string.indexOf("<xliff ") != -1 && parseXliff(fileName)) {
				return XLIFF;
			}
			if (string.startsWith("<?php")) {
				return PHPA;
			}
			if (string.startsWith("<?xml")) {
				if (string.indexOf("<txml ") != -1) {
					return TXML;
				}
				if (string.indexOf("<docu") != -1 && string.indexOf("<?aid ") != -1) {
					return INX;
				}
				if (string.indexOf("xmlns:msdata") != -1 && string.indexOf("<root") != -1) {
					return RESX;
				}
				if (string.indexOf("!DOCTYPE TS>") != -1) {
					return TS;
				}
				if (string.indexOf("<?xml-model") != -1 && string.indexOf("tc:dita:rng") != -1) {
					return DITA;
				}
				if (string.indexOf("<map") != -1 || string.indexOf("<bookmap") != -1
						|| string.indexOf("<!DOCTYPE map") != -1 || string.indexOf("<!DOCTYPE bookmap ") != -1) {
					return DITA;
				}
				if (string.indexOf("<?aid ") != -1 || string.indexOf("<Document ") != -1) {
					return ICML;
				}
				return XML;
			}
			if (string.startsWith("<svg")) {
				return XML;
			}
			if (string.indexOf("<!DOCTYPE TS>") != -1) {
				return TS;
			}
			if (string.startsWith("<!DOCTYPE")) {
				int index = string.indexOf("-//IETF//DTD HTML");
				if (index != -1) {
					return HTML;
				}
				index = string.indexOf("-//W3C//DTD HTML");
				if (index != -1) {
					return HTML;
				}
				index = string.indexOf(" html");
				if (index != -1) {
					return HTML;
				}
				return XML;
			}
			if (string.indexOf("msgid") != -1 && string.indexOf("msgstr") != -1) {
				return PO;
			}
			int index = string.toLowerCase().indexOf("<html");
			if (index != -1) {
				return HTML;
			}

			if (string.startsWith("PK")) {
				// might be a zipped file
				boolean openOffice = false;
				boolean idml = false;
				boolean sdlppx = false;
				boolean qtip = false;
				boolean hascontentTypes = false;
				try (ZipInputStream in = new ZipInputStream(new FileInputStream(file))) {
					ZipEntry entry = null;
					while ((entry = in.getNextEntry()) != null) {
						if (entry.getName().equals("content.xml")) {
							openOffice = true;
							break;
						}
						if (entry.getName().equals("designmap.xml")) {
							idml = true;
							break;
						}
						if (entry.getName().endsWith((".sdlproj"))) {
							sdlppx = true;
							break;
						}
						if (entry.getName().equals("imsmanifest.xml")) {
							qtip = true;
							break;
						}
						if (entry.getName().equals("[Content_Types].xml")) {
							hascontentTypes = true;
							break;
						}
					}
				}
				if (idml) {
					return IDML;
				}
				if (openOffice) {
					return OO;
				}
				if (sdlppx) {
					return SDLPPX;
				}
				if (hascontentTypes) {
					return OFF;
				}
				if (qtip) {
					return QTIP;
				}
			}
			if (string.indexOf("#include") != -1 || string.indexOf("#define") != -1 || string.indexOf("DIALOG") != -1
					|| string.indexOf("DIALOGEX") != -1 || string.indexOf("MENU") != -1
					|| string.indexOf("MENUEX") != -1 || string.indexOf("POPUP") != -1
					|| string.indexOf("STRINGTABLE") != -1 || string.indexOf("AUTO3STATE") != -1
					|| string.indexOf("AUTOCHECKBOX") != -1 || string.indexOf("AUTORADIOBUTTON") != -1
					|| string.indexOf("CHECKBOX") != -1 || string.indexOf("COMBOBOX") != -1
					|| string.indexOf("CONTROL") != -1 || string.indexOf("CTEXT") != -1
					|| string.indexOf("DEFPUSHBUTTON") != -1 || string.indexOf("GROUPBOX") != -1
					|| string.indexOf("ICON") != -1 || string.indexOf("LISTBOX") != -1 || string.indexOf("LTEXT") != -1
					|| string.indexOf("PUSHBOX") != -1 || string.indexOf("PUSHBUTTON") != -1
					|| string.indexOf("RADIOBUTTON") != -1 || string.indexOf("RTEXT") != -1
					|| string.indexOf("SCROLLBAR") != -1 || string.indexOf("STATE3") != -1) {
				return RC;
			}
			if (string.charAt(0) == '<') {
				SAXBuilder builder = new SAXBuilder();
				builder.setValidating(false);
				builder.build(file);
				return XML;
			}
			if (string.indexOf(" --> ") != -1 && string.indexOf(':') != -1) {
				return SRT;
			}
			if ((string.indexOf('{') != -1 && string.indexOf(':') != -1) || string.indexOf('[') != -1) {
				loadJSON(file, bom);
				return JSON;
			}
		} catch (Exception _) {
			// do nothing
		}
		if (fileName.endsWith(".properties")) {
			return JAVA;
		}
		if (fileName.toLowerCase().endsWith(".rc")) {
			return RC;
		}
		return null;
	}

	public static String getLocalizedName(String type) {
		MessageFormat mf = new MessageFormat("FileFormats.{0}");
		return Messages.getString(mf.format(new String[] { getShortName(type) }));
	}

	public static String getShortName(String type) {
		if (type == null) {
			return null;
		}
		if (type.equals(INX)) {
			return "INX";
		}
		if (type.equals(ICML)) {
			return "ICML";
		}
		if (type.equals(IDML)) {
			return "IDML";
		}
		if (type.equals(DITA)) {
			return "DITA";
		}
		if (type.equals(HTML)) {
			return "HTML";
		}
		if (type.equals(JS)) {
			return "JS";
		}
		if (type.equals(JSON)) {
			return "JSON";
		}
		if (type.equals(JAVA)) {
			return "JAVA";
		}
		if (type.equals(MIF)) {
			return "MIF";
		}
		if (type.equals(OFF)) {
			return "OFF";
		}
		if (type.equals(OO)) {
			return "OO";
		}
		if (type.equals(QTI)) {
			return "QTI";
		}
		if (type.equals(QTIP)) {
			return "QTIP";
		}
		if (type.equals(TEXT)) {
			return "TEXT";
		}
		if (type.equals(PHPA)) {
			return "PHPA";
		}
		if (type.equals(PO)) {
			return "PO";
		}
		if (type.equals(RC)) {
			return "RC";
		}
		if (type.equals(RESX)) {
			return "RESX";
		}
		if (type.equals(SDLPPX)) {
			return "SDLPPX";
		}
		if (type.equals(SDLXLIFF)) {
			return "SDLXLIFF";
		}
		if (type.equals(SRT)) {
			return "SRT";
		}
		if (type.equals(TS)) {
			return "TS";
		}
		if (type.equals(TXML)) {
			return "TXML";
		}
		if (type.equals(TXLF)) {
			return "TXLF";
		}
		if (type.equals(WPML)) {
			return "WPML";
		}
		if (type.equals(XLIFF)) {
			return "XLIFF";
		}
		if (type.equals(XML)) {
			return "XML";
		}
		if (type.equals(XMLG)) {
			return "XMLG";
		}
		return null;
	}

	public static String getFullName(String dataType) {
		if (dataType.equals("INX") || dataType.equals("x-inx")) {
			return INX;
		} else if (dataType.equals("ICML") || dataType.equals("x-icml")) {
			return ICML;
		} else if (dataType.equals("IDML") || dataType.equals("x-idml")) {
			return IDML;
		} else if (dataType.equals("DITA") || dataType.equals("x-ditamap")) {
			return DITA;
		} else if (dataType.equals("HTML") || dataType.equals("html")) {
			return HTML;
		} else if (dataType.equals("JS") || dataType.equals("javascript")) {
			return JS;
		} else if (dataType.equals("JSON") || dataType.equals("json")) {
			return JSON;
		} else if (dataType.equals("JAVA") || dataType.equals("javapropertyresourcebundle")
				|| dataType.equals("javalistresourcebundle")) {
			return JAVA;
		} else if (dataType.equals("MIF") || dataType.equals("mif")) {
			return MIF;
		} else if (dataType.equals("OFF") || dataType.equals("x-office")) {
			return OFF;
		} else if (dataType.equals("OO")) {
			return OO;
		} else if (dataType.equals("QTI")) {
			return QTI;
		} else if (dataType.equals("QTIP") || dataType.equals("x-qtipackage")) {
			return QTIP;
		} else if (dataType.equals("TEXT") || dataType.equals("plaintext")) {
			return TEXT;
		} else if (dataType.equals("PHPA") || dataType.equals("x-phparray")) {
			return PHPA;
		} else if (dataType.equals("PO") || dataType.equals("po")) {
			return PO;
		} else if (dataType.equals("RC") || dataType.equals("winres")) {
			return RC;
		} else if (dataType.equals("RESX") || dataType.equals("resx")) {
			return RESX;
		} else if (dataType.equals("SDLPPX") || dataType.equals("x-sdlpackage")) {
			return SDLPPX;
		} else if (dataType.equals("SDLXLIFF") || dataType.equals("x-sdlxliff")) {
			return SDLXLIFF;
		} else if (dataType.equals("SRT") || dataType.equals("x-srt")) {
			return SRT;
		} else if (dataType.equals("TS") || dataType.equals("x-ts")) {
			return TS;
		} else if (dataType.equals("TXML") || dataType.equals("x-txml")) {
			return TXML;
		} else if (dataType.equals("TXLF") || dataType.equals("x-txlf")) {
			return TXLF;
		} else if (dataType.equals("WPML") || dataType.equals("x-wpmlxliff")) {
			return WPML;
		} else if (dataType.equals("XLIFF") || dataType.equals("x-xliff")) {
			return XLIFF;
		} else if (dataType.equals("XML") || dataType.equals("xml")) {
			return XML;
		} else if (dataType.equals("XMLG")) {
			return XMLG;
		}
		return null;
	}

	private static boolean parseXliff(String file) {
		try {
			SAXBuilder builder = new SAXBuilder();
			Document doc = builder.build(file);
			return doc.getRootElement().getName().equals("xliff");
		} catch (SAXException | IOException | ParserConfigurationException e) {
			return false;
		}
	}

	public static String[] getFormats() {
		return formats;
	}

	private static Object loadJSON(File file, Charset charset) throws IOException, JSONException {
		StringBuilder sb = new StringBuilder();
		int bomLength = charset == null ? 0 : 1;
		if (charset == null) {
			charset = StandardCharsets.UTF_8;
		}
		try (FileReader reader = new FileReader(file, charset)) {
			try (BufferedReader buffered = new BufferedReader(reader)) {
				String line = "";
				boolean first = true;
				while ((line = buffered.readLine()) != null) {
					if (!first) {
						sb.append('\n');
					}
					sb.append(line);
					first = false;
				}
			}
		}
		for (int i = bomLength; i < sb.length(); i++) {
			if (sb.charAt(i) == '[') {
				return new JSONArray(sb.toString().substring(bomLength));
			}
			if (sb.charAt(i) == '{') {
				return new JSONObject(sb.toString().substring(bomLength));
			}
			if (!Character.isSpaceChar(sb.charAt(i))) {
				break;
			}
		}
		throw new IOException(Messages.getString("FileFormats.2"));
	}
}
