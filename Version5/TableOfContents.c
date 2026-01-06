/* ITEM:                 INDEX:
 * includes
 * File struct
 * loadFile
 * freeFile
 * nextBlock
 * simd_whitespace
 * simd_id
 * simd_hex
 * simd_num
 * simd_bin
 * simd_zero
 * tokentype declarations .......................................................................................... 307 to 337
 * 	TOKENTYPE_NULL =  0; TOKENTYPE_IF_EXPRESSION        = 11; TOKENTYPE_KEYWORD_PUBLIC   = 21;
 * 	TOKENTYPE_ID   =  1; TOKENTYPE_ELSE_EXPRESSION      = 12; TOKENTYPE_KEYWORD_PRIVATE  = 22;
 *	TOKENTYPE_NUM  =  2; TOKENTYPE_MATCH_CASE           = 13; TOKENTYPE_DECLARATION      = 23;
 * 	TOKENTYPE_HEX  =  3; TOKENTYPE_MATCH_EXPRESSION     = 14; TOKENTYPE_JUMP_STATEMENT   = 24;
 * 	TOKENTYPE_BIN  =  4; TOKENTYPE_EXPRESSION           = 15; TOKENTYPE_LOOP_STATEMENT   = 25;
 * 	TOKENTYPE_FLT  =  5; TOKENTYPE_TYPES                = 16; TOKENTYPE_STATEMENT        = 26;
 * 	TOKENTYPE_SYM  =  6; TOKENTYPE_TYPE                 = 17; TOKENTYPE_STATEMENTS       = 27;
 * 	TOKENTYPE_CHR  =  7; TOKENTYPE_FUNCTION_DECLARATION = 18; TOKENTYPE_BLOCK            = 28;
 * 	TOKENTYPE_STR  =  8; TOKENTYPE_LAMBDA               = 19; TOKENTYPE_IMPORT_STATEMENT = 29;
 * 	TOKENTYPE_KEY  =  9; TOKENTYPE_FUNCTION             = 19;
 * 	TOKENTYPE_EOF  = 10; TOKENTYPE_VARIABLE_DECLARATION = 20;
 * type_name (uint32_t TOKENTYPE) // converts the token type number to a string .................................... 329 to 371
 */Lexer struct = {char *currentChar; char* token; uint32_t type; uint64_t size;} // ............................... 373 to 378
/* printToken (Lexer *lexer) // prints the current token the lexer is on ........................................... 379 to 395
 * nextToken  (Lexer *lexer) // advances the lexer to the next token ............................................... 396 to 587
 * now_ns() // gets the current time ............................................................................... 591 to 595
 * ast node types = AST_NODE_TYPE_(NULL or TERMINAL or NON_TERMINAL) ............................................... 597 to 595
 */AstNode struct = {char *token; uint64_t tokenSize; uint64_t size; uint32_t astNodeType; uint32_t tokenType} //... 600 to 608 
   AST struct = {AstNode* nodes; uint64_t capacity; uint64_t count;} // ............................................ 609 to 613
/* AST_create() // initializes and abstract syntax tree ............................................................ 615 to 622
 * AST_destroy(AST* ast) // deinitializes and abstract sytntax tree ................................................ 623 to 628
 * AST_addNode() // deprecated ..................................................................................... 629 to 640
 * AST_resize(AST* ast) // reallocate the dynamic number of tokens somewhere else on the heap ...................... 641 to 653
 * AST_createNode(AST* ast) // initialize a ast node in the abstract syntax tree node arena ........................ 655 to 664
 * AST_insertNode(AST* ast, AstNode node, uint64_t index) // insert a new node into the abstract synax tree ........ 665 to 680
 * AST_getNode(AST* ast) // get a pointer to the last ast node in the ast .......................................... 682 to 685
 * AST_createNonTerminalNode(AST *ast, AstNode *current, uint32_t tokenType) ....................................... 686 to 691
 * AST_createTerminalNode(Ast *ast, AstNode* current, uint32_t tokenType, char* token, uint64_t tokenSize) ......... 692 to 700
 * parseBlockDeclaration 
 * parseExpressionDeclaration
 * parseIfExpression
 * parseJumpStatement
 * parseMatchCase
 * parseMatchExpression
 * parseExpressionTypes declarations
 * prefixBindingPower ..............................................................................................  878 to 1113
 * postfixBindingPower ............................................................................................. 1117 to 1354
 * leftInfixBindingPower ........................................................................................... 1356 to 1595
 * rightInfixBindingPower .......................................................................................... 1595 to 1830
 * parseLogicalOr
 * prattParsing_parseExpression
 *
 * 
 *
 *
 */
