const std = @import("std");
const builtin = @import("builtin");

pub const LexerError = error{
    UnexpectedCharachter,
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
            .token = &[_]u8{},
        };
        lexer.skipWhiteSpace();
        return lexer;
    }

    pub fn nextToken(self: *Lexer, recognizer: anytype) !void {
        try recognizer(self);
        self.skipWhiteSpace(self);
    }
    pub fn currentChar(self: *const Lexer) ?u8 {
        if (self.pos >= self.source.len) return null;
        return self.source[self.pos];
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
            }

            p += 16;
        }

        // Final tail check if SIMD finished without finding a stop character
        self.pos = @intFromPtr(p) - @intFromPtr(self.source.ptr);
        while (self.pos < self.source.len) : (self.pos += 1) {
            const c = self.source[self.pos];
            if (c == '\n' or c > 0x20) break;
        }
    }
};
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
    OR, // ||
    AND, // &&
    XOR, // ^
    ADD, // +
    SUBTRACT, // -
    MULTIPLY, // *
    DIVIDE, // /
    MODULO, // %
    BITSHIFT_LEFT, // <<
    BITSHIFT_RIGHT, // >>
    ADDRESS, // &
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
    var char = lexer.currentChar() orelse return error.UnexpectedEndOfFile;
    switch (char) {
        ',', '(', ')', '[', ']', '{', '}' => {
            lexer.pos += 1;
            return SymbolType{
                .SYMBOL = char,
            };
        },
        '~' => {
            lexer.pos += 1;
            return SymbolType{
                .OPERATOR = Operator.REFERENCE,
            };
        },
        '=' => {
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .ASSIGNMENT = AssignmentOperator.DEFAULT,
            };
            if (char == '=') {
                lexer.pos += 1;
                return SymbolType{
                    .OPERATOR = Operator.EQUALS,
                };
            }
            return SymbolType{
                .ASSIGNMENT = AssignmentOperator.DEFAULT,
            };
        },
        '/' => {
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .ASSIGNMENT = AssignmentOperator.DIVIDE,
            };
            if (char == '=') {
                lexer.pos += 1;
                return SymbolType{
                    .ASSIGNMENT = AssignmentOperator.DIVIDE,
                };
            }
            return SymbolType{
                .OPERATOR = Operator.DIVIDE,
            };
        },
        '%' => {
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .ASSIGNMENT = AssignmentOperator.MODULO,
            };
            if (char == '=') {
                lexer.pos += 1;
                return SymbolType{
                    .ASSIGNMENT = AssignmentOperator.MODULO,
                };
            }
            return SymbolType{
                .OPERATOR = Operator.MODULO,
            };
        },
        '!' => {
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.NOT,
            };
            if (char == '=') {
                lexer.pos += 1;
                return SymbolType{
                    .OPERATOR = Operator.NOT_EQUALS,
                };
            }
            return SymbolType{
                .OPERATOR = Operator.NOT,
            };
        },
        '.' => {
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.DOT,
            };
            if (char == '.') {
                lexer.pos += 1;
                return SymbolType{
                    .OPERATOR = Operator.RANGE,
                };
            }
            return SymbolType{
                .OPERATOR = Operator.DOT,
            };
        },
        '&' => {
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.BITWISE_AND,
            };
            if (char == '=') {
                lexer.pos += 1;
                return SymbolType{
                    .ASSIGNMENT = AssignmentOperator.AND,
                };
            }
            if (char == '&') {
                lexer.pos += 1;
                return SymbolType{
                    .OPERATOR = Operator.AND,
                };
            }
            return SymbolType{
                .OPERATOR = Operator.BITWISE_AND,
            };
        },
        '|' => {
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.BITWISE_OR,
            };
            if (char == '=') {
                lexer.pos += 1;
                return SymbolType{
                    .ASSIGNMENT = AssignmentOperator.OR,
                };
            }
            if (char == '|') {
                lexer.pos += 1;
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
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.ADD,
            };
            switch (char) {
                '=' => {
                    lexer.pos += 1;
                    return SymbolType{
                        .ASSIGNMENT = AssignmentOperator.ADD,
                    };
                },
                '%' => {
                    lexer.pos += 1;
                    char = lexer.currentChar() orelse return SymbolType{
                        .MOD_OPERATOR = Operator.ADD,
                    };
                    if (char != '=') {
                        return SymbolType{
                            .MOD_OPERATOR = Operator.ADD,
                        };
                    }
                    lexer.pos += 1;
                    return SymbolType{
                        .MOD_ASSIGNMENT = AssignmentOperator.ADD,
                    };
                },
                '|' => {
                    lexer.pos += 1;
                    char = lexer.currentChar() orelse return SymbolType{
                        .SAT_OPERATOR = Operator.ADD,
                    };
                    if (char != '=') {
                        return SymbolType{
                            .SAT_OPERATOR = Operator.ADD,
                        };
                    }
                    lexer.pos += 1;
                    return SymbolType{
                        .SAT_ASSIGNMENT = AssignmentOperator.ADD,
                    };
                },
                else => return SymbolType{
                    .OPERATOR = Operator.ADD,
                },
            }
        },

        // -; -=; -%; -|; -%=; -|=;
        '-' => {
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.SUBTRACT,
            };
            switch (char) {
                '=' => {
                    lexer.pos += 1;
                    return SymbolType{
                        .ASSIGNMENT = AssignmentOperator.SUBTRACT,
                    };
                },
                '%' => {
                    lexer.pos += 1;
                    char = lexer.currentChar() orelse return SymbolType{
                        .MOD_OPERATOR = Operator.SUBTRACT,
                    };
                    if (char != '=') {
                        return SymbolType{
                            .MOD_OPERATOR = Operator.SUBTRACT,
                        };
                    }
                    lexer.pos += 1;
                    return SymbolType{
                        .MOD_ASSIGNMENT = AssignmentOperator.SUBTRACT,
                    };
                },
                '|' => {
                    lexer.pos += 1;
                    char = lexer.currentChar() orelse return SymbolType{
                        .SAT_OPERATOR = Operator.SUBTRACT,
                    };
                    if (char != '=') {
                        return SymbolType{
                            .SAT_OPERATOR = Operator.SUBTRACT,
                        };
                    }
                    lexer.pos += 1;
                    return SymbolType{
                        .SAT_ASSIGNMENT = AssignmentOperator.SUBTRACT,
                    };
                },
                else => return SymbolType{
                    .OPERATOR = Operator.SUBTRACT,
                },
            }
        },

        // *; *=; *%; *|; *%=; *|=;
        '*' => {
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.MULTIPLY,
            };
            switch (char) {
                '=' => {
                    lexer.pos += 1;
                    return SymbolType{
                        .ASSIGNMENT = AssignmentOperator.MULTIPLY,
                    };
                },
                '%' => {
                    lexer.pos += 1;
                    char = lexer.currentChar() orelse return SymbolType{
                        .MOD_OPERATOR = Operator.MULTIPLY,
                    };
                    if (char != '=') {
                        return SymbolType{
                            .MOD_OPERATOR = Operator.MULTIPLY,
                        };
                    }
                    lexer.pos += 1;
                    return SymbolType{
                        .MOD_ASSIGNMENT = AssignmentOperator.MULTIPLY,
                    };
                },
                '|' => {
                    lexer.pos += 1;
                    char = lexer.currentChar() orelse return SymbolType{
                        .SAT_OPERATOR = Operator.MULTIPLY,
                    };
                    if (char != '=') {
                        return SymbolType{
                            .SAT_OPERATOR = Operator.MULTIPLY,
                        };
                    }
                    lexer.pos += 1;
                    return SymbolType{
                        .SAT_ASSIGNMENT = AssignmentOperator.MULTIPLY,
                    };
                },
                else => return SymbolType{
                    .OPERATOR = Operator.MULTIPLY,
                },
            }
        },
        // <; <=; <<; <<=; <<|=;
        '<' => {
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.LESS_THAN,
            };
            if (char == '=') {
                lexer.pos += 1;
                return SymbolType{
                    .OPERATOR = Operator.LESS_THAN_OR_EQUAL_TO,
                };
            }
            if (char != '<') {
                return SymbolType{
                    .OPERATOR = Operator.LESS_THAN,
                };
            }
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.BITSHIFT_LEFT,
            };
            if (char == '=') {
                lexer.pos += 1;
                return SymbolType{
                    .ASSIGNMENT = AssignmentOperator.BITSHIFT_LEFT,
                };
            }
            if (char != '|') {
                return SymbolType{
                    .OPERATOR = Operator.BITSHIFT_LEFT,
                };
            }
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .SAT_OPERATOR = Operator.BITSHIFT_LEFT,
            };
            if (char == '=') {
                lexer.pos += 1;
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
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.GREATER_THAN,
            };
            if (char == '=') {
                lexer.pos += 1;
                return SymbolType{
                    .OPERATOR = Operator.GREATER_THAN_OR_EQUAL_TO,
                };
            }
            if (char != '>') {
                return SymbolType{
                    .OPERATOR = Operator.GREATER_THAN,
                };
            }
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .OPERATOR = Operator.BITSHIFT_RIGHT,
            };
            if (char == '=') {
                lexer.pos += 1;
                return SymbolType{
                    .ASSIGNMENT = AssignmentOperator.BITSHIFT_RIGHT,
                };
            }
            if (char != '|') {
                return SymbolType{
                    .OPERATOR = Operator.BITSHIFT_RIGHT,
                };
            }
            lexer.pos += 1;
            char = lexer.currentChar() orelse return SymbolType{
                .SAT_OPERATOR = Operator.BITSHIFT_RIGHT,
            };
            if (char == '=') {
                lexer.pos += 1;
                return SymbolType{
                    .SAT_ASSIGNMENT = AssignmentOperator.BITSHIFT_RIGHT,
                };
            }
            return SymbolType{
                .SAT_OPERATOR = Operator.BITSHIFT_RIGHT,
            };
        },
        else => return error.UnexpectedCharachter,
    }
}
