package com.viffx.Lang.Automata;

import com.viffx.Lang.CompilerFactory;
import com.viffx.Lang.Grammar.Production;
import com.viffx.Lang.Symbols.NonTerminal;
import com.viffx.Lang.Symbols.Symbol;
import com.viffx.Lang.Grammar.Grammar;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LALR1 {
    private List<HashMap<Integer,Action>> actions;
    private final Grammar grammar;
    public LALR1(Grammar g) {
        grammar = g;
    }
    @NotNull
    private Set<Integer> first(List<Integer> symbols) {
        if (symbols == null || symbols.isEmpty()) return Set.of(grammar.EPSILON);
        Set<Integer> terminals = new HashSet<>();

        boolean nullable;
        for (int i = 0; i < symbols.size(); i++) {
            int I = symbols.get(i);
            Symbol X = grammar.symbol(I);
            if (X == null) continue;
            Set<Integer> first = new HashSet<>();
            if (X instanceof NonTerminal _) {
                first.addAll(grammar.first(I));
                nullable = first.contains(grammar.EPSILON);
            } else {
                first.add(I);
                nullable = I == grammar.EPSILON;
            }
            first.remove(grammar.EPSILON);
            terminals.addAll(first);
            if (nullable) {
                if (i + 1 == symbols.size()) terminals.add(grammar.EPSILON);
                continue;
            }
            break;
        }

        return terminals;
    }
    @NotNull
    private Set<Item> closure(Set<Item> I) {
        Set<Item> J = new LinkedHashSet<>(I);
        Queue<Item> queue = new LinkedList<>(I);
        while (!queue.isEmpty()) {
            Item item = queue.poll();
            assert item != null;
            Integer nt = grammar.symbol(item);
            if (grammar.isNonTerminal(nt)) {
                // Step 1: Get beta (symbols after B in the current production)
                List<Integer> beta = grammar.beta(item);
                beta.add(item.lookahead()); // this becomes βa
                Set<Integer> first = first(beta);
                grammar.forEach(nt, index -> {
                    for (Integer t : first) {
                        Item newItem = new Item(index,0,t);
                        if(!J.add(newItem)) continue;
                        queue.add(newItem);
                    }
                });
            }
        }
        return J;
    }

    /*Note for version 4:
    *   - List<Set<Integer>> derivations = new ArrayList<>(grammar.symbols.size());
    *   - int[] dots = new int[grammar.symbols.size()]*/
    private HashMap<Integer,Set<Integer>> derivations() {
        // TODO: derivations is clearly wrong. When a non terminal at the start of a production contains epsilon you the next symbol is also reachable.
        // NOTE: flash of genius. Work at it from both sides.
        //      (0,0) START > * S;
        //      (1,0) S > * A B;
        //      (2,0) A > * SYM(a);
        //      (3,0) A > *;
        //      (4,0) B > * SYM(b);
        //      derivations iteration0
        //          START > START
        //          S > S
        //          A > A
        //          B > B
        //      iteration1
        //      we discover the production of A that produces epsilon.
        //      so we check all of the items for a nonTerminal that matches A.
        //      we find S > * A B; So we advance the dot, (1,1) S > A * B;
        //      case 1 nothing after the dot. continue. this production will now be treated as an epsilon production.
        //      case 2 terminal after the dot. nothing for now. Maybe useful for calculating first sets.
        //      case 3 NonTerminal after the dot. Add the nonTerminal after the dot as a derivable for our LHS.
        //      (0,0) START > * S;
        //      (1,0) S > A * B;
        //      (2,0) A > * SYM(a);
        //      (3,0) A > *; #A is at the end so add B as a derivable for start.
        //      (4,0) B > * SYM(b);
        //      (0,0) START > * S; #Add S to STAR
        //      derivations iteration0
        //          START > START, S, A, B
        //          S > S, A, B #This B got here because of the empty production of A.
        //          A > A
        //          B > B

        List<Item> productions = new ArrayList<>();
        for (int i = 0; i < grammar.size(); i++) {
            productions.add(new Item(i,0,null));
        }
        Set<Integer> producesEpsilon = new HashSet<>();
        boolean change;
        do {
            change = false;
            for (int i = 0; i < productions.size(); i++) {
                Item item = productions.get(i);
                if (grammar.atEnd(item)) {
                    change |= producesEpsilon.add(grammar.production(item.index()).lhs());
                    continue;
                }
                while (!grammar.atEnd(item) && (grammar.symbol(item) == grammar.EPSILON || producesEpsilon.contains(grammar.symbol(item)))) {
                    item = item.advance();
                    change = true;
                }
                productions.set(i,item);
            }
        } while (change);

        HashMap<Integer,Set<Integer>> data = new HashMap<>();
        // Loop through all non-terminals
        for (int i = 0; i < grammar.symbols().size(); i++) {
            if (!grammar.isNonTerminal(i)) continue;

            // Initialize a set for all non-terminal derivable from i in zero or more steps
            Set<Integer> targets = new HashSet<>();
            data.put(i,targets);

            // i can reach itself in zero steps, add it to the targets
            targets.add(i);

            // loop through every production for the non-terminal i
            grammar.forEach(i, index -> {
                Production p = grammar.production(index);
                if (p.isEmpty()) return;
                Integer symbol = p.getFirst();
                // if any of grammar.productions(symbol) is empty
                if (grammar.isNonTerminal(symbol)) targets.add(symbol);
            });
        }
        for (Item item : productions) {
            Production production = grammar.production(item.index());
            Set<Integer> targets = data.get(production.lhs());
            for (int i = 0; i < item.dot(); i++) {
                targets.add(production.get(i));
            }
        }
        change = true;
        while (change) {
            change = false;
            for (int nt : data.keySet()) {
                for (int nt2 : data.keySet()) {
                    if (nt == nt2) continue;
                    if (data.get(nt2).contains(nt)) {
                        change |= data.get(nt2).addAll(data.get(nt));
                    }
                }
            }
        }

        return data;
    }
    public List<HashMap<Integer,Action>> generate() {
        if (actions != null) return actions;
        HashMap<SignedItem, Set<Integer>> spontaneous = new HashMap<>();
        HashMap<SignedItem, Set<SignedItem>> propagated = new HashMap<>();
        HashMap<Integer,int[]> transitions = new HashMap<>();
        Map<Integer, Map<Item, Set<Integer>>> lookaheads;

        processStates(spontaneous, propagated, transitions);
        lookaheads = initLookaheadTable(spontaneous);
        propagateLookaheads(lookaheads,propagated);

//        if (CompilerFactory.debug) {
//            printChannels(spontaneous, propagated);
//            printLookaheads(lookaheads);
//            printTransitions(transitions);
////            printActions(actions);
//        }
        List<HashMap<Integer,Action>> actions = calculateParseTable(lookaheads,transitions);

        if (CompilerFactory.debug) {
            printChannels(spontaneous, propagated);
            printLookaheads(lookaheads);
            printTransitions(transitions);
            printActions(actions);
        }
        this.actions = actions;
        return actions;
    }

    private void processStates(HashMap<SignedItem, Set<Integer>> spontaneous, HashMap<SignedItem, Set<SignedItem>> propagated, HashMap<Integer, int[]> transitions) {
        // 1. Define the start item
        Item startItem = new Item(grammar.START, 0, null);

        // 2. Supporting grammar data
        HashMap<Integer,Set<Integer>> derivations = derivations();
        System.out.println(derivations.keySet().stream().map(key -> grammar.symbol(key) + " => " + derivations.get(key).stream().map(grammar::symbol).toList()).collect(Collectors.joining("\n")));
        int symbolCount = grammar.symbols().size();

        // 3. State machine bookkeeping
        Map<Set<Item>, Integer> stateToId = new LinkedHashMap<>();
        Queue<Set<Item>> queue = new LinkedList<>();
        Set<Item> start = new HashSet<>();
        start.add(startItem);
        stateToId.put(start, 0);
        queue.add(start);
        int fromState = 0;
        int stateId = 1;

        spontaneous.computeIfAbsent(new SignedItem(0, startItem), _ -> new HashSet<>()).add(grammar.EOF);
        while (!queue.isEmpty()) {
            Set<Item> state = queue.poll();   // current LR(0) kernel
            if (state == null) throw new IllegalStateException("State should never be null");
            System.out.println(fromState + " " + state.stream().map(grammar::toString).toList());
            int[] transitionTable = new int[symbolCount];
            Arrays.fill(transitionTable,-1);

            stateId = expandItems(transitions, state, derivations, stateToId, queue, stateId, transitionTable, fromState);
            calculateLookaheadData(spontaneous, propagated, state, transitionTable, fromState);
            fromState++; // advance to the next state id for propagation tracking
        }
    }

    private int expandItems(HashMap<Integer, int[]> transitions, Set<Item> state, HashMap<Integer,Set<Integer>> derivations, Map<Set<Item>, Integer> stateToId, Queue<Set<Item>> queue, int stateId, int[] transitionTable, int fromState) {
        for (Item item : state) {
            // Only expand items where the dot is not at the end
            if (grammar.atEnd(item)) continue;
            // Advance the dot to build a candidate successor kernel
            Integer gotoSymbol = grammar.symbol(item);
            HashMap<Integer, Set<Item>> gotos = new HashMap<>();
            gotos.computeIfAbsent(gotoSymbol, _ -> new HashSet<>()).add(item.advance());
            if (grammar.isNonTerminal(gotoSymbol)) calculateGotos(derivations.get(gotoSymbol), gotos);
            stateId = buildStates(stateToId, queue, stateId, transitionTable, gotos);
            transitions.put(fromState, transitionTable);
        }
        return stateId;
    }
    private void calculateGotos(Set<Integer> derivations, HashMap<Integer, Set<Item>> gotos) {
        // Group productions by their first symbol
        for (Integer nt : derivations) {
            int[] data = grammar.pointers(nt);
            for (int index = data[0]; index < data[1]; index++) {
                Production p = grammar.production(index);
                Integer s = p.isEmpty() ? grammar.EPSILON : p.getFirst();
                gotos.computeIfAbsent(s, _ -> new HashSet<>())
                        .add(new Item(index, 1, null));
            }
        }
    }
    private int buildStates(Map<Set<Item>, Integer> stateToId, Queue<Set<Item>> queue, int stateId, int[] transitionTable, HashMap<Integer, Set<Item>> gotos) {
        for (int symbol : gotos.keySet()) {
            Set<Item> newState = gotos.get(symbol);
            if (!stateToId.containsKey(newState)) {
                queue.add(newState);
                stateToId.put(newState, stateId);
                transitionTable[symbol] = stateId;
                stateId++;
                continue;
            }
            transitionTable[symbol] = stateToId.get(newState);
        }
        return stateId;
    }
    private void calculateLookaheadData(HashMap<SignedItem, Set<Integer>> spontaneous, HashMap<SignedItem, Set<SignedItem>> propagated, Set<Item> state, int[] transitionTable, int fromState) {
        for (Item item : state) {
            // ==== Lookahead propagation ====
            // Seed an LR(1) item with a placeholder TEST lookahead
            Item seededItem = new Item(item.index(), item.dot(), grammar.TEST);
            Set<Item> seededItemSet = Set.of(seededItem);

            // Compute LR(1) closure of this seeded item
            Set<Item> closure = closure(seededItemSet);

            for (Item B : closure) {
//                if (grammar.atEnd(B)) continue;
                int symbolId = grammar.symbol(B);
                int lookahead = B.lookahead();
                int toState = transitionTable[symbolId];
                if (toState < 0) continue;


                // If this item carries a real lookahead, record spontaneous propagation
                if (lookahead != grammar.TEST) {
                    spontaneous.computeIfAbsent(
                            new SignedItem(toState, B.advance().core()),
                            _ -> new HashSet<>()
                    ).add(lookahead);
                }

                // Record propagated lookahead relation:
                // from (state,item) → to (state,item)
                propagated.computeIfAbsent(
                        new SignedItem(fromState, item),
                        _ -> new HashSet<>()
                ).add(
                        new SignedItem(toState, B.advance().core())
                );
            }
        }
    }

    private static @NotNull Map<Integer, Map<Item, Set<Integer>>> initLookaheadTable(HashMap<SignedItem, Set<Integer>> spontaneous) {
        Map<Integer,Map<Item,Set<Integer>>> lookaheads = new HashMap<>();
        spontaneous.forEach((signedItem, terminals) -> {
            lookaheads.computeIfAbsent(signedItem.state, _ -> new HashMap<>()).computeIfAbsent(signedItem.item, _ -> new HashSet<>()).addAll(terminals);
        });
        return lookaheads;
    }

    private void propagateLookaheads(Map<Integer, Map<Item, Set<Integer>>> lookaheads, Map<SignedItem, Set<SignedItem>> propagated) {

        boolean changed = true;
        while (changed) {
            changed = false;
            for (var entry : propagated.entrySet()) {
                SignedItem from = entry.getKey();
                Set<Integer> terminals = lookaheads
                        .getOrDefault(from.state, Map.of())
                        .getOrDefault(from.item, Set.of());

//                if (terminals.isEmpty()) continue;

                for (SignedItem to : entry.getValue()) {
                    changed |= lookaheads
                            .computeIfAbsent(to.state, _ -> new HashMap<>())
                            .computeIfAbsent(to.item, _ -> new HashSet<>())
                            .addAll(terminals);
                }
            }
        }
    }

    private List<HashMap<Integer,Action>> calculateParseTable(Map<Integer, Map<Item, Set<Integer>>> lookaheads, HashMap<Integer, int[]> transitions) {
        List<HashMap<Integer,Action>> ACTIONS = new ArrayList<>();
//        Map<Integer state, Map<Item itemsInState, Set<Integer> lookaheadsSymbols>> lookaheads
        for (int i = 0; i < lookaheads.size(); i++) {
            Map<Item, Set<Integer>> lookahead = lookaheads.get(i);
            if (lookahead == null) continue;
            HashMap<Integer,Action> actions = new HashMap<>();
            //
            for (Item key : lookahead.keySet()) {
                if (!grammar.atEnd(key)) continue;
                for (int symbol : lookahead.get(key)) {
                    if (key.index() == grammar.START) {
                        actions.put(symbol,new Action(ActionType.ACCEPT,0));
                        continue;
                    }
                    actions.put(symbol,new Action(ActionType.REDUCE, key.index()));
                }
            }
            ACTIONS.add(actions);
        }
        // transitions[fromState][transitionSymbol] = toState
        for (var entry : transitions.entrySet()) {
//            printActions(ACTIONS);
            Integer index = entry.getKey();
            int[] transitionTable = entry.getValue();
            for (int symbol = 0; symbol < transitionTable.length; symbol++) {
                if (symbol == grammar.EPSILON) {
//                    epsilonTransitionMap[index] = transitionTable[symbol];
                    int target = transitionTable[symbol];
                    if (target < 0) continue;
                    System.out.println((ACTIONS.size() - 1) + " < " + target);
                    while (ACTIONS.size() - 1 < target) {
                        ACTIONS.add(new HashMap<>());
                    }
                    HashMap<Integer,Action> toStateActions = ACTIONS.get(target);
                    HashMap<Integer,Action> fromStateActions = ACTIONS.get(index);
                    for (var temp : toStateActions.keySet()) {
                        fromStateActions.put(temp,toStateActions.get(temp));
                    }
//                    continue; // skip creating SHIFT/GOTO for EPSILON
                }
                int state = transitionTable[symbol];
                if (state < 0) continue;
                while (ACTIONS.size() <= index) {
                    ACTIONS.add(new HashMap<>());
                }
                ACTIONS.get(index).put(symbol, new Action(grammar.isNonTerminal(symbol) ? ActionType.GOTO : ActionType.SHIFT, state));
            }
        }
        return ACTIONS;
    }

    private void printChannels(HashMap<SignedItem, Set<Integer>> spontaneous, HashMap<SignedItem, Set<SignedItem>> propagated) {
        spontaneous.forEach((key, values) -> {
            String front = key.toReadable(grammar);
            front += " ".repeat(Math.max(0,20 - front.length()));
            System.out.println(front + " ==> " + values.stream().map(item -> grammar.symbol(item).toString()).collect(Collectors.joining(", ", "[", "]")));
        });
        propagated.forEach((key, values) -> {
            values.forEach(value -> {
                String front = key.toReadable(grammar);
                front += " ".repeat(Math.max(0,20 - front.length()));
                System.out.println(front + " ==> " + value.toReadable(grammar));
            });
        });
    }
    private void printLookaheads(Map<Integer, Map<Item, Set<Integer>>> lookaheads) {
        for (int i = 0; i < lookaheads.size(); i++) {
            Map<Item, Set<Integer>> lookahead = lookaheads.get(i);
            if (lookahead == null) continue;
            for (Item key : lookahead.keySet()) {
                String item = grammar.toString(key);
                String text = item + " ".repeat(Math.max(0,17 - item.length())) + "Lookaheads: " + lookahead.get(key).stream().map(grammar::symbol).toList();
                System.out.println("State: " + i + " Item: " + text);
            }
        }
    }
    private void printTransitions(HashMap<Integer, int[]> transitions) {
        transitions.forEach((state, transitionTable) -> {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < transitionTable.length; i++) {
                if (transitionTable[i] >= 0) {
                    text.append("State ")
                            .append(state)
                            .append(":")
                            .append(" on ")
                            .append(grammar.symbol(i))
                            .append(" goto ")
                            .append(transitionTable[i])
                            .append("\n");
                }
            }
            System.out.print(text);
        });
    }
    private void printActions(List<HashMap<Integer,Action>> ACTIONS) {
        for (int i = 0; i < ACTIONS.size(); i++) {
            HashMap<Integer,Action> actions = ACTIONS.get(i);
            System.out.println(i + " " + actions.keySet().stream().map(key -> '"' + grammar.symbol(key).toString() + "\" ==> " + actions.get(key)).toList());
        }
    }

    private record SignedItem(int state, Item item) {
        private SignedItem {
            if (state < 0) throw new IllegalArgumentException("States must be positive");
        }

        public String toReadable(Grammar g) {
            return "I" + state + ": " + g.toString(item);
        }

        @Override
            public String toString() {
                return "SignedItem[" +
                        "state=" + state + ", " +
                        "item=" + item + ']';
            }

    }
}
