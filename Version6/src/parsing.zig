const std = @import("std");

const Version6 = @import("Version6");
const lexing = Version6.lexing;

const LexerError = lexing.LexerError;
const Lexer = lexing.Lexer;
const SymbolType = lexing.SymbolType;
const NumericType = lexing.NumericType;
const Keyword = lexing.Keyword;

const ParseError = error{
    UnexepectedValue,
    EmptyAst,
};

// So parsing is extremely difficult if I can't jump to the end of a statement.
// Change of approach.
// The first pass of the lexer will split the code into statements.
// By counting the number of ';' and '\n' i've seen I can always jump the the end of a statement.
// AstNode:
//    block
//       block
//       parent
//    parent
//
// parse the deepest block first???
const NodeType = union(enum) {
    KEYWORD: lexing.Keyword,
    NUMERIC: lexing.NumericType,

    ASSIGNMENT: lexing.AssignmentOperator,
    SAT_ASSIGNMENT: lexing.AssignmentOperator,
    MOD_ASSIGNMENT: lexing.AssignmentOperator,
    OPERATOR: lexing.Operator,
    SAT_OPERATOR: lexing.Operator,
    MOD_OPERATOR: lexing.Operator,

    ERROR,

    STATEMENTS,
    STATEMENT,

    pub fn fromSymbolType(symbolType: SymbolType) !NodeType {
        return switch (symbolType) {
            .ASSIGNMENT => .{ .ASSIGNMENT = symbolType.value() },
            .SAT_ASSIGNMENT => .{ .SAT_ASSIGNMENT = symbolType.value() },
            .MOD_ASSIGNMENT => .{ .MOD_ASSIGNMENT = symbolType.value() },
            .OPERATOR => .{ .OPERATOR = symbolType.value() },
            .SAT_OPERATOR => .{ .SAT_OPERATOR = symbolType.value() },
            .MOD_OPERATOR => .{ .MOD_OPERATOR = symbolType.value() },
            else => return ParseError.UnexpectedValue,
        };
    }
    pub fn fromKeyword(keyword: Keyword) NodeType {
        return .{ .KEYWORD = keyword };
    }
    pub fn fromNumericType(numeric: NumericType) NodeType {
        return .{ .NUMERIC = numeric };
    }

    pub fn isNonTerminal(self: NodeType) bool {
        return switch (self) {
            NodeType.KEYWORD, NodeType.NUMERIC, NodeType.ASSIGNMENT, NodeType.SAT_ASSIGNMENT, NodeType.MOD_ASSIGNMENT, NodeType.OPERATOR, NodeType.SAT_OPERATOR, NodeType.ERROR => false,
            else => true,
        };
    }
};
const AstNode = struct {
    children: usize,
    tokenId: ?usize,
    nodeType: NodeType,

    pub fn create(nodeType: NodeType) AstNode {
        return AstNode{ 0, null, nodeType };
    }
};
const StackNode = struct {
    parent: usize,
    nodeType: NodeType,

    pub fn create(parent: usize, nodeType: NodeType) StackNode {
        return StackNode{ parent, nodeType };
    }
};

