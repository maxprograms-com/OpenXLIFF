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
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;

import com.maxprograms.languages.RegistryParser;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLNode;

public class Xliff20 {

	private static final String XLIFF_VALIDATION_2_0 = "urn:oasis:names:tc:xliff:validation:2.0";
	private static final String XLIFF_SIZERESTRICTION_2_0 = "urn:oasis:names:tc:xliff:sizerestriction:2.0";
	private static final String XLIFF_RESOURCEDATA_2_0 = "urn:oasis:names:tc:xliff:resourcedata:2.0";
	private static final String XLIFF_MATCHES_2_0 = "urn:oasis:names:tc:xliff:matches:2.0";
	private static final String XLIFF_GLOSSARY_2_0 = "urn:oasis:names:tc:xliff:glossary:2.0";
	private static final String XLIFF_FS_2_0 = "urn:oasis:names:tc:xliff:fs:2.0";
	private static final String XLIFF_CHANGETRACKING_2_0 = "urn:oasis:names:tc:xliff:changetracking:2.0";
	private static final String XLIFF_METADATA_2_0 = "urn:oasis:names:tc:xliff:metadata:2.0";
	private static final String XLIFF_DOCUMENT_2_0 = "urn:oasis:names:tc:xliff:document:2.0";
	private static final String XLIFF_ITS_2_1 = "urn:oasis:names:tc:xliff:itsm:2.1";
	private static final String XLIFF_DOCUMENT_2_2 = "urn:oasis:names:tc:xliff:document:2.2";
	private static final String XLIFF_PGS_1_0 = "urn:oasis:names:tc:xliff:pgs:1.0";
	private static final String W3_ORG_XML_NAMESPACE = "http://www.w3.org/XML/1998/namespace";
	private static final String W3_ORG_ITS_2_0 = "http://www.w3.org/2005/11/its";

	private static Logger logger = System.getLogger(Xliff20.class.getName());
	private String reason = "";
	private Catalog resolver;

	private RegistryParser registry;

	private Map<String, String> declaredNamespaces;
	private String srcLang;
	private String trgLang;
	private HashSet<String> fileId;
	private HashSet<String> groupId;
	private HashSet<String> unitId;
	private HashSet<String> cantDelete;
	private HashSet<String> sourceId;
	private HashSet<String> dataId;
	private HashSet<String> matchDataId;
	private HashSet<String> matchId;
	private HashSet<String> metaId;
	private HashSet<String> glossId;
	private HashSet<String> noteId;
	private HashSet<String> smId;
	private HashSet<String> orderSet;
	private Map<String, Element> unitScSource;
	private Map<String, Element> unitScTarget;
	private int segCount;
	private int maxSegment;
	private boolean inMatch;
	private boolean isReference;
	private boolean inSource;
	private boolean inTarget;
	private boolean inResourceData;
	private String fsPrefix = "fs";

	private List<String> knownTypes = List.of("fmt", "ui", "quote", "link", "image", "other");
	private List<String> xlfSubTypes = List.of("xlf:lb", "xlf:pb", "xlf:b", "xlf:i", "xlf:u", "xlf:var");
	private List<String> fmtSubTypes = List.of("xlf:b", "xlf:i", "xlf:u", "xlf:lb", "xlf:pb");

	public Xliff20() throws IOException {
		registry = new RegistryParser();
	}

	public boolean validate(String file, String catalog, String version) {
		try {
			StreamSource source = new StreamSource(new File(file));
			resolver = CatalogBuilder.getCatalog(catalog);
			SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			List<Source> schemas = new Vector<>();
			schemas.add(getSource(W3_ORG_XML_NAMESPACE));
			schemas.add(getSource(XLIFF_METADATA_2_0));
			schemas.add(getSource(XLIFF_FS_2_0));
			schemas.add(getSource(XLIFF_GLOSSARY_2_0));
			schemas.add(getSource(XLIFF_MATCHES_2_0));
			schemas.add(getSource(XLIFF_RESOURCEDATA_2_0));
			schemas.add(getSource(XLIFF_SIZERESTRICTION_2_0));
			schemas.add(getSource(XLIFF_VALIDATION_2_0));
			if ("2.0".equals(version)) {
				schemas.add(getSource(XLIFF_DOCUMENT_2_0));
				schemas.add(getSource(XLIFF_CHANGETRACKING_2_0));
			} else if ("2.1".equals(version)) {
				schemas.add(getSource(XLIFF_DOCUMENT_2_0));
				schemas.add(getSource(XLIFF_ITS_2_1));
				schemas.add(getSource(W3_ORG_ITS_2_0));
			} else if ("2.2".equals(version)) {
				schemas.add(getSource(XLIFF_ITS_2_1));
				schemas.add(getSource(XLIFF_DOCUMENT_2_2));
				schemas.add(getSource(XLIFF_PGS_1_0));
				schemas.add(getSource(W3_ORG_ITS_2_0));
			}
			Schema schema = schemaFactory.newSchema(schemas.toArray(new Source[0]));
			schema.newValidator().validate(source);
			return validateContent(file);
		} catch (SAXException | IOException | ParserConfigurationException | URISyntaxException e) {
			logger.log(Level.ERROR, e);
			reason = e.getMessage();
		}
		return false;
	}

