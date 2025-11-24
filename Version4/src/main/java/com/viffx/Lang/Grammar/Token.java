package com.viffx.Lang.Grammar;

import com.viffx.Lang.Symbols.NonTerminal;
import com.viffx.Lang.Symbols.Symbol;
import com.viffx.Lang.Symbols.SymbolType;
import com.viffx.Lang.Symbols.Terminal;

import java.util.Objects;

public record Token(TokenType type, String value) {
    public static final Token EOF = new Token(TokenType.EOF, null);
    public static final Token START = new Token(TokenType.NON_TERMINAL, "START");

    public Symbol decompose() {
        if (type.equals(TokenType.EOF) || type.equals(TokenType.SYMBOL)) throw new IllegalArgumentException("A grammar symbol or eof symbol cannot be decomposed into a symbol");
        if (type.equals(TokenType.NON_TERMINAL)) return new NonTerminal(value);
        String[] parts = value.split(",",1);
        return new Terminal(SymbolType.valueOf(parts[0]),parts[1]);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return Objects.equals(value, token.value) && type == token.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }
}
