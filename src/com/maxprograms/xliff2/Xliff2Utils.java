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
package com.maxprograms.xliff2;

import java.util.List;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.CatalogBuilder;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;

public class Xliff2Utils {

    private Xliff2Utils() {
        // do not instantiate this class
    }

    public static boolean isXliffUpgrade(Element root) {
        List<Element> files = root.getChildren("file");
        if (!files.isEmpty()) {
            Element metadata = files.get(0).getChild("mda:metadata");
            if (metadata != null) {
                for (Element group : metadata.getChildren("mda:metaGroup")) {
                    if ("upgrade".equals(group.getAttributeValue("category"))) {
                        Element meta = group.getChild("mda:meta");
                        if (meta != null && "xliff-upgrade".equals(meta.getAttributeValue("type"))) {
                            return "yes".equals(meta.getText());
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean isDirectXliff2(Element root) {
        List<Element> files = root.getChildren("file");
        if (!files.isEmpty()) {
            Element metadata = files.get(0).getChild("mda:metadata");
            if (metadata != null) {
                for (Element group : metadata.getChildren("mda:metaGroup")) {
                    if ("format".equals(group.getAttributeValue("category"))) {
                        Element meta = group.getChild("mda:meta");
                        if (meta != null && "datatype".equals(meta.getAttributeValue("type"))) {
                            return "x-xliff2".equals(meta.getText());
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean isOpenXliffIntermediate(String file, String catalog) {
        try {
            SAXBuilder builder = new SAXBuilder();
            builder.setEntityResolver(CatalogBuilder.getCatalog(catalog));
            Document doc = builder.build(file);
            Element root = doc.getRootElement();
            List<Element> files = root.getChildren("file");
            if (!files.isEmpty()) {
                Element firstFile = files.get(0);
                if (Constants.TOOLID.equals(firstFile.getAttributeValue("tool-id"))) {
                    return true;
                }
                Element header = firstFile.getChild("header");
                if (header != null) {
                    Element tool = header.getChild("tool");
                    if (tool != null) {
                        return Constants.TOOLID.equals(tool.getAttributeValue("tool-id"));
                    }
                }
            }
        } catch (Exception _) {
            // ignore
        }
        return false;
    }
}
