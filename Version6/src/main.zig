//! Lexing:
//!     Lexing won't be grouped into a single recognizer function as in some states of the parser it
//!     is impossible for a symbol token to occur. Instead the recognizing capability of the lexer
//!     will be split into several different token recognizing functions. This reduces branching in
//!     code.
//!
//! Parsing:
//!     Abstract syntax trees usualy require branching data structures. Instead in lang the abstract
//!     syntax tree is flattened. Nodes are said to be terminals if they have a child count of zero.
//!     Nodes are said to be nonterminals if they have a child count that is greater than zero. To
//!     advance to the next node of the current rule, just advance 1 + the number of child nodes of
//!     the current node.

const std = @import("std");
const builtin = @import("builtin");
const Version6 = @import("Version6");
const Lexing = Version6.Lexing;

pub const MMap = struct {
    ptr: []align(std.heap.page_size_min) u8,

    pub fn open(file: std.fs.File) !MMap {
        const size = (try file.stat()).size;
        if (size == 0) return error.EmptyFile;

        if (builtin.os.tag == .windows) {
            const w = std.os.windows;
            // 1. Create mapping object
            const mapping = try w.createFileMapping(
                file.handle,
                null,
                w.PAGE_READONLY,
                0,
                0,
                null,
            );
            defer w.CloseHandle(mapping);

            // 2, Map view of file
            const raw_ptr = try w.MapViewOfFile(
                mapping,
                w.FILE_MAP_READ,
                0,
                0,
                0,
            );

            // Cast to alligned slice
            return MMap{ .ptr = @as([*]align(std.mem.page_size) u8, @ptrCast(raw_ptr))[0..size] };
        } else {
            // POSIC (Linux and macOS)
            const ptr = try std.posix.mmap(
                null,
                size,
                std.posix.PROT.READ,
                .{ .TYPE = .SHARED },
                file.handle,
                0,
            );
            return MMap{ .ptr = ptr };
        }
    }
    pub fn close(self: *MMap) void {
        if (builtin.os.tag == .windows) {
            std.os.windows.UnmapViewOfFile(self.ptr.ptr);
        } else {
            std.posix.munmap(self.ptr);
        }
        self.ptr = &.{};
    }
};

pub fn main() !void {
    var gpa = std.heap.GeneralPurposeAllocator(.{}){};
    defer _ = gpa.deinit();
    const allocator = gpa.allocator();

    const args = try std.process.argsAlloc(allocator);
    defer std.process.argsFree(allocator, args);

    if (args.len < 2) {
        std.debug.print("usage: compiler \"filepath\"\n", .{});
        return;
    }

    const path = args[1];

    const file = try std.fs.cwd().openFile(path, .{ .mode = .read_only });
    defer file.close();

    var mmap = try MMap.open(file);
    defer mmap.close();
}
