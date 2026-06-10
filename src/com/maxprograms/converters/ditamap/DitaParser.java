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
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.converters.ILogger;
import com.maxprograms.converters.Utils;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.SilentErrorHandler;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;

public class DitaParser {

	private static Logger logger = System.getLogger(DitaParser.class.getName());

	private static ILogger dataLogger;
	private List<String> issues;

	protected class StringArray implements Comparable<StringArray> {
		private String file;
		private String id;

		public StringArray(String file, String id) {
			this.file = file;
			this.id = id;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof StringArray a) {
				return a.getFile().equals(file) && a.getId().equals(id);
			}
			return false;
		}

		@Override
		public int compareTo(StringArray o) {
			if (o.getFile().equals(file) && o.getId().equals(id)) {
				return 0;
			}
			return -1;
		}

		public String getFile() {
			return file;
		}

		public String getId() {
			return id;
		}

		@Override
		public int hashCode() {
			return (id + file).hashCode();
		}
	}

	private Set<String> filesMap;
	private Map<String, Set<String>> excludeTable;
	private Map<String, Set<String>> includeTable;
	private boolean filterAttributes;
	private Scope rootScope;

	private Set<StringArray> searchedConref;
	private Set<String> visiting;
	private Map<Key, Set<String>> usedKeys;
	private Set<String> recursed;
	private Set<String> pendingRecurse;
	private TreeSet<String> topicrefSet;
	private TreeSet<String> topicSet;
	private static TreeSet<String> xrefSet;
	private static TreeSet<String> linkSet;
	private static TreeSet<String> imageSet;
	private List<String> ignored;
	private Map<StringArray, Element> referenceChache;
	private Catalog catalog;
	private List<String> skipped;
	private Map<String, List<String>> images;
	private Set<String> translatableSVG;
	private long scopeBuildTime;

	public List<String> run(Map<String, String> params, Catalog catalog)
			throws IOException, SAXException, ParserConfigurationException {
		List<String> result = new ArrayList<>();
		issues = new ArrayList<>();
		filesMap = new ConcurrentSkipListSet<>();
		searchedConref = new ConcurrentSkipListSet<>();
		usedKeys = new ConcurrentHashMap<>();
		recursed = new ConcurrentSkipListSet<>();
		pendingRecurse = new ConcurrentSkipListSet<>();
		ignored = new ArrayList<>();
		translatableSVG = new ConcurrentSkipListSet<>();
		referenceChache = new ConcurrentHashMap<>();
		skipped = new ArrayList<>();
		images = new ConcurrentHashMap<>();
		visiting = new ConcurrentSkipListSet<>();

		String inputFile = params.get("source");
		this.catalog = catalog;
		String ditaval = params.get("ditaval");

		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(catalog);
		builder.setErrorHandler(new SilentErrorHandler());
		Document doc = builder.build(inputFile);
		String id = doc.getRootElement().getAttributeValue("id");
		List<PI> ish = doc.getPI("ish");
		if (!ish.isEmpty() || id.startsWith("GUID-")) {
			SDLFixer.fix(new File(inputFile).getParentFile(), catalog);
			doc = builder.build(inputFile);
		}

		ScopeBuilder sbuilder = new ScopeBuilder();
		if (dataLogger != null) {
			if (dataLogger.isCancelled()) {
				throw new IOException(Constants.CANCELLED);
			}
			synchronized (dataLogger) {
				dataLogger.log(Messages.getString("DitaParser.01"));
			}
		}
		rootScope = sbuilder.buildScope(inputFile, ditaval, catalog);
		issues.addAll(sbuilder.getIssues());
		scopeBuildTime = sbuilder.getExecutionTime();

		if (ditaval != null) {
			parseDitaVal(ditaval, catalog);
		}

		// Start by queueing the input file for parallel processing
		pendingRecurse.add(inputFile);

		String maxThreadsParam = params.get("maxThreads");
		int maxThreads = Integer.parseInt(maxThreadsParam);

		int count = 0;
		ExecutorService executor = Executors.newFixedThreadPool(maxThreads);
		try {
			do {
				List<String> files = new ArrayList<>(pendingRecurse);
				pendingRecurse.clear();
				
				List<java.util.concurrent.Future<?>> futures = files.stream()
					.filter(file -> !recursed.contains(file))
					.map(file -> executor.submit((Runnable) () -> {
						// Use visiting as a semaphore to prevent concurrent processing of the same file
						if (!visiting.add(file)) {
							return; // Another thread is already processing this file
						}
						try {
							if (dataLogger != null) {
								if (dataLogger.isCancelled()) {
									throw new IOException(Constants.CANCELLED);
								}
								synchronized (dataLogger) {
									dataLogger.log(new File(file).getName());
								}
							}
							SAXBuilder threadBuilder = new SAXBuilder();
							threadBuilder.setEntityResolver(catalog);
							threadBuilder.setErrorHandler(new SilentErrorHandler());
							Element e = threadBuilder.build(file).getRootElement();
							if ("svg".equals(e.getName())) {
								if (containsText(e)) {
									translatableSVG.add(file);
								} else {
									recursed.add(file);
									return;
								}
							}
							// Check if file has translate="no" attribute
							if (e.getAttributeValue("translate", "yes").equals("no")) {
								synchronized (ignored) {
									ignored.add(file);
								}
								MessageFormat mf = new MessageFormat(Messages.getString("DitaParser.02"));
								String issue = mf.format(new Object[] { file });
								logger.log(Level.WARNING, issue);
								synchronized (issues) {
									if (!issues.contains(issue)) {
										issues.add(issue);
									}
								}
								recursed.add(file);
								return;
							}
							recurse(e, file);
							recursed.add(file);
							filesMap.add(file);
						} catch (IOException ex) {
							if (Constants.CANCELLED.equals(ex.getMessage())) {
								throw new RuntimeException(ex);
							}
							// ignore images and other IO errors
						} catch (SAXException | ParserConfigurationException ex) {
							// ignore images
						} finally {
							visiting.remove(file);
						}
				}))
						.collect(Collectors.toList());
				
				// Wait for all tasks to complete
				for (java.util.concurrent.Future<?> future : futures) {
					try {
						future.get();
					} catch (java.util.concurrent.ExecutionException ex) {
						if (ex.getCause() instanceof RuntimeException && 
							ex.getCause().getCause() instanceof IOException &&
							Constants.CANCELLED.equals(ex.getCause().getCause().getMessage())) {
							throw new IOException(Constants.CANCELLED);
						}
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
						throw new IOException("File discovery interrupted", ex);
					}
				}
				count++;
			} while (!pendingRecurse.isEmpty() && count < 4);
		} finally {
			executor.shutdown();
			try {
				// Wait for tasks to complete, then force shutdown
				if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
					executor.shutdownNow();
					// Wait for forced shutdown to complete
					if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
						logger.log(Level.WARNING, "DitaParser thread pool did not terminate");
					}
				}
			} catch (InterruptedException ex) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
		result.addAll(filesMap);
		return result;
	}

	private boolean containsText(Element e) {
		if ("text".equals(e.getName()) && !svgText(e).isEmpty()) {
			return true;
		}
		if ("title".equals(e.getName()) && !svgText(e).isEmpty()) {
			return true;
		}
		if ("desc".equals(e.getName()) && !svgText(e).isEmpty()) {
			return true;
		}
		List<Element> children = e.getChildren();
		Iterator<Element> it = children.iterator();
		while (it.hasNext()) {
			if (containsText(it.next())) {
				return true;
			}
		}
		return false;
	}

	private String svgText(Element e) {
		StringBuilder sb = new StringBuilder();
		List<XMLNode> content = e.getContent();
		Iterator<XMLNode> it = content.iterator();
		while (it.hasNext()) {
			XMLNode node = it.next();
			if (node.getNodeType() == XMLNode.TEXT_NODE) {
				TextNode t = (TextNode) node;
				sb.append(t.getText());
			}
			if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
				sb.append(svgText((Element) node));
			}
		}
		return sb.toString();
	}

	private void recurse(Element e, String parentFile) throws IOException, SAXException, ParserConfigurationException {
		if (filterOut(e)) {
			return;
		}
		if (e.getAttributeValue("translate", "yes").equals("no")) {
			return;
		}
		if (ditaClass(e, "map/topicref") || isTopicref(e.getName())) {
			String href = "";
			String keyref = e.getAttributeValue("keyref");
			if (!keyref.isEmpty()) {
				Key k = rootScope.getKey(keyref);
				if (k != null) {
					Set<String> keySet = usedKeys.computeIfAbsent(k, key -> new ConcurrentSkipListSet<>());
					keySet.add(parentFile);
					href = k.getHref();
				} else {
					href = e.getAttributeValue("href");
					if (!href.isEmpty()) {
						href = URLDecoder.decode(href, StandardCharsets.UTF_8);
						// use href as fallback mechanism
						String format = e.getAttributeValue("format", "dita");
						if (format.startsWith("dita")) {
							href = Utils.getAbsolutePath(parentFile, href);
						}
					}
				}
				if (k != null && k.getTopicmeta() != null && e.getContent().isEmpty()) {
					e.addContent(k.getTopicmeta());
					return;
				}
			} else {
				href = e.getAttributeValue("href");
				String format = e.getAttributeValue("format", "dita");
				if (!href.isEmpty() && format.startsWith("dita")) {
					href = URLDecoder.decode(href, StandardCharsets.UTF_8);
					href = Utils.getAbsolutePath(parentFile, href);
				}
			}
			if (href.startsWith("http:") || href.startsWith("https:") || href.startsWith("ftp:")
					|| href.startsWith("mailto:")) {
				e.setAttribute("scope", "external");
				return;
			}
			String format = e.getAttributeValue("format", "dita");
			if (!href.isEmpty() && ditaClass(e, "ditavalref-d/ditavalref")) {
				skipped.add(href);
				return;
			}
			if (!href.isEmpty() && !format.startsWith("dita")) {
				return;
			}
			if (!href.isEmpty() && !href.equals(parentFile)) {
				href = URLDecoder.decode(href, StandardCharsets.UTF_8);
				File file = new File(href);
				if (file.getName().indexOf('#') != -1) {
					// remove fragment identifier
					file = new File(file.getParentFile(), file.getName().substring(0, file.getName().indexOf('#')));
					href = file.getAbsolutePath();
				}
				if (file.exists()) {
					// Queue file for parallel processing instead of processing synchronously
					if (!recursed.contains(href) && !pendingRecurse.contains(href)) {
						pendingRecurse.add(href);
					}
				} else {
					MessageFormat mf = new MessageFormat(Messages.getString("DitaParser.03"));
					String issue = mf.format(new Object[] { href });
					logger.log(Level.WARNING, issue);
					if (!issues.contains(issue)) {
						issues.add(issue);
					}
				}
			}
		} else {
			String conref = e.getAttributeValue("conref");
			String conkeyref = e.getAttributeValue("conkeyref");
			String conaction = e.getAttributeValue("conaction");
			if (!conaction.isEmpty()) { // it's a conref push
				conkeyref = "";
			}
			if (!conref.isEmpty()) {
				if ("#".equals(conref)) {
					MessageFormat mf = new MessageFormat(Messages.getString("DitaParser.06"));
					String issue = mf.format(new String[] { parentFile });
					logger.log(Level.WARNING, issue);
					if (!issues.contains(issue)) {
						issues.add(issue);
					}
				} else {
					conref = URLDecoder.decode(conref, StandardCharsets.UTF_8);
					if (!visiting.contains(conref)) {
						visiting.add(conref);
					} else {
						MessageFormat mf = new MessageFormat(Messages.getString("DitaParser.07"));
						String issue = mf.format(new Object[] { e.getAttributeValue("conref"), parentFile });
						logger.log(Level.WARNING, issue);
						if (!issues.contains(issue)) {
							issues.add(issue);
						}
						return;
					}
					if (conref.indexOf('#') != -1) {
						String file = conref.substring(0, conref.indexOf('#'));
						if (file.isEmpty()) {
							file = parentFile;
						} else {
							file = Utils.getAbsolutePath(parentFile, file);
						}
						String id = conref.substring(conref.indexOf('#') + 1);
						try {
							Element ref = getReferenced(file, id);
							if (ref != null) {
								e.setContent(ref.getContent());
								List<Element> children = e.getChildren();
								Iterator<Element> ie = children.iterator();
								while (ie.hasNext()) {
									Element child = ie.next();
									recurse(child, file);
								}
							} else {
								MessageFormat mf = new MessageFormat(Messages.getString("DitaParser.08"));
								String issue = mf.format(new Object[] { conref, parentFile });
								logger.log(Level.WARNING, issue);
								if (!issues.contains(issue)) {
									issues.add(issue);
								}
							}
						} catch (Exception ex) {
							MessageFormat mf = new MessageFormat(Messages.getString("DitaParser.09"));
							String issue = mf.format(new Object[] { conref, parentFile });
							logger.log(Level.WARNING, issue);
							if (!issues.contains(issue)) {
								issues.add(issue);
							}
						}
						return;
					} else {
						// points to the root element
						try {
							String file = Utils.getAbsolutePath(parentFile, conref);
							Element ref = getRoot(file);
							e.setContent(ref.getContent());
							List<Element> children = e.getChildren();
							Iterator<Element> ie = children.iterator();
							while (ie.hasNext()) {
								Element child = ie.next();
								recurse(child, file);
							}
						} catch (Exception ex) {
							MessageFormat mf = new MessageFormat(Messages.getString("DitaParser.10"));
							String issue = mf.format(new Object[] { e.getAttributeValue("conref"), parentFile });
							logger.log(Level.WARNING, issue);
							if (!issues.contains(issue)) {
								issues.add(issue);
							}
						}
					}
					visiting.remove(conref);
				}
			}

			if (!conkeyref.isEmpty()) {
				int end = conkeyref.indexOf('/');
				String key = end != -1 ? conkeyref.substring(0, end) : conkeyref;
				String id = end != -1 ? conkeyref.substring(end + 1) : "";
				Key k = rootScope.getKey(key);
				if (k == null) {
					MessageFormat mf = new MessageFormat(Messages.getString("DitaParser.11"));
					String issue = mf.format(new Object[] { conkeyref });
					logger.log(Level.WARNING, issue);
					if (!issues.contains(issue)) {
						issues.add(issue);
					}
					return;
				}
				Set<String> keySet = usedKeys.computeIfAbsent(k, k2 -> new ConcurrentSkipListSet<>());
				keySet.add(parentFile);
				String file = k.getHref();
				if (file == null) {
					MessageFormat mf = new MessageFormat(Messages.getString("DitaParser.12"));
					String issue = mf.format(new Object[] { conkeyref });
					logger.log(Level.WARNING, issue);
					if (!issues.contains(issue)) {
						issues.add(issue);
					}
					return;
				}

				Element ref = getConKeyReferenced(file, id);
				if (ref != null) {
					e.setContent(ref.getContent());
					List<Element> children = e.getChildren();
					Iterator<Element> ie = children.iterator();
					while (ie.hasNext()) {
						recurse(ie.next(), file);
					}
					return;
				}
				MessageFormat mf = new MessageFormat(Messages.getString("DitaParser.13"));
				String issue = mf.format(new Object[] { conkeyref, parentFile });
				logger.log(Level.WARNING, issue);
				if (!issues.contains(issue)) {
					issues.add(issue);
				}
				return;
			}

			String keyref = e.getAttributeValue("keyref");
			if (!keyref.isEmpty()) {
				if (keyref.indexOf('/') == -1) {
					Key k = rootScope.getKey(keyref);
					if (k != null) {
						Set<String> keySet = usedKeys.computeIfAbsent(k, k2 -> new ConcurrentSkipListSet<>());
						keySet.add(parentFile);
						if (k.getTopicmeta() != null && e.getContent().isEmpty()) {

							// empty element that reuses from <topicmeta>
							Element matched = getMatched(e.getName(), k.getTopicmeta());
							if (matched != null) {
								e.setContent(matched.getContent());
								return;
							}
							if (ditaClass(e, "topic/image") || isImage(e.getName())) {
								Element keyword = getMatched("keyword", k.getTopicmeta());
								if (keyword != null) {
									Element alt = new Element("alt");
									alt.setContent(keyword.getContent());
									e.addContent(alt);
									return;
								}
							}
							if (ditaClass(e, "topic/xref") || ditaClass(e, "topic/link") || isXref(e.getName())
									|| isLink(e.getName())) {
								Element keyword = getMatched("keyword", k.getTopicmeta());
								if (keyword != null) {
									Element alt = new Element("linktext");
									alt.setContent(keyword.getContent());
									e.addContent(alt);
									return;
								}
							}
							Element keyword = getMatched("keyword", k.getTopicmeta());
							if (keyword != null) {
								e.addContent(keyword);
							}
							return;
						}
						pendingRecurse.add(k.getHref());
						return;
					}
				} else {

					// behaves like a conkeyref
					// locate an element in the file referenced by the key

					String key = keyref.substring(0, keyref.indexOf('/'));
					String id = keyref.substring(keyref.indexOf('/') + 1);
					Key k = rootScope.getKey(key);
					if (k != null) {
						Set<String> keySet = usedKeys.computeIfAbsent(k, k2 -> new ConcurrentSkipListSet<>());
						keySet.add(parentFile);
						String kref = k.getHref();
						filesMap.add(kref);
						if (!visiting.contains(id + "|" + kref)) {
							visiting.add(id + "|" + kref);
							Element ref = getConKeyReferenced(k.getHref(), id);
							if (ref != null) {
								e.setContent(ref.getContent());
								List<Element> children = e.getChildren();
								Iterator<Element> ie = children.iterator();
								while (ie.hasNext()) {
									recurse(ie.next(), kref);
								}
								return;
							}
							visiting.remove(id + "|" + kref);
						} else {
							MessageFormat mf = new MessageFormat(Messages.getString("DitaParser.14"));
							String issue = mf.format(new Object[] { keyref, parentFile });
							logger.log(Level.WARNING, issue);
							if (!issues.contains(issue)) {
								issues.add(issue);
							}
						}
					}
				}
				MessageFormat mf = new MessageFormat(Messages.getString("DitaParser.15"));
				String issue = mf.format(new Object[] { keyref, parentFile });
				logger.log(Level.WARNING, issue);
				if (!issues.contains(issue)) {
					issues.add(issue);
				}
			}

			String href = e.getAttributeValue("href");
			if (!href.isEmpty() && (ditaClass(e, "topic/image") || isImage(e.getName()))
					&& !"external".equals(e.getAttributeValue("scope"))) {
				// check for SVG
				String imagePath = "";
				try {
					href = URLDecoder.decode(href, StandardCharsets.UTF_8);
					String path = Utils.getAbsolutePath(parentFile, href);
					File f = new File(path);
					if (f.exists()) {
						if (dataLogger != null) {
							if (dataLogger.isCancelled()) {
								throw new IOException(Constants.CANCELLED);
							}
							dataLogger.log(f.getName());
						}
						imagePath = path;
						SAXBuilder builder = new SAXBuilder();
						builder.setEntityResolver(catalog);
						builder.setErrorHandler(new SilentErrorHandler());
						Element svg = builder.build(f).getRootElement();
						if ("svg".equals(svg.getName())) {
							if (hasText(svg)) {
								translatableSVG.add(path);
								filesMap.add(path);
							} else {
								// untranslatable svg, add it to images list
								if (!imagePath.isEmpty()) {
									addToImages(imagePath, href, parentFile);
								}
							}
						}
					}
				} catch (Exception ex) {
					if (!imagePath.isEmpty()) {
						addToImages(imagePath, href, parentFile);
					}
				}
			}
		}
		List<Element> children = e.getChildren();
		Iterator<Element> it = children.iterator();
		while (it.hasNext()) {
			Element child = it.next();
			recurse(child, parentFile);
		}
	}

	private void addToImages(String imagePath, String href, String parentFile) {
		JSONObject json = new JSONObject();
		json.put("imagePath", imagePath);
		json.put("href", href);
		List<String> list = images.computeIfAbsent(parentFile, s -> new ArrayList<>());
		synchronized (list) {
			String string = json.toString();
			if (!list.contains(string)) {
				list.add(string);
			}
		}
	}

	private Element getRoot(String file) throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(catalog);
		builder.setErrorHandler(new SilentErrorHandler());
		Document doc = builder.build(file);
		return doc.getRootElement();
	}

	private static boolean hasText(Element svg) {
		String name = svg.getName();
		if ("text".equals(name) && translatableText(svg.getText().strip())) {
			return true;
		}
		if ("title".equals(name) && translatableText(svg.getText().strip())) {
			return true;
		}
		if ("desc".equals(name) && translatableText(svg.getText().strip())) {
			return true;
		}
		List<Element> children = svg.getChildren();
		Iterator<Element> it = children.iterator();
		while (it.hasNext()) {
			if (hasText(it.next())) {
				return true;
			}
		}
		return false;
	}

	private static boolean translatableText(String string) {
		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			if (Character.isSpaceChar(c)) {
				continue;
			}
			if (Character.isLetterOrDigit(c)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isImage(String name) {
		if (imageSet == null) {
			imageSet = new TreeSet<>();
			imageSet.add("image");
			imageSet.add("glossSymbol");
			imageSet.add("hazardsymbol");
		}
		return imageSet.contains(name);
	}

	public static boolean isLink(String name) {
		if (linkSet == null) {
			linkSet = new TreeSet<>();
			linkSet.add("link");
		}
		return linkSet.contains(name);
	}

	public static boolean isXref(String name) {
		if (xrefSet == null) {
			xrefSet = new TreeSet<>();
			xrefSet.add("xref");
			xrefSet.add("glossAlternateFor");
			xrefSet.add("coderef");
			xrefSet.add("fragref");
			xrefSet.add("synnoteref");
			xrefSet.add("mathmlref");
			xrefSet.add("svgref");
		}
		return xrefSet.contains(name);
	}

	private boolean isTopicref(String name) {
		if (topicrefSet == null) {
			topicrefSet = new TreeSet<>();
			topicrefSet.add("topicref");
			topicrefSet.add("abbrevlist");
			topicrefSet.add("amendments");
			topicrefSet.add("appendix");
			topicrefSet.add("backmatter");
			topicrefSet.add("bibliolist");
			topicrefSet.add("bookabstract");
			topicrefSet.add("booklist");
			topicrefSet.add("booklists");
			topicrefSet.add("chapter");
			topicrefSet.add("colophon");
			topicrefSet.add("dedication");
			topicrefSet.add("draftintro");
			topicrefSet.add("figurelist");
			topicrefSet.add("frontmatter");
			topicrefSet.add("glossarylist");
			topicrefSet.add("indexlist");
			topicrefSet.add("notices");
			topicrefSet.add("part");
			topicrefSet.add("preface");
			topicrefSet.add("tablelist");
			topicrefSet.add("toc");
			topicrefSet.add("trademarklist");
			topicrefSet.add("anchorref");
			topicrefSet.add("keydef");
			topicrefSet.add("mapref");
			topicrefSet.add("topicgroup");
			topicrefSet.add("topichead");
			topicrefSet.add("topicset");
			topicrefSet.add("topicsetref");
			topicrefSet.add("ditavalref");
			topicrefSet.add("glossref");
			topicrefSet.add("subjectref");
			topicrefSet.add("topicapply");
			topicrefSet.add("topicsubject");
			topicrefSet.add("defaultSubject");
			topicrefSet.add("enumerationdef");
			topicrefSet.add("hasInstance");
			topicrefSet.add("hasKind");
			topicrefSet.add("hasNarrower");
			topicrefSet.add("hasPart");
			topicrefSet.add("hasRelated");
			topicrefSet.add("relatedSubjects");
			topicrefSet.add("subjectdef");
			topicrefSet.add("subjectHead");
			topicrefSet.add("schemeref");
			topicrefSet.add("learningContentRef");
			topicrefSet.add("learningGroup");
			topicrefSet.add("learningGroupMapRef");
			topicrefSet.add("learningObject");
			topicrefSet.add("learningObjectMapRef");
			topicrefSet.add("learningOverviewRef");
			topicrefSet.add("learningPlanRef");
			topicrefSet.add("learningPostAssessmentRef");
			topicrefSet.add("learningPreAssessmentRef");
			topicrefSet.add("learningSummaryRef");
		}
		return topicrefSet.contains(name);
	}

	public static Element getMatched(String name, Element topicmeta) {
		if (topicmeta.getName().equals(name)) {
			return topicmeta;
		}
		List<Element> children = topicmeta.getChildren();
		Iterator<Element> it = children.iterator();
		while (it.hasNext()) {
			Element child = it.next();
			if (child.getName().equals(name)) {
				return child;
			}
			Element res = getMatched(name, child);
			if (res != null) {
				return res;
			}
		}
		return null;
	}

	public static boolean ditaClass(Element e, String string) {
		String cls = e.getAttributeValue("class");
		String[] parts = cls.split("\\s");
		for (int i = 0; i < parts.length; i++) {
			if (parts[i].equals(string)) {
				return true;
			}
		}
		return false;
	}

	private void parseDitaVal(String ditaval, Catalog catalog)
			throws SAXException, IOException, ParserConfigurationException {
		if (dataLogger != null) {
			if (dataLogger.isCancelled()) {
				throw new IOException(Constants.CANCELLED);
			}
			synchronized (dataLogger) {
				dataLogger.log(new File(ditaval).getName());
			}
		}
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(catalog);
		builder.setErrorHandler(new SilentErrorHandler());
		Document doc = builder.build(ditaval);
		Element root = doc.getRootElement();
		if (root.getName().equals("val")) {
			List<Element> props = root.getChildren("prop");
			Iterator<Element> it = props.iterator();
			excludeTable = new HashMap<>();
			includeTable = new HashMap<>();
			while (it.hasNext()) {
				Element prop = it.next();
				if (prop.getAttributeValue("action", "include").equals("exclude")) {
					String att = prop.getAttributeValue("att");
					String val = prop.getAttributeValue("val");
					if (!att.isEmpty()) {
						Set<String> set = excludeTable.get(att);
						if (set == null) {
							set = new HashSet<>();
						}
						if (!val.isEmpty()) {
							set.add(val);
						}
						excludeTable.put(att, set);
					}
				}
				if (prop.getAttributeValue("action", "include").equals("include")) {
					String att = prop.getAttributeValue("att");
					String val = prop.getAttributeValue("val");
					if (!att.isEmpty() && !val.isEmpty()) {
						Set<String> set = includeTable.get(att);
						if (set == null) {
							set = new HashSet<>();
						}
						set.add(val);
						includeTable.put(att, set);
					}
				}
			}
			filterAttributes = true;
		}
	}

	private boolean filterOut(Element e) {
		if (filterAttributes) {
			List<Attribute> atts = e.getAttributes();
			Iterator<Attribute> it = atts.iterator();
			while (it.hasNext()) {
				Attribute a = it.next();
				if (excludeTable.containsKey(a.getName())) {
					Set<String> forbidden = excludeTable.get(a.getName());
					if (forbidden.isEmpty() && includeTable.containsKey(a.getName())) {
						Set<String> allowed = includeTable.get(a.getName());
						StringTokenizer tokenizer = new StringTokenizer(a.getValue());
						while (tokenizer.hasMoreTokens()) {
							String token = tokenizer.nextToken();
							if (allowed.contains(token)) {
								return false;
							}
						}
					}
					StringTokenizer tokenizer = new StringTokenizer(a.getValue());
					List<String> tokens = new ArrayList<>();
					while (tokenizer.hasMoreTokens()) {
						tokens.add(tokenizer.nextToken());
					}
					if (forbidden.containsAll(tokens)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private Element getReferenced(String file, String id)
			throws SAXException, IOException, ParserConfigurationException {
		StringArray array = new StringArray(file, id);
		if (referenceChache.containsKey(array)) {
			return referenceChache.get(array);
		}
		if (dataLogger != null) {
			if (dataLogger.isCancelled()) {
				throw new IOException(Constants.CANCELLED);
			}
			synchronized (dataLogger) {
				dataLogger.log(new File(file).getName());
			}
		}
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(catalog);
		builder.setErrorHandler(new SilentErrorHandler());
		Document doc = builder.build(file);
		Element root = doc.getRootElement();
		String topicId = root.getAttributeValue("id");
		if (topicId.equals(id)) {
			if (!filesMap.contains(file)) {
				filesMap.add(file);
			}
			if (!searchedConref.contains(array)) {
				searchedConref.add(array);
				recurse(root, file);
				searchedConref.remove(array);
			}
			referenceChache.put(array, root);
			return root;
		}
		Element result = locate(root, topicId, id);
		if (result != null) {
			if (!filesMap.contains(file)) {
				filesMap.add(file);
			}
			if (!searchedConref.contains(array)) {
				searchedConref.add(array);
				recurse(result, file);
				searchedConref.remove(array);
			}
			referenceChache.put(array, result);
		}
		return result;
	}

	private Element locate(Element e, String topic, String searched) {
		String topicId = topic;
		String id = e.getAttributeValue("id");
		if (searched.equals(topicId + "/" + id)) {
			return e;
		}
		if (ditaClass(e, "topic/topic") || isTopic(e.getName())) {
			topicId = id;
		}
		List<Element> children = e.getChildren();
		Iterator<Element> it = children.iterator();
		while (it.hasNext()) {
			Element child = it.next();
			Element result = locate(child, topicId, searched);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	private boolean isTopic(String name) {
		if (topicSet == null) {
			topicSet = new TreeSet<>();
			topicSet.add("topic");
			topicSet.add("concept");
			topicSet.add("glossentry");
			topicSet.add("reference");
			topicSet.add("task");
			topicSet.add("troubleshooting");
			topicSet.add("glossgroup");
			topicSet.add("learningAssessment");
			topicSet.add("learningBase");
			topicSet.add("learningContent");
			topicSet.add("learningOverview");
			topicSet.add("learningSummary");
			topicSet.add("learningPlan");
		}
		return topicSet.contains(name);
	}

	protected Element getConKeyReferenced(String file, String id)
			throws SAXException, IOException, ParserConfigurationException {
		StringArray array = new StringArray(file, id);
		if (referenceChache.containsKey(array)) {
			return referenceChache.get(array);
		}
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(catalog);
		builder.setErrorHandler(new SilentErrorHandler());
		if (dataLogger != null) {
			if (dataLogger.isCancelled()) {
				throw new IOException(Constants.CANCELLED);
			}
			dataLogger.log(new File(file).getName());
		}
		Document doc = builder.build(file);
		if (id.isEmpty()) {
			Element result = doc.getRootElement();
			referenceChache.put(array, result);
			return result;
		}
		Element result = locateReferenced(doc.getRootElement(), id);
		if (result != null) {
			referenceChache.put(array, result);
		}
		return result;
	}

	private Element locateReferenced(Element root, String id) {
		String current = root.getAttributeValue("id");
		if (current.equals(id)) {
			return root;
		}
		List<Element> children = root.getChildren();
		Iterator<Element> it = children.iterator();
		while (it.hasNext()) {
			Element child = it.next();
			Element result = locateReferenced(child, id);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	public Scope getScope() {
		return rootScope;
	}

	public List<String> getIgnored() {
		return ignored;
	}

	public static void setDataLogger(ILogger dataLogger) {
		DitaParser.dataLogger = dataLogger;
	}

	public List<String> getIssues() {
		return issues;
	}

	public List<String> getSkipped() {
		return skipped;
	}

	public Map<String, List<String>> getImages() {
		return images;
	}

	public Set<String> getTranslatableSVG() {
		return translatableSVG;
	}

	public long getScopeBuildTime() {
		return scopeBuildTime;
	}
}
