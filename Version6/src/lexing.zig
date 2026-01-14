//TODO: FINISH REFACTORING. REPLACE lexer.pos += 1 with lexer.consume();

const std = @import("std");
const builtin = @import("builtin");

pub const LexerError = error{
    UnexpectedCharachter,
    InvalidCharachter,
    UnexpectedEndOfFile,
    InvalidToken,
};

pub const Lexer = struct {
    source: []const u8,
    pos: usize,

    token: []const u8,
    pub fn init(mmap: []const u8) Lexer {
        var lexer = Lexer{
            .source = mmap,
            .pos = 0,
            .token = []const u8{},
        };
        lexer.skipIgnored();
        return lexer;
    }
    pub fn atEnd(self: *Lexer) bool {
        return self.pos >= self.source.len;
    }
    pub fn currentChar(self: *Lexer) ?u8 {
        if (self.pos >= self.source.len) return null;
        return self.source[self.pos];
    }
    pub fn expectChar(self: *Lexer) !u8 {
        if (self.pos >= self.source.len) return LexerError.UnexpectedEndOfFile;
        return self.source[self.pos];
    }
    pub fn advance(self: *Lexer) ?u8 {
        if (self.pos >= self.source.len) return null;
        const c = self.source[self.pos];
        self.pos += 1;
        return c;
    }
    pub fn consume(self: *Lexer) void {
        self.pos += 1;
    }
    fn skipIgnored(self: *Lexer) void {
        self.skipWhiteSpace();
        var char: u8 = undefined;
        while (true) {
            char = self.currentChar() orelse return;
            if (char != '#') break;
            recognizeBlock(self, '#');
            self.skipWhiteSpace();
        }
        return;
    }
    fn skipWhiteSpace(self: *Lexer) void {
        const remaining = self.source.len - self.pos;

        // Fallback for the very end of the file (scalar skip)
        if (remaining < 16) {
            while (self.pos < self.source.len) : (self.pos += 1) {
                const c = self.source[self.pos];
                if (c > 0x20) break;
            }
            return;
        }

        // Branch prediction should hit this branch
        const LIMIT: @Vector(16, u8) = @splat(0x21); // x < 0x21 includes \t, \n and \r
        const NEWLINE: @Vector(16, u8) = @splat('\n');
        const ALL: @Vector(16, u8) = @splat(0xFF);

        var p = self.source.ptr + self.pos;
        // Calculate the safe limit for 16-byte loads
        const end_ptr = self.source.ptr + self.source.len - 16;

        while (@intFromPtr(p) <= @intFromPtr(end_ptr)) {
            const v: @Vector(16, u8) = p[0..16].*;

            // Generate boolean vector
            const is_under_limit = v < LIMIT;
            const is_newline = v == NEWLINE;

            // Logical "Is Whitespace": (char < 0x21) AND (char != '\n')
            // XOR with ALL flips 'is_nl' (true becomes false and vice versa)
            const is_whitespace = is_under_limit & (is_newline ^ ALL);

            // Convert the boolean vector into a 16-bit integer mask
            const mask: u16 = @bitCast(is_whitespace);

            if (mask != 0xFFFF) {
                // Found a non-whitespace char or a newline
                self.pos = @intFromPtr(p) - @intFromPtr(self.source.ptr) + @ctz(~mask);
                return;
            }

            p += 16;
        }

        // Final tail check if SIMD finished without finding a stop character
        self.pos = @intFromPtr(p) - @intFromPtr(self.source.ptr);
        while (self.pos < self.source.len) : (self.pos += 1) {
            const c = self.source[self.pos];
            if (c == '\n' or c > 0x20) break;
        }
        return;
    }
};
pub fn recognizeEndOfStatement(lexer: *Lexer) !usize {
    const char = lexer.advance() orelse {
        return LexerError.UnexpectedEndOfFile;
    };
    if (char == ';' or char == '\n' or char == '#' or char == '}') return lexer.pos;
    const remaining = lexer.source.len - lexer.pos;
    const start = lexer.pos;

    // Fallback for the very end of the file (scalar skip)
    if (remaining < 16) {
        while (lexer.pos < lexer.source.len) : (lexer.pos += 1) {
            const c = lexer.source[lexer.pos];
            if (c == ';' or c == '\n' or c == '#' or c == '}') break;
        }
        return start;
    }

    // Branch prediction should hit this branch
    const NEWLINE: @Vector(16, u8) = @splat('\n');
    const SEMICOLON: @Vector(16, u8) = @splat(';');
    const HASHTAG: @Vector(16, u8) = @splat('#');
    const CURLYBRACE: @Vector(16, u8) = @splat('}');

    var p = lexer.source.ptr + lexer.pos;
    // Calculate the safe limit for 16-byte loads
    const end_ptr = lexer.source.ptr + lexer.source.len - 16;

    while (@intFromPtr(p) <= @intFromPtr(end_ptr)) {
        const v: @Vector(16, u8) = p[0..16].*;

        const boolVector = ((v == NEWLINE) | (v == SEMICOLON)) | ((v == HASHTAG) | (v == CURLYBRACE));

        // Convert the boolean vector into a 16-bit integer mask
        const mask: u16 = @bitCast(boolVector);

        if (mask != 0) {
            // Found a non symbol hexidecimal
            lexer.pos = @intFromPtr(p) - @intFromPtr(lexer.source.ptr) + @ctz(~mask);
            return start;
        }

        p += 16;
    }

    // Final tail check if SIMD finished without finding a stop character
    lexer.pos = @intFromPtr(p) - @intFromPtr(lexer.source.ptr);
    while (lexer.pos < lexer.source.len) : (lexer.pos += 1) {
        const c = lexer.source[lexer.pos];
        if (c == ';' or c == '\n') break start;
    }
}

