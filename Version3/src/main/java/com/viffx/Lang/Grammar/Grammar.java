package com.viffx.Lang.Grammar;

import com.viffx.Lang.Automata.Item;
import com.viffx.Lang.Symbols.*;
import com.viffx.Lang.Utils.BufferedLexer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.viffx.Lang.Symbols.TokenType.*;
import static java.lang.Character.*;

public class Grammar {
    //[GRAMMAR_STRUCTURE]
    private final List<Symbol>          symbols         = new ArrayList<>();
    private final boolean[]             isNonTerminal;
    private final List<int[]>           pointers          = new ArrayList<>();
    private final List<Production>      productions     = new ArrayList<>();
    private final List<Set<Integer>>    first           = new ArrayList<>();
    public final int EPSILON;
    public final int EOF;
    public final int TEST;
    public final int START;

    //[PARSING_STATE]
    private final BufferedLexer lexer;
    private int numRules = 0;
    private int index = 0;


    //[CONSTRUCTORS_AND_FACTORY_METHODS]
    public Grammar(String filePath) throws Exception {
        lexer = new BufferedLexer(filePath) {
            @Override
            public void onNextChar() {}
        };
        Integer epsilon = parseRules();
        if (epsilon == null) {
            symbols.add(Terminal.EPSILON);
            EPSILON = symbols.size() - 1;
        } else {
            EPSILON = epsilon;
        }
        symbols.add(Terminal.EOF);
        EOF = symbols.size() - 1;
        symbols.add(Terminal.TEST);
        TEST = symbols.size() - 1;
        {//Analyze non-terminals
            int start = 1;
            isNonTerminal = new boolean[symbols.size()];
            boolean foundStart = false;
            boolean isCurrentNonTerminal;
            for (int i = 0; i < symbols.size(); i++) {
                Symbol s = symbols.get(i);
                isCurrentNonTerminal = s.type() == NON_TERMINAL;
                isNonTerminal[i] = isCurrentNonTerminal;
                if (isCurrentNonTerminal && s.value().equals("START")) {
                    if (foundStart) throw new IllegalArgumentException("The NonTerminal START cannot be declared more than once.");
                    start = i;
                    foundStart = true;
                }
            }
            START = start;
        }
        {//pad pointers
            List<int[]> temp = new ArrayList<>(pointers);
            pointers.clear();
            int i = 0;
            for (boolean b : isNonTerminal) {
                pointers.add(b ? temp.get(i++) : new int[0]);
            }
        }

        verifyStartFormat();
        calculateFirstSets();
    }
    @NotNull
    @Contract("_ -> new")
    public static Grammar load(String filePath) throws Exception {
        return new Grammar(filePath);
    }

