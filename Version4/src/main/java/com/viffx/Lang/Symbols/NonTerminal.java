package com.viffx.Lang.Symbols;

import java.util.Objects;

public record NonTerminal(String value) implements Symbol {
    public static final NonTerminal START = new NonTerminal("START");

    @Override
    public String toString() {
        return value;
    }

    @Override
    public String value() {
        return value;
    }
}
