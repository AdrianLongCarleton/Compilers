package com.viffx.Lang.Grammar;

import com.viffx.Lang.Automata.Item;
import com.viffx.Lang.Symbols.Symbol;
import com.viffx.Lang.Symbols.SymbolType;
import com.viffx.Lang.Utils.LexicalCharacterBuffer;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

import static java.lang.Character.isLetterOrDigit;
import static java.lang.Character.isWhitespace;

public class Grammar {
    // ====== INSTANCE FIELDS ====== //
    // Symbols fields
    private boolean startDefined = false;
    private final Symbol[] symbols;
    private final boolean[] isNonTerminal;
    private final int[] nonTerminals;
    private int EPSILON;
    private int EOF;
    private int TEST;
    private int START;

    // Productions fields
    private final List<int[]> productionRanges = new ArrayList<>();
    private final List<Production> productions = new ArrayList<>();

    // Lexing fields
    private final LexicalCharacterBuffer lexer;
    private int numRules = 0;
    private int index = 0;
    private final List<Token> currentRule = new ArrayList<Token>();

    // ====== CONSTRUCTORS ====== //
    private Grammar(String filePath) throws Exception {
        lexer = new LexicalCharacterBuffer(filePath);

        ParseResult parseResult = parseRules();
        SymbolProcessingResult symbolProcessingResult = processSymbols(parseResult.symbolsMap);
        symbols = symbolProcessingResult.symbols;
        isNonTerminal = symbolProcessingResult.isNonTerminal;
        nonTerminals = symbolProcessingResult.nonTerminals;

        checkForUndefinedNonTerminals(parseResult);

        // properly pad the pointers
        List<int[]> temp = new ArrayList<>(productionRanges);
        productionRanges.clear();
        int index = 0;
        for (boolean b : isNonTerminal) {
            productionRanges.add(b ? temp.get(index++) : new int[0]);
        }

        // eliminate redundant epsilons
        for (int i = 0; i < productions.size(); i++) {
            Production production = productions.get(i);

            // eliminate epsilon
            Production replacement = new Production(production.lhs());
            for (int symbol : production) {
                if (symbol != EPSILON) replacement.add(symbol);
            }

            // add epsilon to truly empty productions
            if (production.isEmpty()) {
                production.add(EPSILON);
            }

            // replace the production
            productions.set(i,replacement);
        }
    }
    public static Grammar load(String filePath) throws Exception {
        return new Grammar(filePath);
    }

    // ====== PUBLIC API ====== //

    // Items

    /**
     * Returns if the the input {@code item} is at or beyond the end of the production it references
     *
     * @param item the grammar item whose dot position is inspected
     * @return if the item's dot is at or beyond the end of the production it references
     * @throws NullPointerException if {@code item} is {@code null}
     */
    public boolean atEnd(Item item) {
        Objects.requireNonNull(item, "item cannot be null");
        return item.dot() >= productions.get(item.index()).size();
    }

    /**
     * Returns the grammar symbol index at the current dot position of the given item.
     * <p>
     * Each {@link Item} refers to a production and a dot position within that production.
     * If the dot is positioned before the end of the production, this method returns
     * the symbol index at the dot. If the dot is at or beyond the end of the production,
     * an {@code IndexOutOfBoundsException} is thrown.
     *
     * @param item the grammar item whose dot position is inspected
     * @return the symbol index at the dot position
     * @throws IndexOutOfBoundsException if the dot position is equal to or greater than the production size
     * @throws NullPointerException if {@code item} is {@code null}
     */
    public Integer symbol(Item item) {
        Objects.requireNonNull(item, "item cannot be null");

        Production production = productions.get(item.index());
        if (atEnd(item)) {
            throwIndexOutOfBoundsException(item.dot(),production.size());
        }
        return production.get(item.dot());
    }

    /**
     * Returns the symbol indexes after the current symbol index of the production that {@code item} references
     *
     * @param item the grammar item whose dot position is inspected
     * @return a list of all the symbol indexes after the symbol directly after the dot
     * @throws IndexOutOfBoundsException if the {@code dot + 1} is equal to or greater than the production size
     * @throws NullPointerException if {@code item} is {@code null}
     */
    public List<Integer> beta(Item item) {
        Objects.requireNonNull(item, "item cannot be null");

        Production production = productions.get(item.index());
        int dot = item.dot() + 1;
        if (dot >= production.size()) {
            throwIndexOutOfBoundsException(dot,production.size());
        }

        // return a new list containing RHS indices after the dot
        return new ArrayList<>(production.subList(dot, production.size()));
    }