    //[PARSING_WORKFLOW]
    private Integer parseRules() throws Exception {
        Set<Symbol> defined = new HashSet<>();
        HashMap<Symbol,Integer> symbolsMap = new HashMap<>();

        while (true) {
            while (!lexer.eof() && isWhitespace(lexer.crntChar())) {
                lexer.nextChar();
            }
            if (lexer.eof()) break;
            NonTerminal lhs = (NonTerminal) expect(null,NON_TERMINAL);
            int lhsId = parseLHS(defined, lhs, symbolsMap);
            int from = productions.size();
            parseRHS(lhsId, symbolsMap);
            int to = productions.size();
            pointers.add(new int[]{from,to});
        }
        List<Symbol> undefined = new ArrayList<>();
        if (!defined.contains(NonTerminal.START)) undefined.add(new NonTerminal("START\tSTART-must-be-defined-in-the-format:-START->-NON_TERMINAL;\n\t\t\t\tSTART-is-considered-to-be-the-entry-point-to-your-grammar."));
        for (Symbol symbol : symbolsMap.keySet()) {
            if (symbol.type() != NON_TERMINAL) continue;
            if (!defined.contains(symbol)) undefined.add(symbol);
        }
        if (undefined.isEmpty()) return symbolsMap.get(Terminal.EPSILON);;
        throw new IllegalArgumentException("\n\tThe following nonTerminals are undefined in the input grammar: \n\t\t" + undefined.toString().replaceAll("[\\[\\] ]","").replaceAll("-"," ").replaceAll(",","\n\t\t"));
    }
    private int getSymbolId(Symbol s, Map<Symbol,Integer> map) {
        return map.computeIfAbsent(s, sym -> {
            int id = symbols.size();
            symbols.add(sym);
            return id;
        });
    }
    private int parseLHS(Set<Symbol> reserved, NonTerminal lhs, HashMap<Symbol, Integer> symbolsMap) throws Exception {
        numRules++;
        if (!reserved.add(lhs)) throw new IllegalStateException(errorContext() + "nonTerminal: " + lhs + " is already defined");
        int lhsId = getSymbolId(lhs,symbolsMap);
        expect(">", LEX_SYMBOL);
        return lhsId;
    }
    private @NotNull Symbol initRhs() throws Exception {
        Symbol current = next();
        TokenType type = current.type();
        if (Objects.requireNonNull(type) == TokenType.EOF) {
            throw new IOException(errorContext() + "Reached end of file while defining a non terminal");
        } else if (type == LEX_SYMBOL) {
            throw new IOException(errorContext() + current + " cannot follow a LEX_SYMBOL('>')");
        }
        return current;
    }
    private void parseRHS(int lhsId, HashMap<Symbol, Integer> symbolsMap) throws Exception {
        Symbol current = initRhs();
        Production production = new Production(lhsId);
        label:
        while (true) {
            if (current.type() != TokenType.EPSILON) production.add(getSymbolId(current,symbolsMap));
            current = next();

            TokenType type = current.type();
            if (Objects.requireNonNull(type) == TokenType.EOF) throw new IOException(errorContext() + "Reached end of file while defining a non terminal");
            if (type != LEX_SYMBOL) continue;
            productions.add(production);
            production = new Production(lhsId);
            String value = current.value();
            switch (value) {
                case ";":
                    break label;
                case ">":
                    index-= 2;
                    throw new IOException(errorContext() + "MISSING SEMICOLON");
                case "|":
                    current = next();
                    continue;
            }
            throw new IOException(errorContext() + "Unexpected symbol:" + current);
        }
    }
    private void verifyStartFormat() {
        int[] data = pointers.get(START);
        Production production = productions.get(data[0]);
        Integer symbol = production.getFirst();
        if (data[1] - data[0] != 1 || !isNonTerminal(symbol) || production.size() != 1 || symbol(symbol).value().equals("START"))
            throw new IllegalArgumentException(
                    String.format("""
                                    
                                    \tThe NonTerminal START must be defined in the format: START > NON_TERMINAL;
                                    \t\tproductions:    expected: 1           got: %d
                                    \t\tlength:         expected: 1           got: %d
                                    \t\tisNonTerminal:  expected: true        got: %b
                                    \t\tsymbolValue:    expected: not "START" got: "%s\"""",
                            data[1] - data[0],
                            production.size(),
                            isNonTerminal(production.getFirst()),
                            symbol(symbol).value()
                    )
            );
    }
    public void calculateFirstSets() {
        // Ensure first set exists for every symbol
        while (first.size() < symbols.size()) first.add(new HashSet<>());

        boolean changed;
        do {
            changed = false;

            for (Production prod : productions) {
                int lhs = prod.lhs();
                Set<Integer> lhsFirst = first.get(lhs);

                boolean allNullable = true;

                for (int symIndex : prod) {
                    Symbol sym = symbols.get(symIndex);

                    if (sym instanceof Terminal) {
                        // add this terminal to lhs FIRST
                        if (lhsFirst.add(symIndex)) changed = true;
                        allNullable = false;
                        break;
                    } else { // NonTerminal
                        Set<Integer> symFirst = first.get(symIndex);

                        // add everything except EPSILON
                        for (int t : symFirst) {
                            if (t != EPSILON && lhsFirst.add(t)) {
                                changed = true;
                            }
                        }

                        if (!symFirst.contains(EPSILON)) {
                            allNullable = false;
                            break;
                        }
                    }
                }

                if (allNullable) {
                    if (lhsFirst.add(EPSILON)) changed = true;
                }
            }
        } while (changed);
    }

