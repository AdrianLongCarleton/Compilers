package com.viffx.Lang.Symbols;

import java.util.Arrays;
import java.util.Objects;

public record NonTerminal(String value, int[] productionsRange) implements Symbol {
    public NonTerminal(String value) {
        this(value,null);
    }
    public static final NonTerminal START = new NonTerminal("START", null);

    @Override
    public String toString() {
        return value;// + Arrays.toString(productionsRange);
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        NonTerminal that = (NonTerminal) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
