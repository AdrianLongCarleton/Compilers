#include <stdio.h> //#
#include <stdlib.h> //#
#include <stdbool.h> //#
#include <immintrin.h> //#  // SIMD intrinsics (SSE2+)
#include <stdint.h> //#
#include <string.h> //#
#include <time.h> //#
#include <assert.h>

#define DEBUG

typedef struct {
	char *data;
	uint64_t size;
} File;
File *loadFile(const char* filename) {
	FILE *fp = NULL;
	char *buffer = NULL;

	fp = fopen(filename, "rb");
	if (!fp) goto file_not_found;

	// Determine file size
	fseek(fp, 0, SEEK_END);
	const uint64_t length = (uint32_t) ftell(fp);
	rewind(fp);
	
	const uint64_t PAD = 64;
	buffer = malloc((size_t) (length + 1 + PAD));
	if (!buffer) goto malloc_failed;
	
	// Read into buffer
	const uint64_t readCount = (uint32_t) fread(buffer, 1, (size_t) length, fp);
	if (readCount != length) goto read_error;

	// set the last 65 bytes of the buffer to the null terminator so SIMD scans stop safely
	memset(buffer + length, 0, 1 + PAD);

	File *file = malloc(sizeof(File));
	if (!file) goto malloc_failed;

	file->data = buffer;
	file->size = length;
	fclose(fp);

	return file;
// ---- ERROR HANDLERS ----
malloc_failed:
	free(buffer);
	// safe: free(NULL) is no-op
read_error:
	if (fp) fclose(fp);
file_not_found:
	return NULL;
}
void freeFile(File *file) {
	if (!file) return;
	free(file->data);
	free(file);
	file = NULL;
}
char *nextBlock(char c, char *currentChar) {
	const char endChar = c;
	bool escaped = false;
	currentChar += 1;
	c = *currentChar;
	while (c != '\0' && (c != endChar || escaped)) {
		if (c == '\\') {
			escaped = !escaped;
		} else {
			escaped = false;
		}
		currentChar += 1;
		c = *currentChar;
	}
	currentChar += 1;
	return currentChar;
}
static inline uint64_t simd_whitespace(const char *p) {
    const char *start = p;

    const __m128i LIMIT = _mm_set1_epi8(0x21); // <= 0x20
    const __m128i NL    = _mm_set1_epi8('\n');
    const __m128i ZERO  = _mm_setzero_si128();
    const __m128i ALL   = _mm_set1_epi8((char)0xFF);

    for (;;) {
        __m128i v = _mm_loadu_si128((const __m128i *)p);

        __m128i le_20   = _mm_cmplt_epi8(v, LIMIT);
        __m128i not_nl  = _mm_andnot_si128(_mm_cmpeq_epi8(v, NL), ALL);
        __m128i not_0   = _mm_cmpgt_epi8(v, ZERO);

        __m128i is_ws = _mm_and_si128(le_20,
                          _mm_and_si128(not_nl, not_0));

        int mask = _mm_movemask_epi8(is_ws);

        if (mask != 0xFFFF) {
            return (uint64_t)(p - start + __builtin_ctz(~mask));
        }

        p += 16;
    }
}
static inline uint64_t simd_id(const char *p) {
    const char *start = p;

    // ---- scalar first character (identifier start) ----
    unsigned char c = (unsigned char)*p;
    if (!(
        ((c | 0x20) >= 'a' && (c | 0x20) <= 'z') || c == '_'
    )) {
        return 0;
    }

    p++;

    // ---- SIMD constants ----
    const __m128i A_1  = _mm_set1_epi8('a' - 1);
    const __m128i Z_1  = _mm_set1_epi8('z' + 1);
    const __m128i D0_1 = _mm_set1_epi8('0' - 1);
    const __m128i D9_1 = _mm_set1_epi8('9' + 1);
    const __m128i US   = _mm_set1_epi8('_');
    const __m128i OR20 = _mm_set1_epi8(0x20);

    // ---- SIMD loop ----
    for (;;) {
        __m128i v = _mm_loadu_si128((const __m128i *)p);

        // case-fold letters
        __m128i lower = _mm_or_si128(v, OR20);

        // 'a' <= lower <= 'z'
        __m128i is_alpha =
            _mm_and_si128(
                _mm_cmpgt_epi8(lower, A_1),
                _mm_cmplt_epi8(lower, Z_1)
            );

        // '0' <= v <= '9'
        __m128i is_digit =
            _mm_and_si128(
                _mm_cmpgt_epi8(v, D0_1),
                _mm_cmplt_epi8(v, D9_1)
            );

        // v == '_'
        __m128i is_us = _mm_cmpeq_epi8(v, US);

        // identifier continuation
        __m128i is_ident =
            _mm_or_si128(is_alpha,
                _mm_or_si128(is_digit, is_us));

        int mask = _mm_movemask_epi8(is_ident);

        // stop at first non-identifier char
        if (mask != 0xFFFF) {
            return (uint64_t)(p - start + __builtin_ctz(~mask));
        }

        p += 16;
    }
}
static inline uint64_t simd_hex(const char *p) {
    	const char *start = p;
	
    	// ---- scalar first character (identifier start) ----
    unsigned char c = (unsigned char)*p;
    if (!(
	(c >= '0' && c <= '9') ||
	((c | 0x20) >= 'a' && (c | 0x20) <= 'f')
    )) return 0;

    p++;

    // ---- SIMD constants ----
    const __m128i A_1  = _mm_set1_epi8('a' - 1);
    const __m128i Z_1  = _mm_set1_epi8('f' + 1);
    const __m128i D0_1 = _mm_set1_epi8('0' - 1);
    const __m128i D9_1 = _mm_set1_epi8('9' + 1);
    const __m128i OR20 = _mm_set1_epi8(0x20);

    // ---- SIMD loop ----
    for (;;) {
        __m128i v = _mm_loadu_si128((const __m128i *)p);

        // case-fold letters
        __m128i lower = _mm_or_si128(v, OR20);

        // 'a' <= lower <= 'f'
        __m128i is_alpha =
            _mm_and_si128(
                _mm_cmpgt_epi8(lower, A_1),
                _mm_cmplt_epi8(lower, Z_1)
            );

        // '0' <= v <= '9'
        __m128i is_digit =
            _mm_and_si128(
                _mm_cmpgt_epi8(v, D0_1),
                _mm_cmplt_epi8(v, D9_1)
            );

        // identifier continuation
        __m128i is_hex =
            _mm_or_si128(is_alpha,is_digit);

        int mask = _mm_movemask_epi8(is_hex);

        // stop at first non-identifier char
        if (mask != 0xFFFF) {
            return (uint64_t)(p - start + __builtin_ctz(~mask));
        }

        p += 16;
    }
}

static inline uint64_t simd_num(const char *p) {
    const char *start = p;

    // ---- scalar first char (cheap & correct) ----
    unsigned char c = (unsigned char)*p;
    if (c < '0' || c > '9')
        return 0;

    p++;

    // ---- SIMD constants ----
    const __m128i D0_1 = _mm_set1_epi8('0' - 1);
    const __m128i D9_1 = _mm_set1_epi8('9' + 1);

    // ---- SIMD loop ----
    for (;;) {
        __m128i v = _mm_loadu_si128((const __m128i *)p);

        // '0' <= v <= '9'
        __m128i is_digit =
            _mm_and_si128(
                _mm_cmpgt_epi8(v, D0_1),
                _mm_cmplt_epi8(v, D9_1)
            );

        int mask = _mm_movemask_epi8(is_digit);

        // stop at first non-digit
        if (mask != 0xFFFF) {
            return (uint64_t)(p - start + __builtin_ctz(~mask));
        }

        p += 16;
    }
}
static inline uint64_t simd_bin(const char *p) {
    const char *start = p;

    if (*p != '0' && *p != '1')
        return 0;

    p++;

    const __m128i MASK = _mm_set1_epi8((char)~1);
    const __m128i Z    = _mm_set1_epi8('0');

    for (;;) {
        __m128i v = _mm_loadu_si128((const __m128i *)p);

        __m128i masked = _mm_and_si128(v, MASK);
        __m128i is_bin = _mm_cmpeq_epi8(masked, Z);

        int mask = _mm_movemask_epi8(is_bin);

        if (mask != 0xFFFF) {
            return (uint64_t)(p - start + __builtin_ctz(~mask));
        }

        p += 16;
    }
}
static inline uint64_t simd_zero(const char *p) {
    const char *start = p;

    if (*p != '0' )
        return 0;

    p++;

    const __m128i Z    = _mm_set1_epi8('0');

    for (;;) {
        __m128i v = _mm_loadu_si128((const __m128i *)p);

        __m128i is_zero = _mm_cmpeq_epi8(v, Z);

        int mask = _mm_movemask_epi8(is_zero);

        if (mask != 0xFFFF) {
            return (uint64_t)(p - start + __builtin_ctz(~mask));
        }

        p += 16;
    }
}

uint32_t TOKENTYPE_NULL =  0;
uint32_t TOKENTYPE_ID   =  1;
uint32_t TOKENTYPE_NUM  =  2;
uint32_t TOKENTYPE_HEX  =  3;
uint32_t TOKENTYPE_BIN  =  4;
uint32_t TOKENTYPE_FLT  =  5;
uint32_t TOKENTYPE_SYM  =  6;
uint32_t TOKENTYPE_CHR  =  7;
uint32_t TOKENTYPE_STR  =  8;
uint32_t TOKENTYPE_KEY  =  9;
uint32_t TOKENTYPE_EOF  = 10;
uint32_t TOKENTYPE_IF_EXPRESSION = 11;
uint32_t TOKENTYPE_ELSE_EXPRESSION = 12;
uint32_t TOKENTYPE_MATCH_CASE = 13;
uint32_t TOKENTYPE_MATCH_EXPRESSION = 14;
uint32_t TOKENTYPE_EXPRESSION = 15;
uint32_t TOKENTYPE_TYPES = 16;
uint32_t TOKENTYPE_TYPE = 17;
uint32_t TOKENTYPE_FUNCTION_DECLARATION = 18;
uint32_t TOKENTYPE_LAMBDA = 19;
uint32_t TOKENTYPE_FUNCTION = 19;
uint32_t TOKENTYPE_VARIABLE_DECLARATION = 20;
uint32_t TOKENTYPE_KEYWORD_PUBLIC = 21;
uint32_t TOKENTYPE_KEYWORD_PRIVATE = 22;
uint32_t TOKENTYPE_DECLARATION = 23;
uint32_t TOKENTYPE_JUMP_STATEMENT = 24;
uint32_t TOKENTYPE_LOOP_STATEMENT = 25;
uint32_t TOKENTYPE_STATEMENT = 26;
uint32_t TOKENTYPE_STATEMENTS = 27;
uint32_t TOKENTYPE_BLOCK = 28;
uint32_t TOKENTYPE_IMPORT_STATEMENT = 29;

const char *type_name(uint32_t t) {
	if (t == TOKENTYPE_ID) return "ID";
	if (t == TOKENTYPE_NUM) return "NUM";
	if (t == TOKENTYPE_HEX) return "HEX";
	if (t == TOKENTYPE_BIN) return "BIN";
	if (t == TOKENTYPE_FLT) return "FLT";
	if (t == TOKENTYPE_SYM) return "SYM";
	if (t == TOKENTYPE_CHR) return "CHR";
	if (t == TOKENTYPE_STR) return "STR";
	if (t == TOKENTYPE_KEY) return "KEY";
	if (t == TOKENTYPE_EOF) return "EOF";
	if (t == TOKENTYPE_IF_EXPRESSION) return "TOKENTYPE_IF_EXPRESSION";
	if (t == TOKENTYPE_ELSE_EXPRESSION) return "TOKENTYPE_ELSE_EXPRESSION";
	if (t == TOKENTYPE_MATCH_CASE) return "TOKENTYPE_MATCH_CASE";
	if (t == TOKENTYPE_MATCH_EXPRESSION) return "TOKENTYPE_MATCH_EXPRESSION";
	if (t == TOKENTYPE_EXPRESSION) return "TOKENTYPE_EXPRESSION";
	if (t == TOKENTYPE_TYPES) return "TOKENTYPE_TYPES";
	if (t == TOKENTYPE_TYPE) return "TOKENTYPE_TYPE";
	if (t == TOKENTYPE_FUNCTION_DECLARATION) return "TOKENTYPE_FUNCTION_DECLARATION";
	if (t == TOKENTYPE_LAMBDA) return "TOKENTYPE_LAMBDA";
	if (t == TOKENTYPE_FUNCTION) return "TOKENTYPE_FUNCTION";
	if (t == TOKENTYPE_VARIABLE_DECLARATION) return "TOKENTYPE_VARIABLE_DECLARATION";
	if (t == TOKENTYPE_KEYWORD_PUBLIC) return "TOKENTYPE_KEYWORD_PUBLIC";
	if (t == TOKENTYPE_KEYWORD_PRIVATE) return "TOKENTYPE_KEYWORD_PRIVATE";
	if (t == TOKENTYPE_DECLARATION) return "TOKENTYPE_DECLARATION";
	if (t == TOKENTYPE_JUMP_STATEMENT) return "TOKENTYPE_JUMP_STATEMENT";
	if (t == TOKENTYPE_LOOP_STATEMENT) return "TOKENTYPE_LOOP_STATEMENT";
	if (t == TOKENTYPE_STATEMENT) return "TOKENTYPE_STATEMENT";
	if (t == TOKENTYPE_STATEMENTS) return "TOKENTYPE_STATEMENTS";
	if (t == TOKENTYPE_BLOCK) return "TOKENTYPE_BLOCK";
	if (t == TOKENTYPE_IMPORT_STATEMENT) return "TOKENTYPE_IMPORT_STATEMENT";
	return "UNKNOWN";
}

