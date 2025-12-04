package com.viffx.Lang.Compiler;

import com.viffx.Lang.Symbols.*;

import java.util.Stack;

public class Compiler {
    // Lexer
    public final Lexer lexer;

    // Symbols
	public final Symbol symbol0 = new  NonTerminal("START");
	public final Symbol symbol1 = new  NonTerminal("statements");
	public final Symbol symbol2 = new  NonTerminal("statement_prime");
	public final Symbol symbol3 = new  NonTerminal("end_statement");
	public final Symbol symbol4 = new  Terminal(SymbolType.EPSILON, null);
	public final Symbol symbol5 = new  Terminal(SymbolType.SYM, "\n");
	public final Symbol symbol6 = new  Terminal(SymbolType.SYM, ";");
	public final Symbol symbol7 = new  Terminal(SymbolType.KEY, "import");
	public final Symbol symbol8 = new  Terminal(SymbolType.KEY, "import_statement");
	public final Symbol symbol9 = new  NonTerminal("statement");
	public final Symbol symbol10 = new  Terminal(SymbolType.KEY, "expression");
	public final Symbol symbol11 = new  NonTerminal("declaration");
	public final Symbol symbol12 = new  Terminal(SymbolType.KEY, "if");
	public final Symbol symbol13 = new  Terminal(SymbolType.KEY, "if_statement block");
	public final Symbol symbol14 = new  Terminal(SymbolType.KEY, "loop");
	public final Symbol symbol15 = new  Terminal(SymbolType.KEY, "loop_statement");
	public final Symbol symbol16 = new  Terminal(SymbolType.KEY, "match");
	public final Symbol symbol17 = new  Terminal(SymbolType.KEY, "match_statement");
	public final Symbol symbol18 = new  Terminal(SymbolType.KEY, "label");
	public final Symbol symbol19 = new  Terminal(SymbolType.NUM, null);
	public final Symbol symbol20 = new  Terminal(SymbolType.KEY, "goto");
	public final Symbol symbol21 = new  Terminal(SymbolType.KEY, "goto_statement");
	public final Symbol symbol22 = new  Terminal(SymbolType.KEY, "return");
	public final Symbol symbol23 = new  Terminal(SymbolType.KEY, "return_statement");
	public final Symbol symbol24 = new  Terminal(SymbolType.KEY, "continue");
	public final Symbol symbol25 = new  Terminal(SymbolType.KEY, "continue_statement");
	public final Symbol symbol26 = new  NonTerminal("accessibility_modifiers");
	public final Symbol symbol27 = new  Terminal(SymbolType.KEY, "public");
	public final Symbol symbol28 = new  Terminal(SymbolType.KEY, "private");
	public final Symbol symbol29 = new  NonTerminal("mutability_modifiers");
	public final Symbol symbol30 = new  NonTerminal("mutable");
	public final Symbol symbol31 = new  NonTerminal("immutable");
	public final Symbol symbol32 = new  Terminal(SymbolType.KEY, "variable");
	public final Symbol symbol33 = new  Terminal(SymbolType.KEY, "var");
	public final Symbol symbol34 = new  Terminal(SymbolType.KEY, "value");
	public final Symbol symbol35 = new  Terminal(SymbolType.KEY, "val");
	public final Symbol symbol36 = new  NonTerminal("declaration_tail");
	public final Symbol symbol37 = new  NonTerminal("lambda");
	public final Symbol symbol38 = new  Terminal(SymbolType.KEY, "type");
	public final Symbol symbol39 = new  Terminal(SymbolType.KEY, "id");
	public final Symbol symbol40 = new  NonTerminal("assignment_or_function");
	public final Symbol symbol41 = new  Terminal(SymbolType.SYM, "=");
	public final Symbol symbol42 = new  NonTerminal("function");
	public final Symbol symbol43 = new  Terminal(SymbolType.KEY, "lambda");
	public final Symbol symbol44 = new  Terminal(SymbolType.SYM, "(");
	public final Symbol symbol45 = new  Terminal(SymbolType.KEY, "parameters");
	public final Symbol symbol46 = new  Terminal(SymbolType.SYM, ")");
	public final Symbol symbol47 = new  Terminal(SymbolType.KEY, "block");
	public final Symbol symbol48 = new  Terminal(SymbolType.TEST, "#");
	public final Symbol symbol49 = new  Terminal(SymbolType.EOF, "$");

