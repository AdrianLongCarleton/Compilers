package com.viffx.Lang;


import com.viffx.Lang.Automata.Action;
import com.viffx.Lang.Automata.LALR1;
import com.viffx.Lang.Grammar.Grammar;
import com.viffx.Lang.Grammar.Production;
import com.viffx.Lang.Symbols.AstNode;
import com.viffx.Lang.Symbols.NonTerminal;
import com.viffx.Lang.Symbols.Symbol;
import com.viffx.Lang.Symbols.Terminal;

import java.util.*;
import java.util.stream.Collectors;

public class CompilerFactory {
    public static final boolean debug = true;
    private StringBuilder builder = new StringBuilder();
    private final Grammar grammar;
    public final List<HashMap<Integer, Action>> parseTable;

    public CompilerFactory(String filePath) throws Exception {
        Grammar g = Grammar.load(filePath);
        System.out.println(g);
        for (int i = 0; i < g.numSymbols(); i++) {
            if (!g.isNonTerminal(i)) continue;
            g.forEach(i,index -> {
                System.out.println(g.toString(g.production(index)));
            });
        }
        LALR1 automata = new LALR1(g);
        this.grammar = g;
        this.parseTable = automata.generate();
    }

    public static void main(String[] args) throws Exception {
        CompilerFactory factory = new CompilerFactory("src/main/resources/LangGrammar3");
        factory.run();
        System.out.println(factory.builder);
    }

    private void run() {
        builder.append("""
                package com.viffx.Lang;
                \s
                import com.viffx.Lang.Symbols.*;
                \s
                import java.util.Stack;
                \s
                public class Compiler {
                    // Lexer
                    public final Lexer lexer;
                    \s
                    // Symbols
                """);
        List<Symbol> symbols = grammar.symbols();
        for (int i = 0; i < symbols.size(); i++) {
            builder.append("\tpublic final Symbol symbol").append(i).append(" = new ");
            boolean isNull = false;
            switch (symbols.get(i)) {
                case Terminal t -> {
                    isNull = t.value() == null;
                    builder.append(" Terminal(TokenType.").append(t.type()).append(", ");
                    if (!isNull) {
                        builder.append("\"");
                    }
                    builder.append(t.value());
                }
                case NonTerminal nt -> {
                    builder.append(" NonTerminal(\"").append(nt.value());
                }
            }
            if (!isNull) {
                builder.append("\"");
            }
            builder.append(");\n");
        }
        builder.append("""
                    \s
                    // Parsing state
                    public final Stack<Integer> stack = new Stack<>();
                    public final Stack<AstNode> ast = new Stack<>();
                    public Symbol current;
                    \s
                    public Compiler(Lexer lexer) throws Exception {
                        this.lexer = lexer;
                        do {
                            current = lexer.next();
                        } while (current.type() == TokenType.COMMENT);
                        this.stack.push(0);
                    }
                """);


        builder.append("""
                    // === Core parser driver ===
                	public void parse() throws Exception {
                	    boolean success = false;
                		label:
                		while (!stack.isEmpty()) {
                			int state = stack.peek();
                			switch (state) {
                """);
        List<HashMap<Integer,Action>> shiftReduceActionsList = new ArrayList<>();
        HashMap<Integer,HashMap<Integer, Integer>> gotoActionsList = new HashMap<>();
        for (int state = 0; state < parseTable.size(); state++) {
            boolean acceptingState = false;
            HashMap<Integer, Action> integerActionHashMap = parseTable.get(state);
//            append(1, "public void state" + state + "() throws Exception {");
            HashMap<Integer,Action> shiftReduceActions = new HashMap<>();
            HashMap<Integer, Integer> gotos = new HashMap<>();
            Map<Integer, Action> actions = integerActionHashMap;
            for (Integer symbol : actions.keySet()) {
                if (grammar.isNonTerminal(symbol)) {
                    gotos.put(symbol, actions.get(symbol).data());
                    continue;
                }
                Action action = actions.get(symbol);
                switch (action.type()) {
                    case ACCEPT -> {
                        acceptingState = true;
                        shiftReduceActions.put(symbol, action);
                    }
                    case REDUCE, SHIFT -> shiftReduceActions.put(symbol, action);
                    case GOTO -> throw new IllegalStateException();
                }
            }
            shiftReduceActionsList.add(shiftReduceActions);
            if (!gotos.isEmpty()) gotoActionsList.put(state, gotos);
            builder.append("\t".repeat(4)).append("case ").append(state).append(" -> ");
            if (acceptingState) {
                builder.append("{\n").append("\t".repeat(5));
            }
            builder.append("state").append(state).append("();\n");
            if (acceptingState) {
                append(4,"\tsuccess = true;","\tbreak label;","}");
            }
        }
        builder.append("""
                				default -> throw new Exception("Invalid parser state " + state);
                			}
                		}
                		if (!success) {
                		    throw new Exception("Failed parse");
                		}
                		CompilerFactory.printAST(ast.pop());
                	}
                \s
                	// === Utility methods for LR actions ===
                	private void shift(Symbol s, int nextState) throws Exception {
                 		if (!current.equals(s)) throw new Exception("Unexpected token: " + current);
                 		System.out.println("Shift: " + current);
                 		stack.push(nextState);
                 		ast.push(new AstNode(current));
                 		do {
                   			current = lexer.next();
                   		} while (current.type() == TokenType.COMMENT);
                 	}
                \s
                	private void reduce(Symbol lhs, int rhsLength) throws Exception {
                 		assert lhs instanceof NonTerminal;
                 		AstNode node = new AstNode(lhs);
                 		for (int i = 0; i < rhsLength; i++) {
                 			node.add(ast.pop());
                 			stack.pop();
                 		}
                 		ast.push(node);
                 		System.out.println("Reduce: " + lhs + " <- " + rhsLength + " symbols");
                 		// Now do GOTO based on state under top
                 		int state = stack.peek();
                 		gotoState(state,lhs);
                 	}
                \s
                """);
        append(1, "private void gotoState(int state, Symbol nt) throws Exception {");
        append(2, "switch(state) {");
        for (var entry : gotoActionsList.entrySet()) {
            append(3, "case " + entry.getKey() + " -> " + "{");
            generateGotoCode(entry.getKey(),entry.getValue());
            append(3,"}");
        }
        append(1,"\t}","}");
        builder.append("\n");
        append(1, "// === State functions ===");
        for (int state = 0; state < shiftReduceActionsList.size(); state++) {
            append(1,"public void state" + state + "() throws Exception {");
            appendShiftReduceCode(shiftReduceActionsList.get(state),state);
            builder.append("\t\telse throw new Exception(\"Parse error in state").append(state).append(": \" + current);\n\t}\n");
        }
        builder.append("}");
    }
//            System.out.println(terminalActions);
////            append(2, "boolean error = false;");
//            boolean errorPossible = appendShiftReduceCode(terminalActions,state);
//            append(2,"}",
//                    "if (l != 0) {",
//                    "\tl--;",
//                    "\treturn;",
//                    "}");
//
//            generateGotoCode(state,gotoActions);
//
//            append(1,"}");
//        }
//        builder.append("}");
//    }
//
    private void generateGotoCode(int state,Map<Integer, Integer> gotoActions) {
        if (gotoActions.isEmpty()) return;
        boolean first = true;
        String tabs = "\t".repeat(4);
        for (var entry : gotoActions.entrySet()) {
            builder.append(tabs);
            if (first) {
                first = false;
            } else {
                builder.append("else ");
            }
            builder.append("if (nt.equals(symbol").append(entry.getKey()).append(")) stack.push(").append(entry.getValue()).append(");\n");
        }
        builder.append(tabs).append("else throw new Exception(\"state").append(state).append(" has no goto for \" + nt);\n");
    }


