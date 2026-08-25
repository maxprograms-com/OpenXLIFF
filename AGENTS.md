# OpenXLIFF — Agent Guide

This file is a quick orientation for AI coding agents working on the OpenXLIFF repository. It describes the technology stack, build system, code layout, conventions, and testing approach found in the actual project.

## Project Overview

OpenXLIFF is a Java library and command-line toolkit for working with XLIFF (XML Localization Interchange File Format) documents across the translation lifecycle. It supports XLIFF 1.2, 2.0, 2.1, and 2.2.

Key capabilities include:

- Converting source documents to XLIFF and back.
- Validating XLIFF files beyond simple schema checks.
- Converting between XLIFF versions.
- Merging, joining, pseudo-translating, and analyzing XLIFF files.
- Extracting approved segments as TMX.
- Re-segmenting XLIFF 2.x documents.
- Applying XSLT transformations.

The project is maintained by Maxprograms and published under the Eclipse Public License 1.0.

## Technology Stack

- **Language:** Java 25 (source and target).
- **Build Tool:** Gradle 9.5 or newer (the repository uses Gradle 9.7).
- **Module System:** Java Platform Module System (JPMS). The module name is `openxliff` and is declared in `src/module-info.java`.
- **Dependencies:** Pre-built JARs stored in `lib/`:
  - `bcp47j.jar` — BCP 47 language tag handling.
  - `json.jar` — JSON processing (JSON-java).
  - `jsoup.jar` — HTML parsing.
  - `xmljava.jar` — XML parsing utilities.
- **Runtime Packaging:** `jlink` is used to produce a custom runtime image in `dist/`.

## Project Structure

```text
/
├── src/                       # All Java source code and resources
│   ├── module-info.java       # JPMS module declaration
│   └── com/maxprograms/       # Root package
│       ├── converters/        # Main conversion and utility CLIs
│       ├── converters/<fmt>/  # Per-format converters (html, json, xml, idml, ...)
│       ├── segmenter/         # SRX-based segmentation
│       ├── stats/             # Translation analysis / HTML reports
│       ├── validation/        # XLIFF validation and comparison
│       ├── xliff2/            # XLIFF 1.2 <-> 2.x conversions and resegmenting
│       └── xslt/              # XSLT runner
├── lib/                       # External dependencies and the produced openxliff.jar
├── scripts/                   # .cmd and .sh launchers
├── catalog/                   # XML catalogs
├── srx/                       # Default SRX segmentation rules
├── xmlfilter/                 # XML filter configuration files
├── i18n/                      # Localization assets (Spanish TMX/XLIFF)
├── dist/                      # Distribution produced by the build
├── build.gradle               # Gradle build script
├── settings.gradle            # Gradle settings
├── gradle.properties          # Gradle options
└── sonar-project.properties   # SonarQube configuration
```

There are approximately 134 Java source files and 64 `.properties` resource bundles.

## Build Commands

The default Gradle task builds the library, creates a `jlink` runtime image, and copies scripts and resources into `dist/`.

```bash
gradle
```

or explicitly:

```bash
gradle dist
```

Other useful tasks:

```bash
gradle clean          # Remove build artifacts, dist/, lib/openxliff.jar, and bin/
gradle distclean      # Remove dist/
gradle forceClean     # Aggressive clean including stray .class files in src/
gradle jar            # Build lib/openxliff.jar only
gradle jlinkImage     # Build the modular runtime image in dist/
```

The build intentionally disables all caching (`gradle.properties` sets `org.gradle.caching=false` and `org.gradle.configuration-cache=false`, and `build.gradle` marks tasks as `outputs.upToDateWhen { false }`) to ensure deterministic, clean builds.

### Requirements

- JDK 25 (`JAVA_HOME` must point to a JDK 25 installation).
- Gradle 9.5+ on the path.

## Running the Tools

After building, the `dist/` directory contains a self-contained runtime. On Unix/macOS:

```bash
./dist/convert.sh -help
./dist/merge.sh -help
./dist/xliffchecker.sh -help
```

On Windows, use the corresponding `.cmd` launchers.

