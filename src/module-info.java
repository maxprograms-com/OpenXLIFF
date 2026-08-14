/*******************************************************************************
 * Copyright (c) 2018 - 2026 Maxprograms.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors: Maxprograms - initial API and implementation
 *******************************************************************************/
module openxliff {
	exports com.maxprograms.converters;
	exports com.maxprograms.converters.html;
	exports com.maxprograms.converters.po;
	exports com.maxprograms.converters.ts;
	exports com.maxprograms.converters.office;
	exports com.maxprograms.converters.xml;
	exports com.maxprograms.converters.resx;
	exports com.maxprograms.converters.javascript;
	exports com.maxprograms.converters.json;
	exports com.maxprograms.converters.javaproperties;
	exports com.maxprograms.converters.msoffice;
	exports com.maxprograms.converters.php;
	exports com.maxprograms.converters.ditamap;
	exports com.maxprograms.converters.idml;
	exports com.maxprograms.converters.mif;
	exports com.maxprograms.converters.rc;
	exports com.maxprograms.converters.srt;
	exports com.maxprograms.converters.sdlxliff;
	exports com.maxprograms.converters.sdlppx;
	exports com.maxprograms.converters.txml;
	exports com.maxprograms.converters.txlf;
	exports com.maxprograms.converters.plaintext;
	exports com.maxprograms.converters.wpml;
	exports com.maxprograms.converters.xliff;
	exports com.maxprograms.segmenter;
	exports com.maxprograms.stats;
	exports com.maxprograms.xliff2;
	exports com.maxprograms.xslt;
	exports com.maxprograms.validation;

	requires jsoup;
	requires java.base;
	requires java.net.http;
	requires transitive json;
	requires transitive java.xml;
	requires bcp47j;
	requires transitive xmljava;
}