pub const NumericType = enum {
    INTEGER,
    HEX,
    BIN,
    FLOAT,
};
fn isDigit(char: u8) bool {
    return char >= '0' and char <= '9';
}
pub fn isAlpha(char: u8) bool {
    const c = char | 0x20;
    return c >= 'a' and c <= 'z';
}
fn isAlphaHex(char: u8) bool {
    const c = char | 0x20;
    return c >= 'a' and c <= 'f';
}
fn isHex(char: u8) bool {
    return isDigit(char) or isAlphaHex(char);
}
pub fn recognizeHexidecimal(lexer: *Lexer) !void {
    const char = lexer.advance() orelse {
        return LexerError.UnexpectedEndOfFile;
    };
    if (!isHex(char)) {
        return LexerError.InvalidCharachter;
    }
    const remaining = lexer.source.len - lexer.pos;

    // Fallback for the very end of the file (scalar skip)
    if (remaining < 16) {
        while (lexer.pos < lexer.source.len) : (lexer.pos += 1) {
            const c = lexer.source[lexer.pos];
            if (!isHex(c)) break;
        }
        return;
    }

    // Branch prediction should hit this branch
    const A_1: @Vector(16, u8) = @splat('a' - 1);
    const F_1: @Vector(16, u8) = @splat('f' + 1);
    const D0_1: @Vector(16, u8) = @splat('0' - 1);
    const D9_1: @Vector(16, u8) = @splat('9' + 1);
    const OR20: @Vector(16, u8) = @splat(0x20);

    var p = lexer.source.ptr + lexer.pos;
    // Calculate the safe limit for 16-byte loads
    const end_ptr = lexer.source.ptr + lexer.source.len - 16;

    while (@intFromPtr(p) <= @intFromPtr(end_ptr)) {
        const v: @Vector(16, u8) = p[0..16].*;

        // case fold letters
        const lower = v | OR20;

        const isCharAlpha = (lower > A_1) & (lower < F_1);
        const isCharDigit = (v > D0_1) & (v < D9_1);

        const isCharHex = isCharAlpha | isCharDigit;

        // Convert the boolean vector into a 16-bit integer mask
        const mask: u16 = @bitCast(isCharHex);

        if (mask != 0xFFFF) {
            // Found a non symbol hexidecimal
            lexer.pos = @intFromPtr(p) - @intFromPtr(lexer.source.ptr) + @ctz(~mask);
            return;
        }

        p += 16;
    }

    // Final tail check if SIMD finished without finding a stop character
    lexer.pos = @intFromPtr(p) - @intFromPtr(lexer.source.ptr);
    while (lexer.pos < lexer.source.len) : (lexer.pos += 1) {
        const c = lexer.source[lexer.pos];
        if (!isHex(c)) break;
    }
}
pub fn recognizeBinary(lexer: *Lexer) !void {
    const char = lexer.advance() orelse {
        return LexerError.InvalidCharachter;
    };
    if (!isDigit(char)) {
        return LexerError.InvalidCharachter;
    }
    const remaining = lexer.source.len - lexer.pos;

    // Fallback for the very end of the file (scalar skip)
    if (remaining < 16) {
        while (lexer.pos < lexer.source.len) : (lexer.pos += 1) {
            const c = lexer.source[lexer.pos];
            if (c != '0' and c != '1') break;
        }
        return;
    }

    // Branch prediction should hit this branch
    const D0: @Vector(16, u8) = @splat('0');
    const D1: @Vector(16, u8) = @splat('1');

    var p = lexer.source.ptr + lexer.pos;
    // Calculate the safe limit for 16-byte loads
    const end_ptr = lexer.source.ptr + lexer.source.len - 16;

    while (@intFromPtr(p) <= @intFromPtr(end_ptr)) {
        const v: @Vector(16, u8) = p[0..16].*;

        const isCharBin = (v == D0) | (v == D1);

        // Convert the boolean vector into a 16-bit integer mask
        const mask: u16 = @bitCast(isCharBin);

        if (mask != 0xFFFF) {
            // Found a non digit charachter
            lexer.pos = @intFromPtr(p) - @intFromPtr(lexer.source.ptr) + @ctz(~mask);
            return;
        }

        p += 16;
    }

    // Final tail check if SIMD finished without finding a stop character
    lexer.pos = @intFromPtr(p) - @intFromPtr(lexer.source.ptr);
    while (lexer.pos < lexer.source.len) : (lexer.pos += 1) {
        const c = lexer.source[lexer.pos];
        if (!isDigit(c)) break;
    }
}

