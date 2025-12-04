package com.viffx.Lang.Symbols;

import java.util.ArrayList;
import java.util.List;

public final class AstNode {
    private final Symbol symbol;
    private final List<AstNode> children = new ArrayList<>();

    public AstNode(Symbol symbol) {
        this.symbol = symbol;
    }

    public List<AstNode> children() {
        return children;
    }

    public Symbol symbol() {
        return symbol;
    }

    public void add(AstNode child) {
        children.add(child);
    }

    @Override
    public String toString() {
        return "AstNode{" +
                "symbol=" + symbol + ", " +
                "children=" + children +
                '}';
    }
}
