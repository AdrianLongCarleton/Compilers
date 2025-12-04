package com.viffx.Lang;

import com.viffx.Lang.Symbols.*;

import java.util.Stack;

public class Compiler {
    // Lexer
    public final Lexer lexer;

    // Symbols
	public final Symbol symbol0 = new  NonTerminal("START");
	public final Symbol symbol1 = new  NonTerminal("S");
	public final Symbol symbol2 = new  NonTerminal("L");
	public final Symbol symbol3 = new  Terminal(TokenType.SYM, "-");
	public final Symbol symbol4 = new  NonTerminal("R");
	public final Symbol symbol5 = new  Terminal(TokenType.SYM, "*");
	public final Symbol symbol6 = new  Terminal(TokenType.NUM, null);
	public final Symbol symbol7 = new  Terminal(TokenType.EPSILON, null);
	public final Symbol symbol8 = new  Terminal(TokenType.EOF, "$");
	public final Symbol symbol9 = new  Terminal(TokenType.TEST, "#");

    // Parsing state
    public final Stack<Integer> stack = new Stack<>();
    public final Stack<AstNode> ast = new Stack<>();
    public Symbol current;

    public Compiler(Lexer lexer) throws Exception {
        this.lexer = lexer;
        do {
            current = lexer.next();
        } while (current.type() == TokenType.COMMENT);
        this.stack.push(0);
    }
    // === Core parser driver ===
	public void parse() throws Exception {
	    boolean success = false;
		label:
		while (!stack.isEmpty()) {
			int state = stack.peek();
			switch (state) {
				case 0 -> state0();
				case 1 -> {
					state1();
					success = true;
					break label;
				}
				case 2 -> state2();
				case 3 -> state3();
				case 4 -> state4();
				case 5 -> state5();
				case 6 -> state6();
				case 7 -> state7();
				case 8 -> state8();
				case 9 -> state9();
				default -> throw new Exception("Invalid parser state " + state);
			}
		}
		if (!success) {
		    throw new Exception("Failed parse");
		}
		CompilerFactory.printAST(ast.pop());
	}

	// === Utility methods for LR actions ===
	private void shift(Symbol s, int nextState) throws Exception {
 		if (!current.equals(s)) throw new Exception("Unexpected token: " + current);
 		System.out.println("Shift: " + current);
 		stack.push(nextState);
 		ast.push(new AstNode(current));
 		do {
   			current = lexer.next();
   		} while (current.type() == TokenType.COMMENT);
 	}

	private void reduce(Symbol lhs, int rhsLength) throws Exception {
 		assert lhs instanceof NonTerminal;
 		AstNode node = new AstNode(lhs);
 		for (int i = 0; i < rhsLength; i++) {
 			node.add(ast.pop());
 			stack.pop();
 		}
 		ast.push(node);
 		System.out.println("Reduce: " + lhs + " <- " + rhsLength + " symbols");
 		// Now do GOTO based on state under top
 		int state = stack.peek();
 		gotoState(state,lhs);
 	}

	private void gotoState(int state, Symbol nt) throws Exception {
		switch(state) {
			case 0 -> {
				if (nt.equals(symbol1)) stack.push(1);
				else if (nt.equals(symbol2)) stack.push(2);
				else if (nt.equals(symbol4)) stack.push(3);
				else throw new Exception("state0 has no goto for " + nt);
			}
			case 4 -> {
				if (nt.equals(symbol2)) stack.push(7);
				else if (nt.equals(symbol4)) stack.push(8);
				else throw new Exception("state4 has no goto for " + nt);
			}
			case 6 -> {
				if (nt.equals(symbol2)) stack.push(7);
				else if (nt.equals(symbol4)) stack.push(9);
				else throw new Exception("state6 has no goto for " + nt);
			}
		}
	}

	// === State functions ===
	public void state0() throws Exception {
		if (current.equals(symbol5)) shift(symbol5,4);
		else if (current.equals(symbol6)) shift(symbol6,5);
		else throw new Exception("Parse error in state0: " + current);
	}
	public void state1() throws Exception {
		if (current.equals(symbol8)) System.out.println("ACCEPTED");
		else throw new Exception("Parse error in state1: " + current);
	}
	public void state2() throws Exception {
		if (current.equals(symbol3)) shift(symbol3,6);
		else if (current.equals(symbol8)) reduce(symbol4,1);
		else throw new Exception("Parse error in state2: " + current);
	}
	public void state3() throws Exception {
		if (current.equals(symbol8)) reduce(symbol1,1);
		else throw new Exception("Parse error in state3: " + current);
	}
	public void state4() throws Exception {
		if (current.equals(symbol5)) shift(symbol5,4);
		else if (current.equals(symbol6)) shift(symbol6,5);
		else throw new Exception("Parse error in state4: " + current);
	}
	public void state5() throws Exception {
		if (current.equals(symbol3) || current.equals(symbol8)) reduce(symbol2,1);
		else throw new Exception("Parse error in state5: " + current);
	}
	public void state6() throws Exception {
		if (current.equals(symbol5)) shift(symbol5,4);
		else if (current.equals(symbol6)) shift(symbol6,5);
		else throw new Exception("Parse error in state6: " + current);
	}
	public void state7() throws Exception {
		if (current.equals(symbol3) || current.equals(symbol8)) reduce(symbol4,1);
		else throw new Exception("Parse error in state7: " + current);
	}
	public void state8() throws Exception {
		if (current.equals(symbol3) || current.equals(symbol8)) reduce(symbol2,2);
		else throw new Exception("Parse error in state8: " + current);
	}
	public void state9() throws Exception {
		if (current.equals(symbol8)) reduce(symbol1,3);
		else throw new Exception("Parse error in state9: " + current);
	}
}