    private static final class SortedActionMap {
        private final HashMap<Integer, Action> shiftActions = new HashMap<>();
        private final HashMap<Integer, Action> reduceActions = new HashMap<>();

        public Action putShiftAction(Integer index, Action action) {
            return shiftActions.put(index,action);
        }
        public Action putReduceAction(Integer index, Action action) {
            return reduceActions.put(index,action);
        }
        public Action getShiftAction(Integer index) {
            return shiftActions.get(index);
        }
        public Action getReduceAction(Integer index) {
            return reduceActions.get(index);
        }

        @Override
        public String toString() {
            return "Actions[" +
                    "shiftActions=" + shiftActions + ", " +
                    "reduceActions=" + reduceActions + ']';
        }


    }

    private void appendShiftReduceCode(Map<Integer, Action> terminalActions, int state) {
        Map<Action, List<Integer>> grouped = new HashMap<>();
        for (var entry : terminalActions.entrySet()) {
            grouped.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(entry.getKey());
        }
        boolean first = true;
        for (var entry : grouped.entrySet()) {
            Action action = entry.getKey();
            List<String> symbolsList = entry.getValue().stream()
                    .map(i -> "symbol" + i)
                    .toList();
            // Build condition (single == vs multiple ||)
            String condition;
            if (symbolsList.size() == 1) {
                condition = "current.equals(" + symbolsList.getFirst() + ")";
            } else {
                condition = symbolsList.stream()
                        .map(s -> "current.equals(" + s + ")")
                        .collect(Collectors.joining(" || "));
            }
            if (first) {
                builder.append("\t\tif (").append(condition).append(") ");
                generateActionCode(action, symbolsList);
                first = false;
            } else {
                builder.append("\t\telse if (").append(condition).append(") ");
                generateActionCode(action, symbolsList);
            }
//            builder.append("\t\t}");
        }
//        append(2,"} else {");
//        append(3,"throw new IllegalStateException(\"state" + state + " cannot perform an action on the symbol: \"+ current);");
    }

    private void generateActionCode(Action action, List<String> symbolsList) {
        switch (action.type()) {
            case ACCEPT -> {
//                append(3,"l = 1; // Accept");
//                );
                builder.append("System.out.println(\"ACCEPTED\"");
            }
            case SHIFT -> {
                builder.append("shift(").append(symbolsList.getFirst()).append(",").append(action.data());
            }
            case REDUCE -> {
                Production p = grammar.production(action.data());
                builder.append("reduce(symbol").append(p.lhs()).append(",").append(p.size());
            }
            case GOTO -> throw new IllegalStateException();
        }
        builder.append(");\n");
    }

    public void append(int depth, String... text) {
        depth = Math.max(0,depth);
        for (String line : text) {
            builder.append("\t".repeat(depth)).append(line).append("\n");
        }
    }

    public static void printAST(AstNode root) {
        Stack<Integer> depthQueue = new Stack<>();
        Stack<AstNode> astQueue = new Stack<>();
        depthQueue.push(0);
        astQueue.push(root);
        while (!astQueue.isEmpty()) {
            Integer depth = depthQueue.pop();
            AstNode node = astQueue.pop();
            assert depth != null;
            assert node != null;
            System.out.println("\t".repeat(Math.max(0,depth)) + node.symbol());
            for (AstNode child : node.children()) {
                depthQueue.push(depth + 1);
                astQueue.push(child);
            }
        }
    }
}
