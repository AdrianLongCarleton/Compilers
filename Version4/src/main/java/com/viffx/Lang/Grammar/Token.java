package com.viffx.Lang.Grammar;

import com.viffx.Lang.Symbols.NonTerminal;
import com.viffx.Lang.Symbols.Symbol;
import com.viffx.Lang.Symbols.SymbolType;
import com.viffx.Lang.Symbols.Terminal;

import java.util.Arrays;
import java.util.Objects;

public record Token(TokenType type, Symbol symbol) {
    public static final Token EOF = new Token(TokenType.EOF, null);
    public static final Token START = new Token(TokenType.NON_TERMINAL, new NonTerminal("START"));

    public Symbol symbol() {
        return symbol;
    }

    public String value() {
        return symbol.value();
    }

    @Override
    public String toString() {
        return "Token{" +
                "type=" + type +
                ", symbol='" + symbol +
                "'}";
    }
}