    /**
     * Returns a string representation of the given grammar {@link Item}.
     * <p>
     * The format follows a conventional "production with dot" style used in parsing algorithms
     * (e.g., Earley or LR items). For example:
     * <pre>
     *   E → E • + T, [EOF];
     * </pre>
     * indicates that the dot (•) marks the current parsing position within the production.
     * <p>
     * If the production is empty, "EPSILON()" is shown. If the dot position exceeds the production length,
     * an error marker is appended to highlight the invalid state.
     *
     * @param item the grammar item to represent as a string
     * @return a human-readable string showing the production and dot position
     */
    public String toString(Item item) {
        if (item == null) return "null";
        // Retrieve the production corresponding to this item
        Production production = productions.get(item.index());

        StringBuilder builder = new StringBuilder();

        // Append the left-hand side (LHS) symbol of the production
        builder.append(symbols[production.lhs()]);
        builder.append(" > "); // ">" visually separates LHS and RHS

        // Append each symbol in the production's right-hand side (RHS)
        for (int i = 0; i < production.size(); i++) {
            // Insert the dot (•) before the current symbol if it matches the dot position
            if (i == item.dot()) builder.append("• ");

            // Append the current RHS symbol
            builder.append(symbols[production.get(i)]);

            // Add a space if there are more symbols to come
            if (i + 1 < production.size()) builder.append(" ");
        }

        // If the production has no symbols (i.e., empty production)
        if (production.isEmpty()) builder.append("EPSILON()");

        // If the dot is positioned at the very end of the production
        if (production.size() == item.dot()) builder.append(" •");

        // If the dot somehow moved past the production's end, flag it as an error
        if (!production.isEmpty() && item.dot() > production.size()) {
            builder.append(" ERROR, dot past end of production");
        }

        // Append the lookahead symbol (if any)
        Symbol lookahead = symbols[item.lookahead()];
        builder.append(", [")
               .append(lookahead == null ? "" : lookahead)
               .append("];");

        return builder.toString();
    }



    // Productions

    /**
     * Returns a string representation of the given {@link Production}.
     * <p>
     * The output follows a conventional grammar production format:
     * <pre>
     *   A > B C D;
     * </pre>
     * If the production has no symbols on its right-hand side (RHS),
     * the method prints "EPSILON();" to denote an empty production.
     *
     * @param production the grammar production to represent as a string
     * @return a human-readable string showing the production rule
     */
    public String toString(Production production) {
        StringBuilder builder = new StringBuilder();

        // Append the left-hand side (LHS) symbol of the production
        builder.append(symbols[production.lhs()]);
        builder.append(" > "); // visually separate LHS and RHS

        // Handle the case where the production is empty (no RHS symbols)
        if (production.isEmpty()) {
            builder.append("EPSILON();");
            return builder.toString(); // return early since there’s nothing else to append
        }

        // Append each symbol on the right-hand side (RHS) of the production
        for (int i = 0; i < production.size(); i++) {
            builder.append(symbols[production.get(i)]); // append the i-th RHS symbol

            // Add a space between symbols, but not after the last one
            if (i + 1 < production.size()) builder.append(" ");
        }

        // End the production with a semicolon for readability
        builder.append(";");

        return builder.toString();
    }



    // Grammar - Symbol Access

   /**
     * Resolves a symbol index to its corresponding {@link Symbol} object.
     *
     * @param symbol the integer index of the symbol
     * @return the {@link Symbol} corresponding to the index
     */
    public Symbol symbol(int symbol) {
        return symbols[symbol];
    }

    /**
     * Returns whether the {@code symbol} index inputted corresponds to a non-terminal
     *
     * @param symbol the integer index of the symbol
     * @return if the input symbol index is a non-terminal
     */
    public boolean isNonTerminal(int symbol) {
        return isNonTerminal[symbol];
    }

