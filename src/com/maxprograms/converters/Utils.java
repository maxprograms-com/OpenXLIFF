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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.maxprograms.languages.LanguageUtils;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;

public class Utils {

	protected static final Logger logger = System.getLogger(Utils.class.getName());
	private static final ConcurrentHashMap<String, String> pathCache = new ConcurrentHashMap<>();
	private static final int MAX_CACHE_SIZE = 10000; // Limit cache to prevent unbounded growth

	private Utils() {
		// do not instantiate this class
	}

	public static void clearPathCache() {
		pathCache.clear();
	}

	public static String getAbsolutePath(File homeFile, String relative) throws IOException {
		return getAbsolutePath(homeFile.getAbsolutePath(), relative);
	}

	public static String getAbsolutePath(String homeFile, String relative) throws IOException {
		// Use cache to avoid expensive getCanonicalPath() filesystem calls
		String cacheKey = homeFile + "|" + relative;
		String cached = pathCache.get(cacheKey);
		if (cached != null) {
			return cached;
		}

		try {
			if (relative.indexOf('%') != -1) {
				try {
					String decoded = URLDecoder.decode(relative, StandardCharsets.UTF_8);
					relative = decoded;
				} catch (IllegalArgumentException e) {
					// do nothing, '%' may be part of the name
				}
			}
			File result = new File(relative);
			if (!result.isAbsolute()) {
				File home = new File(homeFile);
				// If home is a file, get the parent
				if (!home.isDirectory()) {
					home = home.getParentFile();
				}
				result = new File(home, relative);
			}
			String canonicalPath = result.getCanonicalPath();

			// Prevent unbounded cache growth - clear if exceeds limit
			// This is thread-safe but may cause occasional cache misses during clear
			if (pathCache.size() >= MAX_CACHE_SIZE) {
				pathCache.clear();
			}

			pathCache.put(cacheKey, canonicalPath);
			return canonicalPath;
		} catch (IOException e) {
			MessageFormat mf = new MessageFormat(Messages.getString("Utils.1"));
			logger.log(Level.ERROR, mf.format(new String[] { relative, homeFile }), e);
			throw e;
		}
	}

	public static String getRelativePath(String home, String file) throws IOException {
		File homeFile = new File(home);
		if (!homeFile.isAbsolute()) {
			MessageFormat mf = new MessageFormat(Messages.getString("Utils.2"));
			throw new IOException(mf.format(new String[] { home }));
		}
		if (homeFile.isFile()) {
			homeFile = homeFile.getParentFile();
		}
		Path homePath = homeFile.toPath();
		Path filePath = new File(file).toPath();
		if (homePath.getRoot().equals(filePath.getRoot())) {
			Path relative = homePath.relativize(filePath);
			return relative.toString();
		}
		return filePath.toString();
	}

	public static String[] getPageCodes() {
		TreeMap<String, Charset> charsets = new TreeMap<>(Charset.availableCharsets());
		Set<String> keys = charsets.keySet();
		String[] codes = new String[keys.size()];
		int j = 0;
		for (String key : keys) {
			Charset cset = charsets.get(key);
			codes[j++] = cset.displayName();
		}
		return codes;
	}

	public static void decodeToFile(String dataToDecode, String filename) throws java.io.IOException {
		Decoder decoder = Base64.getMimeDecoder();
		try (FileOutputStream output = new FileOutputStream(filename)) {
			output.write(decoder.decode(dataToDecode));
		}
	}

	public static String encodeFromFile(String filename) throws IOException {
		File file = new File(filename);
		int size = Math.max((int) (file.length() * 1.4), 4096);
		byte[] buffer = new byte[size]; // Need max() for math on small files (v2.2.1)
		int length = 0;
		int numBytes = 0;
		try (FileInputStream input = new FileInputStream(file)) {
			while ((numBytes = input.read(buffer, length, size - length)) != -1) {
				length += numBytes;
			}
		}
		Encoder encoder = Base64.getMimeEncoder();
		return encoder.encodeToString(Arrays.copyOf(buffer, length));
	}

	public static boolean isValidLanguage(String lang) throws IOException, SAXException, ParserConfigurationException {
		return LanguageUtils.getLanguage(lang) != null;
	}

	public static String[] fixPath(String[] args) {
		List<String> result = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.startsWith("-")) {
				if (!current.isEmpty()) {
					result.add(current.toString().trim());
					current = new StringBuilder();
				}
				result.add(arg);
			} else {
				current.append(' ');
				current.append(arg);
			}
		}
		if (!current.isEmpty()) {
			result.add(current.toString().trim());
		}
		return result.toArray(new String[result.size()]);
	}

	public static boolean lookingAt(String target, String text, int start) {
		if (start < 0 || start + target.length() > text.length()) {
			return false;
		}
		return text.startsWith(target, start);
	}

	public static JSONObject readJSON(String jsonFile) throws IOException, JSONException {
		StringBuilder sb = new StringBuilder();
		try (FileInputStream input = new FileInputStream(new File(jsonFile))) {
			try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(input))) {
				String line = "";
				while ((line = reader.readLine()) != null) {
					if (!sb.isEmpty()) {
						sb.append("\n");
					}
					sb.append(line);
				}
			}
		}
		return new JSONObject(sb.toString());
	}

	public static String pureText(Element seg) {
		List<XMLNode> l = seg.getContent();
		StringBuilder text = new StringBuilder();
		for (XMLNode o : l) {
			if (o.getNodeType() == XMLNode.TEXT_NODE) {
				text.append(((TextNode) o).getText());
			} else if (o.getNodeType() == XMLNode.ELEMENT_NODE) {
				String type = ((Element) o).getName();
				// discard all inline elements
				// except <sub> and <mrk>
				if (type.equals("sub") || type.equals("mrk")) {
					Element e = (Element) o;
					text.append(pureText(e));
				}
			}
		}
		return text.toString();
	}

	public static double wrongTags(Element source1, Element source2, double tagPenalty) {
		List<Element> tags = new Vector<>();
		int count = 0;
		int errors = 0;
		List<XMLNode> content = source1.getContent();
		for (XMLNode n : content) {
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				tags.add(e);
				count++;
			}
		}
		content = source2.getContent();
		int c2 = 0;
		for (XMLNode n : content) {
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				c2++;
				boolean found = false;
				for (int j = 0; j < count; j++) {
					if (e.equals(tags.get(j))) {
						tags.set(j, null);
						found = true;
						break;
					}
				}
				if (!found) {
					errors++;
				}
			}
		}
		if (c2 > count) {
			errors += c2 - count;
		}
		if (count > c2) {
			errors += count - c2;
		}
		return errors * tagPenalty;
	}

	public static Element toElement(String string) throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		return builder.build(new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8))).getRootElement();
	}
}
