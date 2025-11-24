package com.viffx.Lang.Symbols;

public enum TokenType {
    EPSILON,
    ID,
    ANY,
    NUM, // a series of digits ex: 1234
    SYM, // a
    CHR,
    STR,
    TERMINAL,
    NON_TERMINAL,
    SYMBOL,
    LEX_SYMBOL,
    COMMENT,
    INDENT,
    DEDENT,
    EOR,
    EOF,  // Enf Of File
    TEST,
    END,

}
