package com.viffx.Lang.Utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Provides a two-character buffered reader for lexers, allowing single-character
 * lookahead and controlled advancement through a text stream.
 *
 * <p>This class abstracts away low-level character I/O, maintaining a rolling
 * buffer of the current and next characters. It is designed to be subclassed by
 * concrete lexer implementations that define tokenization logic via
 * {@link #onNextChar()}.
 *
 * <p>EOF (end-of-file) is detected when the current character slot in the buffer
 * contains {@code -1}.
 */

public class LexicalCharacterBuffer {
    // ====== INSTANCE FIELDS ====== //

    /**
    * Reader supplying character from the source file.
    * */
    private final BufferedReader reader;

    /**
     * Holds the current and next character codes from the input stream.
     * <ul>
     *     <li>{@code buffer[0]} - current character</li>
     *     <li>{@code buffer[1]} - next lookahead character</li>
     * </ul>
     * */
    private final int[] buffer = new int[2];

    /**
     * Indicates whether the end of the input has been reached*/
    private boolean eof = false;

    // ====== CONSTRUCTORS ====== //
    /**
     * Opens the given file for buffered lexical reading and initializes
     * the two-character buffer.
     *
     * @param filePath path to the source file being lexed
     * @throws IOException if an I/O error occurs while opening or reading the file
     */
    public LexicalCharacterBuffer(String filePath) throws IOException {
        reader = new BufferedReader(new FileReader(filePath));

        // initialize the buffer
        buffer[0] = reader.read();
        buffer[1] = reader.read();

        // update EOF status
        eof = buffer[0] == -1;
    }

    // ====== PUBLIC API METHODS ====== //
    /**
     * Returns {@code true} if the end of the input has been reached.
     *
     * @return {@code true} if no more characters are available
     */
    public final boolean eof() {
        return eof;
    }

    /**
     * Returns the current character in the buffer.
     *
     * @return the current character
     */
    public final char crntChar() {
        return (char) buffer[0];
    }

    /**
     * Returns the next character in the buffer without advancing it.
     *
     * @return the next character
     */
    public final char peekChar() {
        return (char) buffer[1];
    }

    /**
     * Advances the buffer by one character, shifting the next lookahead
     * character into the current slot and reading a new lookahead from
     * the underlying reader.
     *
     * <p>Before the shift, this method calls {@link #onNextChar()} to allow
     * subclass-specific behavior, such as tracking line/column information.
     *
     * @return the newly current character after advancing
     * @throws IOException if the end of the file has already been reached
     */
    public final char nextChar() throws IOException {
        if (eof) throw new IOException("Reached the end of the file.");

        onNextChar();

        // shift characters
        buffer[0] = buffer[1];
        buffer[1] = reader.read();

        // update EOF status
        eof = buffer[0] == -1;

        return crntChar();
    }

    // ====== API HOOKS ====== //
    /**
     * Called immediately before advancing the buffer to the next character.
     *
     * <p>Subclasses can override this to update lexer state, such as:
     * <ul>
     *   <li>tracking line and column numbers,</li>
     *   <li>counting total characters read,</li>
     *   <li>or handling escaped sequences.</li>
     * </ul>
     */
    public void onNextChar() {}

    // ====== DEBUG INFO ====== //
    /**
     * Returns a human-readable representation of the current buffer contents,
     * useful for debugging lexer behavior.
     *
     * @return a string showing the current and next characters in the buffer
     */
    public String buffer() {
        String[] chars = new String[2];
        for (int i = 0; i < 2; i++) {
            char c = (char) buffer[i];
            chars[i] = switch (c) {
                case '\b' -> "\\b";
                case '\t' -> "\\t";
                case '\n' -> "\\n";
                case '\f' -> "\\f";
                case '\r' -> "\\r";
                default -> String.valueOf(c);
            };
        }
        return String.format("['%s','%s']",chars[0],chars[1]);
    }
}