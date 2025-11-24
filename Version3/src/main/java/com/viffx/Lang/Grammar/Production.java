package com.viffx.Lang.Grammar;

import java.util.ArrayList;

public class Production extends ArrayList<Integer> {
    private final int lhs;

    public Production( int lhs) {
        this.lhs = lhs;
    }

    public int lhs() {
        return lhs;
    }
}