    /**
     * Returns the total number of grammar symbols (both terminals and non-terminals).
     *
     * @return the number of symbols in the grammar
     */
    public int symbolsSize() {
        return symbols.length;
    }

    /**
     * Applies the given {@link Consumer} action to each symbol in the grammar.
     * <p>
     * This allows functional-style iteration over all symbols without exposing the internal array.
     *
     * @param consumer a function to process each {@link Symbol}
     */
    public void forEachSymbol(Consumer<Symbol> consumer) {
        for (Symbol symbol : symbols) {
            consumer.accept(symbol);
        }
    }

    /**
     * Applies the given {@link Consumer} action to each non-terminal symbol index.
     * <p>
     * Non-terminals are represented by integer IDs; this method iterates over all of them.
     *
     * @param consumer a function to process each non-terminal index
     */
    public void forEachNonTerminal(Consumer<Integer> consumer) {
        for (int nonTerminal : nonTerminals) {
            consumer.accept(nonTerminal);
        }
    }



    // Grammar - Production Access

    /**
     * Resolves a production index to its corresponding {@link Production} object.
     *
     * @param production the integer index of the production
     * @return the {@link Production} corresponding to the index
     */
    public Production production(int production) {
        return productions.get(production);
    }

    /**
     * Returns the total number of productions in the grammar.
     *
     * @return the number of productions
     */
    public int productionsSize() {
        return productions.size();
    }

    /**
     * Applies the given {@link Consumer} action to each {@link Production} in the grammar.
     * <p>
     * Equivalent to calling {@code productions.forEach(consumer)}, but
     * this provides a cleaner, encapsulated API.
     *
     * @param consumer a function to process each {@link Production}
     */
    public void forEachProduction(Consumer<Production> consumer) {
        productions.forEach(consumer);
    }

    /**
     * Applies the given {@link Consumer} action to each production index
     * associated with a specific non-terminal.
     * <p>
     * Each non-terminal has a range of productions stored in {@code productionRanges},
     * where {@code data[0]} is the start index (inclusive) and {@code data[1]} is the end index (exclusive).
     *
     * @param nonTerminal the non-terminal whose productions to iterate over
     * @param consumer a function to process each production index belonging to that non-terminal
     */
    public void forEachProduction(int nonTerminal, Consumer<Integer> consumer) {
        int[] data = productionRanges.get(nonTerminal);

        // Iterate over the production index range for the given non-terminal
        for (int i = data[0]; i < data[1]; i++) {
            consumer.accept(i);
        }
    }

    /**
     * Returns the range of productions associated with the specified non-terminal.
     * <p>
     * Each non-terminal corresponds to a range of production indices represented as a two-element array:
     * <ul>
     *   <li><code>range[0]</code> — the start index (inclusive)</li>
     *   <li><code>range[1]</code> — the end index (exclusive)</li>
     * </ul>
     * This allows efficient lookup of all productions belonging to a specific non-terminal.
     *
     * @param nonTerminal the non-terminal whose production range is requested
     * @return a two-element array containing the start and end indices (inclusive/exclusive)
     * @throws IndexOutOfBoundsException if {@code nonTerminal} is invalid
     */
    public int[] productionRanges(int nonTerminal) {
        return productionRanges.get(nonTerminal);
    }



    // Grammar - Special Symbols

    /**
     * Returns the index of the {@code EPSILON} symbol.
     * <p>
     * {@code EPSILON} typically represents the empty production (ε) in grammar definitions.
     *
     * @return the integer index of the {@code EPSILON} symbol in the symbol table
     */
    public int EPSILON() {
        return EPSILON;
    }

    /**
     * Returns the index of the {@code EOF} (end-of-file) symbol.
     * <p>
     * {@code EOF} marks the end of the input stream in parsing.
     *
     * @return the integer index of the {@code EOF} symbol in the symbol table
     */
    public int EOF() {
        return EOF;
    }

    /**
     * Returns the index of the {@code TEST} symbol.
     * <p>
     * This symbol may represent a debugging or placeholder symbol used internally
     * for grammar validation or testing.
     *
     * @return the integer index of the {@code TEST} symbol in the symbol table
     */
    public int TEST() {
        return TEST;
    }

