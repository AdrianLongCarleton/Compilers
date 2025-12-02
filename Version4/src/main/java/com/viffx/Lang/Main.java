package com.viffx.Lang;

import com.viffx.Lang.Compiler.Parser;
import com.viffx.Lang.Grammar.Grammar;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws Exception {
        Grammar grammar = Grammar.load("src/main/resources/TestGrammar1.txt");
        Parser parser = new Parser(grammar);
    }
}