    // Parsing state
    public final Stack<Integer> stack = new Stack<>();
    public final Stack<AstNode> ast = new Stack<>();
    public Symbol current;

    public Compiler(Lexer lexer) throws Exception {
        this.lexer = lexer;
        do {
            current = lexer.next();
        } while (current instanceof Terminal t && t.type() == SymbolType.COMMENT);
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
				case 1 -> state1();
				case 2 -> state2();
				case 3 -> {
					state3();
					success = true;
					break label;
				}
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
				case 15 -> state15();
				case 16 -> state16();
				case 17 -> state17();
				case 18 -> state18();
				case 19 -> state19();
				case 20 -> state20();
				case 21 -> state21();
				case 22 -> state22();
				case 23 -> state23();
				case 24 -> state24();
				case 25 -> state25();
				case 26 -> state26();
				case 27 -> state27();
				case 28 -> state28();
				case 29 -> state29();
				case 30 -> state30();
				case 31 -> state31();
				case 32 -> state32();
				case 33 -> state33();
				case 34 -> state34();
				case 35 -> state35();
				case 36 -> state36();
				case 37 -> state37();
				case 38 -> state38();
				case 39 -> state39();
				case 40 -> state40();
				case 41 -> state41();
				case 42 -> state42();
				case 43 -> state43();
				case 44 -> state44();
				case 45 -> state45();
				case 46 -> state46();
				case 47 -> state47();
				case 48 -> state48();
				case 49 -> state49();
				case 50 -> state50();
				case 51 -> state51();
				default -> throw new Exception("Invalid parser state " + state);
			}
		}
		if (!success) {
		    throw new Exception("Failed parse");
		}
		ParserGenerator.printAST(ast.pop());
	}

	// === Utility methods for LR actions ===
	private void shift(Symbol s, int nextState) throws Exception {
 		if (!current.equals(s)) throw new Exception("Unexpected token: " + current);
 		System.out.println("Shift: " + current);
 		stack.push(nextState);
 		ast.push(new AstNode(current));
 		do {
   			current = lexer.next();
   		} while (current instanceof Terminal t && t.type() == SymbolType.COMMENT);
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
				if (nt.equals(symbol1)) stack.push(3);
				else if (nt.equals(symbol2)) stack.push(5);
				else if (nt.equals(symbol37)) stack.push(7);
				else if (nt.equals(symbol9)) stack.push(10);
				else if (nt.equals(symbol26)) stack.push(20);
				else if (nt.equals(symbol11)) stack.push(12);
				else if (nt.equals(symbol30)) stack.push(23);
				else if (nt.equals(symbol31)) stack.push(24);
				else throw new Exception("state0 has no goto for " + nt);
			}
			case 20 -> {
				if (nt.equals(symbol29)) stack.push(38);
				else if (nt.equals(symbol30)) stack.push(23);
				else if (nt.equals(symbol31)) stack.push(24);
				else throw new Exception("state20 has no goto for " + nt);
			}
			case 5 -> {
				if (nt.equals(symbol3)) stack.push(25);
				else throw new Exception("state5 has no goto for " + nt);
			}
			case 38 -> {
				if (nt.equals(symbol36)) stack.push(46);
				else if (nt.equals(symbol37)) stack.push(7);
				else throw new Exception("state38 has no goto for " + nt);
			}
			case 25 -> {
				if (nt.equals(symbol1)) stack.push(39);
				else if (nt.equals(symbol2)) stack.push(5);
				else if (nt.equals(symbol37)) stack.push(7);
				else if (nt.equals(symbol9)) stack.push(10);
				else if (nt.equals(symbol26)) stack.push(20);
				else if (nt.equals(symbol11)) stack.push(12);
				else if (nt.equals(symbol30)) stack.push(23);
				else if (nt.equals(symbol31)) stack.push(24);
				else throw new Exception("state25 has no goto for " + nt);
			}
			case 28 -> {
				if (nt.equals(symbol40)) stack.push(40);
				else if (nt.equals(symbol42)) stack.push(42);
				else throw new Exception("state28 has no goto for " + nt);
			}
			case 45 -> {
				if (nt.equals(symbol42)) stack.push(49);
				else throw new Exception("state45 has no goto for " + nt);
			}
		}
	}

	// === State functions ===
	public void state0() throws Exception {
		if (current.equals(symbol35)) shift(symbol35,6);
		else if (current.equals(symbol34)) shift(symbol34,4);
		else if (current.equals(symbol32)) shift(symbol32,1);
		else if (current.equals(symbol33)) shift(symbol33,2);
		else if (current.equals(symbol43)) shift(symbol43,13);
		else if (current.equals(symbol14)) shift(symbol14,14);
		else if (current.equals(symbol10)) shift(symbol10,11);
		else if (current.equals(symbol49)) reduce(symbol1,0);
		else if (current.equals(symbol7)) shift(symbol7,9);
		else if (current.equals(symbol38)) shift(symbol38,8);
		else if (current.equals(symbol27)) shift(symbol27,21);
		else if (current.equals(symbol28)) shift(symbol28,22);
		else if (current.equals(symbol24)) shift(symbol24,19);
		else if (current.equals(symbol20)) shift(symbol20,17);
		else if (current.equals(symbol22)) shift(symbol22,18);
		else if (current.equals(symbol16)) shift(symbol16,15);
		else if (current.equals(symbol18)) shift(symbol18,16);
		else throw new Exception("Parse error in state0: " + current);
	}
	public void state1() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol38) || current.equals(symbol43) || current.equals(symbol12)) reduce(symbol30,1);
		else throw new Exception("Parse error in state1: " + current);
	}
	public void state2() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol38) || current.equals(symbol43) || current.equals(symbol12)) reduce(symbol30,1);
		else throw new Exception("Parse error in state2: " + current);
	}
	public void state3() throws Exception {
		if (current.equals(symbol49)) System.out.println("ACCEPTED");
		else throw new Exception("Parse error in state3: " + current);
	}
	public void state4() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol38) || current.equals(symbol43) || current.equals(symbol12)) reduce(symbol31,1);
		else throw new Exception("Parse error in state4: " + current);
	}
	public void state5() throws Exception {
		if (current.equals(symbol6)) shift(symbol6,27);
		else if (current.equals(symbol49)) reduce(symbol1,1);
		else if (current.equals(symbol5)) shift(symbol5,26);
		else throw new Exception("Parse error in state5: " + current);
	}
	public void state6() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol38) || current.equals(symbol43) || current.equals(symbol12)) reduce(symbol31,1);
		else throw new Exception("Parse error in state6: " + current);
	}
	public void state7() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol12)) reduce(symbol36,1);
		else throw new Exception("Parse error in state7: " + current);
	}
	public void state8() throws Exception {
		if (current.equals(symbol39)) shift(symbol39,28);
		else throw new Exception("Parse error in state8: " + current);
	}
	public void state9() throws Exception {
		if (current.equals(symbol8)) shift(symbol8,29);
		else throw new Exception("Parse error in state9: " + current);
	}
	public void state10() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol5) || current.equals(symbol6)) reduce(symbol2,1);
		else throw new Exception("Parse error in state10: " + current);
	}
	public void state11() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol5) || current.equals(symbol6)) reduce(symbol9,1);
		else throw new Exception("Parse error in state11: " + current);
	}
	public void state12() throws Exception {
		if (current.equals(symbol12)) shift(symbol12,30);
		else throw new Exception("Parse error in state12: " + current);
	}
	public void state13() throws Exception {
		if (current.equals(symbol39)) shift(symbol39,31);
		else throw new Exception("Parse error in state13: " + current);
	}
	public void state14() throws Exception {
		if (current.equals(symbol15)) shift(symbol15,32);
		else throw new Exception("Parse error in state14: " + current);
	}
	public void state15() throws Exception {
		if (current.equals(symbol17)) shift(symbol17,33);
		else throw new Exception("Parse error in state15: " + current);
	}
	public void state16() throws Exception {
		if (current.equals(symbol19)) shift(symbol19,34);
		else throw new Exception("Parse error in state16: " + current);
	}
	public void state17() throws Exception {
		if (current.equals(symbol21)) shift(symbol21,35);
		else throw new Exception("Parse error in state17: " + current);
	}
	public void state18() throws Exception {
		if (current.equals(symbol23)) shift(symbol23,36);
		else throw new Exception("Parse error in state18: " + current);
	}
	public void state19() throws Exception {
		if (current.equals(symbol25)) shift(symbol25,37);
		else throw new Exception("Parse error in state19: " + current);
	}
	public void state20() throws Exception {
		if (current.equals(symbol38) || current.equals(symbol43)) reduce(symbol29,0);
		else if (current.equals(symbol35)) shift(symbol35,6);
		else if (current.equals(symbol34)) shift(symbol34,4);
		else if (current.equals(symbol32)) shift(symbol32,1);
		else if (current.equals(symbol33)) shift(symbol33,2);
		else throw new Exception("Parse error in state20: " + current);
	}
	public void state21() throws Exception {
		if (current.equals(symbol32) || current.equals(symbol33) || current.equals(symbol49) || current.equals(symbol34) || current.equals(symbol35) || current.equals(symbol38) || current.equals(symbol43)) reduce(symbol26,1);
		else throw new Exception("Parse error in state21: " + current);
	}
	public void state22() throws Exception {
		if (current.equals(symbol32) || current.equals(symbol33) || current.equals(symbol49) || current.equals(symbol34) || current.equals(symbol35) || current.equals(symbol38) || current.equals(symbol43)) reduce(symbol26,1);
		else throw new Exception("Parse error in state22: " + current);
	}
	public void state23() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol38) || current.equals(symbol43) || current.equals(symbol12)) reduce(symbol29,1);
		else throw new Exception("Parse error in state23: " + current);
	}
	public void state24() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol38) || current.equals(symbol43) || current.equals(symbol12)) reduce(symbol29,1);
		else throw new Exception("Parse error in state24: " + current);
	}
	public void state25() throws Exception {
		if (current.equals(symbol35)) shift(symbol35,6);
		else if (current.equals(symbol34)) shift(symbol34,4);
		else if (current.equals(symbol32)) shift(symbol32,1);
		else if (current.equals(symbol33)) shift(symbol33,2);
		else if (current.equals(symbol43)) shift(symbol43,13);
		else if (current.equals(symbol14)) shift(symbol14,14);
		else if (current.equals(symbol10)) shift(symbol10,11);
		else if (current.equals(symbol49)) reduce(symbol1,0);
		else if (current.equals(symbol7)) shift(symbol7,9);
		else if (current.equals(symbol38)) shift(symbol38,8);
		else if (current.equals(symbol27)) shift(symbol27,21);
		else if (current.equals(symbol28)) shift(symbol28,22);
		else if (current.equals(symbol24)) shift(symbol24,19);
		else if (current.equals(symbol20)) shift(symbol20,17);
		else if (current.equals(symbol22)) shift(symbol22,18);
		else if (current.equals(symbol16)) shift(symbol16,15);
		else if (current.equals(symbol18)) shift(symbol18,16);
		else throw new Exception("Parse error in state25: " + current);
	}
	public void state26() throws Exception {
		if (current.equals(symbol32) || current.equals(symbol33) || current.equals(symbol34) || current.equals(symbol35) || current.equals(symbol38) || current.equals(symbol7) || current.equals(symbol10) || current.equals(symbol43) || current.equals(symbol14) || current.equals(symbol16) || current.equals(symbol49) || current.equals(symbol18) || current.equals(symbol20) || current.equals(symbol22) || current.equals(symbol24) || current.equals(symbol27) || current.equals(symbol28)) reduce(symbol3,1);
		else throw new Exception("Parse error in state26: " + current);
	}
	public void state27() throws Exception {
		if (current.equals(symbol32) || current.equals(symbol33) || current.equals(symbol34) || current.equals(symbol35) || current.equals(symbol38) || current.equals(symbol7) || current.equals(symbol10) || current.equals(symbol43) || current.equals(symbol14) || current.equals(symbol16) || current.equals(symbol49) || current.equals(symbol18) || current.equals(symbol20) || current.equals(symbol22) || current.equals(symbol24) || current.equals(symbol27) || current.equals(symbol28)) reduce(symbol3,1);
		else throw new Exception("Parse error in state27: " + current);
	}
	public void state28() throws Exception {
		if (current.equals(symbol44)) shift(symbol44,43);
		else if (current.equals(symbol41)) shift(symbol41,41);
		else throw new Exception("Parse error in state28: " + current);
	}
	public void state29() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol5) || current.equals(symbol6)) reduce(symbol2,2);
		else throw new Exception("Parse error in state29: " + current);
	}
	public void state30() throws Exception {
		if (current.equals(symbol13)) shift(symbol13,44);
		else throw new Exception("Parse error in state30: " + current);
	}
	public void state31() throws Exception {
		if (current.equals(symbol41)) shift(symbol41,45);
		else throw new Exception("Parse error in state31: " + current);
	}
	public void state32() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol5) || current.equals(symbol6)) reduce(symbol9,2);
		else throw new Exception("Parse error in state32: " + current);
	}
	public void state33() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol5) || current.equals(symbol6)) reduce(symbol9,2);
		else throw new Exception("Parse error in state33: " + current);
	}
	public void state34() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol5) || current.equals(symbol6)) reduce(symbol9,2);
		else throw new Exception("Parse error in state34: " + current);
	}
	public void state35() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol5) || current.equals(symbol6)) reduce(symbol9,2);
		else throw new Exception("Parse error in state35: " + current);
	}
	public void state36() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol5) || current.equals(symbol6)) reduce(symbol9,2);
		else throw new Exception("Parse error in state36: " + current);
	}
	public void state37() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol5) || current.equals(symbol6)) reduce(symbol9,2);
		else throw new Exception("Parse error in state37: " + current);
	}
	public void state38() throws Exception {
		if (current.equals(symbol43)) shift(symbol43,13);
		else if (current.equals(symbol38)) shift(symbol38,8);
		else throw new Exception("Parse error in state38: " + current);
	}
	public void state39() throws Exception {
		if (current.equals(symbol49)) reduce(symbol1,3);
		else throw new Exception("Parse error in state39: " + current);
	}
	public void state40() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol12)) reduce(symbol36,3);
		else throw new Exception("Parse error in state40: " + current);
	}
	public void state41() throws Exception {
		if (current.equals(symbol10)) shift(symbol10,47);
		else throw new Exception("Parse error in state41: " + current);
	}
	public void state42() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol12)) reduce(symbol40,1);
		else throw new Exception("Parse error in state42: " + current);
	}
	public void state43() throws Exception {
		if (current.equals(symbol45)) shift(symbol45,48);
		else throw new Exception("Parse error in state43: " + current);
	}
	public void state44() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol5) || current.equals(symbol6)) reduce(symbol9,3);
		else throw new Exception("Parse error in state44: " + current);
	}
	public void state45() throws Exception {
		if (current.equals(symbol44)) shift(symbol44,43);
		else throw new Exception("Parse error in state45: " + current);
	}
	public void state46() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol12)) reduce(symbol11,3);
		else throw new Exception("Parse error in state46: " + current);
	}
	public void state47() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol12)) reduce(symbol40,2);
		else throw new Exception("Parse error in state47: " + current);
	}
	public void state48() throws Exception {
		if (current.equals(symbol46)) shift(symbol46,50);
		else throw new Exception("Parse error in state48: " + current);
	}
	public void state49() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol12)) reduce(symbol37,4);
		else throw new Exception("Parse error in state49: " + current);
	}
	public void state50() throws Exception {
		if (current.equals(symbol47)) shift(symbol47,51);
		else throw new Exception("Parse error in state50: " + current);
	}
	public void state51() throws Exception {
		if (current.equals(symbol49) || current.equals(symbol12)) reduce(symbol42,4);
		else throw new Exception("Parse error in state51: " + current);
	}
}