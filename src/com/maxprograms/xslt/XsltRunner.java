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

package com.maxprograms.xslt;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.text.MessageFormat;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import com.maxprograms.converters.Constants;
import com.maxprograms.converters.Utils;

public class XsltRunner {

    private static Logger logger = System.getLogger(XsltRunner.class.getName());

    private static final String[] XPATH_LIMIT_PROPERTIES = {
            "jdk.xml.xpathExprGrpLimit",
            "jdk.xml.xpathExprOpLimit",
            "jdk.xml.xpathTotalOpLimit"
    };

    private XsltRunner() {
        // private for security
    }

    public static void main(String[] args) {
        String xmlFile = "";
        String xslFile = "";
        String outputFile = "";
        String jsonFile = "";

        String[] arguments = Utils.fixPath(args);
        if (arguments.length == 0) {
            help();
            return;
        }
        for (int i = 0; i < arguments.length; i++) {
            String arg = arguments[i];
            if (arg.equals("-lang") && (i + 1) < arguments.length) {
                Locale.setDefault(Locale.forLanguageTag(arguments[i + 1]));
            }
            if (arg.equals("-version")) {
                MessageFormat mf = new MessageFormat(Messages.getString("XsltRunner.6"));
                logger.log(Level.INFO, mf.format(new String[] { Constants.VERSION, Constants.BUILD }));
                return;
            }
            if (arg.equals("-help")) {
                help();
                return;
            }
            if (arg.equals("-xml") && (i + 1) < arguments.length) {
                xmlFile = arguments[i + 1];
            }
            if (arg.equals("-xsl") && (i + 1) < arguments.length) {
                xslFile = arguments[i + 1];
            }
            if (arg.equals("-output") && (i + 1) < arguments.length) {
                outputFile = arguments[i + 1];
            }
            if (arg.equals("-json") && (i + 1) < arguments.length) {
                jsonFile = arguments[i + 1];
                try {
                    run(jsonFile);
                } catch (Exception e) {
                    MessageFormat mf = new MessageFormat(Messages.getString("XsltRunner.4"));
                    logger.log(Level.ERROR, mf.format(new String[] { e.getMessage() }));
                }
                return;
            }
        }

        if (xmlFile.isEmpty()) {
            MessageFormat mf = new MessageFormat(Messages.getString("XsltRunner.5"));
            logger.log(Level.ERROR, mf.format(new String[] { "-xml" }));
            return;
        }
        if (xslFile.isEmpty()) {
            MessageFormat mf = new MessageFormat(Messages.getString("XsltRunner.5"));
            logger.log(Level.ERROR, mf.format(new String[] { "-xsl" }));
            return;
        }
        if (outputFile.isEmpty()) {
            MessageFormat mf = new MessageFormat(Messages.getString("XsltRunner.5"));
            logger.log(Level.ERROR, mf.format(new String[] { "-output" }));
            return;
        }

        try {
            transform(xmlFile, xslFile, outputFile);
        } catch (Exception e) {
            logger.log(Level.ERROR, e);
        }
    }

    private static void run(String jsonFile) throws JSONException, IOException {
        JSONObject json = Utils.readJSON(jsonFile);
        String xmlFile = json.optString("xml", json.optString("xmlFile", ""));
        String xslFile = json.optString("xsl", json.optString("xslFile", ""));
        String outputFile = json.optString("output", json.optString("outputFile", ""));

        if (xmlFile.isEmpty()) {
            MessageFormat mf = new MessageFormat(Messages.getString("XsltRunner.5"));
            logger.log(Level.ERROR, mf.format(new String[] { "-xml" }));
            return;
        }
        if (xslFile.isEmpty()) {
            MessageFormat mf = new MessageFormat(Messages.getString("XsltRunner.5"));
            logger.log(Level.ERROR, mf.format(new String[] { "-xsl" }));
            return;
        }
        if (outputFile.isEmpty()) {
            MessageFormat mf = new MessageFormat(Messages.getString("XsltRunner.5"));
            logger.log(Level.ERROR, mf.format(new String[] { "-output" }));
            return;
        }

        try {
            transform(xmlFile, xslFile, outputFile);
        } catch (Exception e) {
            logger.log(Level.ERROR, e);
        }
    }

    public static void transform(String xmlFile, String xslFile, String outputFile)
            throws IOException, SAXException, ParserConfigurationException, TransformerException {

        File xmlFileObj = new File(xmlFile);
        File xslFileObj = new File(xslFile);
        File outputFileObj = new File(outputFile);

        String xmlAbsolutePath = xmlFileObj.getAbsolutePath();
        String xslAbsolutePath = xslFileObj.getAbsolutePath();

        // Validate input files exist
        if (!xmlFileObj.exists()) {
            MessageFormat mf = new MessageFormat(Messages.getString("XsltRunner.1"));
            throw new FileNotFoundException(mf.format(new Object[] { xmlAbsolutePath }));
        }
        if (!xslFileObj.exists()) {
            MessageFormat mf = new MessageFormat(Messages.getString("XsltRunner.2"));
            throw new FileNotFoundException(mf.format(new Object[] { xslAbsolutePath }));
        }
        // Ensure output directory exists
        File parentDir = outputFileObj.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                MessageFormat mf = new MessageFormat(Messages.getString("XsltRunner.3"));
                throw new IOException(mf.format(new Object[] { parentDir.getAbsolutePath() }));
            }
        }

        configureXPathResourceLimits();

        // Create transformer with DTD validation disabled
        TransformerFactory factory = TransformerFactory.newInstance();
        applyXPathLimitAttributes(factory);

        // Configure factory to disable DTD processing
        try {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (Exception _) {
            // Ignore if features are not supported
        }

        // Create non-validating XML reader for XML source
        SAXParserFactory saxFactory = SAXParserFactory.newInstance();
        saxFactory.setValidating(false);
        saxFactory.setNamespaceAware(true);
        try {
            saxFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            saxFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            saxFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (Exception _) {
            // Ignore if features are not supported
        }

        XMLReader xmlReader = saxFactory.newSAXParser().getXMLReader();
        Source xmlSource = new SAXSource(xmlReader, new InputSource(xmlAbsolutePath));
        Source xslSource = new StreamSource(xslFileObj);

        Transformer transformer = factory.newTransformer(xslSource);
        // set parameters if necessary,
        // e.g. transformer.setParameter("filename", filename);

        Result result = new StreamResult(outputFileObj);
        transformer.transform(xmlSource, result);
    }

    private static void configureXPathResourceLimits() {
        for (String propertyName : XPATH_LIMIT_PROPERTIES) {
            String currentValue = System.getProperty(propertyName);
            if (currentValue == null || currentValue.trim().isEmpty()) {
                // Default to unlimited when the caller does not configure the property.
                System.setProperty(propertyName, "0");
            }
        }
    }

    private static void applyXPathLimitAttributes(TransformerFactory factory) {
        for (String propertyName : XPATH_LIMIT_PROPERTIES) {
            String value = System.getProperty(propertyName);
            if (value != null && !value.trim().isEmpty()) {
                try {
                    factory.setAttribute(propertyName, value.trim());
                } catch (IllegalArgumentException _) {
                    // Ignore factories that do not understand the attribute.
                }
            }
        }
    }

    private static void help() {
        MessageFormat mf = new MessageFormat(Messages.getString("XsltRunner.help"));
        System.out.println(mf.format(new String[] { "XsltRunner" }));
    }
}
