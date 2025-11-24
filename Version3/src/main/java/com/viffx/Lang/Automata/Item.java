package com.viffx.Lang.Automata;

public record Item(int index,int dot,Integer lookahead) {
    public Item core() {
        return new Item(index,dot,null);
    }
    public Item advance() {
        return new Item(index,dot + 1, lookahead);
    }
}
//
//    public String toReadable(@NotNull Grammar g) {
////        return toString();
//        return g.production(index).toReadable(g, dot) + (lookahead == null ? "" : ("," + lookahead)) + ';';
//    }
//set functiontextobj
//    public Production production(@NotNull Grammar g) {
//        return g.production(index);
//    }
//
//    public Symbol symbol(@NotNull Grammar g) {
//        Production production = g.production(index);
//        if (dot < 0 || dot >= production.size()) return null;
//        return g.production(index).get(dot);
//    }
//
//    public List<Symbol> beta(@NotNull Grammar g) {
//        Production production = production(g);
//        if (production == null || dot + 1 >= production.size()) return new ArrayList<>();
//        return new ArrayList<>(production.subList(dot + 1, production.size()));
//    }
//
//    public boolean atEnd(@NotNull Grammar g) {
//        return dot >= production(g).size();
//    }
//
//    public Item advanceWeak(@NotNull Grammar g) {
//        if (atEnd(g)) return new Item(index,dot,null);
//        return new Item(index, dot + 1, null);
//    }
//    public Item advanceStrong(@NotNull Grammar g) {
//        if (atEnd(g)) return this;
//        return new Item(index, dot + 1, lookahead);
//    }
//    public Item core() {
//        return new Item(index,dot,null);
//    }
//
//    public int index() {
//        return index;
//    }
//
//    public int dot() {
//        return dot;
//    }
//
//    public Terminal lookahead() {
//        return lookahead;
//    }
//
//    @Override
//    public boolean equals(Object obj) {
//        if (obj == this) return true;
//        if (obj == null || obj.getClass() != this.getClass()) return false;
//        var that = (Item) obj;
//        return this.index == that.index &&
//                this.dot == that.dot &&
//                Objects.equals(this.lookahead, that.lookahead);
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hash(index, dot, lookahead);
//    }
//
//    @Override
//    public String toString() {
//        return "Item[" +
//                "index=" + index + ", " +
//                "dot=" + dot + ", " +
//                "lookahead=" + lookahead + ']';
//    }
//
//}
