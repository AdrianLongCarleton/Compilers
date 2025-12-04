package com.viffx.Lang.Compiler;

import com.viffx.Lang.Grammar.Grammar;
import com.viffx.Lang.Grammar.Production;
import com.viffx.Lang.Symbols.NonTerminal;
import com.viffx.Lang.Symbols.Symbol;

import java.util.*;
import java.util.stream.Collectors;

public class LALR1ParseTableGenerator {
    //[INSTANCE_FIELDS]
    private final Grammar grammar;
    private final HashMap<Integer,Set<Integer>> derivations;
    private final HashMap<Integer,Set<Integer>> firstSets;
    private final Set<Integer> nullable;
    //[CONSTRUCTORS]
    public LALR1ParseTableGenerator(Grammar grammar) {
        this.grammar = grammar;

        HashMap<Integer,Set<Integer>> derivations = new HashMap<>(grammar.symbolCount());
        HashMap<Integer,Set<Integer>> firstSets = new HashMap<>(grammar.symbolCount());
        Set<Integer> nullable = new HashSet<>();
        final int productionsCount = grammar.productionsCount();
        int[] dots = new int[productionsCount];
        final int EPSILON = grammar.EPSILON();

        // Advance the dots over all nullable non-terminals
        // The symbol after the dot will either be out of bounds or in front of a terminal
        // Use this to calculate the first sets
        // START > S;
        // S > A * C
        // A > * B
        // A > * EPSILON();
        // B > * D
        // C > * c;
        boolean change;
        do {
            change = false;
            for (int i = 0; i < productionsCount; i++) {
                Production production = grammar.production(i);
                if (production.atEnd(dots[i])) {
                    change |= nullable.add(production.lhs());
                    continue;
                }
                while (!production.atEnd(dots[i]) && (production.get(dots[i]) == EPSILON || nullable.contains(production.get(dots[i])))) {
                    dots[i]++;
                    change = true;
                }
            }
        } while (change);

        // Initialize all the derivations
        for (int i = 0; i < productionsCount; i++) {
            Production production = grammar.production(i);
            int lhs = production.lhs();
            Set<Integer> reachable = derivations.computeIfAbsent(lhs, _ -> new HashSet<>());

            // All the non-terminals before the dot are reachable from lhs to epsilon transitions
            reachable.add(production.lhs());
            for (int j = 0; j < dots[i]; j++) {
                reachable.add(production.get(j));
            }
            if (!production.atEnd(dots[i]) && grammar.isNonTerminal(production.get(dots[i]))) {
                reachable.add(production.get(dots[i]));
            }

            // first sets
            // after the dot the production must either be at the end or in front of a terminal

            Set<Integer> firstSet = firstSets.computeIfAbsent(lhs, _ -> new HashSet<>());
            if (production.atEnd(dots[i])) continue;
            Integer symbol = production.get(dots[i]);
            if (grammar.isNonTerminal(symbol)) continue;
            firstSet.add(symbol);
        }

        // Propagate reachability
        do {
            change = false;
            for (int sourceNonTerminal : derivations.keySet()) {
                Set<Integer> derivationsOfSource = derivations.get(sourceNonTerminal);
                // Create a copy of the set to iterate over to prevent ConcurrentModificationException
                List<Integer> currentDerivations = new ArrayList<>(derivationsOfSource);
                for (int targetNonTerminal : currentDerivations) {
                    if (sourceNonTerminal == targetNonTerminal) continue;
                    Set<Integer> derivationsOfTarget = derivations.get(targetNonTerminal);
                    assert derivationsOfTarget != null;
                    change |= derivationsOfSource.addAll(derivationsOfTarget);
                    firstSets.get(sourceNonTerminal).addAll(firstSets.get(targetNonTerminal));
                }
            }
        } while (change);

        System.out.println(derivations
                .keySet()
                .stream()
                .map(
                        key -> grammar.symbol(key) + " => " +
                                derivations.get(key)
                                        .stream()
                                        .map(grammar::symbol)
                                        .toList()
                ).collect(Collectors.joining("\n")));
        System.out.println(firstSets
                .keySet()
                .stream()
                .map(
                        key -> grammar.symbol(key) + " => " +
                                firstSets.get(key)
                                        .stream()
                                        .map(grammar::symbol)
                                        .toList()
                ).collect(Collectors.joining("\n")));
//        System.out.println("FENCE1");

        this.derivations = derivations;
        this.firstSets = firstSets;
        this.nullable = nullable;
    }

