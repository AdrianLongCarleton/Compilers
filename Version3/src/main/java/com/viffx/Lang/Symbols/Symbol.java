package com.viffx.Lang.Symbols;

public sealed interface Symbol permits Terminal, NonTerminal {
    TokenType type();
    String value();
}
