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
package com.maxprograms.converters.ditamap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.converters.ILogger;
import com.maxprograms.converters.Utils;
import com.maxprograms.converters.xml.Xliff2Xml;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;

public class Xliff2DitaMap {

	private Logger logger = System.getLogger(Xliff2DitaMap.class.getName());
	private Map<String, String[]> filesTable;
	private static ILogger dataLogger;

	private class ProcessingResult {
		String errorMessage;
		boolean success;

		ProcessingResult() {
			this.success = true;
		}

		ProcessingResult(String errorMessage) {
			this.errorMessage = errorMessage;
			this.success = false;
		}
	}

	public static List<String> run(Map<String, String> params) {
		Xliff2DitaMap instance = new Xliff2DitaMap();
		return instance.runConversion(params);
	}

	private List<String> runConversion(Map<String, String> params) {
		List<String> result = new ArrayList<>();
		try {
			String xliffFile = params.get("xliff");
			File outputFile = new File(params.get("backfile"));
			File parent = outputFile.getParentFile();
			if (Files.notExists(parent.toPath())) {
				Files.createDirectories(parent.toPath());
			}
			filesTable = new HashMap<>();
			String catalog = params.get("catalog");
			SAXBuilder builder = new SAXBuilder();
			builder.preserveCustomAttributes(true);
			builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));
			Document doc = builder.build(xliffFile);
			Element root = doc.getRootElement();
			List<Element> files = root.getChildren("file");
			Iterator<Element> it = files.iterator();
			while (it.hasNext()) {
				saveFile(it.next(), xliffFile);
			}
			String tgtlang = files.get(0).getAttributeValue("target-language",
					files.get(0).getAttributeValue("source-language"));

			List<String> topicFiles = new ArrayList<>(filesTable.keySet());
			List<ProcessingResult> results = mergeInParallel(topicFiles, params, outputFile, files.size(), tgtlang,
					catalog);

			for (ProcessingResult procResult : results) {
				if (!procResult.success) {
					result.add(Constants.ERROR);
					result.add(procResult.errorMessage);
					return result;
				}
			}
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
			logger.log(Level.ERROR, Messages.getString("Xliff2DitaMap.1"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	private List<ProcessingResult> mergeInParallel(List<String> topicFiles, Map<String, String> params,
			File outputFile, int fileCount, String tgtlang, String catalog) {
		List<ProcessingResult> results = new Vector<>();

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

		try (ExecutorService executor = Executors.newFixedThreadPool(maxThreads)) {
			List<Future<ProcessingResult>> futures = new ArrayList<>();

			for (String topicFile : topicFiles) {
				Future<ProcessingResult> future = executor.submit(() -> {
					return mergeSingleFile(topicFile, params, outputFile, fileCount, tgtlang, catalog);
				});
				futures.add(future);
			}

			for (Future<ProcessingResult> future : futures) {
				try {
					results.add(future.get());
				} catch (Exception e) {
					logger.log(Level.ERROR, "Error processing file", e);
					results.add(new ProcessingResult(e.getMessage()));
				}
			}
		} catch (Exception e) {
			logger.log(Level.ERROR, "Error in parallel processing", e);
		}
		return results;
	}

	private ProcessingResult mergeSingleFile(String topicFile, Map<String, String> params, File outputFile,
			int fileCount, String tgtlang, String catalog) {
		try {
			if (dataLogger != null) {
				if (dataLogger.isCancelled()) {
					return new ProcessingResult(Constants.CANCELLED);
				}
				synchronized (dataLogger) {
					dataLogger.log(topicFile);
				}
			}

			String[] values = filesTable.get(topicFile);
			Map<String, String> params2 = new HashMap<>();
			params2.put("xliff", values[0]);
			params2.put("skeleton", values[1]);
			File topic = fileCount > 1 ? new File(outputFile, topicFile) : outputFile;
			params2.put("backfile", topic.getAbsolutePath());
			params2.put("encoding", params.get("encoding"));
			params2.put("catalog", catalog);
			params2.put("dita_based", "yes");
			List<String> res = Xliff2Xml.run(params2);
			if (!Constants.SUCCESS.equals(res.get(0))) {
				return new ProcessingResult(res.get(1));
			}

			SAXBuilder builder = new SAXBuilder();
			builder.preserveCustomAttributes(true);
			builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));
			Document doc = builder.build(topic);
			Element r = doc.getRootElement();

			List<PI> ish = doc.getPI("ish");
			String id = r.getAttributeValue("id");

			if (!ish.isEmpty() || id.startsWith("GUID-")) {
				restoreGUID(r);
			}
			cleanAttributes(r);
			if (!"svg".equals(r.getName())) {
				r.setAttribute("xml:lang", tgtlang);
				Indenter.indent(r, 2);
				cleanConref(r);
			}

			XMLOutputter outputter = new XMLOutputter();
			outputter.preserveSpace(true);
			try (FileOutputStream out = new FileOutputStream(topic)) {
				outputter.output(doc, out);
			}
			File f = new File(values[0]);
			Files.delete(f.toPath());

			return new ProcessingResult();
		} catch (Exception e) {
			logger.log(Level.ERROR, "Error processing file: " + topicFile, e);
			return new ProcessingResult(e.getMessage());
		}
	}

