package com.viffx.Lang.Compiler;

import com.viffx.Lang.Symbols.Terminal;
import com.viffx.Lang.Utils.LexicalCharacterBuffer;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static com.viffx.Lang.Symbols.SymbolType.*;
import static java.lang.Character.*;

public class Lexer {
    private LexicalCharacterBuffer buffer;
    private int sourceIndex = 1;
    private int index = 1;
    private int rule = 1;
    private Terminal nextToken = null;
    private final boolean[] recognized = {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, true, false, false, false, false, false, false, true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false, false, false, true, true, true, true, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, true, false, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, true, true, true};
    private final Set<String> keywords;

    public Lexer(String filePath, Set<String> keywords) throws IOException {
        buffer = new LexicalCharacterBuffer(filePath) {
            @Override
            public void onNextChar() {
                sourceIndex++;
                index++;
            }
        };
        this.keywords = keywords;
    }

    // Gets the nextToken token.
    public Terminal next() throws Exception {
        // Peeking optimization
        if (nextToken != null) {
            Terminal temp = new Terminal(nextToken.type(),nextToken.value());
            nextToken = null;
            return temp;
        }

        // Never process past the end of the source
        if (buffer.eof()) return Terminal.EOF;

        // Get the nextToken token
        char c = buffer.crntChar();
        // Skip any white space
        if (isWhitespace(c)) {
            boolean detected_new_line = false;
            do {
                detected_new_line |= c == '\n';
                if (buffer.eof()) break;
                c = buffer.nextChar();
            } while (isWhitespace(c));
            if (detected_new_line) return new Terminal(SYM,"\n");
        }

        // Get the nextToken token
        return switch (c) {
            case '"' -> nextStringLiteral();
            case '#' -> nextComment();
            case '\'' -> nextCharLiteral();
            default -> {
                char crntChar = buffer.crntChar();
                if (recognizedChar(crntChar)) yield nextSymbol();
                if (!isLetterOrDigit(crntChar)) throw new RuntimeException("Unrecognized symbol: '" + crntChar + "'" + "Local Index: " + index + " Index: " + sourceIndex);

                StringBuilder builder = new StringBuilder();
                builder.append(crntChar);
                if (!isLetter(crntChar)) {
                    while (!buffer.eof() && isLetterOrDigit(buffer.peekChar())) {
                        builder.append(buffer.nextChar());
                    }
                    buffer.nextChar();
                    if (!builder.isEmpty()) yield new Terminal(NUM,builder.toString());
                    builder.append("@");
                }
                crntChar = buffer.crntChar();
                if (!isLetter(crntChar)) {
                    throw new RuntimeException("Unrecognized symbol: '" + crntChar + "'" + "Local Index: " + index + " Index: " + sourceIndex);
                }
                while (!buffer.eof() && isLetterOrDigit(buffer.peekChar())) {
                    builder.append(buffer.nextChar());
                }
                buffer.nextChar();
                String keywordOrIdentifier = builder.toString();
                if (keywords.contains(keywordOrIdentifier)) {
                    yield new Terminal(KEY,builder.toString());
                }
                yield new Terminal(ID,builder.toString());
            }
        };
    }



    /* Uses nextToken to peek the nextToken token.
     * Next has an optimization where if the stored value of a peek is non-null it returns the peeked value instead
     * This optimization also helps to avoid jump backs*/
    public Terminal peek() throws Exception {
        return nextToken = next();
    }

    // Methods used by peek to handle different cases
    private Terminal nextStringLiteral() throws Exception {
        return new Terminal(STR, nextBlock('"'));
    }
    private Terminal nextCharLiteral() throws Exception {
        return new Terminal(CHR, nextBlock('\''));
    }
    private Terminal nextComment() throws Exception {
        String block = nextBlock('#');
        return new Terminal(COMMENT, block);
    }
    private Terminal nextSymbol() throws Exception {
        StringBuilder builder = new StringBuilder();
        do {
            builder.append(buffer.crntChar());
        } while (!buffer.eof() && recognizedChar(buffer.nextChar()));
        return new Terminal(SYM, builder.toString());
    }

    /* Helper method that enables string literals and comments
     * Returns a string composed of character up to the first non escaped end character
     */
    private String nextBlock(char endChar) throws Exception {
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        char c = buffer.nextChar();

        while (c != endChar || escaped) {
            if (c == '\\') {
                if (escaped) {
                    builder.append('\\');
                }
                escaped = !escaped; // Toggle the escaped state
            } else {
                if (escaped) {
                    builder.append('\\');
                    escaped = false; // Reset escaped after adding backslash
                }
                builder.append(c);
            }
            c = buffer.nextChar();
        }
        buffer.nextChar();
        return builder.toString();
    }
    public boolean recognizedChar(char c) {
        if (c >= recognized.length) return false;
        return recognized[c];
    }
}
