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
package com.maxprograms.converters.php;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.maxprograms.converters.Constants;
import com.maxprograms.segmenter.Segmenter;
import com.maxprograms.segmenter.SegmenterPool;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.XMLUtils;

public class Php2Xliff {

	private static final Pattern START_TAG_PATTERN = Pattern
			.compile("<[A-Za-z0-9]+([\\s][A-Za-z\\-\\.]+=[\"|\'][^<&>]*[\"|\'])[\\s]*/?>");
	private static final Pattern END_TAG_PATTERN = Pattern.compile("</[A-Za-z0-9]+>");

	private static final class Context {
		int segId;
		String sourceLanguage;
		boolean paragraphSegmentation;
		Segmenter segmenter;
	}

	private Php2Xliff() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		List<String> result = new Vector<>();
		Context ctx = new Context();
		ctx.segId = 0;

		String inputFile = params.get("source");
		String xliffFile = params.get("xliff");
		String skeletonFile = params.get("skeleton");
		ctx.sourceLanguage = params.get("srcLang");
		String targetLanguage = params.get("tgtLang");
		String srcEncoding = params.get("srcEncoding");
		String paragraph = params.get("paragraph");
		ctx.paragraphSegmentation = "yes".equals(paragraph);
		String initSegmenter = params.get("srxFile");

