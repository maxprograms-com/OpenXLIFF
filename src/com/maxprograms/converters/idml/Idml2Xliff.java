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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;
import com.maxprograms.xml.XMLUtils;

public class Idml2Xliff {

	private static Logger logger = System.getLogger(Idml2Xliff.class.getName());

	private static Element mergedRoot;
	private static String inputFile;
	private static String skeleton;
	private static ZipOutputStream out;
	private static List<String> used = null;

	private static class StoryEntry {
		String entryName;
		File tempFile;

		StoryEntry(String entryName, File tempFile) {
			this.entryName = entryName;
			this.tempFile = tempFile;
		}
	}

	private static class StoryResult {
		String entryName;
		File originalFile;
		File xliffFile;
		File sklFile;
		boolean processed;

		StoryResult(String entryName, File originalFile, File xliffFile, File sklFile, boolean processed) {
			this.entryName = entryName;
			this.originalFile = originalFile;
			this.xliffFile = xliffFile;
			this.sklFile = sklFile;
			this.processed = processed;
		}

		StoryResult(String entryName, File originalFile) {
			this.entryName = entryName;
			this.originalFile = originalFile;
			this.processed = false;
		}
	}

	private Idml2Xliff() {
		// do not instantiate this class
		// use run method instead
	}

	private static void sortStories() {
		List<Element> files = mergedRoot.getChildren("file");
		List<PI> instructions = mergedRoot.getPI();
		Map<String, Integer> table = new Hashtable<>();
		for (int i = 0; i < files.size(); i++) {
			String key = getKey(files.get(i));
			table.put(key, i);
		}
		List<XMLNode> v = new Vector<>();
		Iterator<String> it = used.iterator();
		while (it.hasNext()) {
			String key = it.next();
			if (table.containsKey(key)) {
				v.add(files.get(table.get(key).intValue()));
			}
		}
		mergedRoot.setContent(v);
		Iterator<PI> pit = instructions.iterator();
		while (pit.hasNext()) {
			mergedRoot.addContent(pit.next());
		}
	}

	private static String getKey(Element file) {
		List<Element> groups = file.getChild("header").getChildren("prop-group");
		for (int i = 0; i < groups.size(); i++) {
			Element group = groups.get(i);
			if (group.getAttributeValue("name").equals("document")) {
				return group.getChild("prop").getText();
			}
		}
		return null;
	}

	private static List<String> getStories(String map)
			throws SAXException, IOException, ParserConfigurationException {
		List<String> result = new Vector<>();
		SAXBuilder builder = new SAXBuilder();
		Document doc = builder.build(map);
		Element root = doc.getRootElement();
		List<Element> stories = root.getChildren("idPkg:Story");
		Iterator<Element> it = stories.iterator();
		while (it.hasNext()) {
			result.add(it.next().getAttributeValue("src"));
		}
		return result;
	}