pub fn recognizeInteger(lexer: *Lexer) !void {
    const char = lexer.advance() orelse {
        return LexerError.InvalidCharachter;
    };
    if (!isDigit(char)) {
        return LexerError.InvalidCharachter;
    }
    const remaining = lexer.source.len - lexer.pos;

    // Fallback for the very end of the file (scalar skip)
    if (remaining < 16) {
        while (lexer.pos < lexer.source.len) : (lexer.pos += 1) {
            const c = lexer.source[lexer.pos];
            if (!isDigit(c)) break;
        }
        return;
    }

    // Branch prediction should hit this branch
    const D0_1: @Vector(16, u8) = @splat('0' - 1);
    const D9_1: @Vector(16, u8) = @splat('9' + 1);

    var p = lexer.source.ptr + lexer.pos;
    // Calculate the safe limit for 16-byte loads
    const end_ptr = lexer.source.ptr + lexer.source.len - 16;

    while (@intFromPtr(p) <= @intFromPtr(end_ptr)) {
        const v: @Vector(16, u8) = p[0..16].*;

        const isCharDigit = (v > D0_1) & (v < D9_1);

        // Convert the boolean vector into a 16-bit integer mask
        const mask: u16 = @bitCast(isCharDigit);

        if (mask != 0xFFFF) {
            // Found a non digit charachter
            lexer.pos = @intFromPtr(p) - @intFromPtr(lexer.source.ptr) + @ctz(~mask);
            return;
        }

        p += 16;
    }

    // Final tail check if SIMD finished without finding a stop character
    lexer.pos = @intFromPtr(p) - @intFromPtr(lexer.source.ptr);
    while (lexer.pos < lexer.source.len) : (lexer.pos += 1) {
        const c = lexer.source[lexer.pos];
        if (!isDigit(c)) break;
    }
}
pub fn recognizeNumeric(lexer: *Lexer) !NumericType {
    var char = try lexer.expectChar();
    if (char < '0' or char > '9') return LexerError.UnexpectedCharachter;
    const numericTokenStart = lexer.pos;
    lexer.consume();

    char = lexer.currentChar() orelse {
        lexer.token = lexer.source[numericTokenStart..(numericTokenStart + 1)];
        return NumericType.INTEGER;
    };

    switch (char) {
        'x', 'X' => {
            lexer.consume();
            try recognizeHexidecimal(lexer);
            lexer.token = lexer.source[numericTokenStart..lexer.pos];
            return NumericType.HEX;
        },
        'b', 'B' => {
            lexer.consume();
            try recognizeBinary(lexer);
            lexer.token = lexer.source[numericTokenStart..lexer.pos];
            return NumericType.BIN;
        },
        '.' => {
            lexer.consume();
            try recognizeInteger(lexer);
            lexer.token = lexer.source[numericTokenStart..lexer.pos];
            return NumericType.FLOAT;
        },
        '0'...'9' => {
            try recognizeInteger(lexer);
            lexer.token = lexer.source[numericTokenStart..lexer.pos];
            char = lexer.currentChar() orelse return NumericType.INTEGER;
            if (char != '.') return NumericType.INTEGER;
            lexer.consume();
            try recognizeInteger(lexer);
            lexer.token = lexer.source[numericTokenStart..lexer.pos];
            return NumericType.FLOAT;
        },
        else => return LexerError.UnexpectedCharachter,
    }
}
const Keyword = enum {
    IDENTIFIER,
    IMPORT,
    AS,
    PUBLIC,
    PRIVATE,
    VAR,
    VAL,
    DEF,
    SELF,
    INTERFACE,
    STRUCT,
    CLASS,
    ENUM,
    ASSERT,
    IF,
    MATCH,
    LOOP,
    WHILE,
    BREAK,
    CONTINUE,
    YIELD,
    RETURN,
    LABEL,
    FLAG,
    FLAGS,
    COMPTIME,
    UNDERSCORE,
    TRUE,
    FALSE,
    NULL,
};
const KeywordMap = std.StaticStringMap(Keyword).initComptime(.{
    .{ "import", .IMPORT },
    .{ "as", .AS },
    .{ "public", .PUBLIC },
    .{ "private", .PRIVATE },
    .{ "var", .VAR },
    .{ "val", .VAL },
    .{ "def", .DEF },
    .{ "self", .SELF },
    .{ "interface", .INTERFACE },
    .{ "struct", .STRUCT },
    .{ "class", .CLASS },
    .{ "enum", .ENUM },
    .{ "assert", .ASSERT },
    .{ "if", .IF },
    .{ "match", .MATCH },
    .{ "loop", .LOOP },
    .{ "while", .WHILE },
    .{ "break", .BREAK },
    .{ "continue", .CONTINUE },
    .{ "yield", .YIELD },
    .{ "return", .RETURN },
    .{ "label", .LABEL },
    .{ "flag", .FLAG },
    .{ "flags", .FLAGS },
    .{ "comptime", .COMPTIME },
    .{ "_", .UNDERSCORE },
    .{ "true", .TRUE },
    .{ "false", .FALSE },
    .{ "null", .NULL },
});