    //[INTERNAL_DATATYPES]
    private record SignedItem(int state, Item item) {
        private SignedItem {
            if (state < 0) throw new IllegalArgumentException("States must be positive");
        }


        public String toReadable(Grammar g) {
            return "I" + state + ": " + g.toString(item);
        }
    }

    //[PRIVATE_METHODS]
    private Set<Integer> first(List<Integer> symbols) {
        if (symbols == null || symbols.isEmpty()) return Set.of(grammar.EPSILON());
        Set<Integer> terminals = new HashSet<>();

        boolean nullable;
        for (int i = 0; i < symbols.size(); i++) {
            int I = symbols.get(i);
            Symbol X = grammar.symbol(I);
            if (X == null) continue;
            Set<Integer> first = new HashSet<>();
            if (X instanceof NonTerminal _) {
                first.addAll(firstSets.get(I));
                nullable = this.nullable.contains(I);
            } else {
                first.add(I);
                nullable = I == grammar.EPSILON();
            }
            first.remove(grammar.EPSILON());
            terminals.addAll(first);
            if (nullable) {
                if (i + 1 == symbols.size()) terminals.add(grammar.EPSILON());
                continue;
            }
            break;
        }

        return terminals;
    }
    private Set<Item> closure(Set<Item> I) {
        Set<Item> J = new LinkedHashSet<>(I);
        Queue<Item> queue = new LinkedList<>(I);
        while (!queue.isEmpty()) {
            Item item = queue.poll();

            if (grammar.atEnd(item)) continue;

            Integer B = grammar.symbol(item);

            if (grammar.isNonTerminal(B)) {
                // Step 1: Get beta (symbols after B in the current production)
                // Step 1: Get beta (symbols after B in the current production)
                List<Integer> beta = grammar.beta(item);   // β

                // Step 2: Compute FIRST(β)
                Set<Integer> firstBeta = first(beta);

                // Step 3: Compute FIRST(βa)
                Set<Integer> firstBetaA = new LinkedHashSet<>();

                // FIRST(β) minus epsilon
                for (int t : firstBeta) {
                    if (t != grammar.EPSILON())
                        firstBetaA.add(t);
                }

                // If β derives epsilon, include FIRST(a) = {a}
                if (firstBeta.contains(grammar.EPSILON()) || beta.isEmpty()) {
                    firstBetaA.add(item.lookahead());
                }

                // Iterate over the production index range for the given non-terminal
                grammar.forEachProduction(B, index -> {
                    for (Integer t : firstBetaA) {
                        Item newItem = new Item(index,0,t);
                        if(!J.add(newItem)) continue;
                        queue.add(newItem);
                        System.out.println(grammar.symbol(t));
                    }
                });
            }
        }
        return J;
    }
    //[PUBLIC_METHODS]
    public List<HashMap<Integer,Action>> generate() {
        HashMap<SignedItem, Set<Integer>> spontaneous = new HashMap<>();
        HashMap<SignedItem, Set<SignedItem>> propagated = new HashMap<>();
        HashMap<Integer,int[]> transitions = new HashMap<>();
        Map<Integer, Map<Item, Set<Integer>>> lookaheads;

        processStates(spontaneous, propagated, transitions);
        lookaheads = initLookaheadTable(spontaneous);
        propagateLookaheads(lookaheads,propagated);

        List<HashMap<Integer,Action>> actions = calculateParseTable(lookaheads,transitions);

        System.out.println("Channels:");
        printChannels(spontaneous, propagated);
        printLookaheads(lookaheads);
        printTransitions(transitions);
        printActions(actions);
        return actions;
    }