Each launcher invokes the embedded `dist/bin/java` with the `openxliff` module and the appropriate main class, for example:

```bash
$OpenXLIFF_HOME/bin/java -XX:+UseCompactObjectHeaders --module-path $OpenXLIFF_HOME/lib -m openxliff/com.maxprograms.converters.Convert
```

## Code Organization and Main Modules

The exported JPMS packages (from `src/module-info.java`) are the public API surface:

- `com.maxprograms.converters` — core conversion CLI entry points (`Convert`, `Merge`, `Join`, `ICEMatches`, `PseudoTranslation`, `CopySources`, `ApproveAll`, `RemoveTargets`, `TmxExporter`).
- `com.maxprograms.converters.<format>` — format-specific conversion implementations.
- `com.maxprograms.segmenter` — SRX-based segmentation (`Segmenter`, `SegmenterPool`).
- `com.maxprograms.stats` — translation status analysis (`RepetitionAnalysis`).
- `com.maxprograms.validation` — XLIFF validation (`XliffChecker`, `XliffComparer`).
- `com.maxprograms.xliff2` — XLIFF 2.x transformations (`ToXliff2`, `FromXliff2`, `Resegmenter`).
- `com.maxprograms.xslt` — XSLT runner (`XsltRunner`).

Internal helpers (such as XML parsing) live in the dependencies `xmljava.jar` and `bcp47j.jar`, not in this repository's source tree.

## Code Style and Conventions

- **Package naming:** All code is under `com.maxprograms.*`.
- **Class naming:** PascalCase for classes (`Convert`, `Xml2Xliff`, `XliffChecker`).
- **Source file header:** Every Java file starts with the standard Eclipse Public License header and a `Contributors: Maxprograms` line.
- **Encoding:** Source files are UTF-8 (`options.encoding = 'UTF-8'` in `build.gradle`, `sonar.sourceEncoding=UTF-8`).
- **String resources:** UI/help strings are stored in per-package `.properties` files (e.g., `com/maxprograms/converters/converters.properties`) and loaded through a small `Messages` helper. Spanish translations use `_es.properties`.
- **Logging:** Uses `java.lang.System.Logger` (JPMS platform logging), not a third-party logging framework.
- **No external build dependency downloads:** The project does not fetch dependencies from Maven Central at build time. All required JARs are checked into `lib/`.

## Localization

Localizing OpenXLIFF means translating the Java `.properties` files under `src/`. The related project [JavaPM](https://www.maxprograms.com/products/javapm.html) is used to round-trip these files through XLIFF.

See `LOCALIZATION.md` for the exact `createxliff.sh` and `mergexliff.sh` commands. Spanish localization assets are already present in `i18n/`.

## Testing

This repository does **not** contain a unit-test suite. There are no JUnit/TestNG source files and no test tasks in `build.gradle`.

Verification is typically done by:

1. Running `gradle dist` to confirm a clean build.
2. Running the CLI tools in `dist/` against sample documents.
3. Using SonarQube for static analysis (configuration in `sonar-project.properties`).

If you add automated tests, introduce a matching test source set and a Gradle test task; keep the change minimal and aligned with the existing JPMS modular layout.

## Security Considerations

- The build runs `jlink` with the local JDK's `jmods` directory and packages a custom runtime. Do not commit the generated `dist/` or `build/` directories.
- XML parsing is central to the tool. Existing code uses project-specific XML utilities (`com.maxprograms.xml.*`) rather than hand-rolled parsers; maintain that pattern when handling XML.
- The repository includes XML catalogs and DTDs in `catalog/`. These are trusted data files used during conversion/validation.
- Avoid introducing new network-fetching build steps. The project deliberately vendors its dependencies in `lib/`.
- Do not add secrets, API keys, or signing credentials to source control.

## Version and Release

Version constants live in `src/com/maxprograms/converters/Constants.java`:

- `VERSION` — human-readable version (e.g., `6.3.0`).
- `BUILD` — build timestamp in `yyyyMMdd_HHmm` format.

The `dist/release` file records the bundled Java version and module list.