pub fn recognizeKeyword(lexer: *Lexer) !Keyword {
    const identifierStartPos = lexer.pos;
    try recognizeIdentifier(lexer);
    lexer.token = lexer.source[identifierStartPos..lexer.pos];
    return KeywordMap.get(lexer.token) orelse Keyword.IDENTIFIER;
}
pub fn recognizeIdentifier(lexer: *Lexer) !void {
    const char = lexer.advance() orelse {
        return LexerError.UnexpectedEndOfFile;
    };
    if (char == '_') return;
    if (!isAlpha(char)) {
        return LexerError.InvalidCharachter;
    }
    const remaining = lexer.source.len - lexer.pos;

    // Fallback for the very end of the file (scalar skip)
    if (remaining < 16) {
        while (lexer.pos < lexer.source.len) : (lexer.pos += 1) {
            const c = lexer.source[lexer.pos];
            if (!isAlpha(c) or !isDigit(c) or c != '_') break;
        }
        return;
    }

    // Branch prediction should hit this branch
    const A_1: @Vector(16, u8) = @splat('a' - 1);
    const Z_1: @Vector(16, u8) = @splat('z' + 1);
    const D0_1: @Vector(16, u8) = @splat('0' - 1);
    const D9_1: @Vector(16, u8) = @splat('9' + 1);
    const UNDR: @Vector(16, u8) = @splat('_');
    const OR20: @Vector(16, u8) = @splat(0x20);

    var p = lexer.source.ptr + lexer.pos;
    // Calculate the safe limit for 16-byte loads
    const end_ptr = lexer.source.ptr + lexer.source.len - 16;

    while (@intFromPtr(p) <= @intFromPtr(end_ptr)) {
        const v: @Vector(16, u8) = p[0..16].*;

        // case fold letters
        const lower = v | OR20;

        const isCharAlpha = (lower > A_1) & (lower < Z_1);
        const isCharDigit = (v > D0_1) & (v < D9_1);
        const isUnderScore = v == UNDR;

        const isValidChar = (isCharAlpha | isCharDigit) | isUnderScore;

        // Convert the boolean vector into a 16-bit integer mask
        const mask: u16 = @bitCast(isValidChar);

        if (mask != 0xFFFF) {
            // Found a non symbol hexidecimal
            lexer.pos = @intFromPtr(p) - @intFromPtr(lexer.source.ptr) + @ctz(~mask);
            return;
        }

        p += 16;
    }

    // Final tail check if SIMD finished without finding a stop character
    lexer.pos = @intFromPtr(p) - @intFromPtr(lexer.source.ptr);
    while (lexer.pos < lexer.source.len) : (lexer.pos += 1) {
        const c = lexer.source[lexer.pos];
        if (!isAlpha(c) || !isDigit(c) || c != '_') break;
    }
}