	private static void addFile(String xliff) throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		Document doc = builder.build(xliff);
		Element root = doc.getRootElement();
		Element file = root.getChild("file");
		Element newFile = new Element("file");
		newFile.clone(file);
		mergedRoot.addContent(newFile);
	}

	private static void updateXliff(String xliff, String original)
			throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		Document doc = builder.build(xliff);
		Element root = doc.getRootElement();
		Element file = root.getChild("file");
		file.setAttribute("datatype", "x-idml");
		file.setAttribute("original", XMLUtils.cleanText(inputFile));
		file.setAttribute("tool-id", Constants.TOOLID);
		Element header = file.getChild("header");
		Element propGroup = new Element("prop-group");
		propGroup.setAttribute("name", "document");
		Element prop = new Element("prop");
		prop.setAttribute("prop-type", "original");
		prop.setText(original);
		propGroup.addContent(prop);
		Element tool = new Element("tool");
		tool.setAttribute("tool-id", Constants.TOOLID);
		tool.setAttribute("tool-name", Constants.TOOLNAME);
		tool.setAttribute("tool-version", Constants.VERSION + " " + Constants.BUILD);
		header.addContent(tool);
		header.addContent(propGroup);

		Element ext = header.getChild("skl").getChild("external-file");
		ext.setAttribute("href", XMLUtils.cleanText(skeleton));

		XMLOutputter outputter = new XMLOutputter();
		outputter.preserveSpace(true);
		Indenter.indent(root, 2);
		try (FileOutputStream output = new FileOutputStream(xliff)) {
			outputter.output(doc, output);
		}
	}

	private static int countSegments(String string) throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		Document doc = builder.build(string);
		Element root = doc.getRootElement();
		return root.getChild("file").getChild("body").getChildren("trans-unit").size();
	}

	private static void saveEntry(ZipEntry entry, String name) throws IOException {
		ZipEntry content = new ZipEntry(entry.getName());
		content.setMethod(ZipEntry.DEFLATED);
		out.putNextEntry(content);
		try (FileInputStream input = new FileInputStream(name)) {
			byte[] array = new byte[1024];
			int len;
			while ((len = input.read(array)) > 0) {
				out.write(array, 0, len);
			}
			out.closeEntry();
		}
	}

	public static List<String> run(Map<String, String> params) {
		List<String> result = new Vector<>();

		inputFile = params.get("source");
		String xliff = params.get("xliff");
		skeleton = params.get("skeleton");
		String encoding = params.get("srcEncoding");

		// Parse maxThreads parameter
		String maxThreadsParam = params.get("maxThreads");
			int maxThreads;
			if (maxThreadsParam != null) {
				try {
					maxThreads = Integer.parseInt(maxThreadsParam);
					if (maxThreads < 1) {
						maxThreads = 1;
					}
				} catch (NumberFormatException e) {
					// Use default if invalid
					maxThreads = Runtime.getRuntime().availableProcessors();
				}
			} else {
				maxThreads = Runtime.getRuntime().availableProcessors();
			}
		params.put("maxThreads", String.valueOf(maxThreads));

		List<String> entryOrder = new Vector<>();
		Map<String, File> allEntries = new Hashtable<>();
		Map<String, File> storyFiles = new Hashtable<>();
		List<StoryResult> results = new Vector<>();

		try {
			Document merged = new Document(null, "xliff", null, null);
			mergedRoot = merged.getRootElement();
			mergedRoot.setAttribute("version", "1.2");
			mergedRoot.setAttribute("xmlns", "urn:oasis:names:tc:xliff:document:1.2");
			mergedRoot.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			mergedRoot.setAttribute("xsi:schemaLocation",
					"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd");
			mergedRoot.addContent(new PI("encoding", encoding));

			try (ZipInputStream in = new ZipInputStream(new FileInputStream(inputFile))) {
				ZipEntry entry = null;
				while ((entry = in.getNextEntry()) != null) {
					String entryName = entry.getName();
					entryOrder.add(entryName);
					
					File tmp = File.createTempFile("idml", ".tmp", new File(skeleton).getParentFile());
					try (FileOutputStream output = new FileOutputStream(tmp.getAbsolutePath())) {
						byte[] buf = new byte[1024];
						int len;
						while ((len = in.read(buf)) > 0) {
							output.write(buf, 0, len);
						}
					}

					allEntries.put(entryName, tmp);

					// Check if it's the designmap to get story list
					if (entryName.matches(".*designmap\\.xml")) {
						used = getStories(tmp.getAbsolutePath());
					}

					if (entryName.matches(".*Story_.*\\.xml")) {
						storyFiles.put(entryName, tmp);
					}
				}
			}

			// Process stories in parallel
			List<StoryEntry> storiesToProcess = new Vector<>();
			for (Map.Entry<String, File> entry : storyFiles.entrySet()) {
				if (used != null && used.contains(entry.getKey())) {
					storiesToProcess.add(new StoryEntry(entry.getKey(), entry.getValue()));
				}
			}

			results = processStoriesInParallel(storiesToProcess, params, maxThreads);
			
			// Index results by entry name for quick lookup
			Map<String, StoryResult> resultMap = new Hashtable<>();
			for (StoryResult storyResult : results) {
				resultMap.put(storyResult.entryName, storyResult);
			}

			// Write output ZIP with entries in original order
			out = new ZipOutputStream(new FileOutputStream(skeleton));

			for (String entryName : entryOrder) {
				if (resultMap.containsKey(entryName)) {
					// This is a processed story
					StoryResult storyResult = resultMap.get(entryName);
					if (storyResult.processed && storyResult.xliffFile != null) {
						synchronized (mergedRoot) {
							addFile(storyResult.xliffFile.getAbsolutePath());
						}
						ZipEntry content = new ZipEntry(entryName + ".skl");
						content.setMethod(ZipEntry.DEFLATED);
						out.putNextEntry(content);
						try (FileInputStream input = new FileInputStream(storyResult.sklFile.getAbsolutePath())) {
							byte[] array = new byte[1024];
							int len;
							while ((len = input.read(array)) > 0) {
								out.write(array, 0, len);
							}
							out.closeEntry();
						}
						// Clean up temp files
						Files.delete(Paths.get(storyResult.xliffFile.toURI()));
						Files.delete(Paths.get(storyResult.sklFile.toURI()));
					} else {
						// Story not processed, copy original
						ZipEntry zipEntry = new ZipEntry(entryName);
						saveEntry(zipEntry, storyResult.originalFile.getAbsolutePath());
					}
					Files.delete(Paths.get(storyResult.originalFile.toURI()));
				} else {
					// Non-story or story not in used list
					File tmp = allEntries.get(entryName);
					ZipEntry zipEntry = new ZipEntry(entryName);
					saveEntry(zipEntry, tmp.getAbsolutePath());
					Files.delete(Paths.get(tmp.toURI()));
				}
			}

			out.close();

			sortStories();

			// output final XLIFF

			XMLOutputter outputter = new XMLOutputter();
			outputter.preserveSpace(true);
			try (FileOutputStream output = new FileOutputStream(xliff)) {
				outputter.output(merged, output);
			}
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | NullPointerException e) {
			logger.log(Level.ERROR, Messages.getString("Idml2Xliff.1"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
			
			// Close ZipOutputStream if it was opened
			if (out != null) {
				try {
					out.close();
				} catch (IOException ioe) {
					logger.log(Level.WARNING, Messages.getString("Idml2Xliff.2"), ioe);
				}
			}
			
			// Clean up all temporary files on error
			for (StoryResult storyResult : results) {
				try {
					if (storyResult.xliffFile != null && storyResult.xliffFile.exists()) {
						Files.delete(Paths.get(storyResult.xliffFile.toURI()));
					}
					if (storyResult.sklFile != null && storyResult.sklFile.exists()) {
						Files.delete(Paths.get(storyResult.sklFile.toURI()));
					}
					if (storyResult.originalFile != null && storyResult.originalFile.exists()) {
						Files.delete(Paths.get(storyResult.originalFile.toURI()));
					}
				} catch (IOException ioe) {
					logger.log(Level.WARNING, Messages.getString("Idml2Xliff.3"), ioe);
				}
			}
			
			for (File tempFile : allEntries.values()) {
				try {
					if (tempFile != null && tempFile.exists()) {
						Files.delete(Paths.get(tempFile.toURI()));
					}
				} catch (IOException ioe) {
					logger.log(Level.WARNING, Messages.getString("Idml2Xliff.3"), ioe);
				}
			}
		}
		return result;
	}

	private static List<StoryResult> processStoriesInParallel(List<StoryEntry> stories, Map<String, String> params,
			int maxThreads) {
		List<StoryResult> results = new Vector<>();

		try (ExecutorService executor = Executors.newFixedThreadPool(maxThreads)) {
			List<Future<StoryResult>> futures = new Vector<>();

			for (StoryEntry story : stories) {
				Future<StoryResult> future = executor.submit(() -> processSingleStory(story, params));
				futures.add(future);
			}

			// Collect results
			for (Future<StoryResult> future : futures) {
				try {
					results.add(future.get());
				} catch (Exception e) {
					logger.log(Level.ERROR, "Error processing story in parallel", e);
				}
			}
		} catch (Exception e) {
			logger.log(Level.ERROR, "Error in parallel story processing", e);
		}
		return results;
	}

	private static StoryResult processSingleStory(StoryEntry story, Map<String, String> params) {
		try {
			File tmp = story.tempFile;
			String entryName = story.entryName;

			Map<String, String> table = new Hashtable<>();
			table.put("source", tmp.getAbsolutePath());
			table.put("xliff", tmp.getAbsolutePath() + ".xlf");
			table.put("skeleton", tmp.getAbsolutePath() + ".skl");
			table.put("catalog", params.get("catalog"));
			table.put("srcLang", params.get("srcLang"));
			String tgtLang = params.get("tgtLang");
			if (tgtLang != null) {
				table.put("tgtLang", tgtLang);
			}
			table.put("srcEncoding", params.get("srcEncoding"));
			table.put("paragraph", params.get("paragraph"));
			table.put("srxFile", params.get("srxFile"));
			table.put("format", params.get("format"));
			table.put("from", "x-idml");
			List<String> res = Story2Xliff.run(table);

			if (Constants.SUCCESS.equals(res.get(0))) {
				if (countSegments(tmp.getAbsolutePath() + ".xlf") > 0) {
					updateXliff(tmp.getAbsolutePath() + ".xlf", entryName);
					File xliffFile = new File(tmp.getAbsolutePath() + ".xlf");
					File sklFile = new File(tmp.getAbsolutePath() + ".skl");
					return new StoryResult(entryName, tmp, xliffFile, sklFile, true);
				} else {
					// No segments, don't process
					// Clean up empty xliff and skeleton files
					File skl = new File(tmp.getAbsolutePath() + ".skl");
					if (skl.exists()) {
						Files.delete(Paths.get(skl.toURI()));
					}
					File xlf = new File(tmp.getAbsolutePath() + ".xlf");
					if (xlf.exists()) {
						Files.delete(Paths.get(xlf.toURI()));
					}
					return new StoryResult(entryName, tmp);
				}
			} else {
				// Conversion failed
				// Clean up any created files
				File skl = new File(tmp.getAbsolutePath() + ".skl");
				if (skl.exists()) {
					Files.delete(Paths.get(skl.toURI()));
				}
				File xlf = new File(tmp.getAbsolutePath() + ".xlf");
				if (xlf.exists()) {
					Files.delete(Paths.get(xlf.toURI()));
				}
				return new StoryResult(entryName, tmp);
			}
		} catch (Exception e) {
			logger.log(Level.ERROR, "Error processing story: " + story.entryName, e);
			// Clean up any partially created files
			File skl = new File(story.tempFile.getAbsolutePath() + ".skl");
			if (skl.exists()) {
				try {
					Files.delete(Paths.get(skl.toURI()));
				} catch (IOException ioe) {
					// ignore cleanup errors
				}
			}
			File xlf = new File(story.tempFile.getAbsolutePath() + ".xlf");
			if (xlf.exists()) {
				try {
					Files.delete(Paths.get(xlf.toURI()));
				} catch (IOException ioe) {
					// ignore cleanup errors
				}
			}
			return new StoryResult(story.entryName, story.tempFile);
		}
	}
}
