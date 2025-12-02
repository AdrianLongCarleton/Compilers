package com.viffx.Lang.Grammar;

import com.viffx.Lang.Compiler.Item;

import java.util.ArrayList;
import java.util.Objects;

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
}
