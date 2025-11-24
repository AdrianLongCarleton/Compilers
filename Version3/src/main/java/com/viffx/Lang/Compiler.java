package com.viffx.Lang;

import com.viffx.Lang.Symbols.*;

import java.util.Stack;

public class Compiler {
    // Lexer
    public final Lexer lexer;

    // Symbols
	public final Symbol symbol0 = new  NonTerminal("START");
	public final Symbol symbol1 = new  NonTerminal("statements");
	public final Symbol symbol2 = new  NonTerminal("statement");
	public final Symbol symbol3 = new  Terminal(TokenType.SYM, ";");
	public final Symbol symbol4 = new  NonTerminal("import_statement");
	public final Symbol symbol5 = new  NonTerminal("declaration_statement");
	public final Symbol symbol6 = new  NonTerminal("modifiers");
	public final Symbol symbol7 = new  Terminal(TokenType.ID, null);
	public final Symbol symbol8 = new  Terminal(TokenType.ID, "public");
	public final Symbol symbol9 = new  Terminal(TokenType.ID, "import");
	public final Symbol symbol10 = new  NonTerminal("str_or_id");
	public final Symbol symbol11 = new  Terminal(TokenType.STR, null);
	public final Symbol symbol12 = new  Terminal(TokenType.EPSILON, null);
	public final Symbol symbol13 = new  Terminal(TokenType.EOF, "$");
	public final Symbol symbol14 = new  Terminal(TokenType.TEST, "#");

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
				case 10 -> state10();
				case 11 -> state11();
				case 12 -> state12();
				case 13 -> state13();
				case 14 -> state14();
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
                System.out.println(nt);
                System.out.println(symbol1 + " " + symbol2 + " " + symbol4);
				if (nt.equals(symbol1)) stack.push(1);
				else if (nt.equals(symbol2)) stack.push(2);
				else if (nt.equals(symbol4)) stack.push(3);
				else if (nt.equals(symbol5)) stack.push(4);
				else if (nt.equals(symbol6)) stack.push(5);
				else throw new Exception("state0 has no goto for " + nt);
			}
			case 7 -> {
				if (nt.equals(symbol10)) stack.push(12);
				else throw new Exception("state7 has no goto for " + nt);
			}
			case 9 -> {
				if (nt.equals(symbol1)) stack.push(14);
				else if (nt.equals(symbol2)) stack.push(2);
				else if (nt.equals(symbol4)) stack.push(3);
				else if (nt.equals(symbol5)) stack.push(4);
				else if (nt.equals(symbol6)) stack.push(5);
				else throw new Exception("state9 has no goto for " + nt);
			}
		}
	}

	// === State functions ===
	public void state0() throws Exception {
		if (current.equals(symbol8)) shift(symbol8,6);
		else if (current.equals(symbol9)) shift(symbol9,7);
		else if (current.equals(symbol3) || current.equals(symbol13)) reduce(symbol6,0);
		else if (current.equals(symbol12)) shift(symbol12,8);
		else throw new Exception("Parse error in state0: " + current);
	}
	public void state1() throws Exception {
		if (current.equals(symbol13)) System.out.println("ACCEPTED");
		else throw new Exception("Parse error in state1: " + current);
	}
	public void state2() throws Exception {
		if (current.equals(symbol3)) shift(symbol3,9);
		else throw new Exception("Parse error in state2: " + current);
	}
	public void state3() throws Exception {
		if (current.equals(symbol3) || current.equals(symbol13)) reduce(symbol2,1);
		else throw new Exception("Parse error in state3: " + current);
	}
	public void state4() throws Exception {
		if (current.equals(symbol3) || current.equals(symbol13)) reduce(symbol2,1);
		else throw new Exception("Parse error in state4: " + current);
	}
	public void state5() throws Exception {
		if (current.equals(symbol7)) shift(symbol7,10);
		else throw new Exception("Parse error in state5: " + current);
	}
	public void state6() throws Exception {
		if (current.equals(symbol3) || current.equals(symbol13)) reduce(symbol6,1);
        else if (current.equals(symbol7)) shift(symbol7,10);
		else throw new Exception("Parse error in state6: " + current);
	}
	public void state7() throws Exception {
        System.out.println("STATE7");
        System.out.println("\t" + current);
        System.out.println("\t" + symbol7);
        System.out.println("\t" + symbol11);
		if (current.equals(symbol7)) shift(symbol7,11);
		else if (current.equals(symbol11)) shift(symbol11,13);
		else throw new Exception("Parse error in state7: " + current);
	}
	public void state8() throws Exception {
		if (current.equals(symbol3) || current.equals(symbol13)) reduce(symbol6,0);
		else throw new Exception("Parse error in state8: " + current);
	}
	public void state9() throws Exception {
        System.out.println("STATE9");
        System.out.println(current);
        System.out.println(symbol8 + " " + symbol9 + " (" + symbol3 + " " + symbol13 + ") " + symbol12);
		if (current.equals(symbol8)) shift(symbol8,6);
		else if (current.equals(symbol9)) shift(symbol9,7);
		else if (current.equals(symbol3)) reduce(symbol6,0);
        else if (current.equals(symbol13)) reduce(symbol1,0);
		else if (current.equals(symbol12)) shift(symbol12,8);
		else throw new Exception("Parse error in state9: " + current);
	}
	public void state10() throws Exception {
		if (current.equals(symbol3) || current.equals(symbol13)) reduce(symbol5,2);
		else throw new Exception("Parse error in state10: " + current);
	}
	public void state11() throws Exception {
		if (current.equals(symbol7) || current.equals(symbol13)) reduce(symbol10,1);
		else throw new Exception("Parse error in state11: " + current);
	}
	public void state12() throws Exception {
		if (current.equals(symbol7) || current.equals(symbol13)) reduce(symbol4,2);
		else throw new Exception("Parse error in state12: " + current);
	}
	public void state13() throws Exception {
        System.out.println(current);
        if (current.equals(symbol3)) reduce(symbol4,2);
		else if (current.equals(symbol7) || current.equals(symbol13)) reduce(symbol10,1);
		else throw new Exception("Parse error in state13: " + current);
	}
	public void state14() throws Exception {
		if (current.equals(symbol13)) reduce(symbol1,3);
		else throw new Exception("Parse error in state14: " + current);
	}
}