    //[API]

    /**
     * Returns the grammar symbol index at the current dot position of the given item.
     * <p>
     * Each {@link Item} refers to a production and a dot position within that production.
     * If the dot is positioned before the end of the production, this method returns
     * the symbol index at the dot. If the dot is at or beyond the end of the production,
     * {@code null} is returned.
     *
     * @param item the grammar item whose dot position is inspected
     * @return {@code null} if the dot is at or beyond the end, else returns
     * the symbol index at the dot position
     */
    public Integer symbol(Item item) {
        Production production = production(item.index());
        if (item.dot() >= production.size()) return EPSILON;
        return production.get(item.dot());
    }
    /**
     * Resolves a symbol index to its corresponding {@link Symbol} object.
     * <p>
     * If the provided index is {@code null}, this method returns {@code null}.
     *
     * @param index the integer index of the symbol, or {@code null}
     * @return the {@link Symbol} corresponding to the index, or {@code null} if the index is {@code null}
     */
    public Symbol symbol(Integer index) {
        if (index == null) return null;
        return symbols.get(index);
    }
    public List<Integer> beta(Item item) {
        Production prod = productions.get(item.index());
        int dot = item.dot() + 1;
        if (dot >= prod.size()) return new ArrayList<>();

        // return a new list containing RHS indices after the dot
        return new ArrayList<>(prod.subList(dot, prod.size()));
    }
    public Set<Integer> first(int index) {
        return first.get(index);
    }
    public boolean atEnd(Item item) {
        return item.dot() >= productions.get(item.index()).size();
    }
    public void forEach(int nonTerminalId, Consumer<Integer> consumer) {
        int[] data = pointers.get(nonTerminalId);
        for (int i = data[0]; i < data[1]; i++) {
            consumer.accept(i);
        }
    }
    public int size() {
        return productions.size();
    }
    public Production production(int index) {
        return productions.get(index);
    }
    public boolean isNonTerminal(Integer index) {
        return index != null && isNonTerminal[index];
    }
    public List<Symbol> symbols() {
        return symbols;
    }
    public int numSymbols() {
        return symbols.size() - 3;
    }
    public int[] pointers(int index) {
        return pointers.get(index);
    }

    //[TOKEN_UTILITIES]
    private static final Set<TokenType> allowedTypes = Set.of(LEX_SYMBOL, CHR,NON_TERMINAL);
    private String errorMsg(String expectedValue, TokenType[] types) {
        StringBuilder builder = new StringBuilder(errorContext());
        builder.append("EXPECTED ONE OF: [");
        for (int i = 0; i < types.length; i++) {
            TokenType type = types[i];
            builder.append(type).append("(");
            if (expectedValue != null) builder.append(expectedValue);
            builder.append(")");
            if (i + 1 < types.length) builder.append(", ");
        }
        builder.append("] GOT: ");
        return builder.toString();
    }
    private Symbol expect(String expectedValue, TokenType... types) throws Exception {
        String errorMsg = errorMsg(expectedValue,types);
        Symbol symbol = next();
        TokenType symbolType = symbol.type();

        if (symbolType.equals(TokenType.EOF))
            throw new IOException(errorMsg + "EOF");
        if (!allowedTypes.contains(symbolType)) {
            throw new IOException(
                    errorMsg + symbol
            );
        }

        IOException exception = new IOException(errorMsg + symbol);
        for (TokenType type : types) {
            if (!type.equals(symbolType)) continue;
            if (expectedValue == null || symbol.value().equals(expectedValue)) return symbol;
            throw exception;
        }
        throw exception;
    }
    private final List<Symbol> currentRule = new ArrayList<>();
    private Symbol next() throws Exception {
        Symbol next = nextSymbol();
        if (next.type() == LEX_SYMBOL && next.value().equals(";")) {
            currentRule.clear();
        } else {
            currentRule.add(next);
        }
        return next;
    }
    private Symbol nextSymbol() throws Exception {
        if (lexer.eof()) {return Terminal.EOF;}
        index++;
        while (!lexer.eof() && isWhitespace(lexer.crntChar())) {
            lexer.nextChar();
        }

        char c = lexer.crntChar();
        if (!isLetterOrDigit(c)) {
            lexer.nextChar();
            return switch (c) {
                case '>', '|', ';' -> new Terminal(LEX_SYMBOL, String.valueOf(c));
                default -> throw new IllegalStateException(errorContext() + " unknown symbol: " + c);
            };
        }
        StringBuilder builder = new StringBuilder();
        while (!lexer.eof() && (isLetterOrDigit(lexer.crntChar()) || lexer.crntChar() == '_')) {
            builder.append(lexer.crntChar());
            lexer.nextChar();
        }
        if (!lexer.eof() && lexer.crntChar() == '(') {
            TokenType type;
            try {
                type = TokenType.valueOf(builder.toString());
                if (type.ordinal() > STR.ordinal()) throw new Exception();
            } catch (Exception e) {
                throw new IllegalStateException(errorContext() + "illegal terminal type: " + builder);
            }
            builder = new StringBuilder();
            while (!lexer.eof()) {
                c = lexer.nextChar();
                if (c == ')') break;
                builder.append(c);
            }
            if (lexer.peekChar() == ')') {
                builder.append(lexer.nextChar());
            }
            if (lexer.eof() && c != ')') throw new IllegalStateException(errorContext() + "expected ')' got EOF");
            String value = builder.toString();
            lexer.nextChar();
            return new Terminal(type,(value.equals("ε") || value.isEmpty())? null : value);
        }
        return new NonTerminal(builder.toString());
    }

