package com.viffx.Lang.Utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public abstract class BufferedLexer {
    private final BufferedReader reader;
    private boolean eof = false;
    private final int[] buffer = new int[2];

    public BufferedLexer(String filePath) throws IOException {
        reader = new BufferedReader(new FileReader(filePath));

        buffer[0] = readCharacter();
        buffer[1] = readCharacter();
    }

    public final boolean eof() {
        return eof;
    }

    private final int readCharacter() throws IOException {
        int character = reader.read();
        if (character == -1) eof = true;
        return character;
    }

    public final char crntChar() {
        return (char) buffer[0];
    }
    public final char nextChar() throws Exception {
        if (eof) throw new Exception("Reached the end of the file while lexing");
        onNextChar();
        buffer[0] = buffer[1];
        buffer[1] = reader.read();
        if (buffer[0] == -1) eof = true;
        return crntChar();
    }
    public final char peekChar() {
        return (char) buffer[1];
    }

    public abstract void onNextChar();

    public int[] buffer() {
        return buffer;
    }
}