const Parser = struct {
    lexer: *Lexer,
    tokens: std.ArrayList(u8),
    ast: std.ArrayList(AstNode),
    stack: std.ArrayList(StackNode),
    current: StackNode,

    pub fn create(allocator: std.mem.Allocator, lexer: *Lexer) !Parser {
        return Parser{
            .lexer = lexer,
            .tokens = try std.ArrayList(u8).initCapacity(allocator, 8192),
            .ast = try std.ArrayList(AstNode).initCapacity(allocator, 8192),
            .stack = try std.ArrayList(StackNode).initCapacity(allocator, 64),
            .current = undefined,
        };
    }
    pub fn destroy(self: *Parser) void {
        self.tokens.deinit();
        self.ast.deinit();
        self.stack.deinit();
    }

    pub fn stackPop(self: *Parser) !void {
        self.current = try self.stack.pop();
    }
    pub fn stackPush(self: *Parser, parent: usize, nodeType: NodeType) !void {
        self.current.parent = parent;
        self.current.nodeType = nodeType;
        try self.stack.append(self.current);
    }

    pub fn astPush(self: *Parser, nodeType: NodeType) !usize {
        const parent: usize = self.ast.items.len;
        try self.ast.append(AstNode.create(nodeType));
        return parent;
    }
    pub fn astGet(parser: *Parser, pos: usize) AstNode {
        return parser.ast.items[pos];
    }

    pub fn assignSliceToCurrentNode(parser: *Parser, token: []u8) !void {
        const len: usize = parser.ast.items.len;
        if (len == 0) return ParseError.EmptyAst;
        parser.ast.items[len - 1].tokenId = parser.tokens.items.len;
        try parser.tokens.appendSlice(token);
        try parser.tokens.append(0);
    }
};
fn parseStatements(parser: *Parser) void {
    parser.stackPop() catch {};
    if (parser.lexer.atEnd()) return;

    const current: usize = parser.astPush(NodeType.STATEMENTS) catch return;
    try parser.stackPush(current, NodeType.STATEMENT);

    return parseStatementHead(parser);
}
fn parseStatementTail(parser: *Parser) !void {
    parser.stackPop() catch return;

    // statement \n
    // statement ; statement
    // statement eof
    // statement else inject a semicolon.

    const symbolType: ?SymbolType = lexing.recognizeSymbol(parser.lexer) catch |err| switch (err) {
        LexerError.UnexpectedEndOfFile => return,
        LexerError.UnexpectedCharachter => null,
        else => unreachable,
    };
    if (symbolType == null or
        symbolType.? != .SYMBOL or
        (symbolType.?.value() != '\n' and symbolType.?.value() != ';'))
    {
        std.debug.print("PARSE ERROR IN STATEMENT at {}: Expected '\n' or ';'. Got: {}. Action: INJECTED ';'\n", .{ parser.lexing.pos, symbolType });
    }
    parser.lexer.skipIgnored();
    if (parser.lexer.atEnd()) return;

    return parseStatementHead();
}

fn parseStatementHead(parser: *Parser) void {
    const keyword: Keyword = lexing.recognizeKeyword() catch |err| switch (err) {
        LexerError.UnexpectedEndOfFile => {
            std.debug.print("PARSE ERROR IN STATEMENT at {}: Expected: keyword or identifier. Got: EOF. Action: REDUCED.", .{parser.lexer.pos});
            return;
        },
        else => return parseExpression(parser),
    };
    switch (keyword) {
        .IMPORT => return parseImportStatement(parser),
        .AS => {
            std.debug.print("PARSE ERROR IN STATEMENT at {}: Illegal: Keyword \"as\" used at the start of a statement. Action: DELETED.", .{parser.lexer.pos});
            return parseStatementHead(parser);
        },
        .INTERFACE, .STRUCT, .CLASS, .ENUM, .PUBLIC, .PRIVATE, .VAR, .VAL, .DEF => return parseDeclaration(parser),
        .SELF => {
            std.debug.print("PARSE ERROR IN STATEMENT at {}: Illegal: Identifer \"self\" used at the start of a statement. Action: DELETED.", .{parser.lexer.pos});
            return parseStatementHead(parser);
        },
        .IF, .MATCH => return parseExpression(parser),
        .LOOP => return parseLoopStatement(parser),
    }
}
fn parseExpression(parser: *Parser) void {
    if (parser != null) {}
}
fn parseImportStatement(parser: *Parser) void {
    if (parser != null) {}
}
fn parseLoopStatement(parser: *Parser) void {
    if (parser != null) {}
}
fn parseDeclaration(parser: *Parser) void {
    if (parser != null) {}
}

pub fn parse(lexer: *Lexer) !void {
    const allocator = std.heap.page_allocator;

    var parser: Parser = Parser.create(allocator, lexer);
    defer parser.destroy();

    parseStatements(&parser);

    // val x = 5
    // ; val y = 2;;;;;;;;\n
    // statements:
    //      statement:
    //          val x = 5
    //      statement:
    //          val y = 2

    // let x = $$$$$$$$...\n;
    // val x = 5
    // val y = 6; val z = 7
}
