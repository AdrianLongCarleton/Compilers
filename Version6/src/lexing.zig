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
    pub fn currentChar(self: Lexer) !u8 {
        if (self.pos >= self.source.len) return error.UnexpectedEndOfFile;
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
    DEFAULT,        //   =
    ADD,            //  +=
    SUBTRACT,       //  -=
    MULTIPLY,       //  *=
    DIVIDE,         //  /=
    MODULO,         //  %=
    OR,             //  |=
    AND,            //  &=
    XOR,            //  ^=
    BITSHIFT_LEFT,  // <<=
    BITSHIFT_RIGHT, // >>=
};
pub const Operator = enum {
    EQUALS,                   // ==
    GREATER_THAN,             // >
    GREATER_THAN_OR_EQUAL_TO, // >=
    LESS_THAN,                // <
    LESS_THAN_OR_EQUAL_TO,    // <=
    NOT,                      // !
    NOT_EQUALS,               // !-
    OR,                       // ||
    AND,                      // &&
    XOR,                      // ^
    ADD,                      // +
    SUBTRACT,                 // -
    MULTIPLY,                 // *
    DIVIDE,                   // /
    MODULO,                   // %
    BITSHIFT_LEFT,            // <<
    BITSHIFT_RIGHT,           // >>
    ADDRESS,                  // &
    REFERENCE,                // ~
};
pub const SymbolType = union(enum) {
    ASSIGNMENT: AssignmentOperator,
    SAT_ASSIGNMENT: AssignmentOperator,
    MOD_ASSIGNMENT: AssignmentOperator,
    OPERATOR: Operator,
    SAT_OPERATOR: Operator,
    MOD_OPERATOR: Operator,

    pub fn isAssignment(self) SymbolType {
        return switch (self) {
            .ASSIGNMENT,
            .SAT_ASSIGNMENT,
            .MOD_ASSIGNMENT => true,

            .OPERATOR,
            .SAT_OPERATOR,
            .MOD_OPERATOR => false,
        };
    }
};
pub fn recognizeSymbol(lexer: *Lexer) !SymbolType {
    const tokenStart: usize = lexer.pos;
    var char = try lexer.currentChar();
    SymbolType symbol = switch (char) {
        '+' => ret: {
            lexer.pos += 1;
            char = lexer.currentChar() catch |_| {
                break :ret SymbolType{.OPERATOR = Operator.ADD,};
            };
            switch (char) {
                '=' => {
                    break :ret SymbolType{.ASSIGNMENT = AsignmentOperator.ADD,};
                },
                '%' => {
                    lexer.pos += 1;
                    char = lexer.currentChar() catch |err| {
                        break :ret SymbolType{.MOD_OPERATOR = Operator.ADD,};
                    }
                    if (char != '=') {
                        break :ret SymbolType{.MOD_OPERATOR = Operator.ADD,};
                    }
                },
                '|' => {
                    lexer.pos += 1;
                    char = lexer.currentChar() catch |err| {
                        break :ret SymbolType{.SAT_OPERATOR = Operator.ADD,};
                    }
                    if (char != '=') {
                        break :ret SymbolType{.SAT_OPERATOR = Operator.ADD,};
                    }
                },
                else => break :ret SymbolType{.OPERATOR = Operator.ADD,},
            }
        },

        '-' => ret: {
            lexer.pos += 1;
            char = lexer.currentChar() catch |_| {
                break :ret SymbolType{.OPERATOR = Operator.SUBTRACT,};
            };
            switch (char) {
                '=' => {
                    break :ret SymbolType{.ASSIGNMENT = AsignmentOperator.SUBTRACT,};
                },
                '%' => {
                    lexer.pos += 1;
                    char = lexer.currentChar() catch |err| {
                        break :ret SymbolType{.MOD_OPERATOR = Operator.SUBTRACT,};
                    }
                    if (char != '=') {
                        break :ret SymbolType{.MOD_OPERATOR = Operator.SUBTRACT,};
                    }
                },
                '|' => {
                    lexer.pos += 1;
                    char = lexer.currentChar() catch |err| {
                        break :ret SymbolType{.SAT_OPERATOR = Operator.SUBTRACT,};
                    }
                    if (char != '=') {
                        break :ret SymbolType{.SAT_OPERATOR = Operator.SUBTRACT,};
                    }
                },
                else => break :ret SymbolType{.OPERATOR = Operator.SUBTRACT,},
            }
        },

        '*' => ret: {
            lexer.pos += 1;
            char = lexer.currentChar() catch |_| {
                break :ret SymbolType{.OPERATOR = Operator.MULTIPLY,};
            };
            switch (char) {
                '=' => {
                    break :ret SymbolType{.ASSIGNMENT = AsignmentOperator.MULTIPLY,};
                },
                '%' => {
                    lexer.pos += 1;
                    char = lexer.currentChar() catch |err| {
                        break :ret SymbolType{.MOD_OPERATOR = Operator.MULITPLY,};
                    }
                    if (char != '=') {
                        break :ret SymbolType{.MOD_OPERATOR = Operator.MULITPLY,};
                    }
                },
                '|' => {
                    lexer.pos += 1;
                    char = lexer.currentChar() catch |err| {
                        break :ret SymbolType{.SAT_OPERATOR = Operator.MULITPLY,};
                    }
                    if (char != '=') {
                        break :ret SymbolType{.SAT_OPERATOR = Operator.MULITPLY,};
                    }
                },
                else => break :ret SymbolType{.OPERATOR = Operator.MULTIPLY,},
            }
        },
        '<',
        '>' => ret: {
            lexer.pos += 1;
            char = lexer.currentChar() catch |_| {
                break :ret SymbolType{.OPERATOR = Operator.GREATER_THAN,};
            }
            if (char == '=') {
                lexer.pos += 1;
                break :ret SymbolType{.OPERATOR = Operator.GREATER_THAN_OR_EQUAL_TO,};
            }
            if (char != '>') {
                break :ret SymbolType{.OPERATOR = Operator.GREATER_THAN,};
            }
            lexer.pos += 1;
            char = lexer.currentChar() catch |_| {
                break :ret SymbolType{.OPERATOR = Operator.BITSHIFT_RIGHT,};   
            }
            if (char == '=') {
                lexer.pos += 1;
                break :ret SymbolType{.ASSIGNMENT = AssignmentOperator.BITSHIFT_RIGHT,};   
            }
            if (char != '|') {
                break :ret SymbolType{.OPERATOR = Operator.BITSHIFT_RIGHT,};
            }
            lexer.pos += 1;
            char = lexer.currentChar() catch |_| {
                break :ret SymbolType{.SAT_OPERATOR = Operator.BITSHIFT_RIGHT,};
            }
            if (char == '=') {
                lexer.pos += 1;
                break :ret SymbolType{.SAT_ASSIGNMENT = AssignmentOperator.BITSHIFT_RIGHT,};
            }
            break :ret SymbolType{.SAT_OPERATOR = Operator.BITSHIFT_RIGHT,};
        },
    };
}
