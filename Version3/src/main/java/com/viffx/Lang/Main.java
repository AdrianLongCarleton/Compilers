package com.viffx.Lang;

import com.viffx.Lang.Automata.Action;
import com.viffx.Lang.Symbols.TokenType;
import com.viffx.Utils.Timer;

import java.util.Arrays;


public class Main {

    public static void main(String[] args) throws Exception {
        Timer timer = new Timer("timer.txt");
        timer.log("Started");
//        Grammar grammar = Grammar.load("src/main/resources/TestGrammar");
//        grammar.productions(NonTerminal.START).forEach(index -> System.out.println(grammar.toString(index)));
//        System.out.println(grammar.productions(NonTerminal.START));
//        System.out.println(new Item(0,0, null).toReadable(grammar));
        CompilerFactory.main(args);
        timer.log("Finished");
        timer.close();
        Compiler compiler = new Compiler(new Lexer("src/main/resources/CodeTest1"));
        compiler.parse();
    }
}