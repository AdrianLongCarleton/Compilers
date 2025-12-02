package com.viffx.Lang.Symbols;

public sealed interface Symbol permits Terminal, NonTerminal {
    String value();
}