	private void cleanAttributes(Element r) {
		r.removeAttribute("class");
		r.removeAttribute("xmlns:ditaarch");
		r.removeAttribute("ditaarch:DITAArchVersion");
		r.removeAttribute("domains");
		if ("removeContent".equals(r.getAttributeValue("status"))) {
			r.setContent(new ArrayList<>());
			r.removeAttribute("status");
		}
		List<Element> children = r.getChildren();
		for (int i = 0; i < children.size(); i++) {
			cleanAttributes(children.get(i));
		}
	}

	private void restoreGUID(Element r) {
		String href = r.getAttributeValue("href");
		if (!href.isEmpty()) {
			int index = href.indexOf("GUID");
			if (index != -1) {
				int end = href.indexOf('=', index + 1);
				if (end != -1) {
					String guid = href.substring(index, end);
					r.setAttribute("href", guid);
				}
			}
		}
		String conref = r.getAttributeValue("conref");
		int index = conref.indexOf('=');
		if (!conref.isEmpty() && conref.startsWith("GUID-") && index != -1) {
			String guid = conref.substring(0, index);
			index = conref.indexOf('#');
			if (index != -1) {
				String rest = conref.substring(index);
				r.setAttribute("conref", guid + rest);
			} else {
				r.setAttribute("conref", guid);
			}
		}
		List<Element> children = r.getChildren();
		for (int i = 0; i < children.size(); i++) {
			restoreGUID(children.get(i));
		}
	}

	private void cleanConref(Element e) {
		if (!e.getAttributeValue("fluentaIgnore").isEmpty()) {
			e.removeAttribute("fluentaIgnore");
		}
		if (!e.getAttributeValue("conref").isEmpty() && e.getAttributeValue("conaction").isEmpty()) {
			emptyElement(e);
		}
		if (!e.getAttributeValue("conkeyref").isEmpty() && e.getAttributeValue("conaction").isEmpty()) {
			emptyElement(e);
		}
		List<Element> list = e.getChildren();
		for (int i = 0; i < list.size(); i++) {
			cleanConref(list.get(i));
		}
	}

	private void emptyElement(Element e) {
		List<Element> children = e.getChildren();
		if (children.isEmpty()) {
			e.setContent(new ArrayList<>());
		} else {
			List<XMLNode> content = e.getContent();
			List<XMLNode> newContent = new ArrayList<>();
			Iterator<XMLNode> it = content.iterator();
			while (it.hasNext()) {
				XMLNode n = it.next();
				if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
					Element child = (Element) n;
					emptyElement(child);
					newContent.add(child);
				}
			}
			e.setContent(newContent);
		}
	}

	private void saveFile(Element element, String xliffFile) throws IOException {
		Document doc = new Document(null, "xliff", null, null);
		Element root = doc.getRootElement();
		root.setAttribute("version", "1.2");
		Element file = new Element("file");
		file.clone(element);
		root.addContent(file);
		List<PI> encoding = element.getPI("encoding");
		if (!encoding.isEmpty()) {
			root.addContent(encoding.get(0));
		}
		File xliff = File.createTempFile("tmp", ".xlf", new File(xliffFile).getParentFile());
		Element internal = file.getChild("header").getChild("skl").getChild("internal-file");
		if (internal != null) {
			// embedded skeleton
			File tmp = File.createTempFile("internal", ".skl", new File(xliffFile).getParentFile());
			tmp.deleteOnExit();
			Utils.decodeToFile(internal.getText(), tmp.getAbsolutePath());
			file.getChild("header").getChild("skl").addContent(new Element("external-file"));
			file.getChild("header").getChild("skl").getChild("external-file").setAttribute("href",
					tmp.getAbsolutePath());
			file.getChild("header").getChild("skl").removeChild("internal-file");
		}
		XMLOutputter outputter = new XMLOutputter();
		outputter.preserveSpace(true);
		try (FileOutputStream output = new FileOutputStream(xliff.getAbsolutePath())) {
			outputter.output(doc, output);
		}
		filesTable.put(element.getAttributeValue("original"), new String[] { xliff.getAbsolutePath(),
				file.getChild("header").getChild("skl").getChild("external-file").getAttributeValue("href") });
	}

	public static void setDataLogger(ILogger dataLogger) {
		Xliff2DitaMap.dataLogger = dataLogger;
	}

}