typedef struct {
	char *currentChar;
	char *token;
	uint32_t type;
	uint64_t size;
} Lexer;
void printToken(const Lexer *lexer) {
	printf("TOKEN{.type=%s, .value='", type_name(lexer->type));
	for (uint64_t i = 0; i < lexer->size; i++) {
		switch (lexer->token[i]) {
		case '\n': printf("\\n"); break;
		case '\t': printf("\\t"); break;
		case '\r': printf("\\r"); break;
		case '\v': printf("\\v"); break;
		case '\f': printf("\\f"); break;
		case '\\': printf("\\\\"); break;
		default:
			putchar(lexer->token[i]);
		}

	}
	printf("'}\n");
}
#ifdef DEBUG
void lex(Lexer *lexer);
void nextToken(Lexer *lexer)
{
	lex(lexer);
	printToken(lexer);
}
void lex(Lexer *lexer) {
#else
void nextToken(Lexer *lexer) {
#endif

	lexer->type = TOKENTYPE_EOF;
	lexer->size = 0;
	char nextChar;
	// skip the white space
	lexer->currentChar += simd_whitespace(lexer->currentChar);
	while (true) {
		char c = *lexer->currentChar;
		lexer->token = lexer->currentChar;
		switch(c) {
			case '\0':
				return;
				//,{}()[].;~\n\r|&+-><=*!/%^\?%`^
				//{ and }
				//( and )
				//[ and ]
				//. and .. and ; and ~ and \n and ,
				//| and || and |=
				//& and && and &=
				//+ and ++ and +=
				//- and -- and -=
				//> and >> and >= 
				//< and << and <= 
				
				//= and ==
				//! and !=
				//* and *=
				//'/' and /=
				//% and %=
				//^ and ^=
			case '=':
			case '!':
			case '*':
			case '/':
			case '%':
			case '^':
				lexer->type = TOKENTYPE_SYM;
				lexer->currentChar += 1;
				nextChar = *lexer->currentChar; 
				if (nextChar == '=') {
					lexer->currentChar += 1;
					lexer->size = 2;
					return;
				}
				lexer->size = 1;
				return;
			case '|':
			case '&':
			case '+':
			case '-':
			case '<':
			case '>':
				lexer->type = TOKENTYPE_SYM;
				lexer->currentChar += 1;
				nextChar = *lexer->currentChar; 
				if (nextChar == c || nextChar == '=') {
					lexer->currentChar += 1;
					lexer->size = 2;
					return;
				}
				lexer->size = 1;
				return;
			case '.':
				lexer->type = TOKENTYPE_SYM;
				lexer->currentChar += 1;
				if (*lexer->currentChar == '.') {
					lexer->currentChar += 1;
					lexer->size = 2;
					return;
				}
				lexer->size = 1;
				return;
			case '{':
			case '}':
			case '(':
			case ')':
			case '[':
			case ']':
			case '~':
			case ';':
			case ',':
			case '\n':
				lexer->type = TOKENTYPE_SYM;
				lexer->currentChar += 1;
				lexer->size = 1;
				return;
			case '"':
				lexer->currentChar = nextBlock(c,lexer->currentChar);
				lexer->type = TOKENTYPE_STR;
				lexer->size = lexer->currentChar - lexer->token;
				return;
			case '\'':
				lexer->currentChar = nextBlock(c,lexer->currentChar);
				lexer->type = TOKENTYPE_CHR;
				lexer->size = lexer->currentChar - lexer->token;
				return;
			case '#':
				lexer->currentChar = nextBlock(c,lexer->currentChar);
				lexer->type = TOKENTYPE_EOF;
				break;
			case '0':
				// skip over all unessesary zeros

				lexer->currentChar += simd_zero(lexer->currentChar);
				// check the character after the zeros
				c = *lexer->currentChar;
				switch(c) {
					// 0x <- hexidecimal
					case 'x':
					case 'X':
						lexer->currentChar += 1; // consume x
						lexer->currentChar += simd_hex(lexer->currentChar);
						lexer->type = TOKENTYPE_HEX;
						lexer->size = lexer->currentChar - lexer->token;
						return;
					// 0b <- binary
					case 'b':
					case 'B':
						lexer->currentChar += 1; // consume b
						lexer->currentChar += simd_bin(lexer->currentChar);
						lexer->type = TOKENTYPE_BIN;
						lexer->size = lexer->currentChar - lexer->token;
						return;
					// 0(1-9) <- the zero was irrelevant loop again.
					case '1':
					case '2':
					case '3':
					case '4':
					case '5':
					case '6':
					case '7':
					case '8':
					case '9':
						break;
					// 0(!(x|X|b|B|1-9)) <- the zero is just the number zero.
					default:
						lexer->type = TOKENTYPE_NUM;
						return;
				}
				break;
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				lexer->type = TOKENTYPE_NUM;
				lexer->currentChar += simd_num(lexer->currentChar);
				if (*lexer->currentChar != '.')  {
					lexer->size = lexer->currentChar - lexer->token;
					return;
				}
				lexer->currentChar += 1;
				c = *lexer->currentChar;

				if (c < '0' || c > '9') {
					lexer->currentChar -= 1;
					lexer->size = lexer->currentChar - lexer->token;
					return;
				}

				lexer->currentChar += simd_num(lexer->currentChar);
				lexer->type = TOKENTYPE_FLT;
				lexer->size = lexer->currentChar - lexer->token;
				return;
			default:
				if (!(('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z'))) {

					lexer->currentChar += 1;
					break;
				}
				lexer->currentChar += simd_id(lexer->currentChar);
				lexer->type = TOKENTYPE_ID;
				lexer->size = lexer->currentChar - lexer->token;
				return;
		}
	}
}



static inline long long now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

uint32_t AST_NODE_TYPE_NULL = 0;
uint32_t AST_NODE_TYPE_TERMINAL = 1;
uint32_t AST_NODE_TYPE_NON_TERMINAL = 2;
typedef struct {
        char *token;
        uint64_t tokenSize;
        
	uint64_t size;

        uint32_t astNodeType;
        uint32_t tokenType;
} AstNode;
typedef struct {
        AstNode *nodes;
        uint64_t capacity;
        uint64_t count;
} AST;

AST *AST_create() {
	AST *ast = malloc(sizeof(AST));
	if(!ast) return NULL;
	ast->capacity = 8192;
	ast->nodes = malloc(sizeof(AstNode) * ast->capacity);
	ast->count = 0;	
	return ast;
}
void AST_destroy(AST *ast) {
	if (!ast) return;
	free(ast->nodes);
	free(ast);
	ast = NULL;
}
bool AST_addNode(AST *ast, AstNode node) {
	if (!ast) return false;
	if (ast->count >= ast->capacity) {
		ast->capacity *= 2;
		if (ast->capacity > SIZE_MAX / sizeof *ast->nodes) return false;
		AstNode *nodes = realloc(ast->nodes, ast->capacity * sizeof *ast->nodes);
		if (!nodes) return false;
		ast->nodes = nodes;
	}
	ast->nodes[ast->count++] = node;
	return true;
}
bool AST_resize(AST *ast) {
    if (ast->capacity > SIZE_MAX / 2) return false;

    size_t new_cap = ast->capacity * 2;
    if (new_cap > SIZE_MAX / sizeof *ast->nodes) return false;

    AstNode *nodes = realloc(ast->nodes, new_cap * sizeof *ast->nodes);
    if (!nodes) return false;

    ast->nodes = nodes;
    ast->capacity = new_cap;
    return true;
}

bool AST_createNode(AST *ast) {
        if (!ast) return false;
        if (ast->count >= ast->capacity) {
		if (!AST_resize(ast)) return false;
	} 

	ast->nodes[ast->count] = (AstNode){0};
	ast->count++;
        return true;
}
bool AST_insertNode(AST *ast, AstNode node, uint64_t index) {
    if (!ast) return false;
    if (index > ast->count) return false;

    if (ast->count >= ast->capacity) {
        if (!AST_resize(ast)) return false;
    }

    memmove(&ast->nodes[index + 1],
            &ast->nodes[index],
            (ast->count - index) * sizeof *ast->nodes);

    ast->nodes[index] = node;
    ast->count++;
    return true;
}

static AstNode *AST_getNode(AST *ast) {
	if (ast->count == 0) AST_createNode(ast);
	return &ast->nodes[ast->count - 1];
}
static void AST_createNonTerminalNode(AST *ast, AstNode *current, uint32_t tokenType) {
	current->astNodeType = AST_NODE_TYPE_NON_TERMINAL;
	current->tokenType = tokenType;
	current->size = 1;
	AST_createNode(ast);
}
static void AST_createTerminalNode(AST *ast, AstNode *current, uint32_t tokenType, char *token, uint64_t tokenSize) {
	current->astNodeType = AST_NODE_TYPE_TERMINAL;
	current->tokenType = tokenType;

	current->token = token;
	current->tokenSize = tokenSize;

	AST_createNode(ast);
}
uint64_t parseBlock(Lexer *lexer, AST *ast, bool *parseError); 
uint64_t parseExpression(Lexer *lexer, AST *ast, bool *parseError, uint32_t flag);
#ifdef DEBUG
uint64_t debug_parseIfExpression(Lexer* lexer, AST *ast, bool *parseError);
uint64_t parseIfExpression(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing IfExpression...\n");
	const uint64_t size = debug_parseIfExpression(lexer,ast,parseError);
	printf("Reducing IfExpression\n");
	return size;
}
uint64_t debug_parseIfExpression(Lexer* lexer, AST *ast, bool *parseError) {
#else

uint64_t parseIfExpression(Lexer* lexer, AST *ast, bool *parseError) {
#endif

	AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_IF_EXPRESSION);

	current->size += 1;
	current->size += parseExpression(lexer,ast,parseError,0);
	if (*parseError) return current->size;
	current->size += parseBlock(lexer,ast,parseError);
	if (*parseError) return current->size;
	Lexer temp = *lexer;
	nextToken(&temp);
	if (temp.type == TOKENTYPE_ID && temp.size == 5 && memcmp(temp.token,"else",4) == 0) {
		*lexer = temp;
		nextToken(lexer);
		AstNode *elseBranch = AST_getNode(ast);
		AST_createNonTerminalNode(ast,elseBranch,TOKENTYPE_ELSE_EXPRESSION);
		elseBranch->size = parseBlock(lexer,ast,parseError);
		if (*parseError) return current->size;
		current->size += elseBranch->size;
	}
	return current->size;
}


uint64_t parseJumpStatement(Lexer *lexer, AST *ast, bool *parseError);

#ifdef DEBUG
uint64_t debug_parseMatchCase(Lexer* lexer, AST *ast, bool *parseError);
uint64_t parseMatchCase(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing MatchCase...\n");
	const uint64_t size = debug_parseMatchCase(lexer,ast,parseError);
	printf("Reducing MatchCase\n");
	return size;
}
uint64_t debug_parseMatchCase(Lexer* lexer, AST *ast, bool *parseError) {
#else

uint64_t parseMatchCase(Lexer* lexer, AST *ast, bool *parseError) {
#endif
	AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_MATCH_CASE);

	if (lexer->type == TOKENTYPE_SYM) {
		*parseError = true;
		return 0;
	}

	nextToken(lexer);
	current->size += parseExpression(lexer,ast,parseError,0);
	if (*parseError) return current->size;
	
	if (lexer->type != TOKENTYPE_SYM && lexer->size == 1 && *(lexer->token) == ':') {
		*parseError = true;
		return current->size;
	}
	nextToken(lexer);


	const char *token = lexer->token;
	if (lexer->type == TOKENTYPE_ID) {
		switch(lexer->size) {
			case 2:
				if (token[0] == 'i' && token[1] == 'f') {
					current->size += parseExpression(lexer,ast,parseError,1);
				}
				break;
			case 4:
				if (memcmp(token,"loop",4) == 0) {
					current->size += parseJumpStatement(lexer,ast,parseError);
				}
				break;
			case 5:
				if (memcmp(token,"break",5) == 0 || memcmp(token,"yield",5) == 0) {
					current->size += parseJumpStatement(lexer,ast,parseError);
				} else if (memcmp(token,"match",5) == 0) {
					current->size += parseExpression(lexer,ast,parseError,2);
				}
				break;
			case 6: 
				if(memcmp(token,"return",6) == 0) {
					current->size += parseJumpStatement(lexer,ast,parseError);
				}
				break;
			default:
				current->size += parseExpression(lexer,ast,parseError,0);
		}
	} else if (lexer->type == TOKENTYPE_SYM && lexer->size == 1 && *token == '{') {
		current->size += parseBlock(lexer,ast,parseError);
	} else {
		current->size += parseExpression(lexer,ast,parseError,0);
	}
	return current->size;
}

#ifdef DEBUG
uint64_t debug_parseMatchExpression(Lexer* lexer, AST *ast, bool *parseError);
uint64_t parseMatchExpression(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing MatchExpression...\n");
	const uint64_t size = debug_parseMatchExpression(lexer,ast,parseError);
	printf("Reducing MatchExpression\n");
	return size;
}
uint64_t debug_parseMatchExpression(Lexer* lexer, AST *ast, bool *parseError) {
#else

uint64_t parseMatchExpression(Lexer* lexer, AST *ast, bool *parseError) {
#endif
	AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_MATCH_EXPRESSION);
	current->size += parseExpression(lexer,ast,parseError,0);

	if (lexer->type != TOKENTYPE_SYM && lexer->size != 1 && *(lexer->token) != '{') {
		*parseError = true;
		return 0;
	}
	while(true) {
		current->size += parseMatchCase(lexer,ast,parseError);
		if (parseError) return current->size;
		nextToken(lexer);
		if (lexer->type == TOKENTYPE_EOF) {
			*parseError = true;
			return current->size;
		}
		char c = *(lexer->token);
		if (c != '\n' && c != '}') {
			*parseError = true;
			return current->size;
		}
		nextToken(lexer);
		if (*(lexer->token) == '}') break;
	}
	return current->size;
}

uint32_t PARSE_EXPRESSION_DEFAULT = 0;
uint32_t PARSE_EXPRESSION_ID 	  = 1;
uint32_t PARSE_EXPRESSION_MATCH   = 2;
uint32_t PARSE_EXPRESSION_IF 	  = 3;





#ifdef DEBUG
uint64_t debug_parse(Lexer* lexer, AST *ast, bool *parseError);
uint64_t parse(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing ...\n");
	const uint64_t size = debug_parse(lexer,ast,parseError);
	printf("Reducing \n");
	return size;
}
uint64_t debug_parse(Lexer* lexer, AST *ast, bool *parseError) {
#else

uint64_t parse(Lexer* lexer, AST *ast, bool *parseError) {
#endif
}

static const uint8_t prefixBindingPower[235] {
	0, // 0: ' !'
	0, // 1: UNDEFINED
	0, // 2: UNDEFINED
	0, // 3: '*='
	0, // 4: ' %'
	0, // 5: ' &'
	0, // 6: '++'
	0, // 7: ' ('
	0, // 8: ' )'
	0, // 9: ' *'
	0, // 10: ' +'
	0, // 11: ' ,' 
	0, // 12: ' -'
	0, // 13: ' .'
	0, // 14: ' /'
	0, // 15: UNDEFINED
	0, // 16: UNDEFINED
	0, // 17: UNDEFINED
	0, // 18: UNDEFINED
	0, // 19: UNDEFINED
	0, // 20: UNDEFINED
	0, // 21: UNDEFINED
	0, // 22: UNDEFINED
	0, // 23: UNDEFINED
	0, // 24: '+='
	0, // 25: UNDEFINED
	0, // 26: ' ;'
	0, // 27: ' <'
	0, // 28: ' ='
	0, // 29: ' >'
	0, // 30: UNDEFINED
	0, // 31: UNDEFINED
	0, // 32: UNDEFINED
	0, // 33: UNDEFINED
	0, // 34: UNDEFINED
	0, // 35: UNDEFINED
	0, // 36: UNDEFINED
	0, // 37: UNDEFINED
	0, // 38: UNDEFINED
	0, // 39: UNDEFINED
	0, // 40: UNDEFINED
	0, // 41: UNDEFINED
	0, // 42: UNDEFINED
	0, // 43: UNDEFINED
	0, // 44: UNDEFINED
	0, // 45: UNDEFINED
	0, // 46: UNDEFINED
	0, // 47: UNDEFINED
	0, // 48: UNDEFINED
	0, // 49: '!='
	0, // 50: '--'
	0, // 51: UNDEFINED
	0, // 52: UNDEFINED
	0, // 53: UNDEFINED
	0, // 54: UNDEFINED
	0, // 55: UNDEFINED
	0, // 56: UNDEFINED
	0, // 57: UNDEFINED
	0, // 58: ' ['
	0, // 59: UNDEFINED
	0, // 60: ' ]'
	0, // 61: ' ^'
	0, // 62: UNDEFINED
	0, // 63: UNDEFINED
	0, // 64: UNDEFINED
	0, // 65: UNDEFINED
	0, // 66: '-='
	0, // 67: UNDEFINED
	0, // 68: UNDEFINED
	0, // 69: UNDEFINED
	0, // 70: UNDEFINED
	0, // 71: UNDEFINED
	0, // 72: '..'
	0, // 73: UNDEFINED
	0, // 74: UNDEFINED
	0, // 75: UNDEFINED
	0, // 76: UNDEFINED
	0, // 77: UNDEFINED
	0, // 78: UNDEFINED
	0, // 79: UNDEFINED
	0, // 80: '|='
	0, // 81: UNDEFINED
	0, // 82: UNDEFINED
	0, // 83: UNDEFINED
	0, // 84: UNDEFINED
	0, // 85: UNDEFINED
	0, // 86: UNDEFINED
	0, // 87: UNDEFINED
	0, // 88: UNDEFINED
	0, // 89: UNDEFINED
	0, // 90: ' {'
	0, // 91: ' |'
	0, // 92: ' }'
	0, // 93: ' ~'
	0, // 94: UNDEFINED
	0, // 95: UNDEFINED
	0, // 96: UNDEFINED
	0, // 97: UNDEFINED
	0, // 98: UNDEFINED
	0, // 99: UNDEFINED
	0, // 100: UNDEFINED
	0, // 101: UNDEFINED
	0, // 102: UNDEFINED
	0, // 103: UNDEFINED
	0, // 104: UNDEFINED
	0, // 105: UNDEFINED
	0, // 106: UNDEFINED
	0, // 107: UNDEFINED
	0, // 108: '/='
	0, // 109: UNDEFINED
	0, // 110: UNDEFINED
	0, // 111: UNDEFINED
	0, // 112: UNDEFINED
	0, // 113: UNDEFINED
	0, // 114: UNDEFINED
	0, // 115: UNDEFINED
	0, // 116: UNDEFINED
	0, // 117: UNDEFINED
	0, // 118: UNDEFINED
	0, // 119: UNDEFINED
	0, // 120: UNDEFINED
	0, // 121: UNDEFINED
	0, // 122: UNDEFINED
	0, // 123: UNDEFINED
	0, // 124: UNDEFINED
	0, // 125: UNDEFINED
	0, // 126: UNDEFINED
	0, // 127: UNDEFINED
	0, // 128: UNDEFINED
	0, // 129: UNDEFINED
	0, // 130: UNDEFINED
	0, // 131: '&&'
	0, // 132: UNDEFINED
	0, // 133: '%='
	0, // 134: UNDEFINED
	0, // 135: UNDEFINED
	0, // 136: UNDEFINED
	0, // 137: UNDEFINED
	0, // 138: UNDEFINED
	0, // 139: UNDEFINED
	0, // 140: UNDEFINED
	0, // 141: UNDEFINED
	0, // 142: UNDEFINED
	0, // 143: '||'
	0, // 144: UNDEFINED
	0, // 145: '<<'
	0, // 146: '<='
	0, // 147: UNDEFINED
	0, // 148: UNDEFINED
	0, // 149: UNDEFINED
	0, // 150: UNDEFINED
	0, // 151: UNDEFINED
	0, // 152: UNDEFINED
	0, // 153: UNDEFINED
	0, // 154: '&='
	0, // 155: '^='
	0, // 156: UNDEFINED
	0, // 157: UNDEFINED
	0, // 158: UNDEFINED
	0, // 159: UNDEFINED
	0, // 160: UNDEFINED
	0, // 161: UNDEFINED
	0, // 162: UNDEFINED
	0, // 163: UNDEFINED
	0, // 164: UNDEFINED
	0, // 165: UNDEFINED
	0, // 166: UNDEFINED
	0, // 167: '=='
	0, // 168: UNDEFINED
	0, // 169: UNDEFINED
	0, // 170: UNDEFINED
	0, // 171: UNDEFINED
	0, // 172: UNDEFINED
	0, // 173: UNDEFINED
	0, // 174: UNDEFINED
	0, // 175: UNDEFINED
	0, // 176: UNDEFINED
	0, // 177: UNDEFINED
	0, // 178: UNDEFINED
	0, // 179: UNDEFINED
	0, // 180: UNDEFINED
	0, // 181: UNDEFINED
	0, // 182: UNDEFINED
	0, // 183: UNDEFINED
	0, // 184: UNDEFINED
	0, // 185: UNDEFINED
	0, // 186: UNDEFINED
	0, // 187: UNDEFINED
	0, // 188: '>='
	0, // 189: '>>'
	0, // 190: UNDEFINED
	0, // 191: UNDEFINED
	0, // 192: UNDEFINED
	0, // 193: UNDEFINED
	0, // 194: UNDEFINED
	0, // 195: UNDEFINED
	0, // 196: UNDEFINED
	0, // 197: UNDEFINED
	0, // 198: UNDEFINED
	0, // 199: UNDEFINED
	0, // 200: UNDEFINED
	0, // 201: UNDEFINED
	0, // 202: UNDEFINED
	0, // 203: UNDEFINED
	0, // 204: UNDEFINED
	0, // 205: UNDEFINED
	0, // 206: UNDEFINED
	0, // 207: UNDEFINED
	0, // 208: UNDEFINED
	0, // 209: UNDEFINED
	0, // 210: UNDEFINED
	0, // 211: UNDEFINED
	0, // 212: ' \n'
	0, // 213: UNDEFINED
	0, // 214: UNDEFINED
	0, // 215: UNDEFINED
	0, // 216: UNDEFINED
	0, // 217: UNDEFINED
	0, // 218: UNDEFINED
	0, // 219: UNDEFINED
	0, // 220: UNDEFINED
	0, // 221: UNDEFINED
	0, // 222: UNDEFINED
	0, // 223: UNDEFINED
	0, // 224: UNDEFINED
	0, // 225: UNDEFINED
	0, // 226: UNDEFINED
	0, // 227: UNDEFINED
	0, // 228: UNDEFINED
	0, // 229: UNDEFINED
	0, // 230: UNDEFINED
	0, // 231: UNDEFINED
	0, // 232: UNDEFINED
	0, // 233: UNDEFINED
	0, // 234: UNDEFINED
}

static const uint8_t postfixBindingPower[235] {
        0, // 0: ' !'
	0, // 1: UNDEFINED
	0, // 2: UNDEFINED
	0, // 3: '*='
	0, // 4: ' %'
	0, // 5: ' &'
	0, // 6: '++'
	0, // 7: ' ('
	0, // 8: ' )'
	0, // 9: ' *'
	0, // 10: ' +'
	0, // 11: UNDEFINED
	0, // 12: ' -'
	0, // 13: ' .'
	0, // 14: ' /'
	0, // 15: UNDEFINED
	0, // 16: UNDEFINED
	0, // 17: UNDEFINED
	0, // 18: UNDEFINED
	0, // 19: UNDEFINED
	0, // 20: UNDEFINED
	0, // 21: UNDEFINED
	0, // 22: UNDEFINED
	0, // 23: UNDEFINED
	0, // 24: '+='
	0, // 25: UNDEFINED
	0, // 26: ' ;'
	0, // 27: ' <'
	0, // 28: ' ='
	0, // 29: ' >'
	0, // 30: UNDEFINED
	0, // 31: UNDEFINED
	0, // 32: UNDEFINED
	0, // 33: UNDEFINED
	0, // 34: UNDEFINED
	0, // 35: UNDEFINED
	0, // 36: UNDEFINED
	0, // 37: UNDEFINED
	0, // 38: UNDEFINED
	0, // 39: UNDEFINED
	0, // 40: UNDEFINED
	0, // 41: UNDEFINED
	0, // 42: UNDEFINED
	0, // 43: UNDEFINED
	0, // 44: UNDEFINED
	0, // 45: UNDEFINED
	0, // 46: UNDEFINED
	0, // 47: UNDEFINED
	0, // 48: UNDEFINED
	0, // 49: '!='
	0, // 50: '--'
	0, // 51: UNDEFINED
	0, // 52: UNDEFINED
	0, // 53: UNDEFINED
	0, // 54: UNDEFINED
	0, // 55: UNDEFINED
	0, // 56: UNDEFINED
	0, // 57: UNDEFINED
	0, // 58: ' ['
	0, // 59: UNDEFINED
	0, // 60: ' ]'
	0, // 61: ' ^'
	0, // 62: UNDEFINED
	0, // 63: UNDEFINED
	0, // 64: UNDEFINED
	0, // 65: UNDEFINED
	0, // 66: '-='
	0, // 67: UNDEFINED
	0, // 68: UNDEFINED
	0, // 69: UNDEFINED
	0, // 70: UNDEFINED
	0, // 71: UNDEFINED
	0, // 72: '..'
	0, // 73: UNDEFINED
	0, // 74: UNDEFINED
	0, // 75: UNDEFINED
	0, // 76: UNDEFINED
	0, // 77: UNDEFINED
	0, // 78: UNDEFINED
	0, // 79: UNDEFINED
	0, // 80: '|='
	0, // 81: UNDEFINED
	0, // 82: UNDEFINED
	0, // 83: UNDEFINED
	0, // 84: UNDEFINED
	0, // 85: UNDEFINED
	0, // 86: UNDEFINED
	0, // 87: UNDEFINED
	0, // 88: UNDEFINED
	0, // 89: UNDEFINED
	0, // 90: ' {'
	0, // 91: ' |'
	0, // 92: ' }'
	0, // 93: ' ~'
	0, // 94: UNDEFINED
	0, // 95: UNDEFINED
	0, // 96: UNDEFINED
	0, // 97: UNDEFINED
	0, // 98: UNDEFINED
	0, // 99: UNDEFINED
	0, // 100: UNDEFINED
	0, // 101: UNDEFINED
	0, // 102: UNDEFINED
	0, // 103: UNDEFINED
	0, // 104: UNDEFINED
	0, // 105: UNDEFINED
	0, // 106: UNDEFINED
	0, // 107: UNDEFINED
	0, // 108: '/='
	0, // 109: UNDEFINED
	0, // 110: UNDEFINED
	0, // 111: UNDEFINED
	0, // 112: UNDEFINED
	0, // 113: UNDEFINED
	0, // 114: UNDEFINED
	0, // 115: UNDEFINED
	0, // 116: UNDEFINED
	0, // 117: UNDEFINED
	0, // 118: UNDEFINED
	0, // 119: UNDEFINED
	0, // 120: UNDEFINED
	0, // 121: UNDEFINED
	0, // 122: UNDEFINED
	0, // 123: UNDEFINED
	0, // 124: UNDEFINED
	0, // 125: UNDEFINED
	0, // 126: UNDEFINED
	0, // 127: UNDEFINED
	0, // 128: UNDEFINED
	0, // 129: UNDEFINED
	0, // 130: UNDEFINED
	0, // 131: '&&'
	0, // 132: UNDEFINED
	0, // 133: '%='
	0, // 134: UNDEFINED
	0, // 135: UNDEFINED
	0, // 136: UNDEFINED
	0, // 137: UNDEFINED
	0, // 138: UNDEFINED
	0, // 139: UNDEFINED
	0, // 140: UNDEFINED
	0, // 141: UNDEFINED
	0, // 142: UNDEFINED
	0, // 143: '||'
	0, // 144: UNDEFINED
	0, // 145: '<<'
	0, // 146: '<='
	0, // 147: UNDEFINED
	0, // 148: UNDEFINED
	0, // 149: UNDEFINED
	0, // 150: UNDEFINED
	0, // 151: UNDEFINED
	0, // 152: UNDEFINED
	0, // 153: UNDEFINED
	0, // 154: '&='
	0, // 155: '^='
	0, // 156: UNDEFINED
	0, // 157: UNDEFINED
	0, // 158: UNDEFINED
	0, // 159: UNDEFINED
	0, // 160: UNDEFINED
	0, // 161: UNDEFINED
	0, // 162: UNDEFINED
	0, // 163: UNDEFINED
	0, // 164: UNDEFINED
	0, // 165: UNDEFINED
	0, // 166: UNDEFINED
	0, // 167: '=='
	0, // 168: UNDEFINED
	0, // 169: UNDEFINED
	0, // 170: UNDEFINED
	0, // 171: UNDEFINED
	0, // 172: UNDEFINED
	0, // 173: UNDEFINED
	0, // 174: UNDEFINED
	0, // 175: UNDEFINED
	0, // 176: UNDEFINED
	0, // 177: UNDEFINED
	0, // 178: UNDEFINED
	0, // 179: UNDEFINED
	0, // 180: UNDEFINED
	0, // 181: UNDEFINED
	0, // 182: UNDEFINED
	0, // 183: UNDEFINED
	0, // 184: UNDEFINED
	0, // 185: UNDEFINED
	0, // 186: UNDEFINED
	0, // 187: UNDEFINED
	0, // 188: '>='
	0, // 189: '>>'
	0, // 190: UNDEFINED
	0, // 191: UNDEFINED
	0, // 192: UNDEFINED
	0, // 193: UNDEFINED
	0, // 194: UNDEFINED
	0, // 195: UNDEFINED
	0, // 196: UNDEFINED
	0, // 197: UNDEFINED
	0, // 198: UNDEFINED
	0, // 199: UNDEFINED
	0, // 200: UNDEFINED
	0, // 201: UNDEFINED
	0, // 202: UNDEFINED
	0, // 203: UNDEFINED
	0, // 204: UNDEFINED
	0, // 205: UNDEFINED
	0, // 206: UNDEFINED
	0, // 207: UNDEFINED
	0, // 208: UNDEFINED
	0, // 209: UNDEFINED
	0, // 210: UNDEFINED
	0, // 211: UNDEFINED
	0, // 212: ' \n'
	0, // 213: UNDEFINED
	0, // 214: UNDEFINED
	0, // 215: UNDEFINED
	0, // 216: UNDEFINED
	0, // 217: UNDEFINED
	0, // 218: UNDEFINED
	0, // 219: UNDEFINED
	0, // 220: UNDEFINED
	0, // 221: UNDEFINED
	0, // 222: UNDEFINED
	0, // 223: UNDEFINED
	0, // 224: UNDEFINED
	0, // 225: UNDEFINED
	0, // 226: UNDEFINED
	0, // 227: UNDEFINED
	0, // 228: UNDEFINED
	0, // 229: UNDEFINED
	0, // 230: UNDEFINED
	0, // 231: UNDEFINED
	0, // 232: UNDEFINED
	0, // 233: UNDEFINED
	0, // 234: UNDEFINED
}

static const uint8_t leftInfixBindingPower[235] {
	0, // 0: ' !'
	0, // 1: UNDEFINED
	0, // 2: UNDEFINED
	0, // 3: '*='
	0, // 4: ' %'
	0, // 5: ' &'
	0, // 6: '++'
	0, // 7: ' ('
	0, // 8: ' )'
	0, // 9: ' *'
	0, // 10: ' +'
	0, // 11: UNDEFINED
	0, // 12: ' -'
	0, // 13: ' .'
	0, // 14: ' /'
	0, // 15: UNDEFINED
	0, // 16: UNDEFINED
	0, // 17: UNDEFINED
	0, // 18: UNDEFINED
	0, // 19: UNDEFINED
	0, // 20: UNDEFINED
	0, // 21: UNDEFINED
	0, // 22: UNDEFINED
	0, // 23: UNDEFINED
	0, // 24: '+='
	0, // 25: UNDEFINED
	0, // 26: ' ;'
	0, // 27: ' <'
	0, // 28: ' ='
	0, // 29: ' >'
	0, // 30: UNDEFINED
	0, // 31: UNDEFINED
	0, // 32: UNDEFINED
	0, // 33: UNDEFINED
	0, // 34: UNDEFINED
	0, // 35: UNDEFINED
	0, // 36: UNDEFINED
	0, // 37: UNDEFINED
	0, // 38: UNDEFINED
	0, // 39: UNDEFINED
	0, // 40: UNDEFINED
	0, // 41: UNDEFINED
	0, // 42: UNDEFINED
	0, // 43: UNDEFINED
	0, // 44: UNDEFINED
	0, // 45: UNDEFINED
	0, // 46: UNDEFINED
	0, // 47: UNDEFINED
	0, // 48: UNDEFINED
	0, // 49: '!='
	0, // 50: '--'
	0, // 51: UNDEFINED
	0, // 52: UNDEFINED
	0, // 53: UNDEFINED
	0, // 54: UNDEFINED
	0, // 55: UNDEFINED
	0, // 56: UNDEFINED
	0, // 57: UNDEFINED
	0, // 58: ' ['
	0, // 59: UNDEFINED
	0, // 60: ' ]'
	0, // 61: ' ^'
	0, // 62: UNDEFINED
	0, // 63: UNDEFINED
	0, // 64: UNDEFINED
	0, // 65: UNDEFINED
	0, // 66: '-='
	0, // 67: UNDEFINED
	0, // 68: UNDEFINED
	0, // 69: UNDEFINED
	0, // 70: UNDEFINED
	0, // 71: UNDEFINED
	0, // 72: '..'
	0, // 73: UNDEFINED
	0, // 74: UNDEFINED
	0, // 75: UNDEFINED
	0, // 76: UNDEFINED
	0, // 77: UNDEFINED
	0, // 78: UNDEFINED
	0, // 79: UNDEFINED
	0, // 80: '|='
	0, // 81: UNDEFINED
	0, // 82: UNDEFINED
	0, // 83: UNDEFINED
	0, // 84: UNDEFINED
	0, // 85: UNDEFINED
	0, // 86: UNDEFINED
	0, // 87: UNDEFINED
	0, // 88: UNDEFINED
	0, // 89: UNDEFINED
	0, // 90: ' {'
	0, // 91: ' |'
	0, // 92: ' }'
	0, // 93: ' ~'
	0, // 94: UNDEFINED
	0, // 95: UNDEFINED
	0, // 96: UNDEFINED
	0, // 97: UNDEFINED
	0, // 98: UNDEFINED
	0, // 99: UNDEFINED
	0, // 100: UNDEFINED
	0, // 101: UNDEFINED
	0, // 102: UNDEFINED
	0, // 103: UNDEFINED
	0, // 104: UNDEFINED
	0, // 105: UNDEFINED
	0, // 106: UNDEFINED
	0, // 107: UNDEFINED
	0, // 108: '/='
	0, // 109: UNDEFINED
	0, // 110: UNDEFINED
	0, // 111: UNDEFINED
	0, // 112: UNDEFINED
	0, // 113: UNDEFINED
	0, // 114: UNDEFINED
	0, // 115: UNDEFINED
	0, // 116: UNDEFINED
	0, // 117: UNDEFINED
	0, // 118: UNDEFINED
	0, // 119: UNDEFINED
	0, // 120: UNDEFINED
	0, // 121: UNDEFINED
	0, // 122: UNDEFINED
	0, // 123: UNDEFINED
	0, // 124: UNDEFINED
	0, // 125: UNDEFINED
	0, // 126: UNDEFINED
	0, // 127: UNDEFINED
	0, // 128: UNDEFINED
	0, // 129: UNDEFINED
	0, // 130: UNDEFINED
	0, // 131: '&&'
	0, // 132: UNDEFINED
	0, // 133: '%='
	0, // 134: UNDEFINED
	0, // 135: UNDEFINED
	0, // 136: UNDEFINED
	0, // 137: UNDEFINED
	0, // 138: UNDEFINED
	0, // 139: UNDEFINED
	0, // 140: UNDEFINED
	0, // 141: UNDEFINED
	0, // 142: UNDEFINED
	0, // 143: '||'
	0, // 144: UNDEFINED
	0, // 145: '<<'
	0, // 146: '<='
	0, // 147: UNDEFINED
	0, // 148: UNDEFINED
	0, // 149: UNDEFINED
	0, // 150: UNDEFINED
	0, // 151: UNDEFINED
	0, // 152: UNDEFINED
	0, // 153: UNDEFINED
	0, // 154: '&='
	0, // 155: '^='
	0, // 156: UNDEFINED
	0, // 157: UNDEFINED
	0, // 158: UNDEFINED
	0, // 159: UNDEFINED
	0, // 160: UNDEFINED
	0, // 161: UNDEFINED
	0, // 162: UNDEFINED
	0, // 163: UNDEFINED
	0, // 164: UNDEFINED
	0, // 165: UNDEFINED
	0, // 166: UNDEFINED
	0, // 167: '=='
	0, // 168: UNDEFINED
	0, // 169: UNDEFINED
	0, // 170: UNDEFINED
	0, // 171: UNDEFINED
	0, // 172: UNDEFINED
	0, // 173: UNDEFINED
	0, // 174: UNDEFINED
	0, // 175: UNDEFINED
	0, // 176: UNDEFINED
	0, // 177: UNDEFINED
	0, // 178: UNDEFINED
	0, // 179: UNDEFINED
	0, // 180: UNDEFINED
	0, // 181: UNDEFINED
	0, // 182: UNDEFINED
	0, // 183: UNDEFINED
	0, // 184: UNDEFINED
	0, // 185: UNDEFINED
	0, // 186: UNDEFINED
	0, // 187: UNDEFINED
	0, // 188: '>='
	0, // 189: '>>'
	0, // 190: UNDEFINED
	0, // 191: UNDEFINED
	0, // 192: UNDEFINED
	0, // 193: UNDEFINED
	0, // 194: UNDEFINED
	0, // 195: UNDEFINED
	0, // 196: UNDEFINED
	0, // 197: UNDEFINED
	0, // 198: UNDEFINED
	0, // 199: UNDEFINED
	0, // 200: UNDEFINED
	0, // 201: UNDEFINED
	0, // 202: UNDEFINED
	0, // 203: UNDEFINED
	0, // 204: UNDEFINED
	0, // 205: UNDEFINED
	0, // 206: UNDEFINED
	0, // 207: UNDEFINED
	0, // 208: UNDEFINED
	0, // 209: UNDEFINED
	0, // 210: UNDEFINED
	0, // 211: UNDEFINED
	0, // 212: ' \n'
	0, // 213: UNDEFINED
	0, // 214: UNDEFINED
	0, // 215: UNDEFINED
	0, // 216: UNDEFINED
	0, // 217: UNDEFINED
	0, // 218: UNDEFINED
	0, // 219: UNDEFINED
	0, // 220: UNDEFINED
	0, // 221: UNDEFINED
	0, // 222: UNDEFINED
	0, // 223: UNDEFINED
	0, // 224: UNDEFINED
	0, // 225: UNDEFINED
	0, // 226: UNDEFINED
	0, // 227: UNDEFINED
	0, // 228: UNDEFINED
	0, // 229: UNDEFINED
	0, // 230: UNDEFINED
	0, // 231: UNDEFINED
	0, // 232: UNDEFINED
	0, // 233: UNDEFINED
	0, // 234: UNDEFINED
}

static const uint8_t rightInfixBindingPower[235] {
	0, // 0: ' !'
	0, // 1: UNDEFINED
	0, // 2: UNDEFINED
	0, // 3: '*='
	0, // 4: ' %'
	0, // 5: ' &'
	0, // 6: '++'
	0, // 7: ' ('
	0, // 8: ' )'
	0, // 9: ' *'
	0, // 10: ' +'
	0, // 11: UNDEFINED
	0, // 12: ' -'
	0, // 13: ' .'
	0, // 14: ' /'
	0, // 15: UNDEFINED
	0, // 16: UNDEFINED
	0, // 17: UNDEFINED
	0, // 18: UNDEFINED
	0, // 19: UNDEFINED
	0, // 20: UNDEFINED
	0, // 21: UNDEFINED
	0, // 22: UNDEFINED
	0, // 23: UNDEFINED
	0, // 24: '+='
	0, // 25: UNDEFINED
	0, // 26: ' ;'
	0, // 27: ' <'
	0, // 28: ' ='
	0, // 29: ' >'
	0, // 30: UNDEFINED
	0, // 31: UNDEFINED
	0, // 32: UNDEFINED
	0, // 33: UNDEFINED
	0, // 34: UNDEFINED
	0, // 35: UNDEFINED
	0, // 36: UNDEFINED
	0, // 37: UNDEFINED
	0, // 38: UNDEFINED
	0, // 39: UNDEFINED
	0, // 40: UNDEFINED
	0, // 41: UNDEFINED
	0, // 42: UNDEFINED
	0, // 43: UNDEFINED
	0, // 44: UNDEFINED
	0, // 45: UNDEFINED
	0, // 46: UNDEFINED
	0, // 47: UNDEFINED
	0, // 48: UNDEFINED
	0, // 49: '!='
	0, // 50: '--'
	0, // 51: UNDEFINED
	0, // 52: UNDEFINED
	0, // 53: UNDEFINED
	0, // 54: UNDEFINED
	0, // 55: UNDEFINED
	0, // 56: UNDEFINED
	0, // 57: UNDEFINED
	0, // 58: ' ['
	0, // 59: UNDEFINED
	0, // 60: ' ]'
	0, // 61: ' ^'
	0, // 62: UNDEFINED
	0, // 63: UNDEFINED
	0, // 64: UNDEFINED
	0, // 65: UNDEFINED
	0, // 66: '-='
	0, // 67: UNDEFINED
	0, // 68: UNDEFINED
	0, // 69: UNDEFINED
	0, // 70: UNDEFINED
	0, // 71: UNDEFINED
	0, // 72: '..'
	0, // 73: UNDEFINED
	0, // 74: UNDEFINED
	0, // 75: UNDEFINED
	0, // 76: UNDEFINED
	0, // 77: UNDEFINED
	0, // 78: UNDEFINED
	0, // 79: UNDEFINED
	0, // 80: '|='
	0, // 81: UNDEFINED
	0, // 82: UNDEFINED
	0, // 83: UNDEFINED
	0, // 84: UNDEFINED
	0, // 85: UNDEFINED
	0, // 86: UNDEFINED
	0, // 87: UNDEFINED
	0, // 88: UNDEFINED
	0, // 89: UNDEFINED
	0, // 90: ' {'
	0, // 91: ' |'
	0, // 92: ' }'
	0, // 93: ' ~'
	0, // 94: UNDEFINED
	0, // 95: UNDEFINED
	0, // 96: UNDEFINED
	0, // 97: UNDEFINED
	0, // 98: UNDEFINED
	0, // 99: UNDEFINED
	0, // 100: UNDEFINED
	0, // 101: UNDEFINED
	0, // 102: UNDEFINED
	0, // 103: UNDEFINED
	0, // 104: UNDEFINED
	0, // 105: UNDEFINED
	0, // 106: UNDEFINED
	0, // 107: UNDEFINED
	0, // 108: '/='
	0, // 109: UNDEFINED
	0, // 110: UNDEFINED
	0, // 111: UNDEFINED
	0, // 112: UNDEFINED
	0, // 113: UNDEFINED
	0, // 114: UNDEFINED
	0, // 115: UNDEFINED
	0, // 116: UNDEFINED
	0, // 117: UNDEFINED
	0, // 118: UNDEFINED
	0, // 119: UNDEFINED
	0, // 120: UNDEFINED
	0, // 121: UNDEFINED
	0, // 122: UNDEFINED
	0, // 123: UNDEFINED
	0, // 124: UNDEFINED
	0, // 125: UNDEFINED
	0, // 126: UNDEFINED
	0, // 127: UNDEFINED
	0, // 128: UNDEFINED
	0, // 129: UNDEFINED
	0, // 130: UNDEFINED
	0, // 131: '&&'
	0, // 132: UNDEFINED
	0, // 133: '%='
	0, // 134: UNDEFINED
	0, // 135: UNDEFINED
	0, // 136: UNDEFINED
	0, // 137: UNDEFINED
	0, // 138: UNDEFINED
	0, // 139: UNDEFINED
	0, // 140: UNDEFINED
	0, // 141: UNDEFINED
	0, // 142: UNDEFINED
	0, // 143: '||'
	0, // 144: UNDEFINED
	0, // 145: '<<'
	0, // 146: '<='
	0, // 147: UNDEFINED
	0, // 148: UNDEFINED
	0, // 149: UNDEFINED
	0, // 150: UNDEFINED
	0, // 151: UNDEFINED
	0, // 152: UNDEFINED
	0, // 153: UNDEFINED
	0, // 154: '&='
	0, // 155: '^='
	0, // 156: UNDEFINED
	0, // 157: UNDEFINED
	0, // 158: UNDEFINED
	0, // 159: UNDEFINED
	0, // 160: UNDEFINED
	0, // 161: UNDEFINED
	0, // 162: UNDEFINED
	0, // 163: UNDEFINED
	0, // 164: UNDEFINED
	0, // 165: UNDEFINED
	0, // 166: UNDEFINED
	0, // 167: '=='
	0, // 168: UNDEFINED
	0, // 169: UNDEFINED
	0, // 170: UNDEFINED
	0, // 171: UNDEFINED
	0, // 172: UNDEFINED
	0, // 173: UNDEFINED
	0, // 174: UNDEFINED
	0, // 175: UNDEFINED
	0, // 176: UNDEFINED
	0, // 177: UNDEFINED
	0, // 178: UNDEFINED
	0, // 179: UNDEFINED
	0, // 180: UNDEFINED
	0, // 181: UNDEFINED
	0, // 182: UNDEFINED
	0, // 183: UNDEFINED
	0, // 184: UNDEFINED
	0, // 185: UNDEFINED
	0, // 186: UNDEFINED
	0, // 187: UNDEFINED
	0, // 188: '>='
	0, // 189: '>>'
	0, // 190: UNDEFINED
	0, // 191: UNDEFINED
	0, // 192: UNDEFINED
	0, // 193: UNDEFINED
	0, // 194: UNDEFINED
	0, // 195: UNDEFINED
	0, // 196: UNDEFINED
	0, // 197: UNDEFINED
	0, // 198: UNDEFINED
	0, // 199: UNDEFINED
	0, // 200: UNDEFINED
	0, // 201: UNDEFINED
	0, // 202: UNDEFINED
	0, // 203: UNDEFINED
	0, // 204: UNDEFINED
	0, // 205: UNDEFINED
	0, // 206: UNDEFINED
	0, // 207: UNDEFINED
	0, // 208: UNDEFINED
	0, // 209: UNDEFINED
	0, // 210: UNDEFINED
	0, // 211: UNDEFINED
	0, // 212: ' \n'
	0, // 213: UNDEFINED
	0, // 214: UNDEFINED
	0, // 215: UNDEFINED
	0, // 216: UNDEFINED
	0, // 217: UNDEFINED
	0, // 218: UNDEFINED
	0, // 219: UNDEFINED
	0, // 220: UNDEFINED
	0, // 221: UNDEFINED
	0, // 222: UNDEFINED
	0, // 223: UNDEFINED
	0, // 224: UNDEFINED
	0, // 225: UNDEFINED
	0, // 226: UNDEFINED
	0, // 227: UNDEFINED
	0, // 228: UNDEFINED
	0, // 229: UNDEFINED
	0, // 230: UNDEFINED
	0, // 231: UNDEFINED
	0, // 232: UNDEFINED
	0, // 233: UNDEFINED
	0, // 234: UNDEFINED 
}

#ifdef DEBUG
#ifdef DEBUG
uint64_t debug_parseLogicalOr(Lexer* lexer, AST *ast, bool *parseError);
uint64_t parse(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing LogicalOr...\n");
	const uint64_t size = debug_parseLogicalOr(lexer,ast,parseError);
	printf("Reducing LogicalOr\n");
	return size;
}
uint64_t debug_parseLogicalOr(Lexer* lexer, AST *ast, bool *parseError) {
#else

uint64_t parseLogicalOr(Lexer* lexer, AST *ast, bool *parseError) {
#endif
	Lexer savedLexer = {0};
	return prattParsing_parseExpression(lexer,&savedLexer,ast,parseError,0);
}
uint64_t prattParsing_parseExpression(Lexer* lexer, Lexer* savedLexer, AST *ast, bool* parseError, uint8_t minBindingPower) {
	//,{}()[].;~\n\r|&+-><=*!/%^\?%`^
	//{ and }
	//( and )
	//[ and ]
	//. and .. and ; and ~ and \n and ,
	//| and || and |=
	//& and && and &=
	//+ and ++ and +=
	//- and -- and -=
	//> and >> and >= 
	//< and << and <= 
	
	//= and ==
	//! and !=
	//* and *=
	//'/' and /=
	//% and %=
	//^ and ^=
	uint64_t lhsIndex = ast->count - 1;
	AstNode* lhs = AST_getNode(ast);
	if (lexer->type == TOKENTYPE_SYM) {
		if (lexer->size == 1 && *lexer->token == '(') {
			AST_createTerminalNode(ast,lhs,TOKENTYPE_PAREN,lexer->token,lexer->size);
			lhs->size += prattParsing_parseExpression(lexer,savedLexer,ast,parseError,RIBP);
			if (*parseError) break;
			*savedLexer = *lexer;
			nextToken(lexer);
			if (lexer->size != 1 || lexer->type != TOKENTYPE_SYM || *lexer->token != ')') {
				*parseError = true;
				return lhs->size;
			}
		} else {
			uint16_t operator = 32 << 8 | *lexer->token;
			if (lexer->size >= 2) {
				operator = operator << 8 | *lexer->token[1];
			}
			operator %= 235;
			uint8_t RPBP = prefixBindingPower[operator];
			if (RPBP == 0) {
				*parseError = true;
				return lhs->size;
			}
			AST_createTerminalNode(ast,AST_getNode(ast),TOKENTYPE_OPERATOR,lexer->token,lexer->size); 

			*savedLexer = *lexer;
			nextToken(lexer);

			lhs->size += prattParsing_parseExpression(lexer,savedLexer,ast,parseError,RIBP);
			if (*parseError) return lhs->size;
		}
	} else {
		AST_createTerminalNode(ast,AST_getNode(ast),lexer->type,lexer->token,lexer->size); 
	}
	*savedLexer = *lexer;
	nextToken(lexer); // consume the literal
	while (lexer->type == TOKENTYPE_SYM) {
		uint16_t operator = 32 << 8 | *lexer->token;
		if (lexer->size >= 2) {
			operator = operator << 8 | *lexer->token[1];
		}
		operator %= 235;

		uint8_t LPBP = postfixBindingPower[operator];
		if (LPBP != 0) {
			if (LPBP < minBindingPower) break;
			AstNode node = {0};
			node->token = lexer->token;
			node->tokenSize = lexer->size;
			node->size = 1 + lhs->size;
			node->astNodeType = AST_NODE_TYPE_TERMINAL;
			node->tokenType = TOKENTYPE_OPERATOR;
			AST_insert(ast,node,lhsIndex);
			*savedLexer = *lexer;
			nextToken(lexer); // consume the operator

			// 58 = '['
			lhs->size += prattParsing_parseExpression(lexer,savedLexer,ast,parseError,RIBP);
			if (operator == 58) {
				*savedLexer = *lexer;
				nextToken(lexer);
				if (lexer->type != TOKENTYPE_SYM || lexer->size != 1 || *lexer->token == ']') {
					*parseError = true;
					return lhs->size;
				}
			}
			if (*parseError) break;
			continue;
		}
		uint8_t LIBP = leftInfixBindingPower[operator];
		uint8_t RIBP = rightInfixBindingPower[operator];
		if (LIBP != 0 && RIBP != 0) {
			if (LIBP < minBindingPower) break;
			
			AstNode node = {0};
			node->token = lexer->token;
			node->tokenSize = lexer->size;
			node->size = 1 + lhs->size;
			node->astNodeType = AST_NODE_TYPE_TERMINAL;
			node->tokenType = TOKENTYPE_OPERATOR;
			AST_insert(ast,node,lhsIndex);
			*savedLexer = *lexer;
			nextToken(lexer); // consume the operator
			lhs->size += prattParsing_parseExpression(lexer,savedLexer,ast,parseError,RIBP);
			if (*parseError) break;
		}
	} 
	*lexer = *savedLexer;
	return lhs->size;
}







#ifdef DEBUG
uint64_t debug_parseEvaluableExpression(Lexer* lexer, AST *ast, bool *parseError, const uint32_t flag);
uint64_t parseEvaluableExpression(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing EvaluableExpression...\n");
	const uint64_t size = debug_parseEvaluableExpression(lexer,ast,parseError, flag);
	printf("Reducing EvaluableExpression\n");
	return size;
}
uint64_t debug_parseEvaluableExpression(Lexer* lexer, AST *ast, bool *parseError, const uint32_t flag) {
#else

uint64_t parseEvaluableExpression(Lexer* lexer, AST *ast, bool *parseError, const uint32_t flag) {
#endif
	AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_EVALUBALE_EXPRESSION);
	switch (flag) {
		case PARSE_EXPRESSION_DEFAULT:
		case PARSE_EXPRESSION_ID:
			current->size += parseLogicalOr(lexer,ast,parseError);
			break;
		case PARSE_EXPRESSION_MATCH:
			current->size += parseMatchExpression(lexer,ast,parseError);
			break;
		case PARSE_EXPRESSION_IF:
			current->size += parseIfExpression(lexer,ast,parseError);
			break;
		default:
			*flag = true
			return 0;
	}
	return current->size;
}

#ifdef DEBUG
uint64_t debug_parseEffectiveExpression(Lexer* lexer, AST *ast, bool *parseError, const uint32_t flag);
uint64_t parseEvaluableExpression(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing EffectiveExpression...\n");
	const uint64_t size = debug_parseEffectiveExpression(lexer,ast,parseError, flag);
	printf("Reducing EffectiveExpression\n");
	return size;
}
uint64_t debug_parseEffectiveExpression(Lexer* lexer, AST *ast, bool *parseError, const uint32_t flag) {
#else

uint64_t parseEffectiveExpression(Lexer* lexer, AST *ast, bool *parseError, const uint32_t flag) {
#endif
	AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_EFFECTIVE_EXPRESSION);

	switch (flag) {
				case PARSE_EXPRESSION_DEFAULT:
			switch (lexer->type) {
				case TOKENTYPE_NUM:
				case TOKENTYPE_HEX:
				case TOKENTYPE_BIN:
				case TOKENTYPE_FLT:
				case TOKENTYPE_STR:
					// parse numeric expression or character expression
					// TYPE.foo().bar()...
					AST_createTerminalNode(ast,AST_getNode(ast),lexer->type,lexer->token,lexer->size); // create a numeric literal in nodes arena
					current->size += 1;
					nextTeken(lexer) // consume the numeric literal

					if (lexer->size != 1 || lexer->type != TOKENTYPE_SYM || *lexer->token != '.') {
						*parseError = true;
						return 1;
					}
					while (true) {
						nextToken(lexer); // consume a '.'
						if (lexer->type != TOKENTYPE_ID) {
							*parseError = true;
							return current->size;
						}			
						nextToken(lexer); // consume ID
						if (lexer->size != 1 || lexer->type != TOKENTYPE_SYM || *lexer->token != '(') {
							*parseError = true;
							return current->size;
						}
						nextToken(lexer); // consume '('

						// consume arguments
						current->size += parseArguments(lexer,ast,parseError);
						if (*parseError) return current->size;

						
						if (lexer->size != 1 || lexer->type != TOKENTYPE_SYM || *lexer->token != ')') {
							*parseError = true;
							return current->size;
						}
						Lexer temp = *lexer;
						nextToken(temp); // consume ')'
						if (temp.size != 1 || temp.type != TOKENTYPE_SYM || temp.token != '.') {
							return current->size;						
						}
						*lexer = temp;
					}
					if (current->size == 1) {
						*parseError = true;
						return 1;
					}
					return current->size;
				case TOKENTYPE_STR:
					AST_creatTerminalNode(ast,AST_getNode(ast),TOKENTYPE_STR,lexer->token,lexer->size); // create a numeric literal in nodes arena
					current->size += 1;
					nextTeken(lexer); // consume the numeric literal
					
					bool usedAFunctionCall = false;
					bool usedAnArrayAccess = false;
					while (true) {
						if (current->size != 1 || current->type != TOKENTYPE_SYM) {
							*parseError = true;
							return current->size;
						}
						char c = *lexer->token;
						if (c == '.') {
							usedAFunctionCall = true;
							nextToken(lexer); // consume a '.'
							if (lexer->type != TOKENTYPE_ID) {
								*parseError = true;
								return current->size;
							}			
							nextToken(lexer); // consume ID
							if (lexer->size != 1 || lexer->type != TOKENTYPE_SYM || *lexer->token != '(') {
								*parseError = true;
								return current->size;
							}
							nextToken(lexer); // consume '('

							// consume arguments
							current->size += parseArguments(lexer,ast,parseError);
							if (*parseError) return current->size;

							
							if (lexer->size != 1 || lexer->type != TOKENTYPE_SYM || *lexer->token != ')') {
								*parseError = true;
								return current->size;
							}
							Lexer temp = *lexer;
							nextToken(temp); // consume ')'
							if (temp.size != 1 || temp.type != TOKENTYPE_SYM || temp.token != '.') {
								return current->size;						
							}
							*lexer = temp;
						} else if (c == '[') {
							nextToken(lexer);
							if (usedAFunctionCall || usedAnArrayAccess) {
								*parseError = true;
								return current->size;
							}
							current->size += parseLogicalOr(lexer,ast,parseError);
							if (*parseError) return current->size;
							if (lexer->size == 2 && lexer->type == TOKENTYPE_SYM && memcmp(lexer->token,2,"..") == 0) {
								nextToken(lexer);
								current->size += parseLogicalOr(lexer,ast,parseError);
								if (*parseError) return current->size;
							} else {
								usedAnArrayAccess = true;
							}
							nextToken(lexer);
							*parseError = lexer->size != 1 || lexer->type != TOKENTYPE_SYM || *lexer->token != ']';
							return current->size;
						} else {
							break;
						}
					}
					*parseError = current->size == 1;
					return current->size;
				default: 
					*parseError-true;
					return current->size;

			}
		case PARSE_EXPRESSION_ID:
			AST_createTerminalNode(ast,AST_getNode(ast),TOKENTYPE_ID,lexer->token,lexer->size);
			current-> size += 1;
			Lexer temp = *lexer;
			nextToken(lexer);

			bool expressionOpt = false;
			while(true) {
				if (lexer->type != TOKENTYPE_SYM || lexer->size > 2) {
					*parseError = true;
					*lexer = temp;
					return current->size;
				}
				if (lexer->size == 2) {
					if (expressionOpt) {
						*parseError = true;
						*lexer = temp;
						return current->size;
					} else if (*lexer->token == '+' && lexer->token[1] == '+') {
						AST_createNonTerminalNode(ast,AST_getNode(ast),TOKENTYPE_INCREMENT);
						current->size += 1;
						return current->size;
					} else if (*lexer->token == '-' && lexer->token[1] == '-') {
						AST_createNonTerminalNode(ast,AST_getNode(ast),TOKENTYPE_DECREMENT);
						current->size += 1;
						return current->size;
					} else {
						*parseError = true;
						*lexer = temp;
						return current->size;
					}
				}
				char c = *lexer->token;
				temp = *lexer;
				nextToken(lexer);
				switch (c) {
					case '.':
						if (lexer->type != TOKENTYPE_ID) {
							*parseError = true;
							return current->size;
						}
						AST_createTerminalNode(ast,AST_getNode(ast),TOKENTYPE_ID,lexer->token,lexer->size);
						temp = *lexer;
						nextToken(lexer);
						break;
					case '[':
					
						current->size += parseLogicalOr(lexer,ast,parseError);
						if (*parseError) return current->size;
						if (lexer->size == 2 && lexer->type == TOKENTYPE_SYM && memcmp(lexer->token,2,"..") == 0) {
							nextToken(lexer);
							current->size += parseLogicalOr(lexer,ast,parseError);
							if (*parseError) return current->size;
						}
						nextToken(lexer);
						if(lexer->size != 1 || lexer->type != TOKENTYPE_SYM || *lexer->token != ']') {
							*parseError = true;
							return current->size;
						}
						break;
					case '(':
						current->size += parseArguments(lexer,ast,parseError);
						if (*parseError) return current->size;
						nextToken(lexer);
						if(lexer->size != 1 || lexer->type != TOKENTYPE_SYM || *lexer->token != ')') {
							*parseError = true;
							return current->size;
						}
						expressionOpt = true;
						break;
					case '=':
						if (expressionOpt) {
							*parseError = true;
							return current->size;
						}
						current->size += parseEvaluableExpression(lexer,ast,parseError,PARSE_EXPRESSION_DEFAULT);
						return current->size;
					default:
						*lexer = temp;
						return current->size;
				}
				temp = *lexer;
				nextToken(lexer);
			}
			return current->size;


		case PARSE_EXPRESSION_MATCH:
			current->size += parseMatchExpression(lexer,ast,parseError);
			break;
		case PARSE_EXPRESSION_IF:
			current->size += parseIfExpression(lexer,ast,parseError);
			break;
		default:
			*flag = true
			return 0;

	}

	return current->size;
}





#ifdef DEBUG
uint64_t debug_parseExpression(Lexer* lexer, AST *ast, bool *parseError, uint32_t flag);

uint64_t parseExpression(Lexer *lexer, AST *ast, bool *parseError, const uint32_t flag)
{
	printf("Parsing Expression...\n");
	const uint64_t size = debug_parseExpression(lexer,ast,parseError,flag);
	printf("Reducing Expression\n");
	return size;
}

uint64_t debug_parseExpression(Lexer* lexer, AST *ast, bool *parseError, const uint32_t flag) {
#else


uint32_t PARSE_EXPRESSION_EXPECTING_ANY = 0;
uint32_t PARSE_EXPRESSION_EXPECTING_QUALIFIED_NAME = 1;
uint32_t PARSE_EXPRESSION_EXPECTING_BOOLEAN = 2;
uint32_t PARSE_EXPRESSION_EXPECTING_ASSIGNMENT = 3;

/* uint32_t TOKENTYPE_NULL =  0;
 * uint32_t TOKENTYPE_ID   =  1;
 * uint32_t TOKENTYPE_NUM  =  2;
 * uint32_t TOKENTYPE_HEX  =  3;
 * uint32_t TOKENTYPE_BIN  =  4;
 * uint32_t TOKENTYPE_FLT  =  5;
 * uint32_t TOKENTYPE_SYM  =  6;
 * uint32_t TOKENTYPE_CHR  =  7;
 * uint32_t TOKENTYPE_STR  =  8;
 * uint32_t TOKENTYPE_KEY  =  9;
 * uint32_t TOKENTYPE_EOF  = 10;
 * 
 * assignemnt
 * ID = logical_or
 * ID[logical_or] = logical_or
 * ID.x = logical_or
 * NO: ID.foo(arguments) = logical_or
 * NO: ID.foo(arguments)[logical_or] = logical_or
 * 
 * call
 * ID.x.foo(arguments)
 * ID.x[arguments].foo(arguments)
 * ID.foo(arguments)
 * ID[logical_or].foo(arguments)
 * ID.foo(arguments)[logical_or].foo(arguments)
 * LITERAL.foo()
 * NO: NUMERIC.x
 * NO: NUMERIC[arguments]
 * NO: ALPHABETIC.x
 * ALPHABETIC[logical_or].foo(arguments)
 *
 * can only assign to an ID if no function invocation was used in the left hand side of the assignement
 * can always call a function on a left hand side
 * 
 * SET A =  {no function call rules} IF A USES A FUNCTION CALL UPGRADE TO B
 * SET B = {function call rules} NEVER DOWN GRADE TO B
 *
 * SET A CAN BE TERMINATED BY A FUNCTION CALL OR AN ASSIGNMENT
 * SET B CAN BE TERMINATED BY A FUNCTION CALL
 * id.foo()
 *
 * EPSILON > ;
 * statements > statement ';' statemetns | EPSILON;
 * statement > effetive_expression;
 * if_expression > "if" evaluable_expression '{' statements '}'
 * match_expression > "match" evaluable_expression '{' statements '}'
 * logical_or > ... '*' deref_expression | excetera;
 * evaluable_expression > if_expression | match_expression | ID id_expression_b | logical_or;
 * effective_expression > if_expression | match_expression | numeric_literal numberic_expression | alphabetic_literal alphabetic_expression_a; | ID id_expression_a | '*' deref_expression id_expression_a;
 * 	deref_expression > '*' deref_expression | '(' deref_expression ')' | ID id_expression_c;
 * 	id_expression_a > '.' ID id_expression_a | '[' logical_or ']' id_expression_a | '(' arguments ')' id_expression_b_opt | "++" | "--" | '=' evaluable_expression;
 * 	id_expression_b > '.' ID id_expression_b | '[' logical_or ']' id_expression_b | '(' arguments ')' id_expression_b_opt;
 * 	id_expression_c > '.' ID id_expression_c | '[' logical_or ']' id_expression_c | "++" | "--" | EPSILON;
 * 	id_expression_d > '.' ID id_expression_d | '[' logical_or ']' id_expression_d | '(' arguments ')' id_expression_d | EPSILON;
 * 	id_expression_b_opt > id_expression_b | EPSILON;
 * 	numeric_literal > NUM | HEX | BIN | FLT;
 * 	numeric_expression > '.' ID '(' arguments ')' numberic_expression | EPSILON;
 * 	aplhabetic_literal > STR | CHR;
 * 	alhpabetic_expression_a > '[' logical_or ']' alphabetic_expression_a | '.' ID '(' arguments ')' alhpabetic_expression_b | EPSILON;
 *	alhpabetic_expression_b > '.' ID '(' arguments ')' aplhabetic_expression | EPSILON;
 *
 * 
 * 
 * ID = logical_or
 * ID[logical_or]
 * literal > NUM | HEX | BIN | FLT | CHR | STR; 
 * logical_or > undefined;
 * expression > ID expression_tail_end | literal expression_tail;
 * 	ID_expreession > ID epression_tail_end;
 *	expression_tail > '.' ID expression_tail_opt | '[' logical_or ']' expression_tail_opt 
 *	expression_tail_opt > expression_tail_end | EPSILON();
 *	eppression_tail_end > "++" | "--" | '(' parameters ')' expression_tail_opt | expression_tail;
 *
 *
 * 
 *
 * 
 * expression > assignment
 * 	      | if_experssion
 * 	      | match_expression
 * 	      | logical_or
 * */

uint64_t parseExpression(Lexer *lexer, AST *ast, bool *parseError, const uint32_t flag) {
#endif
	AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_EXPRESSION);
	switch (flag) {
		case PARSE_EXPRESSION_MATCH:
			current->size += parseMatchExpression(lexer,ast,parseError);
			break;
		case PARSE_EXPRESSION_IF:
			current->size += parseIfExpression(lexer,ast,parseError);
			break;
		case PARSE_EXPRESSION_ID:
			break;
		case PARSE_EXPRESSION_DEFAULT:
			break;
		default:
			*parseError = true;
			return 0;
	}
	return current->size;
}
uint64_t parseType(Lexer *lexer, AST *ast, bool *parseError);

#ifdef DEBUG
uint64_t debug_parseTypes(Lexer* lexer, AST *ast, bool *parseError);
uint64_t parseTypes(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing Types...\n");
	const uint64_t size = debug_parseTypes(lexer,ast,parseError);
	printf("Reducing Types\n");
	return size;
}
uint64_t debug_parseTypes(Lexer* lexer, AST *ast, bool *parseError) {
#else
uint64_t parseTypes(Lexer *lexer, AST *ast, bool *parseError) {
#endif
	AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_TYPES);
	while (true) {
		current->size += parseType(lexer,ast,parseError);
		if (*parseError) return current->size;
		Lexer temp = *lexer;
		nextToken(lexer);
		if (lexer->type != TOKENTYPE_SYM || lexer->size != 1 || *(lexer->token) != ',') {
			*lexer = temp;
			break;
		}
	}
	return current->size;
}

#ifdef DEBUG
uint64_t debug_parseType(Lexer* lexer, AST *ast, bool *parseError);
uint64_t parseType(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing Type...\n");
	const uint64_t size = debug_parseType(lexer,ast,parseError);
	printf("Reducing Type\n");
	return size;
}
uint64_t debug_parseType(Lexer* lexer, AST *ast, bool *parseError) {
#else
uint64_t parseType(Lexer *lexer, AST *ast, bool *parseError) {
#endif
AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_TYPE);
	if (lexer->type == TOKENTYPE_ID) {
		AST_createTerminalNode(ast,AST_getNode(ast),TOKENTYPE_ID,lexer->token,lexer->size);
		current->size += 1;
		nextToken(lexer);
		if (lexer->type != TOKENTYPE_ID) {
			*parseError = true;
			return 0;
		}
		current->size += 1;
		return current->size;
	}
	if (lexer->type == TOKENTYPE_SYM && lexer->size == 1 && *(lexer->token) == '(') {
		current->size += parseTypes(lexer,ast,parseError);
		nextToken(lexer);
		if (lexer->type != TOKENTYPE_SYM || lexer->size != 0 || *(lexer->token) == ')') {
			*parseError = true;
			return current->size;
		}
		nextToken(lexer);
		if (lexer->type != TOKENTYPE_ID) {
			*parseError = true;
			return current->size;
		}
		current->size += 1;
		return current->size;
	}
	*parseError = true;
	return 0;
}

#ifdef DEBUG
uint64_t debug_parseFunctionDeclaration(Lexer* lexer, AST *ast, bool *parseError);
uint64_t parseFunctionDeclaration(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing FunctionDeclaration...\n");
	const uint64_t size = debug_parseFunctionDeclaration(lexer,ast,parseError);
	printf("Reducing FunctionDeclaration\n");
	return size;
}
uint64_t debug_parseFunctionDeclaration(Lexer* lexer, AST *ast, bool *parseError) {
#else
uint64_t parseFunctionDeclaration(Lexer *lexer, AST *ast, bool *parseError) {
#endif
AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_FUNCTION_DECLARATION);
	if (lexer->type != TOKENTYPE_ID) {
		*parseError = true;
		return 0;
	}
	nextToken(lexer);
	if (lexer->type != TOKENTYPE_SYM || lexer->size != 1) {
		*parseError = true;
		return 0;
	}

	if (*lexer->token == '=') {
		nextToken(lexer);
		AST_createNonTerminalNode(ast,current,TOKENTYPE_LAMBDA);
	} else {
		AST_createNonTerminalNode(ast,current,TOKENTYPE_FUNCTION);
	}

	if (lexer->type != TOKENTYPE_SYM || lexer->size != 1 || *lexer->token != '(') {
		*parseError = true;
		return 1;
	}

	nextToken(lexer);
	if (lexer->type != TOKENTYPE_SYM || lexer->size != 1 || lexer->token[0] != ')')
	{
		current->size += parseTypes(lexer,ast,parseError);
		if (*parseError) return current->size;
	}

	nextToken(lexer);
	if (lexer->type != TOKENTYPE_SYM || lexer->size != 1 || *lexer->token != ')') {
		*parseError = true;
		return current->size;
	}

	nextToken(lexer);
	current->size += parseBlock(lexer,ast,parseError);

	return current->size;
}

#ifdef DEBUG
uint64_t debug_parseVariableDeclaration(Lexer *lexer, AST *ast, bool *parseError, const uint32_t flag);
uint64_t parseVariableDeclaration(Lexer *lexer, AST *ast, bool *parseError, const uint32_t flag) {
	printf("Parsing VariableDeclaration...\n");
	const uint64_t size = debug_parseVariableDeclaration(lexer,ast,parseError,flag);
	printf("Reducing VariableDeclaration\n");
	return size;
}
uint64_t debug_parseVariableDeclaration(Lexer *lexer, AST *ast, bool *parseError, const uint32_t flag) {
#else

uint64_t parseVariableDeclaration(Lexer *lexer, AST *ast, bool *parseError, const uint32_t flag) {
#endif
AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_VARIABLE_DECLARATION);

	if (flag == 2) {
		AST_createNonTerminalNode(ast,AST_getNode(ast),TOKENTYPE_KEYWORD_PUBLIC);
	} else {
		AST_createNonTerminalNode(ast,AST_getNode(ast),TOKENTYPE_KEYWORD_PRIVATE);
	}	
	current->size += 1 + parseType(lexer,ast,parseError);
	if (*parseError) return current->size;
	nextToken(lexer);
	if (lexer->type != TOKENTYPE_SYM || *(lexer->token) != '=') {
		*parseError = true;
		return current->size;
	}
	nextToken(lexer);
	current->size += parseExpression(lexer,ast,parseError,0);
	return current->size;
}


#ifdef DEBUG
uint64_t debug_parseDeclaration(Lexer *lexer, AST *ast, bool *parseError, uint32_t flag);

uint64_t parseDeclaration(Lexer *lexer, AST *ast, bool *parseError, uint32_t flag) {
	printf("Parsing Declaration...\n");
	const uint64_t size = debug_parseDeclaration(lexer,ast,parseError,flag);
	printf("Reducing Declaration\n");
	return size;
}
uint64_t debug_parseDeclaration(Lexer *lexer, AST *ast, bool *parseError, uint32_t flag) {
#else

uint64_t parseDeclaration(Lexer *lexer, AST *ast, bool *parseError, uint32_t flag) {
#endif
AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_DECLARATION);

	nextToken(lexer);
	switch (flag) {
		case 1:
			current->size += parseFunctionDeclaration(lexer,ast,parseError);
			return current->size;
		case 2:
		case 3:
			current->size += parseVariableDeclaration(lexer,ast,parseError,flag);
			return current->size;
		case 4:
		case 5:
			if (flag == 4) {
				AST_createNonTerminalNode(ast,AST_getNode(ast),TOKENTYPE_KEYWORD_PUBLIC);
			} else {
				AST_createNonTerminalNode(ast,AST_getNode(ast),TOKENTYPE_KEYWORD_PRIVATE);
			}
			current->size += 1;
			if (lexer->type != TOKENTYPE_ID || lexer->size != 3) {
				*parseError = true;
				return current->size;
			}
			char *token = lexer->token;
			if (token[0] == 'v' && token[1] == 'a') {
				if (token[0] == 'r') {
					flag = 2;
				} else if (token[1] == 'l') {
					flag = 3;
				} else {
					*parseError = true;
					return current->size;
				}
				nextToken(lexer);
				current->size += parseVariableDeclaration(lexer,ast,parseError,flag);
			} else {
				*parseError = true;
				return current->size;
			}

			
			break;
		default:
			*parseError = true;
			return 0;
	}
	return current->size;
}

#ifdef DEBUG
uint64_t debug_parseJumpStatement(Lexer* lexer, AST *ast, bool *parseError);
uint64_t parseJumpStatement(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing JumpStatement...\n");
	const uint64_t size = debug_parseJumpStatement(lexer,ast,parseError);
	printf("Reducing JumpStatement\n");
	return size;
}
uint64_t debug_parseJumpStatement(Lexer* lexer, AST *ast, bool *parseError) {
#else

uint64_t parseJumpStatement(Lexer* lexer, AST *ast, bool *parseError) {
#endif
AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_JUMP_STATEMENT);
	AST_createTerminalNode(ast,current,TOKENTYPE_ID,lexer->token,lexer->size);
	current->size += 1;

	const Lexer temp = *lexer;
	switch(*lexer->token) {
		case 'c':
		case 'b':
			nextToken(lexer);
			if (lexer->type == TOKENTYPE_ID) {
				AST_createTerminalNode(ast,current,TOKENTYPE_ID,lexer->token,lexer->size);
			} else {
				*lexer = temp;
				*parseError = true;
				return current->size;
			}
		case 'r':
		case 'y':
			current->size += parseExpression(lexer,ast,parseError,0);
			return current->size;
		default:
			*parseError = true;
			return current->size;
	}
		
}

#ifdef DEBUG
uint64_t debug_parseLoopStatement(Lexer* lexer, AST *ast, bool *parseError);
uint64_t parseLoopStatement(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing LoopStatement...\n");
	const uint64_t size = debug_parseLoopStatement(lexer,ast,parseError);
	printf("Reducing LoopStatement\n");
	return size;
}
uint64_t debug_parseLoopStatement(Lexer* lexer, AST *ast, bool *parseError) {
#else

uint64_t parseLoopStatement(Lexer* lexer, AST *ast, bool *parseError) {
#endif
	AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_LOOP_STATEMENT);

	nextToken(lexer);
	if (lexer->type == TOKENTYPE_SYM && lexer->size == 1 && *(lexer->token) == '{') {
		current->size += parseBlock(lexer,ast,parseError);
		if (*parseError) return current->size;
		Lexer temp = *lexer;
		nextToken(lexer);
		if (lexer->type == TOKENTYPE_ID && lexer->size == 5 && memcmp(lexer->token,"while",5) == 0) {
			nextToken(lexer);
			current->size += parseExpression(lexer,ast,parseError,0);
		} else {
			*lexer = temp;
		}
		return current->size;
	}
	if (lexer->type == TOKENTYPE_ID && lexer->size == 5 && memcmp(lexer->token,"while",5) == 0) {
		nextToken(lexer);
		current->size += parseExpression(lexer,ast,parseError,0);
		if (*parseError) return current->size;
		current->size += parseBlock(lexer,ast,parseError);
		return current->size;
	}
	current->size += parseType(lexer,ast,parseError);
	return current->size;
}

#ifdef DEBUG
uint64_t debug_parseImportStatement(Lexer* lexer, AST *ast, bool *parseError);
uint64_t parseImportStatement(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing ImportStatement...\n");
	const uint64_t size = debug_parseImportStatement(lexer,ast,parseError);
	printf("Reducing ImportStatement\n");
	return size;
}
uint64_t debug_parseImportStatement(Lexer* lexer, AST *ast, bool *parseError) {
#else

uint64_t parseImportStatement(Lexer* lexer, AST *ast, bool *parseError) {
#endif
	AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_IMPORT_STATEMENT);
	nextToken(lexer);
	if (lexer->type == TOKENTYPE_ID) {
		AST_createTerminalNode(ast,current,TOKENTYPE_ID,lexer->token,lexer->size);
		current->size += 1;
		return current->size;
	}
	if (lexer->type == TOKENTYPE_STR)
	{
		AST_createTerminalNode(ast,current,TOKENTYPE_STR,lexer->token,lexer->size);
		current->size += 1;
		return current->size;
	}
	*parseError = true;
	return 0;
}
#ifdef DEBUG
uint64_t debug_parseStatement(Lexer* lexer, AST *ast, bool *parseError);
uint64_t parseStatement(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing Statement...\n");
	const uint64_t size = debug_parseStatement(lexer,ast,parseError);
	printf("Reducing Statement\n");
	return size;
}
uint64_t debug_parseStatement(Lexer* lexer, AST *ast, bool *parseError) {
#else

uint64_t parseStatement(Lexer* lexer, AST *ast, bool *parseError) {
#endif
	AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_STATEMENT);

	if (lexer->type == TOKENTYPE_EOF) return 0;

	char *token = lexer->token;
	if (lexer->type == TOKENTYPE_ID) {
		switch(lexer->size) {
			case 2:
				if (token[0] == 'i' && token[1] == 'f') {
					current->size += parseExpression(lexer,ast,parseError,PARSE_EXPRESSION_IF);
				}
				break;
			case 3:
				if (token[0] == 'd' && token[1] == 'e' && token[2] == 'f') {
					current->size += parseDeclaration(lexer,ast,parseError,1);
				} else if (token[0] == 'v' && token[1] == 'a') {
					if (token[2] == 'r') {
						current->size += parseDeclaration(lexer,ast,parseError,2);
					} else if (token[2] == 'l') {
						current->size += parseDeclaration(lexer,ast,parseError,3);
					}
				}
				break;
			case 4:
				if (memcmp(token,"loop",4) == 0) {
					current->size += parseLoopStatement(lexer,ast,parseError);
				}
				break;
			case 5:
				if (memcmp(token,"break",5) == 0 || memcmp(token,"yield",5) == 0) {
					current->size += parseJumpStatement(lexer,ast,parseError);
				} else if (memcmp(token,"match",5) == 0) {
					current->size += parseExpression(lexer,ast,parseError,PARSE_EXPRESSION_MATCH);
				}
				break;
			case 6:
				if (memcmp(token,"public",6) == 0) {
					current->size += parseDeclaration(lexer,ast,parseError,4);
				} else if(memcmp(token,"return",6) == 0) {
					current->size += parseJumpStatement(lexer,ast,parseError);
				} else if (memcmp(token,"import",6) == 0) {
					current->size += parseImportStatement(lexer,ast,parseError);
				}
				break;
			case 7:
				if (memcmp(token,"private",7) == 0) {
					current->size += parseDeclaration(lexer,ast,parseError,5);
				}
				break;
			case 8:
				if (memcmp(token,"continue",8) == 0) {
					current->size += parseJumpStatement(lexer,ast,parseError);
				}
			default:
				current->size += parseExpression(lexer,ast,parseError,PARSE_EXPRESION_ID);
				break;
		}
	} else {
		current->size += parseExpression(lexer,ast,parseError,PARSE_EXPRESSION_DEFAULT);
	}
	return current->size;

}
#ifdef DEBUG
uint64_t debug_parseStatements(Lexer* lexer, AST *ast, bool *parseError);
uint64_t parseStatements(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing Statements...\n");
	const uint64_t size = debug_parseStatements(lexer,ast,parseError);
	printf("Reducing Statements\n");
	return size;
}
uint64_t debug_parseStatements(Lexer* lexer, AST *ast, bool *parseError) {
#else

uint64_t parseStatements(Lexer* lexer, AST *ast, bool *parseError) {
#endif

	AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_STATEMENTS);
	printf("%s %llu, ",type_name(ast->nodes[0].tokenType),ast->nodes[0].size);
	while(true) {
		current->size += parseStatement(lexer,ast,parseError);
		if (*parseError) {
			*parseError = true;
			return current->size;
		}		
		nextToken(lexer);
		while (lexer->type == TOKENTYPE_SYM && lexer->size == 1 && (*lexer->token == ';' || *lexer->token == '\n'))
		{
			nextToken(lexer);
		}
		if (lexer->type == TOKENTYPE_SYM && lexer->size == 1 && *lexer->token == '}') {
			return current->size;
		}
		if (lexer->type == TOKENTYPE_EOF) break;
	}
	return current->size;
}

#ifdef DEBUG
uint64_t debug_parseBlock(Lexer* lexer, AST *ast, bool *parseError);
uint64_t parseBlock(Lexer* lexer, AST *ast, bool *parseError)
{
	printf("Parsing Block...\n");
	const uint64_t size = debug_parseBlock(lexer,ast,parseError);
	printf("Reducing Block\n");
	return size;
}
uint64_t debug_parseBlock(Lexer* lexer, AST *ast, bool *parseError) {
#else

uint64_t parseBlock(Lexer* lexer, AST *ast, bool *parseError) {
#endif
	AstNode* current = AST_getNode(ast);
	AST_createNonTerminalNode(ast,current,TOKENTYPE_BLOCK);

	if (lexer->type != TOKENTYPE_SYM && lexer->size != 1 && *(lexer->token) != '{') {
		*parseError = true;
		return 0;
	}
	Lexer temp = *lexer;
	nextToken(&temp);
	if (temp.type != TOKENTYPE_SYM || temp.size != 1 || *temp.token != '}') {
		current->size += parseStatements(lexer,ast,parseError);
		if (*parseError || !(lexer->type == TOKENTYPE_SYM && lexer->size == 1 && *lexer->token == '}')) {
			*parseError = true;
			return current->size;
		}
	}
	*lexer = temp;

	return current->size;
} 
AST *parse(const char*fileName) {
	File *file = loadFile(fileName);
	if (!file) return NULL;
	AST* ast = AST_create();
	if (!ast) {freeFile(file); return NULL;};

	Lexer lexer = {0};
	lexer.currentChar = file->data;
	lexer.token = NULL;

	bool parseError = false;
	
	// create the statements node
	nextToken(&lexer);
	parseStatements(&lexer,ast,&parseError);

	for (int i = 0; i < ast->count; i++)
	{
		printf("%s %llu, ",type_name(ast->nodes[i].tokenType),ast->nodes[i].size);
	}

	if (parseError) {
		printf("Error: Unknown token = ");
		printToken(&lexer);
		printf("\n");
	} else 	{
		printf("Successfully parsed file!\n");
	}
	freeFile(file);
	return ast;
}


int main(int argc, char **argv) {
	_Static_assert(sizeof(size_t) <= sizeof(uint64_t),
               "size_t larger than uint64_t");

	if (argc < 2)
	{
		printf("Usage: %s <file>\n", argv[0]);
		return 1;
	}
	printf("Compiling %s\n",argv[1]);

	AST *ast = parse(argv[1]);
	free(ast);
}
