package com.viffx.Lang.Symbols;

import java.util.Objects;

public record Terminal(SymbolType type, String value) implements Symbol {
    public static final Terminal EPSILON = new Terminal(SymbolType.EPSILON,null);
    public static final Terminal EOF = new Terminal(SymbolType.EOF,"$");
    public static final Terminal TEST = new Terminal(SymbolType.TEST,"#");

    public Terminal(String value) {
        this(null,value);
    }

    @Override
    public String toString() {
        return type + "(" + (value == null ? "" : value) + ")";
    }
}