    /**
     * Returns the index of the {@code START} symbol.
     * <p>
     * {@code START} is the root non-terminal from which parsing begins.
     *
     * @return the integer index of the {@code START} symbol in the symbol table
     */
    public int START() {
        return START;
    }



    // Public API helper methods

    private void throwIndexOutOfBoundsException(int index, int size) {
        throw new IndexOutOfBoundsException(
                String.format("Index %d out of bounds for length %d",
                        index,
                        size
                )
        );
    }



    // ====== INTERNAL DATA TYPES ====== //
    private record ParseResult(HashMap<Token,Integer> symbolsMap, HashSet<Token> defined) {}
    private record SymbolProcessingResult(Symbol[] symbols, boolean[] isNonTerminal, int[] nonTerminals) {}

    // ====== PARSING METHODS ====== //

    // Repeatedly call the parseRule method until all the rules have been parsed
    private ParseResult parseRules() throws Exception {
        HashMap<Token,Integer> symbols = new HashMap<>();
        HashSet<Token> defined = new HashSet<>();
        while (true) {
            ignoreWhiteSpace();

            // break if after absorbing white space we reach the end of the file
            if (lexer.eof()) break;

            // parse a rule
            defined.add(parseRule(symbols));
        }
        return new ParseResult(symbols,defined);
    }

    /**
     * Parses a single grammar rule and updates the symbol and production tables accordingly.
     * <p>
     * In memory, the grammar maintains:
     * <ul>
     *   <li>A list of {@code symbols}, where each symbol (terminal or non-terminal) is assigned a unique integer index.</li>
     *   <li>A list of {@code productions}, where each production is represented as a list of integer symbol indices.</li>
     *   <li>A list of {@code productionRanges}, mapping each non-terminal to a range of production indices.</li>
     * </ul>
     *
     * This method processes input in the form:
     * <pre>
     *   NonTerminal1 > NonTerminal2 Terminal1(1) | NonTerminal3;
     * </pre>
     *
     * And compiles it into the following internal structures:
     * <p>
     * <b>symbolsMap:</b>
     * <pre>
     *   NonTerminal1  -> 0
     *   NonTerminal2  -> 1
     *   Terminal1(1)  -> 2
     *   NonTerminal3  -> 3
     * </pre>
     *
     * <b>productionsList:</b>
     * <pre>
     *   0 > 1, 2;
     *   0 > 3;
     * </pre>
     *
     * <b>productionRanges:</b>
     * <pre>
     *   [0, 2)
     * </pre>
     * where the range indicates the subset of productions belonging to the non-terminal {@code NonTerminal1}.
     * <p>
     * The method also enforces the following constraints:
     * <ul>
     *   <li>The {@code START} non-terminal may only be defined once.</li>
     *   <li>The {@code START} non-terminal must have exactly one production.</li>
     *   <li>The {@code START} production must contain exactly one symbol on its right-hand side.</li>
     * </ul>
     *
     * @param symbols a map tracking all defined symbols, associating each {@link Token} with a unique integer ID
     * @return the {@link Token} representing the left-hand side (LHS) non-terminal of the parsed rule
     * @throws IOException if the grammar rule is malformed, violates constraints, or the file ends unexpectedly
     */
    private Token parseRule(HashMap<Token,Integer> symbols) throws IOException {
        // update and reset parsing state
        numRules++;
        currentRule.clear();

        // ------ Parse the non-terminal declaration (left hand side) ------ //
        Token leftHandSide = expect(null,TokenType.NON_TERMINAL);
        if (Token.START.equals(leftHandSide)) {
            if (startDefined) throw new IOException(errorContext() +"The NonTerminal START can only be defined once.");
            startDefined = true;
        }
        if (symbols.containsKey(leftHandSide)) throw new IOException(errorContext() + "nonTerminal: " + leftHandSide + " is already defined");

        currentRule.add(leftHandSide);
        // register it
        int id = symbols.size();
        symbols.put(leftHandSide,id);

        // parse some syntax
        expect(">",TokenType.SYMBOL);
        Token current = next();
        if (current.type().equals(TokenType.EOF)) {
            throw new IOException(errorContext() + "Reached end of file while defining a non terminal");
        } else if (current.type().equals(TokenType.SYMBOL)) {
            throw new IOException(errorContext() + current + " cannot follow a LEX_SYMBOL('>')");
        }
        currentRule.add(current);

        // ------ parse the right hand side ------ //
        int from = productions.size();

        Production production = new Production(id);
        // State machine stuff
        label:
        while (true) {
            // detect an illegal double symbol usage
            if (current.type() == TokenType.SYMBOL) {
                throw new IOException(errorContext() + "Unexpected symbol:" + current);
            }
            if (Token.START.equals(current)) {
                throw new IOException(errorContext() + "The NonTerminal START cannot be part of any right hand side.");
            }

            // get the integer that represent the current token
            id = symbols.computeIfAbsent(current,_ -> symbols.size());

            // append the symbol id to the end of the current production
            production.add(id);

            // advance to the next token
            current = next();
            currentRule.add(current);
            TokenType type = current.type();

            if (Objects.requireNonNull(type) == TokenType.EOF) throw new IOException(errorContext() + "Reached end of file while defining a non terminal");
            if (type != TokenType.SYMBOL) continue;

            // A ";", ">" or a "|" has been detected meaning that a change of state is required

            // The current production is terminated
            // Begin a new production for id
            productions.add(production);
            production = new Production(id);

            // Change the state of the parsing state machine
            String value = current.value();
            switch (value) {
                case ";": // The current rule is done being defined, exit.
                    break label;
                case ">": // Use began defining a new rule while the current rule is not being defined. Report an error.
                    index-= 2;
                    throw new IOException(errorContext() + "MISSING SEMICOLON");
                case "|": // A new production is being defined for lhs, continue parsing
                    current = next(); // absorb the grammar symbol
                    currentRule.add(current);
            }
        }

        // record the productionRange for the left hand side
        int to = productions.size();

        if (Token.START.equals(leftHandSide)) {
            if (to - from != 1) throw new IOException(errorContext() + " The NonTerminal START must have only one production.");
            if (productions.get(from).size() != 1) throw new IOException(errorContext() + " The NonTerminal START may only have one symbol in the right hand side.");
        }

        productionRanges.add(new int[]{from,to});

        return leftHandSide;
    }

