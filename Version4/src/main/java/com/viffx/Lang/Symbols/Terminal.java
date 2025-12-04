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
        return type + "(" + (value == null ? "" : value.replaceAll("\n", "\\\\n")) + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Terminal terminal = (Terminal) o;
        if (terminal.value == null) return type == terminal.type;
        return Objects.equals(value, terminal.value) && type == terminal.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }
}
