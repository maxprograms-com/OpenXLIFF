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
/**
 * 
 */
package com.maxprograms.converters.rc;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.XMLUtils;

public class Rc2Xliff {

	private InputStreamReader buffer;
	private FileOutputStream output;
	private FileOutputStream skeleton;
	private String lastWord = "";
	private int segId;
	private String stack;
	private int blockStack;

	private Rc2Xliff() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		return new Rc2Xliff().convert(params);
	}

	private List<String> convert(Map<String, String> params) {
		List<String> result = new ArrayList<>();
		segId = 0;
		String inputFile = params.get("source");
		String xliffFile = params.get("xliff");
		String skeletonFile = params.get("skeleton");
		String sourceLanguage = params.get("srcLang");
		String targetLanguage = params.get("tgtLang");
		String srcEncoding = params.get("srcEncoding");
		String tgtLang = "";
		if (targetLanguage != null) {
			tgtLang = "\" target-language=\"" + targetLanguage;
		}

		try {
			try (FileInputStream input = new FileInputStream(inputFile)) {
				buffer = new InputStreamReader(input, srcEncoding);
				output = new FileOutputStream(xliffFile);
				stack = "";
				writeString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
				writeString("<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" "
						+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
						+ "xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");

				writeString("<file original=\"" + inputFile + "\" source-language=\"" + sourceLanguage + tgtLang
						+ "\" tool-id=\"" + Constants.TOOLID + "\" datatype=\"winres\">\n");
				writeString("<header>\n");
				writeString("   <skl>\n");
				writeString("      <external-file href=\"" + skeletonFile + "\"/>\n");
				writeString("   </skl>\n");
				writeString("   <tool tool-version=\"" + Constants.VERSION + " " + Constants.BUILD + "\" tool-id=\""
						+ Constants.TOOLID + "\" tool-name=\"" + Constants.TOOLNAME + "\"/>\n");
				writeString("</header>\n");
				writeString("<?encoding " + srcEncoding + "?>\n");
				writeString("<body>\n");

				skeleton = new FileOutputStream(skeletonFile);

				parseRC();

				skeleton.close();

				writeString("</body>\n");
				writeString("</file>\n");
				writeString("</xliff>");
				buffer.close();
			}
			output.close();
			result.add(Constants.SUCCESS);
		} catch (IOException e) {
			Logger logger = System.getLogger(Rc2Xliff.class.getName());
			logger.log(Level.ERROR, Messages.getString("Rc2Xliff.1"), e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}
		return result;
	}

	private void writeString(String string) throws IOException {
		output.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private void writeSkeleton(String string) throws IOException {
		skeleton.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private void writeSkeleton(char character) throws IOException {
		writeSkeleton(String.valueOf(character));
	}

	private void writeSegment(String segment) throws IOException {
		if (segment.isEmpty()) {
			return;
		}
		writeString("   <trans-unit id=\"" + segId + "\" xml:space=\"preserve\">\n      <source>"
				+ XMLUtils.cleanText(segment) + "</source>\n   </trans-unit>\n");
		writeSkeleton("%%%" + segId++ + "%%%");
	}

	private void parseRC() throws IOException {
		char character;
		while (buffer.ready()) {
			character = (char) buffer.read();
			if (character == '#') {// directives
				parseDirective();
			} else if (character == '/') {// comments
				// comment /* or //
				parseComment(); // Keep the state
			} else if (!blankChar(character)) {
				parseStatement(character);
			} else {
				writeSkeleton(character);
			}
		}
	}

	private void parseComment() throws IOException {
		writeSkeleton("/"); // Last character read

		boolean bLargeComment;
		char prevChar = ' ';
		char character;

		if (buffer.ready()) {
			character = (char) buffer.read();
			writeSkeleton(character);
			bLargeComment = character == '*'; // Large comment /* */

			// Add the comment to the skeleton
			while (buffer.ready()) {
				character = (char) buffer.read();
				writeSkeleton(character);
				if (bLargeComment && prevChar == '*' && character == '/') {
					break;
				} else if (!bLargeComment && (character == '\n' || character == '\r')) {
					break;
				}
				prevChar = character;
			}
		}
	}

	private static boolean blankChar(char c) {
		return c == ' ' || c == '\n' || c == '\t' || c == '\r';
	}

	private static boolean beginBlock(String word) {
		return word.trim().equals("BEGIN") || word.trim().equals("{");
	}

	private static boolean endBlock(String word) {
		return word.trim().equals("END") || word.trim().equals("}");
	}

	private void parseStatement(char initial) throws IOException {
		String statement = String.valueOf(initial);
		writeSkeleton(initial);
		statement = statement.concat(parseWords(" ,\n\r\t", true, false));
		if (statement.trim().equals("STRINGTABLE")) {
			parseStringTable();
		} else if (statement.trim().equals("DIALOG") || statement.trim().equals("DIALOGEX")) {
			parseDialog();
		} else if (statement.trim().equals("MENU") || statement.trim().equals("MENUEX")) {
			parseMenu();
		} else if (statement.trim().equals("POPUP")) {
			parsePopup();
		} else if (statement.trim().equals("DLGINIT")) {
			parseDlgInit();
		} else if (beginBlock(statement.trim())) {// BEGIN
			blockStack = 0;
			parseBlock();
		}
	}

	private void parseDirective() throws IOException {
		char character = ' ';
		writeSkeleton('#');
		String statement = parseWords(" \t", true, false);
		if (statement.trim().equals("define")) {
			parseDefine();
		} else {
			while (buffer.ready() && character != '\r' && character != '\n') {
				character = (char) buffer.read();
				writeSkeleton(character);
			}
		}
	}

	private void parseDefine() throws IOException {
		String word = "";
		while (buffer.ready()) {
			stack = "";
			word = parseWords(" \n\t\r,\"L", false, true);
			if (word.trim().equals("\"")) {
				captureString(true);
			} else { // is END or ID
				writeSkeleton(stack);
				if (word.equals("\r") || word.equals("\n")) {
					break;
				}
			}
		}
	}

	private void parseBlock() throws IOException {
		blockStack++;
		String statement = "";
		while (blockStack != 0 && buffer.ready()) {
			statement = parseWords(" \n\t\r", true, false);
			if (beginBlock(statement.trim())) {
				blockStack++;
			} else if (endBlock(statement.trim())) {
				blockStack--;
			}
		}
	}

	private void parseDialog() throws IOException {
		parseDialogContent();
		parseControlBlock();
	}

	private void parseDialogContent() throws IOException {
		String word = " ";
		while (!beginBlock(word)) {
			word = parseWords(" \n\t\r(),", true, false);
			if (word.trim().equals("CAPTION")) {
				captureString(false);
			}
		}
	}

	private void parseControlBlock() throws IOException {
		boolean isEnd = false;
		parseWords(" (),\r\n\t", true, false);
		do {
			lastWord = lastWord.trim();
			if (lastWord.equals("CONTROL") || lastWord.equals("LTEXT") || lastWord.equals("CTEXT")
					|| lastWord.equals("RTEXT") || lastWord.equals("AUTO3STATE") ||
					lastWord.equals("AUTOCHECKBOX") || lastWord.equals("AUTORADIOBUTTON") || lastWord.equals("CHECKBOX")
					|| lastWord.equals("PUSHBOX") || lastWord.equals("PUSHBUTTON") || lastWord.equals("DEFPUSHBUTTON")
					|| lastWord.equals("RADIOBUTTON") || lastWord.equals("STATE3") || lastWord.equals("USERBUTTON")
					|| lastWord.equals("GROUPBOX")) {
				isEnd = parseControlTypeI();
			} else if (lastWord.equals("EDITTEXT") || lastWord.equals("BEDIT") || lastWord.equals("IEDIT")
					|| lastWord.equals("HEDIT") || lastWord.equals("COMBOBOX") || lastWord.equals("LISTBOX")
					|| lastWord.equals("SCROLLBAR") || lastWord.equals("ICON")) {
				isEnd = parseControlTypeI();
			} else {
				parseWords(" (),\r\t\n", true, false);
			}
			if (isEnd) {// End of block in last control
				break;
			}
		} while (true);
	}

	private static boolean isEndControlStatement(String word) {
		// list of all posible controls and END keyword
		String[] controls = new String[] { "END", "CONTROL", "LTEXT", "CTEXT", "RTEXT", "AUTO3STATE", "AUTOCHECKBOX",
				"AUTORADIOBUTTON", "CHECKBOX", "PUSHBOX", "PUSHBUTTON", "DEFPUSHBUTTON",
				"RADIOBUTTON", "STATE3", "USERBUTTON", "GROUPBOX", "EDITTEXT", "BEDIT",
				"IEDIT", "HEDIT", "COMBOBOX", "LISTBOX", "SCROLLBAR", "ICON" };

		for (int i = 0; i < controls.length; i++) {
			if (controls[i].equals(word)) {
				return true;
			}
		}
		return false;
	}

	// return true if it has a block in the control
	private boolean parseControlTypeI() throws IOException {
		char cIni = ' ';
		while (blankChar(cIni) && buffer.ready()) {
			cIni = (char) buffer.read();
			if (cIni != '"' && cIni != 'L') {
				writeSkeleton(cIni);
			}
		}

		// first parameter optional string
		if (cIni == '"') {
			captureString(true);
		} else {
			if (cIni == 'L') { // String type L"String"
				writeSkeleton(cIni);
				cIni = (char) buffer.read();
				if (cIni == '"') {
					captureString(true);
				} else {// don't have string
					writeSkeleton(cIni);
				}
			}
		}

		String word = " ";
		boolean hasBlock = false;
		while (!isEndControlStatement(word)) {
			word = parseWords(" \t(),\r\n", true, false).trim();
			if (beginBlock(word)) { // begin an optional data block in the control
				hasBlock = true;
			}
		}
		return !hasBlock && endBlock(word);// end of control block?
	}

	private void writeConditional(char character, boolean write) throws IOException {
		if (write) {
			writeSkeleton(character);
		} else {
			stack += String.valueOf(character);
		}

	}

	private void parseComment(boolean write, boolean large) throws IOException {
		writeConditional('/', write); // Last character read
		if (large) {
			writeConditional('*', write);
		} else {
			writeConditional('/', write);
		}

		char prevChar = ' ';
		char character;

		// Add the comment to the skeleton
		while (buffer.ready()) {
			character = (char) buffer.read();
			writeConditional(character, write);
			if (large && prevChar == '*' && character == '/') {
				break;
			} else if (!large && (character == '\n' || character == '\r')) {
				break;
			}
			prevChar = character;
		}

	}

	private String parseWords(String separators, boolean write, boolean withSeparator) throws IOException {
		String word = "";
		char lastChar;
		char character = 'a'; // initial value any character not in separators
		while (buffer.ready() && separators.indexOf(character) == -1) {
			character = (char) buffer.read();
			if (character == '/') {
				lastChar = character;
				character = (char) buffer.read();
				if (character == '/') {
					parseComment(write, false);
					break;
				} else if (character == '*') { // skip comments
					parseComment(write, true);
					break;
				} else { // write the last two characters
					if (write) {
						writeSkeleton(lastChar);
						writeSkeleton(character);
					} else {
						stack += String.valueOf(lastChar);
						stack += String.valueOf(character);
					}
					word = word.concat(String.valueOf(lastChar));
					if (separators.indexOf(character) == -1 || withSeparator) { // return with the separator or not
						word = word.concat(String.valueOf(character));
					}
				}
			} else if (character == '\\') {
				lastChar = character;
				character = (char) buffer.read();
				if (character != '\r' || character != '\n') {
					if (write) { // must write and \character
						writeSkeleton(lastChar);
						writeSkeleton(character);
						while (blankChar(character) && character != ' ') {
							character = (char) buffer.read();
							writeSkeleton(character);
						}
						word = word.concat(String.valueOf(character));
					} else {// not write and \ character
						stack += String.valueOf(lastChar);
						stack += String.valueOf(character);
						while (blankChar(character)) {
							character = (char) buffer.read();
							stack += String.valueOf(character);
						}
						word = word.concat(String.valueOf(character));
					}
				} else {
					if (write) {
						writeSkeleton(lastChar);
						writeSkeleton(character);
					} else {
						stack += String.valueOf(lastChar);
						stack += String.valueOf(character);
					}
					word = word.concat(String.valueOf(lastChar));
					if (separators.indexOf(character) == -1 || withSeparator) { // return with the separator or not
						word = word.concat(String.valueOf(character));
					}
				}
			} else { // not is a special character (comment or \)

				if (write) {
					writeSkeleton(character);
				} else {

					stack += String.valueOf(character);
				}
				if (separators.indexOf(character) == -1 || withSeparator) { // return with the separator or not
					word = word.concat(String.valueOf(character));
				}
			}
		}
		lastWord = word;
		return word;
	}

	private void captureString(boolean startNow) throws IOException {
		int quotes = 0;
		if (startNow) {
			quotes = 1; // now in the string
		}
		char character;
		char lastChar;
		String word = "";
		while (buffer.ready() && quotes < 2) {
			character = (char) buffer.read();
			if (quotes == 0) { // not in the string yet
				if (character == '"') {// begining of string
					quotes++;
				} else {// not in the string yet
					writeSkeleton(character);
				}
			} else { // is in the string
				if (character == '"') { // end of string Careful can be the \" escape character
					if (word.isEmpty()) {
						writeSkeleton('"');
						writeSkeleton('"');
					} else { // string is empty string
						writeSegment(word);
					}
					quotes++;
				} else { // midle of string
					if (character == '\\') { // if is escape character
						lastChar = character;
						character = (char) buffer.read();
						if (character == '\n' || character == '\r') {
							while (blankChar(character) && character != ' ') {
								character = (char) buffer.read();
							}
							if (character == '"') {// if end of string in first character of the next line
								if (word.isEmpty()) {
									writeSkeleton('"');
									writeSkeleton('"');
								} else { // string is empty string
									writeSegment(word);
								}
							} else { // normal character in the line below \
								word = word.concat(String.valueOf(character));
							}
						} else { // Normal escape character
							word = word.concat(String.valueOf(lastChar));
							word = word.concat(String.valueOf(character));
						}
					} else { // Normal character
						word = word.concat(String.valueOf(character));
					}
				}
			}
		}
	}

	private void parseStringTable() throws IOException {
		String word = " ";
		while (!beginBlock(word)) {
			word = parseWords(" \n\t\r,", true, false);
		}

		while (!endBlock(word)) {
			stack = "";
			word = parseWords(" \n\t\r,\"L", false, true);
			if (word.trim().equals("\"")) {
				captureString(true);
			} else { // is END or ID
				writeSkeleton(stack);
			}
		}
	}

	private void parseMenu() throws IOException {
		String word = " ";
		while (!beginBlock(word)) {
			word = parseWords(" \n\t\r,", true, false);
		}
		parseMenuBlock();
	}

	private void parseMenuBlock() throws IOException {
		String word = " ";
		while (!endBlock(word)) {
			word = parseWords(" ,\n\t\r\"", false, true);
			if (word.trim().equals("MENUITEM")) {
				writeSkeleton(stack);
				stack = "";
				word = parseWords(" ,\n\t\r\"L", false, true);
				if (word.trim().equals("\"")) {
					stack = "";
					captureString(true);
				} else { // SEPARATOR
					writeSkeleton(stack);
					stack = "";
				}
			} else if (word.trim().equals("POPUP")) {
				writeSkeleton(stack);
				stack = "";
				parsePopup();
			} else if (word.trim().equals("\"")) {
				stack = "";
				captureString(true);
			} else {
				writeSkeleton(stack);
				stack = "";
			}
		}
	}

	private void parsePopup() throws IOException {
		String word = " ";

		while (!beginBlock(word)) {
			stack = "";
			word = parseWords(" \n\t\r,\"L", false, true);
			if (word.trim().equals("\"")) {
				captureString(true);
			} else { // is END or ID
				writeSkeleton(stack);
			}
		}

		stack = "";
		parseMenuBlock();
	}

	private void parseDlgInit() throws IOException {
		String word = "";
		while (buffer.ready() && !beginBlock(word)) {
			word = parseWords(" \n\t\r,", true, false);
		}

		parseDlgInitBlock();
	}

	private void parseDlgInitBlock() throws IOException {
		String word = "";
		int position = 0; // parse position in the dlginitblock
		int dataLength = 0;
		stack = "";
		while (buffer.ready() && !endBlock(word)) {
			word = parseWords(" \n\t\r,", false, false).trim();
			if (!word.isEmpty()) {
				if (word.equals("0") && position == 0) {
					break;
				}
				switch (position) {
					case 0: // id
						dataLength = 0;
						position++;
						break;
					case 1: // type
						if (word.equals("0x403") || word.equals("0x1234")) {
							position++;
						} else {
							position = 6;
						}
						writeSkeleton(stack);
						stack = "";
						break;

					case 2: // length
						if (validateNumber(word)) {
							dataLength = Integer.parseInt(word);
							position++;
						} else {
							position = 6;
						}
						break;
					case 3: // end of align 0
						position++;
						break;
					case 4: // first time data
						if (word.charAt(0) == '\"') { // case "/000"
							position = 0;
							writeSkeleton(stack);
							stack = "";
							break;
						}
						stack = "";
						writeSkeleton("###" + segId + "###,0 \r\n");
						// go to the next case now without break
					case 5: // midle of data
						extractString(word, dataLength);
						position = 0;
						stack = "";
						break;
					case 6: // not string
						writeSkeleton(stack);
						stack = "";
						break;
				}
			}
		}
		writeSkeleton(stack);
		stack = "";
	}

	private void extractString(String ini, int dataLength) throws IOException {
		byte[] array = new byte[dataLength];
		String word = "";
		int i = 1;
		int length = 0;
		array[length++] = (byte) Integer.decode(ini).intValue();
		array[length++] = (byte) (Integer.decode(ini).intValue() >> 8);
		while (i < dataLength / 2 && buffer.ready()) {
			word = parseWords(",\"", false, false).trim();
			if (word.charAt(1) == 'x' && word.length() > 3) {
				array[length++] = (byte) Integer.decode(word).intValue();
				array[length++] = (byte) (Integer.decode(word).intValue() >> 8);
			}
			i++;
		}
		if (dataLength % 2 > 0) {
			while (!word.equals("\"000\"")) {
				word = parseWords(",\n\t\r ", false, false);
			}
		}
		writeSegment(new String(array, StandardCharsets.UTF_8));
	}

	private static boolean validateNumber(String num) {
		try {
			Integer.parseInt(num);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

}