    // ====== SYMBOL FINALIZATION ====== //
    // register special symbols and finalize the symbols data structure by reducing them to arrays
    private SymbolProcessingResult processSymbols(HashMap<Token,Integer> symbolsMap) {
        EPSILON = symbolsMap.computeIfAbsent(new Token(TokenType.TERMINAL,"EPSILON,null"),_ -> symbolsMap.size());
        TEST = symbolsMap.computeIfAbsent(new Token(TokenType.TERMINAL,"TEST,#"), _ -> symbolsMap.size());
        EOF = symbolsMap.computeIfAbsent(new Token(TokenType.TERMINAL,"EOF,$"), _ -> symbolsMap.size());

        Symbol[] symbols = new Symbol[symbolsMap.size()];
        boolean[] isNonTerminal = new boolean[symbolsMap.size()];
        int[] nonTerminals = new int[symbolsMap.size()];
        int nonTerminalCount = 0;
        for (Token token : symbolsMap.keySet()) {
            int index = symbolsMap.get(token);
            Symbol symbol = token.decompose();
            symbols[index] = symbol;
            isNonTerminal[index] = token.type().equals(TokenType.NON_TERMINAL);
            if (isNonTerminal[index]) {
                nonTerminals[nonTerminalCount++] = index;
                if (symbol.value().equals("START")) START = index;
            }
        }

        // shorten the nonTerminals array to the propper length
        System.arraycopy(nonTerminals, 0, nonTerminals, 0, nonTerminalCount);

        return new SymbolProcessingResult(symbols,isNonTerminal,nonTerminals);
    }
    // find all used non-terminals that are not defined and notify the user
    private void checkForUndefinedNonTerminals(ParseResult parseResult) {
        List<Token> undefined = new ArrayList<>();
        for (Token symbol : parseResult.symbolsMap.keySet()) {
            if (symbol.type() != TokenType.NON_TERMINAL) continue;
            if (!parseResult.defined.contains(symbol)) undefined.add(symbol);
        }
        if (!undefined.isEmpty())
            throw new IllegalArgumentException("\n\tThe following nonTerminals are undefined in the input grammar: \n\t\t" + undefined.toString().replaceAll("[\\[\\] ]","").replaceAll("-"," ").replaceAll(",","\n\t\t"));
    }