    //[HELPER_METHODS_FOR_GENERATE]
    private void processStates(HashMap<SignedItem, Set<Integer>> spontaneous, HashMap<SignedItem, Set<SignedItem>> propagated, HashMap<Integer, int[]> transitions) {
        // 1. Define the start item
        Item startItem = new Item(grammar.START(), 0, null);

        // 2. Supporting grammar data

        int symbolCount = grammar.symbolCount();

        // 3. State machine bookkeeping
        Map<Set<Item>, Integer> stateToId = new LinkedHashMap<>();
        Queue<Set<Item>> queue = new LinkedList<>();
        Set<Item> start = new HashSet<>();
        start.add(startItem);
        stateToId.put(start, 0);
        queue.add(start);
        int fromState = 0;
        int stateId = 1;

        spontaneous.computeIfAbsent(new SignedItem(0, startItem), _ -> new HashSet<>()).add(grammar.EOF());
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
        HashMap<Integer, Set<Item>> allGotos = new HashMap<>();

        for (Item item : state) {
            // Only expand items where the dot is not at the end
            if (grammar.atEnd(item)) continue;

            System.out.println("\t"+grammar.toString(item));
            // Advance the dot to build a candidate successor kernel
            Integer gotoSymbol = grammar.symbol(item);
            System.out.println("\t\tGotoSymbol:" + grammar.symbol(gotoSymbol));

            allGotos.computeIfAbsent(gotoSymbol, _ -> new HashSet<>()).add(item.advance());

            if (grammar.isNonTerminal(gotoSymbol)) calculateGotos(derivations.get(gotoSymbol), allGotos);
        }

        stateId = buildStates(stateToId, queue, stateId, transitionTable, allGotos);
        transitions.put(fromState, transitionTable);

        return stateId;
    }
    private void calculateGotos(Set<Integer> derivations, HashMap<Integer, Set<Item>> gotos) {
        // Group productions by their first symbol
        for (Integer nt : derivations) {
            grammar.forEachProduction(nt,index -> {
                Production p = grammar.production(index);
                if (p.isEmpty()) return;
                Integer s = p.getFirst();
                gotos.computeIfAbsent(s, _ -> new HashSet<>())
                        .add(new Item(index, 1, null));
            });

        }
        System.out.println("\t\t\t" +
                    gotos.keySet().stream()
                            .map(key -> grammar.symbol(key) + " -> " +
                                    gotos.get(key).stream()
                                            .map(grammar::toString)
                                            .collect(Collectors.toSet())
                            ).collect(Collectors.joining("\n\t\t\t"))
            );
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
            Item seededItem = new Item(item.index(), item.dot(), grammar.TEST());
            Set<Item> seededItemSet = Set.of(seededItem);

            // Compute LR(1) closure of this seeded item
            Set<Item> closure = closure(seededItemSet);
            System.out.println("Closure:\n\t"+closure.stream().map(grammar::toString).collect(Collectors.joining("\n\t")));

            for (Item B : closure) {
//                if (grammar.atEnd(B)) continue;
                Production production = grammar.production(B.index());
                int lookahead = B.lookahead();

                if (production.atEnd(B.dot())) {
                    // At dot at end: this item should emit a reduce lookahead.
                    // It should NOT be skipped — record propagation!
                    if (lookahead == grammar.TEST()) {
                        propagated.computeIfAbsent(new SignedItem(fromState, item), _ -> new HashSet<>())
                                .add(new SignedItem(fromState, B.core()));
                    } else {
                        spontaneous.computeIfAbsent(new SignedItem(fromState, B.core()), _ -> new HashSet<>())
                                .add(lookahead);
                    }
                    continue;
                }

                int symbolId = grammar.symbol(B);
                int toState = transitionTable[symbolId];
                if (toState < 0) continue;


                // If this item carries a real lookahead, record spontaneous propagation
                if (lookahead != grammar.TEST()) {
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

    private static Map<Integer, Map<Item, Set<Integer>>> initLookaheadTable(HashMap<SignedItem, Set<Integer>> spontaneous) {
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
                    if (key.index() == grammar.START()) {
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
                if (symbol == grammar.EPSILON()) {
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
        System.out.println("Channels:");
        System.out.println("\tSpontaneous");
        spontaneous.forEach((key, values) -> {
            String front = key.toReadable(grammar);
            front += " ".repeat(Math.max(0,20 - front.length()));
            System.out.println("\t\t" +front + " ==> " + values.stream().map(item -> grammar.symbol(item).toString()).collect(Collectors.joining(", ", "[", "]")));
        });
        System.out.println("\tPropagated");
        propagated.forEach((key, values) -> {
            values.forEach(value -> {
                String front = key.toReadable(grammar);
                front += " ".repeat(Math.max(0,20 - front.length()));
                System.out.println("\t\t" + front + " ==> " + value.toReadable(grammar));
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
}