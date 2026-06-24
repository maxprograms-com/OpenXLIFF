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
package com.maxprograms.converters.po;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.XMLUtils;

public class Po2Xliff {

	private FileOutputStream output;
	private FileOutputStream skeleton;

	private String source;
	private String target;
	private String comment;
	private String context;
	private String reference;
	private String flags;
	private boolean fuzzy;
	private boolean cformat;

	private int segId;
	private int domainId;
	private int contextId = 1;
	private int refId = 1;
	private String newContext;
	private List<String> pluralTargets;
	private int plurals;
	private String pluralSource;

	private Po2Xliff() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		return new Po2Xliff().convert(params);
	}

	private List<String> convert(Map<String, String> params) {
		List<String> result = new ArrayList<>();

		String inputFile = params.get("source");
		String xliffFile = params.get("xliff");
		String skeletonFile = params.get("skeleton");
		String sourceLanguage = params.get("srcLang");
		String targetLanguage = params.get("tgtLang");
		String srcEncoding = params.get("srcEncoding");
		String tgtLang = "";
		if (targetLanguage != null) {
			tgtLang = "\" target-language=\"" + targetLanguage;
		}

		source = "";
		pluralSource = "";
		pluralTargets = new ArrayList<>();
		target = "";
		comment = "";
		context = "";
		reference = "";
		flags = "";
		newContext = "";
		fuzzy = false;
		boolean inDomain = false;
		cformat = false;

		try {
			try (FileInputStream stream = new FileInputStream(inputFile)) {
				try (InputStreamReader input = new InputStreamReader(stream, srcEncoding)) {
					BufferedReader buffer = new BufferedReader(input);

					output = new FileOutputStream(xliffFile);

					writeString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
					writeString("<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" "
							+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
							+ "xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");

					writeString("<file original=\"" + inputFile + "\" source-language=\"" + sourceLanguage + tgtLang
							+ "\" tool-id=\"" + Constants.TOOLID + "\" datatype=\"po\">\n");
					writeString("<header>\n");
					writeString("   <skl>\n");
					writeString("      <external-file href=\"" + XMLUtils.cleanText(skeletonFile) + "\"/>\n");
					writeString("   </skl>\n");
					writeString("   <tool tool-version=\"" + Constants.VERSION + " " + Constants.BUILD + "\" tool-id=\""
							+ Constants.TOOLID + "\" tool-name=\"" + Constants.TOOLNAME + "\"/>\n");
					writeString("</header>\n");
					writeString("<?encoding " + srcEncoding + "?>\n");
					writeString("<body>\n");

					skeleton = new FileOutputStream(skeletonFile);

					String line = buffer.readLine();
					while (line != null) {
						line = line + "\n";

						if (line.isBlank()) {
							// no text in this line
							// segment separator
							writeSkeleton(line);
						} else {
							if (line.startsWith("#:")) {
								// it is a reference
								if (reference.isEmpty()) {
									reference = line.substring(2);
								} else {
									reference = reference + " " + line.substring(2);
								}
							}
							if (line.startsWith("# ")) {
								// translator comment
								comment = comment + line.substring(2);
							}
							if (line.trim().equals("#")) {
								comment = comment + "\n";
							}
							if (line.startsWith("#.")) {
								// automatic comment
								context = context + line.substring(2);
							}
							if (line.startsWith("#,")) {
								flags = line.substring(2);
								// check for fuzzy
								if (flags.indexOf("fuzzy") != -1) {
									fuzzy = true;
								}
								// Only c-format is parsed. Tags from other
								// formats, like php-format or python-format,
								// are left as part of the text
								if (flags.indexOf("c-format") != -1 && flags.indexOf("no-c-format") == -1) {
									cformat = true;
								}
							}
							if (line.startsWith("#~")) {
								// commented entry
								writeSkeleton(line);
							}
							if (line.startsWith("msgctxt")) {
								// get context text
								line = line.substring(5);
								newContext = line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
								line = buffer.readLine();
								while (line.startsWith("\"")) {
									newContext = newContext + "\n"
											+ line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
									line = buffer.readLine();
								}
								continue;
							}
							if (line.startsWith("msgid")) {
								if (line.startsWith("msgid_plural")) {
									// get plural source
									line = line.substring(12);
									pluralSource = line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
									line = buffer.readLine();
									while (line.startsWith("\"")) {
										pluralSource = pluralSource + "\n"
												+ line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
										line = buffer.readLine();
									}
								} else {
									// get source text
									line = line.substring(5);
									source = line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
									line = buffer.readLine();
									while (line.trim().startsWith("\"")) {
										source = source + "\n"
												+ line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
										line = buffer.readLine();
									}
								}
								continue;
							}
							if (line.startsWith("msgstr")) {
								if (line.startsWith("msgstr[")) {
									while (line.startsWith("msgstr[")) {
										// get all plural targets
										line = line.substring(line.indexOf(']') + 1);
										String pluralTarget = line.substring(line.indexOf('\"') + 1,
												line.lastIndexOf('\"'));
										line = buffer.readLine();
										while (line != null && line.trim().startsWith("\"")) {
											pluralTarget = pluralTarget + "\n"
													+ line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
											line = buffer.readLine();
											if (line == null) {
												line = "";
											}
										}
										if (line == null) {
											line = "";
										}
										pluralTargets.add(pluralTarget);
									}
									writeSegment();
								} else {
									// get the target
									line = line.substring(6);
									target = line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
									line = buffer.readLine();
									while (line != null && line.trim().startsWith("\"")) {
										target = target + "\n"
												+ line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
										line = buffer.readLine();
										if (line == null) {
											line = "";
										}
										if (line.trim().startsWith("\"Plural-Forms:")) {
											parsePlural(line);
										}
									}
									if (pluralSource.isEmpty()) {
										writeSegment();
									}
								}
								continue;
							}
							if (line.startsWith("domain")) {
								if (inDomain) {
									writeString("   </group>\n");
								}
								inDomain = true;
								writeString(
										"   <group id=\"##" + domainId++ + "\" restype=\"x-gettext-domain\" resname=\""
												+ line.substring(6).trim() + "\">\n");
								writeSkeleton(line);
							}
						}
						line = buffer.readLine();
					}

					skeleton.close();

					if (inDomain) {
						writeString("   </group>\n");
					}
					writeString("</body>\n");
					writeString("</file>\n");
					writeString("</xliff>");
					output.close();
				}
			}
			result.add(Constants.SUCCESS);
		} catch (IOException e) {
			Logger logger = System.getLogger(Po2Xliff.class.getName());
			logger.log(Level.ERROR, Messages.getString("Po2Xliff.0"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}

		return result;
	}

	private void parsePlural(String line) {
		String string = line.substring(line.indexOf("nplurals") + 8).trim();
		String number = string.substring(string.indexOf('=') + 1, string.indexOf(';'));
		plurals = Integer.parseInt(number);
	}

	private void writeString(String string) throws IOException {
		output.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private void writeSkeleton(String string) throws IOException {
		skeleton.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private void writeSegment() throws IOException {
		if (!pluralSource.isEmpty()) {
			writeString("   <group restype=\"x-gettext-plurals\" id=\"" + segId + "\">\n");
			if (!context.isEmpty()) {
				writeString("      <context-group name=\"x-po-entry-header#" + contextId++
						+ "\" purpose=\"information\">\n" + "         <context context-type=\"x-po-autocomment\">"
						+ XMLUtils.cleanText(context) + "</context>\n" + "      </context-group>\n");
			}
			if (!reference.isEmpty()) {
				parseReference(reference);
			}
			if (!newContext.isEmpty()) {
				writeString("      <context-group name=\"x-po-msgctxt#" + contextId++ + "\" purpose=\"information\">\n"
						+ "         <context context-type=\"x-msgctxt\">" + XMLUtils.cleanText(newContext)
						+ "</context>\n" + "      </context-group>\n");
			}
			if (!flags.isEmpty()) {
				writeString("      <prop-group>\n" + "         <prop prop-type=\"x-po-flags\">"
						+ XMLUtils.cleanText(flags).trim() + "</prop>\n" + "      </prop-group>\n");
			}
			// write singular first
			if (!pluralTargets.isEmpty()) {
				target = pluralTargets.get(0);
			} else {
				target = "";
			}
			String approved = "no";
			if (!fuzzy && !target.trim().isEmpty()) {
				approved = "yes";
			}
			writeString("      <trans-unit id=\"" + segId + " [0]\" xml:space=\"preserve\" approved=\"" + approved
					+ "\">\n");
			if (cformat) {
				writeString("         <source>" + parseString(XMLUtils.cleanText(source)) + "</source>\n");
				if (!target.isEmpty() || approved.equals("yes")) {
					writeString("         <target>" + parseString(XMLUtils.cleanText(target)) + "</target>\n");
				}
			} else {
				writeString("         <source>" + XMLUtils.cleanText(source) + "</source>\n");
				if (!target.isEmpty() || approved.equals("yes")) {
					writeString("         <target>" + XMLUtils.cleanText(target) + "</target>\n");
				}
			}
			if (!comment.isEmpty()) {
				writeString("         <note from=\"po-file\">" + XMLUtils.cleanText(comment) + "</note>\n");
			}
			writeString("         <note from=\"po-file\" annotates=\"source\">" + "Singular form" + "</note>\n");
			writeString("      </trans-unit>\n");
			// write plurals
			for (int i = 1; i < plurals; i++) {
				if (pluralTargets.size() > i) {
					target = pluralTargets.get(i);
				} else {
					target = "";
				}
				if (!fuzzy && !target.trim().isEmpty()) {
					approved = "yes";
				}
				writeString("      <trans-unit id=\"" + segId + " [" + i + "]\" xml:space=\"preserve\" approved=\""
						+ approved + "\">\n");
				if (cformat) {
					writeString("         <source>" + parseString(XMLUtils.cleanText(pluralSource)) + "</source>\n");
					if (!target.isEmpty() || approved.equals("yes")) {
						writeString("         <target>" + parseString(XMLUtils.cleanText(target)) + "</target>\n");
					}
				} else {
					writeString("         <source>" + XMLUtils.cleanText(pluralSource) + "</source>\n");
					if (!target.isEmpty() || approved.equals("yes")) {
						writeString("         <target>" + XMLUtils.cleanText(target) + "</target>\n");
					}
				}
				writeString("         <note from=\"po-file\" annotates=\"source\">" + "Plural form: [" + i + "]"
						+ "</note>\n");
				writeString("      </trans-unit>\n");
			}
			writeString("   </group>\n");
		} else {
			// only singular
			String approved = "no";
			if (!fuzzy && !target.trim().isEmpty()) {
				approved = "yes";
			}
			String restype = "";
			if (source.trim().isEmpty()) {
				restype = " restype=\"x-gettext-domain-header\" ";
			}
			writeString("   <trans-unit id=\"" + segId + "\" xml:space=\"preserve\" approved=\"" + approved + "\""
					+ restype + ">\n");
			if (cformat) {
				writeString("      <source>" + parseString(XMLUtils.cleanText(source)) + "</source>\n");
				if (!target.isEmpty() || approved.equals("yes")) {
					writeString("      <target>" + parseString(XMLUtils.cleanText(target)) + "</target>\n");
				}
			} else {
				if (source.trim().isEmpty()) {
					source = target;
				}
				writeString("      <source>" + XMLUtils.cleanText(source) + "</source>\n");
				if (!target.isEmpty() || approved.equals("yes")) {
					writeString("      <target>" + XMLUtils.cleanText(target) + "</target>\n");
				}
			}
			if (!comment.isBlank()) {
				writeString("      <note from=\"po-file\">" + XMLUtils.cleanText(comment) + "</note>\n");
			}
			if (!context.isBlank()) {
				writeString("      <context-group name=\"x-po-entry-header#" + contextId++
						+ "\" purpose=\"information\">\n" + "         <context context-type=\"x-po-autocomment\">"
						+ XMLUtils.cleanText(context) + "</context>\n" + "      </context-group>\n");
			}
			if (!reference.isBlank()) {
				parseReference(reference);
			}
			if (!newContext.isBlank()) {
				writeString("      <context-group name=\"x-po-msgctxt#" + contextId++ + "\" purpose=\"information\">\n"
						+ "         <context context-type=\"x-msgctxt\">" + XMLUtils.cleanText(newContext)
						+ "</context>\n" + "      </context-group>\n");
			}
			if (!flags.isEmpty()) {
				writeString("      <prop-group>\n" + "         <prop prop-type=\"x-po-flags\">"
						+ XMLUtils.cleanText(flags).trim() + "</prop>\n" + "      </prop-group>\n");
			}
			writeString("   </trans-unit>\n");
		}

		writeSkeleton("%%%" + segId++ + "%%%\n");

		source = "";
		pluralSource = "";
		pluralTargets.clear();
		target = "";
		comment = "";
		context = "";
		reference = "";
		flags = "";
		newContext = "";
		fuzzy = false;
		cformat = false;
	}

	private static String parseString(String string) {
		// Valid c format especifications must end
		// with one of diouxXfeEgGcs

		int id = 1;
		int index = string.indexOf('%');
		if (index == -1) {
			return string;
		}
		if (string.charAt(index + 1) == '%') {
			index = string.indexOf('%', index + 2);
		}
		String result = "";
		while (index != -1) {
			result = result + string.substring(0, index) + "<ph ctype=\"x-c-param\" id=\"" + id++ + "\">";
			int i = index;
			char c = string.charAt(i++);
			while (i < string.length() && "diouxXfeEgGcs".indexOf(c) == -1) {
				result = result + c;
				c = string.charAt(i++);
			}
			result = result + c + "</ph>";
			string = string.substring(i);
			index = string.indexOf('%');
			if (index != -1 && index < string.length() && string.charAt(index + 1) == '%') {
				index = string.indexOf('%', index + 2);
			}
		}
		result = result + string;
		return result;
	}

	private void parseReference(String ref) throws IOException {
		if (ref.trim().isEmpty()) {
			return;
		}
		writeString("      <context-group name=\"x-po-reference#" + refId++ + "\" purpose=\"x-unknown\">\n");
		String[] lines = ref.split("\\n");
		for (int i = 0; i < lines.length; i++) {
			writeString("         <context context-type=\"x-unknown\">" + XMLUtils.cleanText(lines[i]).trim()
					+ "</context>\n");
		}
		writeString("      </context-group>\n");
	}

}
