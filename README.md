# OpenXLIFF

![OpenXLIFF logo](openxliff_128.png)

**OpenXLIFF** is a Java library for working with XLIFF 1.2, 2.0, 2.1, and 2.2 across the entire translation lifecycle. It enables conversion between source documents and XLIFF, validation beyond schema-level checks, transformation between XLIFF versions, and advanced operations such as merging, analysis, and translation data extraction.

Support for multiple input formats is built in through internal filters, allowing the library to function as a self-contained engine for XLIFF processing in automated workflows and applications.

- **[Binary Downloads](https://www.maxprograms.com/products/openxliff.html)**
- **[Filters Configuration](https://github.com/maxprograms-com/OpenXLIFF#filters-configuration)**
- **[Supported File Formats](https://github.com/maxprograms-com/OpenXLIFF#supported-file-formats)**
- **[Building OpenXLIFF](https://github.com/maxprograms-com/OpenXLIFF/tree/master?tab=readme-ov-file#building-openxliff)**

## Features

- **[Convert Document to XLIFF](https://github.com/maxprograms-com/OpenXLIFF#convert-document-to-xliff)**  
- **[Convert XLIFF to Original Format](https://github.com/maxprograms-com/OpenXLIFF#convert-xliff-to-original-format)**
- **[XLIFF Validation](https://github.com/maxprograms-com/OpenXLIFF#xliff-validation)**
- **[Convert XLIFF 1.2 to XLIFF 2.x](https://github.com/maxprograms-com/OpenXLIFF#convert-xliff-12-to-xliff-2x)**
- **[Convert XLIFF 2.x to XLIFF 1.2](https://github.com/maxprograms-com/OpenXLIFF#convert-xliff-2x-to-xliff-12)**
- **[Recover ICE (In-Context Exact) Matches](https://github.com/maxprograms-com/OpenXLIFF#recover-ice-in-context-exact-matches)**
- **[Translation Status Analysis](https://github.com/maxprograms-com/OpenXLIFF#translation-status-analysis)**
- **[Join multiple XLIFF files](https://github.com/maxprograms-com/OpenXLIFF#join-multiple-xliff-files)**
- **[Pseudo-translate XLIFF file](https://github.com/maxprograms-com/OpenXLIFF#pseudo-translate-xliff-file)**
- **[Copy Source to Target](https://github.com/maxprograms-com/OpenXLIFF#copy-source-to-target)**
- **[Approve All Segments](https://github.com/maxprograms-com/OpenXLIFF#approve-all-segments)**
- **[Remove All Targets](https://github.com/maxprograms-com/OpenXLIFF#remove-all-targets)**
- **[Export Approved Segments as TMX](https://github.com/maxprograms-com/OpenXLIFF#export-approved-segments-as-tmx)**
- **[Compare XLIFF files](https://github.com/maxprograms-com/OpenXLIFF#compare-xliff-files)**

## Filters Configuration

XML and JSON filters are configurable

- **[XML Filter Configuration](https://github.com/maxprograms-com/OpenXLIFF/tree/master/src/com/maxprograms/converters/xml#readme)**
- **[JSON Filter Configuration](https://github.com/maxprograms-com/OpenXLIFF/tree/master/src/com/maxprograms/converters/json#readme)**

## Related Projects

- **[XLIFF Manager](https://github.com/maxprograms-com/XLIFFManager)** implements an easy-to-use user interface for creating, merging, validating, and manipulating XLIFF files in a graphical environment.
- **[Swordfish](https://github.com/maxprograms-com/Swordfish)** uses OpenXLIFF Filters to extract translatable text from supported formats.
- **[RemoteTM](https://github.com/maxprograms-com/RemoteTM)** uses OpenXLIFF Filters to handle all TMX processing.
- **[Stingray](https://github.com/maxprograms-com/Stingray)** uses OpenXLIFF Filters for extracting the text to align from supported monolingual documents.
- **[XLIFF Validation](https://github.com/maxprograms-com/XLIFFValidation)** web-based XLIFF Validation Service.
- **[Fluenta](https://github.com/maxprograms-com/Fluenta)** a Translation Manager that uses OpenXLIFF Filters to generate XLIFF from DITA projects.
- **[JavaPM](https://github.com/maxprograms-com/JavaPM)** a set of scripts for localizing Java `.properties` files using XLIFF.

## Supported Versions

| Version   | Supported          |
| --------- | ------------------ |
| Latest    | :white_check_mark: |
| Any other | :x:                |

## Supported File Formats

OpenXLIFF Filters can generate XLIFF 1.2, 2.0, 2.1 and 2.2 from these formats:

- **General Documentation**
  - Adobe InCopy ICML
  - Adobe InDesign Interchange (INX)
  - Adobe InDesign IDML CS4, CS5, CS6 & CC
  - HTML
  - Markdown (CommonMark, GitHub Flavored Markdown, Pandoc and other dialects)
  - Microsoft Office (2007 and newer)
  - Microsoft Visio XML Drawings (2007 and newer)
  - MIF (Maker Interchange Format)
  - OpenOffice / LibreOffice / StarOffice
  - Plain Text
  - QTI (IMS Question and Test Interoperability)
  - QTI Packages
  - SDLXLIFF (Trados Studio)
  - SRT Subtitles
  - Trados Studio Packages (*.sdlppx)
  - TXML (GlobalLink/Wordfast PRO)
  - VTT Subtitles (WebVTT)
  - WPML XLIFF (WordPress Multilingual Plugin)
  - Wordfast/GlobalLink XLIFF (*.txlf)
  - XLIFF from Other Tools (.mqxliff, .txlf, .xliff, etc.)
- **XML Formats**
  - XML (Generic)
  - DITA 1.0, 1.1, 1.2 and 1.3
  - DocBook 3.x, 4.x and 5.x
  - SVG
  - Word 2003 ML
  - XHTML
- **Software Development**
  - JavaScript
  - JSON
  - Java Properties
  - PHP Arrays
  - PO (Portable Objects)
  - RC (Windows C/C++ Resources)
  - ResX (Windows .NET Resources)
  - TS (Qt Linguist translation source)

## Building OpenXLIFF

### Requirements

- JDK 25 is required for compiling and building. Pre-built binaries already include everything you need to run all options.
- Gradle 9.5 or newer. get it from [Gradle Releases](https://gradle.org/releases/).

### Steps for building

- Clone this repository.
- Point your JAVA_HOME variable to JDK 25
- Run `gradle` to generate a binary distribution in `./dist`

``` bash
  git clone https://github.com/maxprograms-com/OpenXLIFF.git
  cd OpenXLIFF
  gradle
```

A binary distribution will be created in `/dist` folder.

## Convert Document to XLIFF

You can use the library in your own Java code. Conversion to XLIFF is handled by the class `com.maxprograms.converters.Convert`.

If you use binaries from the command line, running `.\convert.cmd` or `./convert.sh` without parameters displays help for XLIFF generation.

```text
Usage:

convert.sh [-help] [-version] -file sourceFile -srcLang sourceLang 
        [-tgtLang targetLang] [-skl skeletonFile] [-xliff xliffFile] 
        [-type fileType] [-enc encoding] [-srx srxFile] [-catalog catalogFile] 
        [-maxThreads threads] [-divatal ditaval] [-config configFile] [-embed] 
        [-paragraph] [-xmlfilter folder] [-2.0] [-2.1] [-2.2] [-ignoretc] [-ignoresvg] 
        [-strict] [-charsets] [-types]

Where:

   -help:      (optional) display this help information and exit
   -version:   (optional) display version & build information and exit
   -file:      source file to convert
   -srcLang:   source language code
   -tgtLang:   (optional) target language code
   -xliff:     (optional) XLIFF file to generate
   -skl:       (optional) skeleton file to generate
   -type:      (optional) document type
   -enc:       (optional) character set code for the source file
   -srx:       (optional) SRX file to use for segmentation
   -catalog:   (optional) XML catalog to use for processing
   -maxThreads:(optional) Maximum number of threads to use while converting
   -ditaval:   (optional) conditional processing file to use when converting DITA maps
   -config:    (optional) configuration file to use when converting JSON documents
   -embed:     (optional) store skeleton inside the XLIFF file
   -paragraph: (optional) use paragraph segmentation
   -xmlfilter: (optional) folder containing configuration files for the XML filter
   -ignoretc:  (optional) ignore tracked changes from Oxygen XML Editor in XML files
   -ignoresvg: (optional) ignore translatable SVG files when parsing DITA maps
   -2.0:       (optional) generate XLIFF 2.0
   -2.1:       (optional) generate XLIFF 2.1
   -2.2:       (optional) generate XLIFF 2.2
   -strict:    (optional) validate QTI files/packages
   -charsets:  (optional) display a list of available character sets and exit
   -types:     (optional) display a list of supported document types and exit
```

Only two parameters are absolutely required: `-file` and `-srcLang`. The library tries to automatically detect format and encoding and exits with an error message if it can't guess them. If automatic detection doesn't work, add `-type` and `-enc` parameters.

Character sets vary with the operating system. Run the conversion script with `-charsets` to get a list of character sets available in your OS.

By default, XLIFF and skeleton are generated in the folder where the source document is located. Extensions used for XLIFF and Skeleton are `.xlf` and `.skl`.

The `XML` type handles multiple document formats, like `XHTML`, `SVG` or `DocBook` files.

Default XML catalog and SRX file are provided. You can also use custom versions if required.

### Supported document types

```text
   INX = Adobe InDesign Interchange
   ICML = Adobe InCopy ICML
   IDML = Adobe InDesign IDML
   DITA = DITA Map
   HTML = HTML Page
   JS = JavaScript
   JSON = JSON
   JAVA = Java Properties
   MD = Markdown
   MIF = MIF (Maker Interchange Format)
   OFF = Microsoft Office 2007 Document
   OO = OpenOffice Document
   PHPA = PHP Array
   PO = PO (Portable Objects)
   QTI = IMS Question and Test Interoperability
   QTIP = QTI Package
   RC = RC (Windows C/C++ Resources)
   RESX = ResX (Windows .NET Resources)
   SDLPPX = Trados Studio Package
   SDLXLIFF = SDLXLIFF Document
   SRT = SRT Subtitle
   TEXT = Plain Text
   TS = TS (Qt Linguist translation source)
   TXLF = Wordfast/GlobalLink XLIFF
   TXML = TXML Document
   VTT = VTT Subtitle
   WPML = WPML XLIFF
   XLIFF = XLIFF Document
   XML = XML Document
   XMLG = XML (Generic)
```

## Convert XLIFF to Original Format

You can convert XLIFF files created with OpenXLIFF Filters to original format using class `com.maxprograms.converters.Merge` in your Java code.

If you use binaries from the command line, running `.\merge.cmd` or `./merge.sh` without parameters will display the information you need to merge an XLIFF file.

```text
Usage:

merge.sh [-help] [-version] -xliff xliffFile -target targetFile [-catalog catalogFile] 
        [-maxThreads threads] [-unapproved] [-export]

Where:

    -help:       (optional) display this help information and exit
    -version:    (optional) display version & build information and exit
    -xliff:      XLIFF file to merge
    -target:     (optional) translated file or folder where to store translated files
    -catalog:    (optional) XML catalog to use for processing
    -maxThreads: (optional) Maximum number of threads to use while merging
    -unapproved: (optional) accept translations from unapproved segments
    -export:     (optional) generate TMX file from approved segments
    -getTarget:  (optional) display a potential target file name and exit
```

## Recover ICE (In-Context Exact) Matches

You can recover previous translations stored in an XLIFF file and apply them as ICE (In-Context Exact) Matches to another XLIFF file using class `com.maxprograms.converters.ICEMatches` in your Java code.

If you use binaries from the command line, running `.\iceMatches.cmd` or `./iceMatches.sh` without parameters displays help for recovering ICE Matches.

```text
Usage:

iceMatches.sh [-help] -old oldXliff -new newXliff [-catalog catalogFile]

Where:

    -help:    (optional) Display this help information and exit
    -old:     XLIFF file with previous translations
    -new:     XLIFF file that receives previous translations
    -catalog: (optional) XML catalog to use for processing
```

## XLIFF Validation

The original [XLIFFChecker code](http://sourceforge.net/p/xliffchecker/code/) supports XLIFF 1.0, 1.1 and 1.2. The new version incorporated in OpenXLIFF Filters also supports XLIFF 2.0, 2.1 and 2.2.

Standard XML Schema validation does not detect the use of duplicated 'id' attributes, wrong language codes and other constraints written in the different XLIFF specifications.

All XLIFF 2.0 modules are validated using XML Schema validation in a first pass. Extra validation is then performed using Java code for XLIFF 2.0 Core and for Metadata, Matches and Glossary modules.

You can validate XLIFF files using your own Java code. Validation of XLIFF files is handled by the class `com.maxprograms.validation.XliffChecker`.

If you use binaries from the command line, running `.\xliffchecker.cmd` or `./xliffchecker.sh` without parameters displays help for XLIFF validation.

```text
Usage:

   xliffchecker.cmd [-help] -file xliffFile [-catalog catalogFile]

Where:

   -help:      (optional) Display this help information and exit
   -file:      XLIFF file to validate
   -catalog:   (optional) XML catalog to use for processing
```

### Convert XLIFF 1.2 to XLIFF 2.x

You can convert XLIFF 1.2 files to XLIFF 2.0, 2.1 or 2.2 using class `com.maxprograms.xliff2.ToXliff2` from your Java code or using the provided scripts.

```text
Usage:

toxliff2.sh [-help] -source sourceFile -target targetFile [-2.0] [-2.1] [-2.2] [-catalog catalogFile] 

Where:

   -help:      (optional) display this help information and exit
   -source:    XLIFF 1.2 file to convert
   -target:    XLIFF 2.x to generate
   -2.0:       (optional) generate XLIFF 2.0
   -2.1:       (optional) generate XLIFF 2.1
   -2.2:       (optional) generate XLIFF 2.2
   -catalog:   (optional) XML catalog to use for processing
```

### Convert XLIFF 2.x to XLIFF 1.2

You can convert XLIFF 2.0, 2.1 or 2.2 files to XLIFF 1.2 using class `com.maxprograms.xliff2.FromXliff2` from your Java code or using the provided scripts.

```text
Usage:

fromxliff2.sh [-help] -source sourceFile -target targetFile [-catalog catalogFile] 

Where:

   -help:      (optional) display this help information and exit
   -source:    XLIFF 2.x file to convert
   -target:    XLIFF 1.2 to generate
   -catalog:   (optional) XML catalog to use for processing
```

### XLIFF Validation Service

You can validate your XLIFF files online at [https://dev.maxprograms.com/Validation/](https://dev.maxprograms.com/Validation/)

## Translation Status Analysis

This library lets you produce an HTML file with word counts and segment status statistics from an XLIFF file.  

If you use binaries from the command line, running `.\analysis.cmd` or `./analysis.sh` without parameters displays help for statistics generation.

You can generate statistics using your own Java code. Statistics generation is handled by the class `com.maxprograms.stats.RepetitionAnalysis`.

```text
Usage:

   analysis.sh [-help] -file xliffFile [-catalog catalogFile]

Where:

   -help:      (optional) Display this help information and exit
   -file:      XLIFF file to analyze
   -catalog:   (optional) XML catalog to use for processing
```

The HTML report is generated in the folder where the XLIFF file is located and its name is the name of the XLIFF plus `.log.html`.

## Join multiple XLIFF files

You can combine several XLIFF files into a larger one using class `com.maxprograms.converters.Join` from your Java code or using the provided scripts.

Running `.\join.cmd` or `./join.sh` without parameters displays help for joining files.

```text
Usage:

   join.sh [-help] -target targetFile -files file1,file2,file3...

 Where:

   -help:     (optional) Display this help information and exit
   -target:   combined output XLIFF file
```

The merge process automatically splits the files when converting back to original format.

## Pseudo-translate XLIFF file

You can pseudo-translate  all untranslated segments using class `com.maxprograms.converters.PseudoTranslation` from your Java code or using the provided scripts.

Running `.\pseudotranslate.cmd` or `./pseudotranslate.sh` without parameters displays help for pseudo-translating an XLIFF file.

```text
Usage:

   pseudotranslate.cmd [-help] -xliff xliffFile [-catalog catalogFile]

Where:

   -help:      (optional) Display this help information and exit
   -xliff:     XLIFF file to pseudo-translate
   -catalog:   (optional) XML catalog to use for processing
```

## Copy Source to Target

You can copy the content of `<source>` elements to new `<target>` elements for all untranslated segments using class `com.maxprograms.converters.CopySources` from your Java code or using the provided scripts.

Running `.\copysources.cmd` or `./copysources.sh` without parameters displays help for copying source to target in an XLIFF file.

```text
Usage:

   copysources.cmd [-help] -xliff xliffFile [-catalog catalogFile]

Where:

   -help:      (optional) Display this help information and exit
   -xliff:     XLIFF file to process
   -catalog:   (optional) XML catalog to use for processing
```

## Approve All Segments

You can set all `<trans-unit>` or `<segment>` elements as `approved` or `final` if they contain target text using class `com.maxprograms.converters.ApproveAll` from your Java code or using the provided scripts.

Running `.\approveall.cmd` or `./approveall.sh` without parameters displays help for approving or confirming all segments in an XLIFF file.

```text
Usage:

   approveall.cmd [-help] -xliff xliffFile [-catalog catalogFile]

Where:

   -help:      (optional) Display this help information and exit
   -xliff:     XLIFF file to process
   -catalog:   (optional) XML catalog to use for processing
```

## Remove All Targets

You can remove`<target>` elements from all `<segment>` or `<trans-unit>` elements using class `com.maxprograms.converters.RemoveTargets` from your Java code or using the provided scripts.

Running `.\removetargets.cmd` or `./removetargets.sh` without parameters displays help for removing targets from an XLIFF file.

```text
Usage:

   removetargets.cmd [-help] -xliff xliffFile [-catalog catalogFile]

Where:

   -help:      (optional) Display this help information and exit
   -xliff:     XLIFF file to process
   -catalog:   (optional) XML catalog to use for processing
```

## Export Approved Segments as TMX

You can export all aproved segments from an XLIFF file as TMX using class `com.maxprograms.converters.TmxExporter` from your Java code or using the provided scripts.

Running `.\exporttmx.cmd` or `./exporttmx.sh` without parameters displays help for exporting approved segments from an XLIFF file.

```text
Usage:

exporttmx.sh [-help] -xliff xliffFile [-tmx tmxFile] [-catalog catalogFile]

Where:

    -help:      (optional) Display this help information and exit
    -xliff:     XLIFF file to process
    -tmx:       (optional) TMX file to generate
    -catalog:   (optional) XML catalog to use for processing
```

If the optional `-tmx` parameter is not provided, the TMX file will be generated in the same folder as the XLIFF file and its name will be the same as the XLIFF file plus `.tmx`.

## Compare XLIFF files

Use class `com.maxprograms.validation.XliffComparer` from your Java code to compare two XLIFF files and generate a report of differences.

Running `.\xliffcomparer.cmd` or `./xliffcomparer.sh` without parameters displays help for comparing two XLIFF files.

```text
Usage: 

xliffcomparer.sh [-help] -file1 xliffFile1 -file2 xliffFile2 [-catalog catalogFile]

Where:

    -help:      (optional) Display this help information and exit
    -file1:     First XLIFF file to compare
    -file2:     Second XLIFF file to compare
    -catalog:   (optional) XML catalog to use for processing
    
Compares two XLIFF files for semantic equivalence, ignoring:

  - Skeleton file references and paths
  - Whitespace and formatting differences
  - Attribute order

Exit codes:

  0 - Files are equivalent
  1 - Files differ or error occurred
```

## Legal

License information for all included components is available in the [licenses](licenses/README.md) directory.
