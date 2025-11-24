package com.viffx.Lang.Symbols;

import java.util.Objects;

public record NonTerminal(String value) implements Symbol {
    public static final NonTerminal START = new NonTerminal("START");

    @Override
    public TokenType type() {
        return TokenType.NON_TERMINAL;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (NonTerminal) obj;
        return Objects.equals(this.value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

}
