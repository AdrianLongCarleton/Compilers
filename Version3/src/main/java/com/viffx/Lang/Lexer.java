package com.viffx.Lang;

import com.viffx.Lang.Symbols.Terminal;
import com.viffx.Lang.Utils.BufferedLexer;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

import static com.viffx.Lang.Symbols.TokenType.*;
import static java.lang.Character.*;

public class Lexer {
    BufferedLexer lexer;
    private int sourceIndex = 1;
    private int index = 1;
    private int rule = 1;
    private Terminal nextToken = null;
    private final boolean[] recognized = {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, true, false, false, false, false, false, false, true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false, false, false, true, true, true, true, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, true, false, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, true, true, true};

    public Lexer(String filePath) throws IOException {
        lexer = new BufferedLexer(filePath) {
            @Override
            public void onNextChar() {
                sourceIndex++;
                index++;
            }
        };
    }

    // Gets the nextToken token.
    public Terminal next() throws Exception {
        // Peeking optimization
        if (nextToken != null) {
            Terminal temp = nextToken.copy();
            nextToken = null;
            return temp;
        }

        // Never process past the end of the source
        if (lexer.eof()) return Terminal.EOF;

        // Get the nextToken token
        char c = lexer.crntChar();
        // Skip any white space
        if (isWhitespace(c)) {
            do {
                if (lexer.eof()) break;
                c = lexer.nextChar();
            } while (isWhitespace(c));
        }

        // Get the nextToken token
        return switch (c) {
            case '"' -> nextStringLiteral();
            case '#' -> nextComment();
            case '\'' -> nextCharLiteral();
            default -> {
                char crntChar = lexer.crntChar();
                if (recognizedChar(crntChar)) yield nextSymbol();
                if (!isLetterOrDigit(crntChar)) throw new RuntimeException("Unrecognized symbol: '" + crntChar + "'" + "Local Index: " + index + " Index: " + sourceIndex);

                StringBuilder builder = new StringBuilder();
                builder.append(crntChar);
                if (!isLetter(crntChar)) {
                    while (!lexer.eof() && isLetterOrDigit(lexer.peekChar())) {
                        builder.append(lexer.nextChar());
                    }
                    lexer.nextChar();
                    if (!builder.isEmpty()) yield new Terminal(NUM,builder.toString());
                    builder.append("@");
                }
                crntChar = lexer.crntChar();
                if (!isLetter(crntChar)) {
                    throw new RuntimeException("Unrecognized symbol: '" + crntChar + "'" + "Local Index: " + index + " Index: " + sourceIndex);
                }
                while (!lexer.eof() && isLetterOrDigit(lexer.peekChar())) {
                    builder.append(lexer.nextChar());
                }
                lexer.nextChar();
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
            builder.append(lexer.crntChar());
        } while (!lexer.eof() && recognizedChar(lexer.nextChar()));
        return new Terminal(SYM, builder.toString());
    }

    /* Helper method that enables string literals and comments
     * Returns a string composed of character up to the first non escaped end character
     */
    private String nextBlock(char endChar) throws Exception {
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        char c = lexer.nextChar();

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
            c = lexer.nextChar();
        }
        lexer.nextChar();
        return builder.toString();
    }
    public boolean recognizedChar(char c) {
        if (c >= recognized.length) return false;
        return recognized[c];
    }
}