fn recognizeBlock(lexer: *Lexer, char: u8) void {
    var escaped: bool = false;
    const startOfBlock: usize = lexer.pos;
    lexer.consume();

    var c: u8 = undefined;
    while (true) {
        c = lexer.currentChar() orelse {
            lexer.token = lexer.source[startOfBlock..lexer.pos];
            return;
        };
        if (c == '\\') {
            escaped = !escaped;
        } else {
            escaped = false;
        }
        if (!(lexer.pos < lexer.source.len and (c != char or escaped))) break;
        lexer.consume();
    }
    lexer.token = lexer.source[startOfBlock..(startOfBlock + 1)];
}
pub fn recognizeStrLiteral(lexer: *Lexer) !void {
    const char: u8 = lexer.currentChar() orelse return LexerError.UnexpectedEndOfFile;

    if (char != '"' and char != '\'') return LexerError.UnexpectedCharachter;
    const startOfString = lexer.pos;
    lexer.consume();

    recognizeBlock(lexer, char);
    lexer.token = lexer.source[startOfString..lexer.pos];
}

pub const AssignmentOperator = enum {
    DEFAULT, //   =
    ADD, //  +=
    SUBTRACT, //  -=
    MULTIPLY, //  *=
    DIVIDE, //  /=
    MODULO, //  %=
    OR, //  |=
    AND, //  &=
    XOR, //  ^=
    BITSHIFT_LEFT, // <<=
    BITSHIFT_RIGHT, // >>=
};
pub const Operator = enum {
    EQUALS, // ==
    GREATER_THAN, // >
    GREATER_THAN_OR_EQUAL_TO, // >=
    LESS_THAN, // <
    LESS_THAN_OR_EQUAL_TO, // <=
    NOT, // !
    NOT_EQUALS, // !-
    BITWISE_OR, // |
    OR, // ||
    BITWISE_AND, // &
    AND, // &&
    XOR, // ^
    ADD, // +
    SUBTRACT, // -
    MULTIPLY, // *
    DIVIDE, // /
    MODULO, // %
    BITSHIFT_LEFT, // <<
    BITSHIFT_RIGHT, // >>
    REFERENCE, // ~
    DOT, // .
    RANGE, // ..
};
pub const SymbolType = union(enum) {
    ASSIGNMENT: AssignmentOperator,
    SAT_ASSIGNMENT: AssignmentOperator,
    MOD_ASSIGNMENT: AssignmentOperator,
    OPERATOR: Operator,
    SAT_OPERATOR: Operator,
    MOD_OPERATOR: Operator,
    SYMBOL: u8,

    pub fn isAssignment(self: SymbolType) bool {
        return switch (self) {
            .ASSIGNMENT, .SAT_ASSIGNMENT, .MOD_ASSIGNMENT => true,
            .OPERATOR, .SAT_OPERATOR, .MOD_OPERATOR => false,
        };
    }
};
pub fn recognizeSymbol(lexer: *Lexer) !SymbolType {
    var char = try lexer.expectChar();
    lexer.consume();
    switch (char) {
        '\n', ';', ',', '(', ')', '[', ']', '{', '}' => {
            return SymbolType{
                .SYMBOL = char,
            };
        },
        '~' => {
            return SymbolType{
                .OPERATOR = Operator.REFERENCE,
            };
        },
        '=' => {
            char = lexer.currentChar() orelse return SymbolType{
                .ASSIGNMENT = AssignmentOperator.DEFAULT,
            };
            if (char == '=') {
                lexer.consume();
                return SymbolType{
                    .OPERATOR = Operator.EQUALS,
                };
            }
            return SymbolType{
                .ASSIGNMENT = AssignmentOperator.DEFAULT,
            };
        },
        '/' => {
            char = lexer.currentChar() orelse return SymbolType{
                .ASSIGNMENT = AssignmentOperator.DIVIDE,
            };
            if (char == '=') {
                lexer.consume();
                return SymbolType{
                    .ASSIGNMENT = AssignmentOperator.DIVIDE,
                };
            }
            return SymbolType{
                .OPERATOR = Operator.DIVIDE,
            };
        },
        '%' => {
            char = lexer.currentChar() orelse return SymbolType{
                .ASSIGNMENT = AssignmentOperator.MODULO,
            };
            if (char == '=') {
                lexer.consume();
                return SymbolType{
                    .ASSIGNMENT = AssignmentOperator.MODULO,
                };
            }
            return SymbolType{
                .OPERATOR = Operator.MODULO,
            };
        },
        '!' => {
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.NOT,
            };
            if (char == '=') {
                lexer.consume();
                return SymbolType{
                    .OPERATOR = Operator.NOT_EQUALS,
                };
            }
            return SymbolType{
                .OPERATOR = Operator.NOT,
            };
        },
        '.' => {
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.DOT,
            };
            if (char == '.') {
                lexer.consume();
                return SymbolType{
                    .OPERATOR = Operator.RANGE,
                };
            }
            return SymbolType{
                .OPERATOR = Operator.DOT,
            };
        },
        '&' => {
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.BITWISE_AND,
            };
            if (char == '=') {
                lexer.consume();
                return SymbolType{
                    .ASSIGNMENT = AssignmentOperator.AND,
                };
            }
            if (char == '&') {
                lexer.consume();
                return SymbolType{
                    .OPERATOR = Operator.AND,
                };
            }
            return SymbolType{
                .OPERATOR = Operator.BITWISE_AND,
            };
        },
        '|' => {
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.BITWISE_OR,
            };
            if (char == '=') {
                lexer.consume();
                return SymbolType{
                    .ASSIGNMENT = AssignmentOperator.OR,
                };
            }
            if (char == '|') {
                lexer.consume();
                return SymbolType{
                    .OPERATOR = Operator.OR,
                };
            }
            return SymbolType{
                .OPERATOR = Operator.BITWISE_OR,
            };
        },

        // +; +=; +%; +|; +%=; +|=;
        '+' => {
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.ADD,
            };
            switch (char) {
                '=' => {
                    lexer.consume();
                    return SymbolType{
                        .ASSIGNMENT = AssignmentOperator.ADD,
                    };
                },
                '%' => {
                    lexer.consume();
                    char = lexer.currentChar() orelse return SymbolType{
                        .MOD_OPERATOR = Operator.ADD,
                    };
                    if (char == '=') {
                        lexer.consume();
                        return SymbolType{
                            .MOD_ASSIGNMENT = AssignmentOperator.ADD,
                        };
                    }
                    return SymbolType{
                        .MOD_OPERATOR = Operator.ADD,
                    };
                },
                '|' => {
                    lexer.consume();
                    char = lexer.currentChar() orelse return SymbolType{
                        .SAT_OPERATOR = Operator.ADD,
                    };
                    if (char == '=') {
                        lexer.consume();
                        return SymbolType{
                            .SAT_ASSIGNMENT = AssignmentOperator.ADD,
                        };
                    }
                    return SymbolType{
                        .SAT_OPERATOR = Operator.ADD,
                    };
                },
                else => return SymbolType{
                    .OPERATOR = Operator.ADD,
                },
            }
        },

        // -; -=; -%; -|; -%=; -|=;
        '-' => {
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.SUBTRACT,
            };
            switch (char) {
                '=' => {
                    lexer.consume();
                    return SymbolType{
                        .ASSIGNMENT = AssignmentOperator.SUBTRACT,
                    };
                },
                '%' => {
                    lexer.consume();
                    char = lexer.currentChar() orelse return SymbolType{
                        .MOD_OPERATOR = Operator.SUBTRACT,
                    };
                    if (char == '=') {
                        lexer.consume();
                        return SymbolType{
                            .MOD_ASSIGNMENT = AssignmentOperator.SUBTRACT,
                        };
                    }
                    return SymbolType{
                        .MOD_OPERATOR = Operator.SUBTRACT,
                    };
                },
                '|' => {
                    lexer.consume();
                    char = lexer.currentChar() orelse return SymbolType{
                        .SAT_OPERATOR = Operator.SUBTRACT,
                    };
                    if (char == '=') {
                        lexer.consume();
                        return SymbolType{
                            .SAT_ASSIGNMENT = AssignmentOperator.SUBTRACT,
                        };
                    }
                    return SymbolType{
                        .SAT_OPERATOR = Operator.SUBTRACT,
                    };
                },
                else => return SymbolType{
                    .OPERATOR = Operator.SUBTRACT,
                },
            }
        },

        // *; *=; *%; *|; *%=; *|=;
        '*' => {
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.MULTIPLY,
            };
            switch (char) {
                '=' => {
                    lexer.consume();
                    return SymbolType{
                        .ASSIGNMENT = AssignmentOperator.MULTIPLY,
                    };
                },
                '%' => {
                    lexer.consume();
                    char = lexer.currentChar() orelse return SymbolType{
                        .MOD_OPERATOR = Operator.MULTIPLY,
                    };
                    if (char == '=') {
                        lexer.pos += 1;
                        return SymbolType{
                            .MOD_ASSIGNMENT = AssignmentOperator.MULTIPLY,
                        };
                    }
                    return SymbolType{
                        .MOD_OPERATOR = Operator.MULTIPLY,
                    };
                },
                '|' => {
                    lexer.pos += 1;
                    char = lexer.currentChar() orelse return SymbolType{
                        .SAT_OPERATOR = Operator.MULTIPLY,
                    };
                    if (char == '=') {
                        lexer.consume();
                        return SymbolType{
                            .SAT_ASSIGNMENT = AssignmentOperator.MULTIPLY,
                        };
                    }
                    return SymbolType{
                        .SAT_OPERATOR = Operator.MULTIPLY,
                    };
                },
                else => return SymbolType{
                    .OPERATOR = Operator.MULTIPLY,
                },
            }
        },
        // <; <=; <<; <<=; <<|=;
        '<' => {
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.LESS_THAN,
            };
            if (char == '=') {
                lexer.consume();
                return SymbolType{
                    .OPERATOR = Operator.LESS_THAN_OR_EQUAL_TO,
                };
            }
            if (char != '<') {
                return SymbolType{
                    .OPERATOR = Operator.LESS_THAN,
                };
            }
            lexer.consume();
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.BITSHIFT_LEFT,
            };
            if (char == '=') {
                lexer.consume();
                return SymbolType{
                    .ASSIGNMENT = AssignmentOperator.BITSHIFT_LEFT,
                };
            }
            if (char != '|') {
                return SymbolType{
                    .OPERATOR = Operator.BITSHIFT_LEFT,
                };
            }
            lexer.consume();
            char = lexer.currentChar() orelse return SymbolType{
                .SAT_OPERATOR = Operator.BITSHIFT_LEFT,
            };
            if (char == '=') {
                lexer.consume();
                return SymbolType{
                    .SAT_ASSIGNMENT = AssignmentOperator.BITSHIFT_LEFT,
                };
            }
            return SymbolType{
                .SAT_OPERATOR = Operator.BITSHIFT_LEFT,
            };
        },
        // >; >=; >>=; >>|; >>|=;
        '>' => {
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.GREATER_THAN,
            };
            if (char == '=') {
                lexer.consume();
                return SymbolType{
                    .OPERATOR = Operator.GREATER_THAN_OR_EQUAL_TO,
                };
            }
            if (char != '>') {
                return SymbolType{
                    .OPERATOR = Operator.GREATER_THAN,
                };
            }
            lexer.consume();
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.BITSHIFT_RIGHT,
            };
            if (char == '=') {
                lexer.consume();
                return SymbolType{
                    .ASSIGNMENT = AssignmentOperator.BITSHIFT_RIGHT,
                };
            }
            if (char != '|') {
                return SymbolType{
                    .OPERATOR = Operator.BITSHIFT_RIGHT,
                };
            }
            lexer.consume();
            char = lexer.currentChar() orelse return SymbolType{
                .SAT_OPERATOR = Operator.BITSHIFT_RIGHT,
            };
            if (char == '=') {
                lexer.consume();
                return SymbolType{
                    .SAT_ASSIGNMENT = AssignmentOperator.BITSHIFT_RIGHT,
                };
            }
            return SymbolType{
                .SAT_OPERATOR = Operator.BITSHIFT_RIGHT,
            };
        },
        else => {
            lexer.pos -= 1;
            return LexerError.UnexpectedCharachter;
        },
    }
}
