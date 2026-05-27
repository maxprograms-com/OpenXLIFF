# OpenXLIFF Filters Localization

Localizing OpenXLIFF Filters requires processing Java `.properties` files.

[JavaPM](https://www.maxprograms.com/products/javapm.html) is used to generate XLIFF from `/src` folder.

Use a command like this to generate XLIFF:

```bash
/path-to-Javapm/createxliff.sh -src /path-to-OpenXLIFF/src -xliff yourXliffFile.xlf -srcLang en -tgtLang fr -enc UTF-8 -reuse
```

OpenXLIFF Filters .properties are encoded in UTF-8; translated versions must be generated using `UTF-8` character set.

After translating the XLIFF file, use the `mergexliff` command from `JavaPM` like this to merge the translated XLIFF back into the Java `.properties` files and store them in the corresponding subfolders of `/src`:

```bash
/path-to-Javapm/mergexliff.sh -src /path-to-OpenXLIFF/src -xliff yourTranslatedXliff.xlf -unaproved -export
```

You can find the XLIFF an TMX files for Spanish in `/i18n` folder.

## Dependencies

OpenXLIFF Filters depends on these related projects:

- [BCP47J](https://github.com/maxprograms-com/BCP47J) for language tags
- [XMLJava](https://github.com/maxprograms-com/XMLJava) for XML parsing

You may need to localize these projects as well.