	private boolean validateContent(String file) throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		builder.setValidating(true);
		builder.setEntityResolver(resolver);
		Document document = builder.build(file);
		declaredNamespaces = new Hashtable<>();
		return recurse(document.getRootElement());
	}

	private boolean recurse(Element e) {
		String namespace = e.getNamespace();
		if (!namespace.isEmpty()) {

			if (XLIFF_MATCHES_2_0.equals(declaredNamespaces.get(namespace))) {
				// In Matches module

				if ("matches".equals(e.getLocalName())) {
					matchId = new HashSet<>();
				}
				if ("match".equals(e.getLocalName())) {
					String id = e.getAttributeValue("id");
					if (!id.isEmpty()) {
						if (matchId.contains(id)) {
							reason = Messages.getString("Xliff20.01");
							return false;
						}
						matchId.add(id);
					}
					inMatch = true;
					matchDataId = new HashSet<>();
					isReference = e.getAttributeValue("reference", "no").equals("yes");
				}
			}

			if (XLIFF_METADATA_2_0.equals(declaredNamespaces.get(namespace))) {
				// In Metadata module

				if ("metadata".equals(e.getLocalName())) {
					metaId = new HashSet<>();
					String id = e.getAttributeValue("id");
					if (!id.isEmpty()) {
						metaId.add(id);
					}
				}
				if ("metaGroup".equals(e.getLocalName())) {
					String id = e.getAttributeValue("id");
					if (!id.isEmpty()) {
						if (metaId.contains(id)) {
							reason = Messages.getString("Xliff20.02");
							return false;
						}
						metaId.add(id);
					}
				}
			}

			if (XLIFF_GLOSSARY_2_0.equals(declaredNamespaces.get(namespace))) {
				// In Glossary module

				if ("glossary".equals(e.getLocalName())) {
					glossId = new HashSet<>();
				}
				if ("glossEntry".equals(e.getLocalName())) {
					String id = e.getAttributeValue("id");
					if (!id.isEmpty()) {
						if (glossId.contains(id)) {
							reason = Messages.getString("Xliff20.03");
							return false;
						}
						glossId.add(id);
					}
				}
				if ("translation".equals(e.getLocalName())) {
					String id = e.getAttributeValue("id");
					if (!id.isEmpty()) {
						if (glossId.contains(id)) {
							reason = Messages.getString("Xliff20.04");
							return false;
						}
						glossId.add(id);
					}
				}
			}

			if (XLIFF_RESOURCEDATA_2_0.equals(declaredNamespaces.get(namespace))) {
				// In Resource Data module
				if ("resourceItem".equals(e.getLocalName())) {
					inResourceData = true;
				}
			}
		}

		// Element from XLIFF Core

		if ("xliff".equals(e.getLocalName())) {
			srcLang = e.getAttributeValue("srcLang");
			if (!checkLanguage(srcLang)) {
				MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.05"));
				reason = mf.format(new String[] { srcLang });
				return false;
			}
			trgLang = e.getAttributeValue("trgLang");
			if (!trgLang.isEmpty() && !checkLanguage(trgLang)) {
				MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.06"));
				reason = mf.format(new String[] { trgLang });
				return false;
			}
			fileId = new HashSet<>();
		}

		// check namespaces
		List<Attribute> atts = e.getAttributes();
		for (Attribute a : atts) {
			String prefix = a.getNamespace();
			if ("xmlns".equals(prefix)) {
				declaredNamespaces.put(a.getLocalName(), a.getValue());
				if (XLIFF_FS_2_0.equals(a.getValue())) {
					fsPrefix = a.getLocalName();

				}
			}
			if (fsPrefix.equals(prefix) && !("fs".equals(a.getLocalName()) || "subFs".equals(a.getLocalName()))) {
				MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.07"));
				reason = mf.format(new String[] { a.toString() });
				return false;
			}
		}

		if ("file".equals(e.getLocalName())) {
			String id = e.getAttributeValue("id");
			if (fileId.contains(id)) {
				reason = Messages.getString("Xliff20.08");
				return false;
			}
			fileId.add(id);
			groupId = new HashSet<>();
			unitId = new HashSet<>();
			if (noteId != null) {
				noteId = null;
			}
		}

		if ("skeleton".equals(e.getLocalName())) {
			List<XMLNode> content = e.getContent();
			if (content.isEmpty()) {
				String href = e.getAttributeValue("href");
				if (href.isEmpty()) {
					reason = Messages.getString("Xliff20.09");
					return false;
				}
			} else {
				if (!e.getAttributeValue("href").isEmpty()) {
					reason = Messages.getString("Xliff20.10");
					return false;
				}
			}
		}

		if ("notes".equals(e.getLocalName())) {
			noteId = new HashSet<>();
		}

		if ("note".equals(e.getLocalName())) {
			String id = e.getAttributeValue("id");
			if (!id.isBlank()) {
				if (noteId.contains(id)) {
					reason = Messages.getString("Xliff20.11");
					return false;
				}
				noteId.add(id);
			}
		}

		if ("group".equals(e.getLocalName())) {
			String id = e.getAttributeValue("id");
			if (groupId.contains(id)) {
				reason = Messages.getString("Xliff20.12");
				return false;
			}
			groupId.add(id);
		}

		if ("unit".equals(e.getLocalName())) {
			String id = e.getAttributeValue("id");
			if (unitId.contains(id)) {
				reason = Messages.getString("Xliff20.13");
				return false;
			}
			unitId.add(id);
			List<Element> segments = e.getChildren("segment");
			if (segments.isEmpty()) {
				reason = Messages.getString("Xliff20.14");
				return false;
			}
			segCount = 0;
			maxSegment = segments.size() + e.getChildren("ignorable").size();
			dataId = new HashSet<>();
			if (noteId != null) {
				noteId = null;
			}
			smId = new HashSet<>();
			orderSet = new HashSet<>();
			sourceId = new HashSet<>();
			unitScSource = new Hashtable<>();
			unitScTarget = new Hashtable<>();
		}

		if ("ignorable".equals(e.getLocalName())) {
			String id = e.getAttributeValue("id");
			if (!id.isEmpty()) {
				if (sourceId.contains(id)) {
					reason = Messages.getString("Xliff20.15");
					return false;
				}
				sourceId.add(id);
			}
			segCount++;
		}

		if ("segment".equals(e.getLocalName())) {
			String id = e.getAttributeValue("id");
			if (!id.isEmpty()) {
				if (sourceId.contains(id)) {
					reason = Messages.getString("Xliff20.16");
					return false;
				}
				sourceId.add(id);
			}
			String currentState = e.getAttributeValue("state", "initial");
			Element target = e.getChild("target");
			if (target == null && !"initial".equals(currentState)) {
				MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.17"));
				reason = mf.format(new String[] { currentState });
				return false;
			}
			if ("final".equals(currentState) && !validateInlineElements(e)) {
				return false;
			}
			String subState = e.getAttributeValue("subState");
			if (!subState.isEmpty()) {
				String state = e.getAttributeValue("state");
				if (state.isEmpty()) {
					reason = Messages.getString("Xliff20.18");
					return false;
				}
				int index = subState.indexOf(':');
				if (index == -1) {
					MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.19"));
					reason = mf.format(new String[] { subState });
					return false;
				}
			}
			segCount++;
		}

		if ("source".equals(e.getLocalName())) {
			String lang = e.getAttributeValue("xml:lang");
			if (!lang.isEmpty() && !srcLang.equals(lang)) {
				reason = Messages.getString("Xliff20.20");
				return false;
			}
			inSource = true;
			cantDelete = new HashSet<>();
		}

		if ("target".equals(e.getLocalName())) {
			String lang = e.getAttributeValue("xml:lang");
			if (trgLang.isEmpty()) {
				reason = Messages.getString("Xliff20.21");
				return false;
			}
			if (!inMatch && !lang.isEmpty() && !trgLang.equals(lang)) {
				reason = Messages.getString("Xliff20.22");
				return false;
			}
			if (inMatch && !isReference && !lang.isEmpty() && !trgLang.equals(lang)) {
				reason = Messages.getString("Xliff20.23");
				return false;
			}
			if (!inMatch && !inResourceData) {
				String order = e.getAttributeValue("order", "" + segCount);
				int value = Integer.parseInt(order);
				if (value > maxSegment) {
					MessageFormat mf = new MessageFormat(
							Messages.getString("Xliff20.24"));
					reason = mf.format(new String[] { order, "" + maxSegment });
					return false;
				}
				if (orderSet.contains(order)) {
					MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.25"));
					reason = mf.format(new String[] { order });
					return false;
				}
				orderSet.add(order);
			}
			inTarget = true;
		}

		if ("data".equals(e.getLocalName())) {
			String id = e.getAttributeValue("id");
			if (e.hasAttribute("xml:space") && !"preserve".equals(e.getAttributeValue("xml:space"))) {
				reason = Messages.getString("Xliff20.26");
				return false;
			}
			if (inMatch) {
				if (matchDataId.contains(id)) {
					reason = Messages.getString("Xliff20.27");
					return false;
				}
				matchDataId.add(id);
			} else {
				if (dataId.contains(id)) {
					reason = Messages.getString("Xliff20.28");
					return false;
				}
				dataId.add(id);
			}
		}

		// Inline elements

		if ("cp".equals(e.getLocalName())) {
			// #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
			String hex = e.getAttributeValue("hex");
			int value = Integer.valueOf(hex, 16);
			if (value == 0x9 || value == 0xA || value == 0xD || (value >= 0x20 && value <= 0xD7FF)
					|| (value >= 0xE000 && value <= 0xFFFD) || (value >= 0x10000 && value <= 0x10FFFF)) {
				MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.29"));
				reason = mf.format(new String[] { hex });
				return false;
			}
		}

		if ("ph".equals(e.getLocalName())) {
			String id = e.getAttributeValue("id");
			if (inSource) {
				if (sourceId.contains(id)) {
					reason = Messages.getString("Xliff20.30");
					return false;
				}
				sourceId.add(id);
				if (e.getAttributeValue("canDelete", "yes").equals("no")) {
					cantDelete.add(id);
				}
			}
			if (inTarget && e.getAttributeValue("canDelete", "yes").equals("no")) {
				cantDelete.remove(id);
			}
			boolean isCopy = !e.getAttributeValue("copyOf").isEmpty();
			String dataRef = e.getAttributeValue("dataRef");
			if (isCopy && !dataRef.isEmpty()) {
				reason = Messages.getString("Xliff20.31");
				return false;
			}
			if (!dataRef.isEmpty()) {
				if (inMatch) {
					if (!matchDataId.contains(dataRef)) {
						reason = Messages.getString("Xliff20.32");
						return false;
					}
				} else {
					if (!dataId.contains(dataRef)) {
						reason = Messages.getString("Xliff20.33");
						return false;
					}
				}
			}
			String type = e.getAttributeValue("type");
			if (!type.isEmpty() && !knownTypes.contains(type)) {
				reason = Messages.getString("Xliff20.34");
				return false;
			}
			String subType = e.getAttributeValue("subType");
			if (!subType.isEmpty()) {
				if (type.isEmpty()) {
					reason = Messages.getString("Xliff20.35");
					return false;
				}
				int index = subType.indexOf(':');
				if (index == -1) {
					MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.36"));
					reason = mf.format(new String[] { subType });
					return false;
				}
				String prefix = subType.substring(0, index);
				if ("xlf".equals(prefix)) {
					if (!xlfSubTypes.contains(subType)) {
						MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.37"));
						reason = mf.format(new String[] { subType });
						return false;
					}
					if ("ui".equals(type) && !"xlf:var".equals(subType)) {
						reason = Messages.getString("Xliff20.38");
						return false;
					}
					if ("fmt".equals(type) && !fmtSubTypes.contains(subType)) {
						reason = Messages.getString("Xliff20.39");
						return false;
					}
				}
			}
		}

		if ("pc".equals(e.getLocalName())) {
			String id = e.getAttributeValue("id");
			if (inSource) {
				if (sourceId.contains(id)) {
					reason = Messages.getString("Xliff20.40");
					return false;
				}
				sourceId.add(id);
				if (e.getAttributeValue("canDelete", "yes").equals("no")) {
					cantDelete.add(id);
				}
			}
			if (inTarget && e.getAttributeValue("canDelete", "yes").equals("no")) {
				cantDelete.remove(id);
			}
			boolean isCopy = !e.getAttributeValue("copyOf").isEmpty();
			String dataRefStart = e.getAttributeValue("dataRefStart");
			if (isCopy && !dataRefStart.isEmpty()) {
				reason = Messages.getString("Xliff20.42");
				return false;
			}
			if (!dataRefStart.isEmpty()) {
				if (inMatch) {
					if (!matchDataId.contains(dataRefStart)) {
						reason = Messages.getString("Xliff20.43");
						return false;
					}
				} else {
					if (!dataId.contains(dataRefStart)) {
						reason = Messages.getString("Xliff20.44");
						return false;
					}
				}
			}
			String dataRefEnd = e.getAttributeValue("dataRefEnd");
			if (isCopy && !dataRefEnd.isEmpty()) {
				reason = Messages.getString("Xliff20.45");
				return false;
			}
			if (!dataRefEnd.isEmpty()) {
				if (inMatch) {
					if (!matchDataId.contains(dataRefEnd)) {
						reason = Messages.getString("Xliff20.46");
						return false;
					}
				} else {
					if (!dataId.contains(dataRefEnd)) {
						reason = Messages.getString("Xliff20.47");
						return false;
					}
				}
			}
			String type = e.getAttributeValue("type");
			if (!type.isEmpty() && !knownTypes.contains(type)) {
				reason = Messages.getString("Xliff20.48");
				return false;
			}
			String subType = e.getAttributeValue("subType");
			if (!subType.isEmpty()) {
				if (type.isEmpty()) {
					reason = Messages.getString("Xliff20.49");
					return false;
				}
				int index = subType.indexOf(':');
				if (index == -1) {
					MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.50"));
					reason = mf.format(new String[] { subType });
					return false;
				}
				String prefix = subType.substring(0, index);
				if ("xlf".equals(prefix)) {
					if (!xlfSubTypes.contains(subType)) {
						MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.51"));
						reason = mf.format(new String[] { subType });
						return false;
					}
					if ("ui".equals(type) && !"xlf:var".equals(subType)) {
						reason = Messages.getString("Xliff20.52");
						return false;
					}
					if ("fmt".equals(type) && !fmtSubTypes.contains(subType)) {
						reason = Messages.getString("Xliff20.53");
						return false;
					}
				}
			}
		}

		if ("sc".equals(e.getLocalName())) {
			String id = e.getAttributeValue("id");
			if (!"yes".equals(e.getAttributeValue("isolated"))) {
				if (inTarget) {
					unitScTarget.put(id, e);
				} else {
					unitScSource.put(id, e);
				}
			}
			if (inSource) {
				if (sourceId.contains(id)) {
					reason = Messages.getString("Xliff20.54");
					return false;
				}
				sourceId.add(id);
				if (e.getAttributeValue("canDelete", "yes").equals("no")) {
					cantDelete.add(id);
				}
			}
			if (inTarget && e.getAttributeValue("canDelete", "yes").equals("no")) {
				cantDelete.remove(id);
			}
			boolean isCopy = !e.getAttributeValue("copyOf").isEmpty();
			String dataRef = e.getAttributeValue("dataRef");
			if (isCopy && !dataRef.isEmpty()) {
				reason = Messages.getString("Xliff20.55");
				return false;
			}
			if (!dataRef.isEmpty()) {
				if (inMatch) {
					if (!matchDataId.contains(dataRef)) {
						reason = Messages.getString("Xliff20.56");
						return false;
					}
				} else {
					if (!dataId.contains(dataRef)) {
						reason = Messages.getString("Xliff20.57");
						return false;
					}
				}
			}
			String type = e.getAttributeValue("type");
			if (!type.isEmpty() && !knownTypes.contains(type)) {
				reason = Messages.getString("Xliff20.58");
				return false;
			}
			String subType = e.getAttributeValue("subType");
			if (!subType.isEmpty()) {
				if (type.isEmpty()) {
					reason = Messages.getString("Xliff20.59");
					return false;
				}
				int index = subType.indexOf(':');
				if (index == -1) {
					MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.60"));
					reason = mf.format(new String[] { subType });
					return false;
				}
				String prefix = subType.substring(0, index);
				if ("xlf".equals(prefix)) {
					if (!xlfSubTypes.contains(subType)) {
						MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.61"));
						reason = mf.format(new String[] { subType });
						return false;
					}
					if ("ui".equals(type) && !"xlf:var".equals(subType)) {
						reason = Messages.getString("Xliff20.62");
						return false;
					}
					if ("fmt".equals(type) && !fmtSubTypes.contains(subType)) {
						reason = Messages.getString("Xliff20.63");
						return false;
					}
				}
			}
		}

		if ("ec".equals(e.getLocalName())) {
			boolean isolated = e.getAttributeValue("isolated", "no").equals("yes");
			if (isolated) {
				String id = e.getAttributeValue("id");
				if (id.isEmpty()) {
					reason = Messages.getString("Xliff20.64");
					return false;
				}
				if (inSource) {
					if (sourceId.contains(id)) {
						reason = Messages.getString("Xliff20.65");
						return false;
					}
					sourceId.add(id);
					if (e.getAttributeValue("canDelete", "yes").equals("no")) {
						cantDelete.add(id);
					}
				}
				if (inTarget && e.getAttributeValue("canDelete", "yes").equals("no")) {
					cantDelete.remove(id);
				}
			} else {
				if (e.hasAttribute("dir")) {
					reason = Messages.getString("Xliff20.66");
					return false;
				}
				String startRef = e.getAttributeValue("startRef");
				if (startRef.isEmpty()) {
					reason = Messages.getString("Xliff20.67");
					return false;
				}
				Map<String, Element> scMap = inTarget ? unitScTarget : unitScSource;
				if (!scMap.containsKey(startRef)) {
					MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.68"));
					reason = mf.format(new String[] { startRef });
					return false;
				}
				Element sc = scMap.get(startRef);
				if (!sc.getAttributeValue("canCopy", "yes").equals(e.getAttributeValue("canCopy", "yes"))) {
					MessageFormat mf = new MessageFormat(
							Messages.getString("Xliff20.69"));
					reason = mf.format(new String[] { startRef });
					return false;
				}
				if (!sc.getAttributeValue("canDelete", "yes").equals(e.getAttributeValue("canDelete", "yes"))) {
					MessageFormat mf = new MessageFormat(
							Messages.getString("Xliff20.70"));
					reason = mf.format(new String[] { startRef });
					return false;
				}
				if (!sc.getAttributeValue("canOverlap", "yes").equals(e.getAttributeValue("canOverlap", "yes"))) {
					MessageFormat mf = new MessageFormat(
							Messages.getString("Xliff20.71"));
					reason = mf.format(new String[] { startRef });
					return false;
				}
				if (!sc.getAttributeValue("canReorder", "yes").equals(e.getAttributeValue("canReorder", "yes"))) {
					MessageFormat mf = new MessageFormat(
							Messages.getString("Xliff20.72"));
					reason = mf.format(new String[] { startRef });
					return false;
				}
				scMap.remove(startRef);
			}
			boolean isCopy = !e.getAttributeValue("copyOf").isEmpty();
			String dataRef = e.getAttributeValue("dataRef");
			if (isCopy && !dataRef.isEmpty()) {
				reason = Messages.getString("Xliff20.73");
				return false;
			}
			if (!dataRef.isEmpty()) {
				if (inMatch) {
					if (!matchDataId.contains(dataRef)) {
						reason = Messages.getString("Xliff20.74");
						return false;
					}
				} else {
					if (!dataId.contains(dataRef)) {
						reason = Messages.getString("Xliff20.75");
						return false;
					}
				}
			}
			String type = e.getAttributeValue("type");
			if (!type.isEmpty() && !knownTypes.contains(type)) {
				reason = Messages.getString("Xliff20.76");
				return false;
			}
			String subType = e.getAttributeValue("subType");
			if (!subType.isEmpty()) {
				if (type.isEmpty()) {
					reason = Messages.getString("Xliff20.77");
					return false;
				}
				int index = subType.indexOf(':');
				if (index == -1) {
					MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.78"));
					reason = mf.format(new String[] { subType });
					return false;
				}
				String prefix = subType.substring(0, index);
				if ("xlf".equals(prefix)) {
					if (!xlfSubTypes.contains(subType)) {
						MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.79"));
						reason = mf.format(new String[] { subType });
						return false;
					}
					if ("ui".equals(type) && !"xlf:var".equals(subType)) {
						reason = Messages.getString("Xliff20.80");
						return false;
					}
					if ("fmt".equals(type) && !fmtSubTypes.contains(subType)) {
						reason = Messages.getString("Xliff20.81");
						return false;
					}
				}
			}
		}

		if ("mrk".equals(e.getLocalName())) {
			String id = e.getAttributeValue("id");
			if (inSource) {
				if (sourceId.contains(id)) {
					reason = Messages.getString("Xliff20.82");
					return false;
				}
				sourceId.add(id);
			}
			String type = e.getAttributeValue("type");
			if ("comment".equals(type)) {
				if (e.getAttributeValue("value").isEmpty()) {
					String ref = e.getAttributeValue("ref");
					if (ref.isEmpty()) {
						reason = Messages.getString("Xliff20.83");
						return false;
					}
					if (!ref.startsWith("#n") || ref.indexOf('=') == -1) {
						MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.84"));
						reason = mf.format(new String[] { ref });
						return false;
					}
					String refId = ref.substring(ref.indexOf('=') + 1);
					if (noteId == null || !noteId.contains(refId)) {
						reason = Messages.getString("Xliff20.85");
						return false;
					}
				} else {
					if (!e.getAttributeValue("ref").isEmpty()) {
						reason = Messages.getString("Xliff20.86");
						return false;
					}
				}
			}
		}

		if ("sm".equals(e.getLocalName())) {
			String id = e.getAttributeValue("id");
			if (inSource) {
				if (sourceId.contains(id)) {
					reason = Messages.getString("Xliff20.87");
					return false;
				}
				sourceId.add(id);
				smId.add(id);
			}
		}

		if ("em".equals(e.getLocalName())) {
			String startRef = e.getAttributeValue("startRef");
			if (!smId.contains(startRef)) {
				MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.88"));
				reason = mf.format(new String[] { startRef });
				return false;
			}
		}

		// attributes from fs module

		if (!fsPrefix.isEmpty()) {
			String fs = e.getAttributeValue(fsPrefix + ":fs");
			String subFs = e.getAttributeValue(fsPrefix + ":subFs");
			if (!subFs.isEmpty() && fs.isEmpty()) {
				MessageFormat mf = new MessageFormat(Messages.getString("Xliff20.89"));
				reason = mf.format(new String[] { fsPrefix, fsPrefix });
				return false;
			}
		}

		List<Element> children = e.getChildren();
		for (int i = 0; i < children.size(); i++) {
			Element child = children.get(i);
			boolean result = recurse(child);
			if (!result) {
				return false;
			}
			if ("source".equals(child.getLocalName())) {
				inSource = false;
			}
		}

		if ("unit".equals(e.getLocalName())) {
			if (!unitScSource.isEmpty()) {
				reason = Messages.getString("Xliff20.90");
				return false;
			}
			if (!unitScTarget.isEmpty()) {
				reason = Messages.getString("Xliff20.91");
				return false;
			}
		}

		if (XLIFF_MATCHES_2_0.equals(declaredNamespaces.get(namespace)) && "match".equals(e.getLocalName())) {
			inMatch = false;
			isReference = false;
		}

		if (XLIFF_RESOURCEDATA_2_0.equals(declaredNamespaces.get(namespace))
				&& "resourceItem".equals(e.getLocalName())) {
			inResourceData = false;
		}

		// remove namespaces declared in current element
		for (Attribute a : atts) {
			String prefix = a.getNamespace();
			if ("xmlns".equals(prefix)) {
				declaredNamespaces.remove(prefix);
			}
		}
		if ("target".equals(e.getLocalName())) {
			if (!cantDelete.isEmpty()) {
				reason = Messages.getString("Xliff20.92");
				return false;
			}
			inTarget = false;
		}
		if ("ignorable".equals(e.getLocalName()) && !validateInlineElements(e)) {
			return false;
		}
		return true;
	}

	private boolean validateInlineElements(Element segment) {
		Element target = segment.getChild("target");
		if ("ignorable".equals(segment.getLocalName()) && target == null) {
			return true;
		}
		Element source = segment.getChild("source");
		List<Element> sourceList = source.getChildren();
		for (Element tag : sourceList) {
			if ("ph".equals(tag.getName())) {
				List<Element> phList = target.getChildren("ph");
				for (int i = 0; i < phList.size(); i++) {
					Element ph = phList.get(i);
					if (tag.getAttributeValue("id").equals(ph.getAttributeValue("id"))) {
						if (!tag.getAttributeValue("canCopy").equals(ph.getAttributeValue("canCopy"))) {
							reason = Messages.getString("Xliff20.93");
							return false;
						}
						if (!tag.getAttributeValue("canDelete").equals(ph.getAttributeValue("canDelete"))) {
							reason = Messages.getString("Xliff20.94");
							return false;
						}
						if (!tag.getAttributeValue("canReorder").equals(ph.getAttributeValue("canReorder"))) {
							reason = Messages.getString("Xliff20.95");
							return false;
						}
						if (!tag.getAttributeValue("copyOf").equals(ph.getAttributeValue("copyOf"))) {
							reason = Messages.getString("Xliff20.96");
							return false;
						}
						if (!tag.getAttributeValue("dataRef").equals(ph.getAttributeValue("dataRef"))) {
							reason = Messages.getString("Xliff20.97");
							return false;
						}
						if (!tag.getAttributeValue("subFlows").equals(ph.getAttributeValue("subFlows"))) {
							reason = Messages.getString("Xliff20.98");
							return false;
						}
						if (!tag.getAttributeValue("type").equals(ph.getAttributeValue("type"))) {
							reason = Messages.getString("Xliff20.99");
							return false;
						}
					}
				}
			}
			if ("pc".equals(tag.getName())) {
				List<Element> pcList = target.getChildren("pc");
				for (int i = 0; i < pcList.size(); i++) {
					Element pc = pcList.get(i);
					if (tag.getAttributeValue("id").equals(pc.getAttributeValue("id"))) {
						if (!tag.getAttributeValue("canCopy").equals(pc.getAttributeValue("canCopy"))) {
							reason = Messages.getString("Xliff20.100");
							return false;
						}
						if (!tag.getAttributeValue("canDelete").equals(pc.getAttributeValue("canDelete"))) {
							reason = Messages.getString("Xliff20.101");
							return false;
						}
						if (!tag.getAttributeValue("canOverlap").equals(pc.getAttributeValue("canOverlap"))) {
							reason = Messages.getString("Xliff20.102");
							return false;
						}
						if (!tag.getAttributeValue("canReorder").equals(pc.getAttributeValue("canReorder"))) {
							reason = Messages.getString("Xliff20.103");
							return false;
						}
						if (!tag.getAttributeValue("copyOf").equals(pc.getAttributeValue("copyOf"))) {
							reason = Messages.getString("Xliff20.104");
							return false;
						}
						if (!tag.getAttributeValue("dataRefStart").equals(pc.getAttributeValue("dataRefStart"))) {
							reason = Messages.getString("Xliff20.105");
							return false;
						}
						if (!tag.getAttributeValue("dataRefEnd").equals(pc.getAttributeValue("dataRefEnd"))) {
							reason = Messages.getString("Xliff20.106");
							return false;
						}
						if (!tag.getAttributeValue("subFlowsStart").equals(pc.getAttributeValue("subFlowsStart"))) {
							reason = Messages.getString("Xliff20.108");
							return false;
						}
						if (!tag.getAttributeValue("subFlowsEnd").equals(pc.getAttributeValue("subFlowsEnd"))) {
							reason = Messages.getString("Xliff20.109");
							return false;
						}
						if (!tag.getAttributeValue("type").equals(pc.getAttributeValue("type"))) {
							reason = Messages.getString("Xliff20.110");
							return false;
						}
					}
				}
			}
			if ("sc".equals(tag.getName())) {
				List<Element> scList = target.getChildren("sc");
				for (int i = 0; i < scList.size(); i++) {
					Element sc = scList.get(i);
					if (tag.getAttributeValue("id").equals(sc.getAttributeValue("id"))) {
						if (!tag.getAttributeValue("canCopy").equals(sc.getAttributeValue("canCopy"))) {
							reason = Messages.getString("Xliff20.111");
							return false;
						}
						if (!tag.getAttributeValue("canDelete").equals(sc.getAttributeValue("canDelete"))) {
							reason = Messages.getString("Xliff20.112");
							return false;
						}
						if (!tag.getAttributeValue("canOverlap").equals(sc.getAttributeValue("canOverlap"))) {
							reason = Messages.getString("Xliff20.113");
							return false;
						}
						if (!tag.getAttributeValue("canReorder").equals(sc.getAttributeValue("canReorder"))) {
							reason = Messages.getString("Xliff20.114");
							return false;
						}
						if (!tag.getAttributeValue("copyOf").equals(sc.getAttributeValue("copyOf"))) {
							reason = Messages.getString("Xliff20.115");
							return false;
						}
						if (!tag.getAttributeValue("dataRef").equals(sc.getAttributeValue("dataRef"))) {
							reason = Messages.getString("Xliff20.116");
							return false;
						}
						if (!tag.getAttributeValue("subFlows").equals(sc.getAttributeValue("subFlows"))) {
							reason = Messages.getString("Xliff20.117");
							return false;
						}
						if (!tag.getAttributeValue("type").equals(sc.getAttributeValue("type"))) {
							reason = Messages.getString("Xliff20.118");
							return false;
						}
						if (!tag.getAttributeValue("isolated", "no").equals(sc.getAttributeValue("isolated", "no"))) {
							reason = Messages.getString("Xliff20.119");
							return false;
						}
					}
				}
				boolean isolated = tag.getAttributeValue("isolated", "no").equals("yes");
				if (!isolated) {
					List<Element> ecList = target.getChildren("ec");
					for (int i = 0; i < ecList.size(); i++) {
						Element ec = ecList.get(i);
						if (tag.getAttributeValue("id").equals(ec.getAttributeValue("startRef"))) {
							if (!tag.getAttributeValue("canCopy").equals(ec.getAttributeValue("canCopy"))) {
								reason = Messages.getString("Xliff20.120");
								return false;
							}
							if (!tag.getAttributeValue("canDelete").equals(ec.getAttributeValue("canDelete"))) {
								reason = Messages.getString("Xliff20.121");
								return false;
							}
							if (!tag.getAttributeValue("canOverlap").equals(ec.getAttributeValue("canOverlap"))) {
								reason = Messages.getString("Xliff20.122");
								return false;
							}
							if (!tag.getAttributeValue("canReorder").equals(ec.getAttributeValue("canReorder"))) {
								reason = Messages.getString("Xliff20.123");
								return false;
							}
							if (!tag.getAttributeValue("copyOf").equals(ec.getAttributeValue("copyOf"))) {
								reason = Messages.getString("Xliff20.124");
								return false;
							}
							if (!tag.getAttributeValue("dataRef").equals(ec.getAttributeValue("dataRef"))) {
								reason = Messages.getString("Xliff20.125");
								return false;
							}
							if (!tag.getAttributeValue("subFlows").equals(ec.getAttributeValue("subFlows"))) {
								reason = Messages.getString("Xliff20.126");
								return false;
							}
							if (!tag.getAttributeValue("type").equals(ec.getAttributeValue("type"))) {
								reason = Messages.getString("Xliff20.127");
								return false;
							}
							if (!tag.getAttributeValue("isolated", "no")
									.equals(ec.getAttributeValue("isolated", "no"))) {
								reason = Messages.getString("Xliff20.128");
								return false;
							}
						}
					}
				}
			}
			if ("ec".equals(tag.getName())) {
				List<Element> ecList = target.getChildren("ec");
				for (int i = 0; i < ecList.size(); i++) {
					Element ec = ecList.get(i);
					if (tag.getAttributeValue("id").equals(ec.getAttributeValue("id"))) {
						if (!tag.getAttributeValue("canCopy").equals(ec.getAttributeValue("canCopy"))) {
							reason = Messages.getString("Xliff20.129");
							return false;
						}
						if (!tag.getAttributeValue("canDelete").equals(ec.getAttributeValue("canDelete"))) {
							reason = Messages.getString("Xliff20.130");
							return false;
						}
						if (!tag.getAttributeValue("canOverlap").equals(ec.getAttributeValue("canOverlap"))) {
							reason = Messages.getString("Xliff20.131");
							return false;
						}
						if (!tag.getAttributeValue("canReorder").equals(ec.getAttributeValue("canReorder"))) {
							reason = Messages.getString("Xliff20.132");
							return false;
						}
						if (!tag.getAttributeValue("copyOf").equals(ec.getAttributeValue("copyOf"))) {
							reason = Messages.getString("Xliff20.133");
							return false;
						}
						if (!tag.getAttributeValue("startRef").equals(ec.getAttributeValue("startRef"))) {
							reason = Messages.getString("Xliff20.134");
							return false;
						}
						if (!tag.getAttributeValue("subFlows").equals(ec.getAttributeValue("subFlows"))) {
							reason = Messages.getString("Xliff20.135");
							return false;
						}
						if (!tag.getAttributeValue("type").equals(ec.getAttributeValue("type"))) {
							reason = Messages.getString("Xliff20.136");
							return false;
						}
						if (!tag.getAttributeValue("isolated", "no").equals(ec.getAttributeValue("isolated", "no"))) {
							reason = Messages.getString("Xliff20.137");
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	private Source getSource(String string) {
		String location = resolver.matchURI(string);
		if (location == null) {
			location = resolver.matchPublic(string);
		}
		if (location == null) {
			location = resolver.matchSystem("", string);
		}
		return new StreamSource(location);
	}

	public String getReason() {
		return reason;
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
			// private use subtag, e.g. "en-x-abc"
			return true;
		}
		return lang.equalsIgnoreCase(registry.normalizeCode(lang));
	}
}
