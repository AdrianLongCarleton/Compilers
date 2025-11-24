package com.viffx.Lang.Automata;

public record Item(int index, int dot, Integer lookahead) {
    public Item core() {
        return new Item(index,dot,null);
    }
    public Item advance() {
        return new Item(index,dot + 1, lookahead);
    }
}