    //[ERROR_HANDLING]
    private String errorContext() {
        int[] buffer = lexer.buffer();
        String[] chars = new String[2];
        for (int i = 0; i < 2; i++) {
            char c = (char) buffer[i];
            chars[i] = switch (c) {
                case '\b' -> "\\b";
                case '\t' -> "\\t";
                case '\n' -> "\\n";
                case '\f' -> "\\f";
                case '\r' -> "\\r";
                default -> String.valueOf(c);
            };
        }
        return  "\n\tERROR: Rule:" + numRules + " Index: " + index +
                "\n\tCONTEXT: " + currentRule +
                "\n\tBuffer:['" + chars[0] + "','" + chars[1] + "']\n\t";
    }
    public String toString(Production production) {
        StringBuilder builder = new StringBuilder();
        builder.append(symbol(production.lhs()));
        builder.append(" > ");
        if (production.isEmpty()) {
            builder.append("EPSILON();");
            return builder.toString();
        }
        for (int i = 0; i < production.size(); i++) {
            builder.append(symbol(production.get(i)));
            if (i + 1 < production.size()) builder.append(" ");
        }
        builder.append(";");
        return builder.toString();
    }
    public String toString(Item item) {
        Production production = productions.get(item.index());
        StringBuilder builder = new StringBuilder();
        builder.append(symbol(production.lhs()));
        builder.append(" > ");
        for (int i = 0; i < production.size(); i++) {
            if (i == item.dot()) builder.append("• ");
            builder.append(symbol(production.get(i)));
            if (i + 1 < production.size()) builder.append(" ");
        }
        if (production.isEmpty()) builder.append("EPSILON()");
        if (production.size() == item.dot()) builder.append(" •");
        if (!production.isEmpty() && item.dot() > production.size()) builder.append(" ERROR, dot past end of production");
        Symbol lookahead = symbol(item.lookahead());
        builder.append(", [").append(lookahead == null ? "" : lookahead).append("];");
        return builder.toString();
    }
    public String toString(Set<Item> state) {
        return state.stream().map(this::toString).toList().toString();
    }
    public String toString() {
        return "symbols = " + symbols + "\n" +
                "pointers = " +
                pointers.stream()
                        .map(Arrays::toString)
                        .collect(Collectors.joining(", ", "[", "]")) +
                "\n" +
                "productions = " + productions + "\n" +
                "first = " + first + "\n" +
                "isNonTerminal = " +
                Arrays.toString(isNonTerminal) +
                "\n";

    }
}