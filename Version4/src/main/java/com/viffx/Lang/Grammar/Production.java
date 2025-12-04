package com.viffx.Lang.Grammar;

import com.viffx.Lang.Compiler.Item;

import java.util.ArrayList;
import java.util.List;

public class Production extends ArrayList<Integer> {
    private final int lhs;

    public Production( int lhs) {
        this.lhs = lhs;
    }

    public int lhs() {
        return lhs;
    }

    public boolean atEnd(int dot) {
        return dot >= size();
    }

    public List<Integer> beta(int dot) {
        dot++;
        if (atEnd(dot)) {
            Grammar.throwIndexOutOfBoundsException(dot,size());
        }

        // return a new list containing RHS indices after the dot
        return new ArrayList<>(subList(dot, size()));
    }

    @Override
    public String toString() {
        return "Production{" +
                "lhs=" + lhs +
                ", elements=" + super.toString() +
                '}';
    }
}