    // ====== LEXICAL UTILITIES ====== //
    private void ignoreWhiteSpace() throws IOException {
        while (!lexer.eof() && isWhitespace(lexer.crntChar())) {
            lexer.nextChar();
        }
    }
    /**
     * Reads and returns the next {@link Token} from the grammar input stream.
     * <p>
     * This method acts as the lexer for grammar definitions. It skips whitespace,
     * detects grammar punctuation symbols ('>', '|', ';'), parses non-terminal names,
     * and recognizes terminal declarations of the form {@code TYPE(value)}.
     * <p>
     * Returns {@link Token#EOF} when the end of input is reached.
     *
     * @return the next token from the grammar input, or {@code Token.EOF} if end-of-file is reached
     * @throws IOException if an unknown symbol or malformed token is encountered
     */
    private Token next() throws IOException {
        if (lexer.eof()) return Token.EOF;

        index++;
        ignoreWhiteSpace();

        // Detect a grammar symbol
        char c = lexer.crntChar();
        if (!isLetterOrDigit(c)) {
            lexer.nextChar();
            return switch (c) {
                case '>', '|', ';' -> new Token(TokenType.SYMBOL, String.valueOf(c));
                default -> throw new IOException(errorContext() + " unknown symbol: " + c);
            };
        }

        // Read the name of the token
        StringBuilder builder = new StringBuilder();
        while (!lexer.eof() && (isLetterOrDigit(lexer.crntChar()) || lexer.crntChar() == '_')) {
            builder.append(lexer.crntChar());
            lexer.nextChar();
        }

        // decide if the token is a nonTerminal or a terminal
        // if it's a terminal then the name is the value of the non-terminal
        // else it's the type of the terminal
        if (lexer.eof() || lexer.crntChar() != '(')
            return new Token(TokenType.NON_TERMINAL,builder.toString());

        // Check that the user entered a valid type name
        SymbolType type;
        try {
            type = SymbolType.valueOf(builder.toString());
            if (type.ordinal() > SymbolType.STR.ordinal()) throw new Exception();
        } catch (Exception e) {
            throw new IOException(errorContext() + "illegal terminal type: " + builder);
        }

        // parse the value field of the terminal
        builder = new StringBuilder();
        while (!lexer.eof()) {
            c = lexer.nextChar();
            if (c == ')') break;
            builder.append(c);
        }
        if (lexer.peekChar() == ')') {
            builder.append(lexer.nextChar());
        }
        if (lexer.eof() && c != ')') throw new IOException(errorContext() + "expected ')' got EOF");

        // handle the wild care terminal declaration
        String value = builder.toString();
        value = (value.equals("ε") || value.isEmpty())? null : value;

        // consume the closing bracket
        lexer.nextChar();
        return new Token(TokenType.TERMINAL,type + "," + value);
    }
    private Token expect(String expectedValue, TokenType... types) throws IOException {
        StringBuilder errorMessage = new StringBuilder();
        errorMessage.append(errorContext());
        errorMessage.append("EXPECTED ONE OF: [");
        for (int i = 0; i < types.length; i++) {
            TokenType type = types[i];
            errorMessage.append(type).append("(");
            if (expectedValue != null) errorMessage.append(expectedValue);
            errorMessage.append(")");
            if (i + 1 < types.length) errorMessage.append(", ");
        }
        errorMessage.append("] GOT: ");

        Token token = next();
        TokenType tokenType = token.type();
        errorMessage.append(tokenType);
        IOException exception = new IOException(errorMessage.toString());

        if (token.type().equals(TokenType.EOF)) throw exception;
        for (TokenType type : types) {
            if (!type.equals(tokenType)) continue;
            if (expectedValue == null || token.value().equals(expectedValue)) return token;
            throw exception;
        }
        throw exception;
    }

    // ====== DEBUG / ERROR REPORTING ====== //
    private String errorContext() {
        return "\n\tERROR: Rule:" + numRules + " Index: " + index +
                "\n\tCONTEXT: " + currentRule +
                "\n\tBuffer: " + lexer.buffer() + "\n\t";
    }
}
