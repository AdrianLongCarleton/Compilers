const std = @import("std");
const builtin = @import("builtin");

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
        skipWhiteSpace(&lexer);
        return lexer;
    }

    pub fn nextToken(self: *Lexer, recognizer: anytype) !void {
        try recognizer(self);
        self.skipWhiteSpace(self);
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
