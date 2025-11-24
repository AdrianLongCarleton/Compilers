package com.viffx.Lang.Symbols;

import java.util.Objects;

public record Terminal(SymbolType type, String value) implements Symbol {
    public static final Terminal EOR = new Terminal(SymbolType.EOR,null);
    public static final Terminal EPSILON = new Terminal(SymbolType.EPSILON,null);
    public static final Terminal EOF = new Terminal(SymbolType.EOF,"$");
    public static final Terminal TEST = new Terminal(SymbolType.TEST,"#");
//    public static final Terminal END =  new Terminal(TokenType.END,"$");

    public Terminal(String value) {
        this(null,value);
    }

    public Terminal copy() {
         return new Terminal(type,value);
    }

    @Override
    public String toString() {
//        return value;
        return type + "(" + (value == null ? "" : value) + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Terminal terminal = (Terminal) o;
//        if (this.type() == TokenType.ANY || terminal.type() == TokenType.ANY) return true;
        return type == terminal.type && (value == null || terminal.value == null || Objects.equals(value, terminal.value));
    }
}
