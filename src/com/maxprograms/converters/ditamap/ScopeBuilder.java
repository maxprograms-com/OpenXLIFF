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

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Utils;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.SilentErrorHandler;

public class ScopeBuilder {

	private static Logger logger = System.getLogger(ScopeBuilder.class.getName());

	private Scope currentScope;
	private Set<String> recursed;

	private Catalog catalog;
	private static Map<String, Set<String>> excludeTable;
	private static Map<String, Set<String>> includeTable;
	private static boolean filterAttributes;
	private List<String> issues;
	private long executionTime = 0l;

	public Scope buildScope(String inputFile, String ditavalFile, Catalog catalog)
			throws SAXException, IOException, ParserConfigurationException {
		long start = System.currentTimeMillis();
		this.catalog = catalog;
		issues = new ArrayList<>();
		if (ditavalFile != null) {
			parseDitaVal(ditavalFile, catalog);
		}

		recursed = new TreeSet<>();

		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(catalog);
		builder.setErrorHandler(new SilentErrorHandler());
		Document doc = builder.build(inputFile);
		Element root = doc.getRootElement();

		currentScope = new Scope(root.getAttributeValue("keyscope"));
		Scope rootScope = currentScope;
		recurse(root, inputFile);

		executionTime = System.currentTimeMillis() - start;
		return rootScope;
	}

	private void recurse(Element e, String parentFile) {

		if (filterOut(e)) {
			return;
		}

		if (!e.getAttributeValue("format", "dita").startsWith("dita") && e.getAttributeValue("keys").isEmpty()) {
			return;
		}

		if (e.getAttributeValue("scope", "local").equals("external")) {
			return;
		}

		String scope = e.getAttributeValue("keyscope");
		Scope oldScope = null;
		if (!scope.isEmpty()) {
			oldScope = currentScope;
			Scope c = currentScope.scopeForName(scope);
			currentScope = c;
		}

		String href = e.getAttributeValue("href");

		String path = "";
		if (!href.isEmpty()) {
			href = URLDecoder.decode(href, StandardCharsets.UTF_8);
			try {
				path = Utils.getAbsolutePath(parentFile, href);
				if (!recursed.contains(path)) {
					File f = new File(path);
					if (f.exists() && f.isFile()) {
						String format = e.getAttributeValue("format", "dita");
						if (format.startsWith("dita") && !DitaParser.ditaClass(e, "topic/image")) {
							SAXBuilder builder = new SAXBuilder();
							builder.setEntityResolver(catalog);
							builder.setErrorHandler(new SilentErrorHandler());
							Element root = builder.build(f).getRootElement();
							recursed.add(path);
							recurse(root, path);
						}
					}
				}
			} catch (IOException | SAXException | ParserConfigurationException e1) {
				// skipped images and broken links
			}
		}

		String val = e.getAttributeValue("keys");

		if (!val.isEmpty()) {
			String[] keys = val.split("\\s");
			Element topicmeta = e.getChild("topicmeta");
			String keyref = e.getAttributeValue("keyref");
			for (int i = 0; i < keys.length; i++) {
				String key = keys[i];
				if (!keyref.isEmpty()) {
					if (!currentScope.addKey(new Key(key, keyref, parentFile))) {
						MessageFormat mf = new MessageFormat(Messages.getString("ScopeBuilder.1"));
						String issue = mf.format(new Object[] { key, keyref });
						logger.log(Level.WARNING, issue);
						issues.add(issue);
					}
				} else {
					boolean translate = "yes".equals(e.getAttributeValue("translate", "yes"));
					if (href.isEmpty()) {
						if (!currentScope.addKey(new Key(key, parentFile, topicmeta, parentFile, translate))) {
							MessageFormat mf = new MessageFormat(Messages.getString("ScopeBuilder.2"));
							String issue = mf.format(new Object[] { key });
							logger.log(Level.WARNING, issue);
							issues.add(issue);
						}
					} else {
						try {
							path = Utils.getAbsolutePath(parentFile, href);
							File f = new File(path);
							if (f.exists() && f.isFile()
									&& !currentScope.addKey(new Key(key, path, topicmeta, parentFile, translate))) {
								MessageFormat mf = new MessageFormat(Messages.getString("ScopeBuilder.1"));
								String issue = mf.format(new Object[] { key, path });
								logger.log(Level.WARNING, issue);
								issues.add(issue);
							}
						} catch (IOException _) {
							// ignore files that can't be parsed
						}
					}
				}
			}
		}
		List<Element> children = e.getChildren();
		Iterator<Element> it = children.iterator();
		while (it.hasNext()) {
			recurse(it.next(), parentFile);
		}
		if (!scope.isEmpty()) {
			currentScope = oldScope;
		}
	}

	private static void parseDitaVal(String ditaval, Catalog catalog)
			throws SAXException, IOException, ParserConfigurationException {
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

	private static boolean filterOut(Element e) {
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

	public List<String> getIssues() {
		return issues;
	}

	public long getExecutionTime() {
		return executionTime;
	}
}