		try {
			Catalog catalog = CatalogBuilder.getCatalog(params.get("catalog"));
			if (!ctx.paragraphSegmentation) {
				ctx.segmenter = SegmenterPool.getSegmenter(initSegmenter, ctx.sourceLanguage, catalog);
			}

			try (FileOutputStream output = new FileOutputStream(xliffFile)) {
				writeString(output, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
				writeString(output, "<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" " +
						"xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
						"xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");
				writeString(output, "<?encoding " + srcEncoding + "?>\n");

				String target = "";
				if (targetLanguage != null) {
					target = "\" target-language=\"" + targetLanguage;
				}

				writeString(output, "<file original=\"" + inputFile
						+ "\" source-language=\"" + ctx.sourceLanguage
						+ target
						+ "\" datatype=\"x-phparray\">\n");
				writeString(output, "<header>\n");
				writeString(output, "   <skl>\n");
				writeString(output, "      <external-file href=\"" + XMLUtils.cleanText(skeletonFile) + "\"/>\n");
				writeString(output, "   </skl>\n");
				writeString(output, "   <tool tool-version=\"" + Constants.VERSION + " " + Constants.BUILD
						+ "\" tool-id=\"" + Constants.TOOLID + "\" tool-name=\"" + Constants.TOOLNAME + "\"/>\n");
				writeString(output, "</header>\n");
				writeString(output, "<body>\n");

				try (FileOutputStream skeleton = new FileOutputStream(skeletonFile)) {
					StringBuilder sb = new StringBuilder();
					try (FileReader reader = new FileReader(inputFile, Charset.forName(srcEncoding))) {
						try (BufferedReader buffer = new BufferedReader(reader)) {
							String line = "";
							while ((line = buffer.readLine()) != null) {
								if (!sb.isEmpty()) {
									sb.append('\n');
								}
								sb.append(line);
							}
						}
					}
					String text = sb.toString();
					char finishMark = ')';
					String start = "";
					int index = text.indexOf("array(");
					if (index != -1) {
						start = text.substring(0, index + "array(".length());
					} else if ((index = text.indexOf("return")) != -1) {
						index = text.indexOf("[", index + "return".length());
						if (index != -1) {
							start = text.substring(0, index + "[".length());
							finishMark = ']';
						}
					}
					if (start.isEmpty()) {
						throw new IOException(Messages.getString("Php2Xliff.1"));
					}
					writeSkeleton(skeleton, start);
					text = text.substring(index + "array(".length());

					boolean finished = false;
					do {
						String spacing = "";
						char delimiter = '\'';
						for (int i = 0; i < text.length(); i++) {
							char c = text.charAt(i);
							if (c == finishMark) {
								finished = true;
								break;
							}
							if (c == '\'' || c == '"') {
								delimiter = c;
								break;
							}
							spacing = spacing + c;
						}
						writeSkeleton(skeleton, spacing);
						text = text.substring(spacing.length());
						if (!finished) {
							String key = "" + delimiter;
							for (int i = 1; i < text.length(); i++) {
								char c = text.charAt(i);
								key = key + c;
								if (c == delimiter && !key.endsWith("\\" + delimiter)) {
									break;
								}
							}
							writeSkeleton(skeleton, key);
							text = text.substring(key.length());

							spacing = "";
							for (int i = 0; i < text.length(); i++) {
								char c = text.charAt(i);
								if (c == '\'' || c == '"') {
									delimiter = c;
									break;
								}
								spacing = spacing + c;
							}
							writeSkeleton(skeleton, spacing);
							text = text.substring(spacing.length());

							String value = "" + delimiter;
							for (int i = 1; i < text.length(); i++) {
								char c = text.charAt(i);
								value = value + c;
								if (c == delimiter && !value.endsWith("\\" + delimiter)) {
									break;
								}
							}
							writeSegment(ctx, output, skeleton, value);
							text = text.substring(value.length());
						}
					} while (!finished);
					writeSkeleton(skeleton, text);
				}
				writeString(output, "</body>\n");
				writeString(output, "</file>\n");
				writeString(output, "</xliff>");
			}
			result.add(Constants.SUCCESS);
		} catch (Exception e) {
			Logger logger = System.getLogger(Php2Xliff.class.getName());
			logger.log(Level.ERROR, Messages.getString("Php2Xliff.2"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	private static void writeString(FileOutputStream output, String string)
			throws IOException {
		output.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private static void writeSkeleton(FileOutputStream skeleton, String string)
			throws IOException {
		skeleton.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private static void writeSegment(Context ctx, FileOutputStream output, FileOutputStream skeleton, String source)
			throws IOException {
		source = source.replace("\\'", "'");
		source = source.replace("\\\"", "\"");
		source = source.replace("\\\\", "\\");
		String delimiter = source.substring(0, 1);
		source = source.substring(1);
		source = source.substring(0, source.length() - 1);
		writeSkeleton(skeleton, delimiter);
		String[] segments;
		if (!ctx.paragraphSegmentation) {
			segments = ctx.segmenter.segment(source);
		} else {
			segments = new String[] { source };
		}
		for (int i = 0; i < segments.length; i++) {
			if (XMLUtils.cleanText(segments[i]).trim().equals("")) {
				writeSkeleton(skeleton, segments[i]);
			} else {
				String string = segments[i];
				if (hasHtml(string)) {
					string = fixHtml(string);
				} else {
					string = XMLUtils.cleanText(string);
				}
				writeString(output, "   <trans-unit id=\"" + ctx.segId
						+ "\" xml:space=\"preserve\" approved=\"no\">\n"
						+ "      <source xml:lang=\"" + ctx.sourceLanguage + "\">"
						+ string + "</source>\n");
				writeString(output, "   </trans-unit>\n");
				writeSkeleton(skeleton, "%%%" + ctx.segId++ + "%%%");
			}
		}
		writeSkeleton(skeleton, delimiter);
	}

	private static String fixHtml(String text) {
		String temp = "";
		int count = 0;
		Map<String, Element> table = new Hashtable<>();
		Matcher matcher = START_TAG_PATTERN.matcher(text);
		if (matcher.find()) {
			matcher.reset();
			while (matcher.find()) {
				int start = matcher.start();
				int end = matcher.end();

				String s = text.substring(0, start);
				temp = temp + s;

				String tag = text.substring(start, end);
				Element ph = new Element("ph");
				ph.setAttribute("id", "" + count);
				ph.setText(tag);
				table.put("[[[" + count + "]]]", ph);

				temp = temp + "[[[" + count + "]]]";

				text = text.substring(end);
				matcher = START_TAG_PATTERN.matcher(text);
				count++;
			}
			temp = temp + text;
		} else {
			temp = text;
		}
		matcher = END_TAG_PATTERN.matcher(temp);
		String result = "";
		if (matcher.find()) {
			matcher.reset();
			while (matcher.find()) {
				int start = matcher.start();
				int end = matcher.end();

				String s = temp.substring(0, start);
				result = result + s;

				String tag = temp.substring(start, end);
				Element ph = new Element("ph");
				ph.setAttribute("id", "" + count);
				ph.setText(tag);
				table.put("[[[" + count + "]]]", ph);

				result = result + "[[[" + count + "]]]";

				temp = temp.substring(end);
				matcher = START_TAG_PATTERN.matcher(temp);
				count++;
			}
			result = result + temp;
		} else {
			result = temp;
		}

		result = XMLUtils.cleanText(result);

		Set<String> keys = table.keySet();
		for (String key : keys) {
			Element tag = table.get(key);
			result = replaceTag(result, key, tag);
		}
		return result;
	}

	private static String replaceTag(String string, String key, Element tag) {
		int index = string.indexOf(key);
		String start = string.substring(0, index);
		String end = string.substring(index + key.length());
		return start + tag.toString() + end;
	}

	private static boolean hasHtml(String source) {
		Matcher matcher = START_TAG_PATTERN.matcher(source);
		if (matcher.find()) {
			return true;
		}
		matcher = END_TAG_PATTERN.matcher(source);
		return matcher.find();
	}
}
