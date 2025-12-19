#include <stdio.h> //#
#include <stdlib.h> //#
#include <stdbool.h> //#
#include <immintrin.h> //#  // SIMD intrinsics (SSE2+)
#include <stdint.h> //#
#include <string.h> //#
#include <time.h> //#
typedef struct {
	char *data;
	size_t size;
} File;
File *loadFile(const char* filename) {
	FILE *fp = NULL;
	char *buffer = NULL;
	File *file = NULL;

	fp = fopen(filename, "rb");
	if (!fp) goto file_not_found;

	// Determine file size
	fseek(fp, 0, SEEK_END);
	long length = ftell(fp);
	if (length < 0) goto read_error;
	rewind(fp);
	
	const size_t PAD = 64;
	buffer = malloc((size_t)length + 1 + PAD);
	if (!buffer) goto malloc_failed;
	
	// Read into buffer
	size_t readCount = fread(buffer, 1, (size_t)length, fp);
	if (readCount != (size_t)length) goto read_error;

	// set the last 65 bytes of the buffer to the null terminator so SIMD scans stop safely
	memset(buffer + length, 0, 1 + PAD);

	file = malloc(sizeof *file);
	if (!file) goto malloc_failed;

	file->data = buffer;
	file->size = (size_t)length;
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
void nextBlock(char c, char **currentChar) {
       char endChar = c;
       bool escaped = false;
       *currentChar += 1;
       c = **currentChar;
       while (c != '\0' && (c != endChar || escaped)) {
	       if (c == '\\') {
		       escaped = !escaped;
	       } else {
		       escaped = false;
	       }
	       *currentChar += 1;
	       c = **currentChar;
       }
       (*currentChar)++;
}
static inline size_t simd_whitespace(const char *p) {
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
            return (size_t)(p - start + __builtin_ctz(~mask));
        }

        p += 16;
    }
}
static inline size_t simd_id(const char *p) {
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
            return (size_t)(p - start + __builtin_ctz(~mask));
        }

        p += 16;
    }
}
static inline size_t simd_hex(const char *p) {
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
            return (size_t)(p - start + __builtin_ctz(~mask));
        }

        p += 16;
    }
}

static inline size_t simd_num(const char *p) {
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
            return (size_t)(p - start + __builtin_ctz(~mask));
        }

        p += 16;
    }
}
static inline size_t simd_bin(const char *p) {
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
            return (size_t)(p - start + __builtin_ctz(~mask));
        }

        p += 16;
    }
}
static inline size_t simd_zero(const char *p) {
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
            return (size_t)(p - start + __builtin_ctz(~mask));
        }

        p += 16;
    }
}

size_t lex(char **currentChar, char **token, Type *type) {
	*type = END_OF_FILE;
	char c;
	char nextChar;
	// skip white space
	*currentChar += simd_whitespace(*currentChar);
	while (true) {
		c = **currentChar;
		*token = *currentChar;
		switch(c) {
			case '\0':
				return 0;
				//{}()[].;~\n\r|&+-><=*!/%^\?%`^
				//{ and }
				//( and )
				//[ and ]
				//.
				//;
				//~
				//\n
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
				*type = SYM;
				(*currentChar)++;
				nextChar = **currentChar; 
				if (nextChar == '=') {
					*currentChar += 1;
					return 2;
				}
				return 1;
			case '|':
			case '&':
			case '+':
			case '-':
			case '<':
			case '>':
				*type = SYM;
				(*currentChar)++;
				nextChar = **currentChar; 
				if (nextChar == c || nextChar == '=') {
					*currentChar += 1;
					return 2;
				}
				return 1;
			case '{':
			case '}':
			case '(':
			case ')':
			case '[':
			case ']':
			case '.':
			case '~':
			case ';':
			case ',':
			case '\n':
				(*currentChar)++;
				*type = SYM;
				return 1;
			case '"':
				nextBlock(c,currentChar);
				*type = STR;
				return *currentChar - *token;
			case '\'':
				nextBlock(c,currentChar);
				*type = CHR;
				return *currentChar - *token;
			case '#':
				*type = END_OF_FILE;
				nextBlock(c,currentChar);
				break;
			case '0':
				// skip over all unessesary zeros

				*currentChar += simd_zero(*currentChar);
				// check the character after the zeros
				c = **currentChar;
				switch(c) {
					// 0x <- hexidecimal
					case 'x':
					case 'X':
						(*currentChar)++; // consume x
						*currentChar += simd_hex(*currentChar);
						*type = HEX;
						return *currentChar - *token;
					// 0b <- binary
					case 'b':
					case 'B':
						(*currentChar)++; // consume b
						*currentChar += simd_bin(*currentChar);
						*type = BIN;
						return *currentChar - *token;
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
						*type = NUM;
						return 1;
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
				*type = NUM;
				*currentChar += simd_num(*currentChar);
				if (**currentChar != '.')  {
					return *currentChar - *token;
				}
				(*currentChar)++;
				c = **currentChar;

				if (c < '0' || c > '9') {
					(*currentChar)--;
					return *currentChar - *token;
				}

				*currentChar += simd_num(*currentChar);
				*type = FLT;
				return *currentChar - *token;
			default:
				if (!(('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z'))) {
					(*currentChar)++;
					break;
				}
				*currentChar += simd_id(*currentChar);
				*type = ID;
				return *currentChar - *token;
		}
	}
}
typedef struct AstNode {
	AstNode *parent;

	AstNode **children;
	size_t childCapacity;
	size_t childCount;

	Type type;
	char *token;
	size_t size;
} AstNode;
AstNode *newAstNode(Type type) {
	AstNode *n = malloc(sizeof(AstNode));
	if (!n) return NULL;
	n->parent = NULL;
	n->children = NULL;
	n->childCount = 0;
	n->childCapacity = 0;
	n->type = type;
	n->token = NULL;
	n->size = 0;
	return n;
}
void freeAstNode(AstNode *node) {
	if (!node) return;
	for (size_t i = 0; i < node->childCount; i++) {
		freeAstNode(node->children[i]);
	}
	free(node->children);
	free(node);
}
AstNode *addChildAstNode(AstNode *parent, AstNode *child) {
        if (parent->childCount == parent->childCapacity) {
                size_t newCapacity = parent->childCapacity == 0 ? 4 : parent->childCapacity * 2;
                parent->children = realloc(parent->children, newCapacity * sizeof(AstNode*));
                if (!(parent->children)) {
                        return NULL;
                }
                parent->childCapacity = newCapacity;
        }

        parent->children[parent->childCount++] = child;
        child->parent = parent;
        return child;	
}
const char *type_name(Type t) {
    switch (t) {
        case ID:          return "ID";
        case NUM:         return "NUM";
        case HEX:         return "HEX";
        case BIN:         return "BIN";
        case FLT:         return "FLT";
        case SYM:         return "SYM";
        case CHR:         return "CHR";
        case STR:         return "STR";
        case KEY:         return "KEY";
        case END_OF_FILE: return "END_OF_FILE";
        default:          return "UNKNOWN";
    }
}
static void print_token(char *token, Type type, int size) {
	printf("TOKEN{.type=%s, .value='", type_name(type));
	for (size_t i = 0; i < (size_t) size; i++) {
		switch (token[i]) {
			case '\n': printf("\\n"); break;
			case '\t': printf("\\t"); break;
			case '\r': printf("\\r"); break;
			case '\v': printf("\\v"); break;
			case '\f': printf("\\f"); break;
			case '\\': printf("\\\\"); break;
			default:
			   putchar(token[i]);	   
		}

    	}
	printf("'}");
}
static inline long long now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}
typedef enum {
	STATEMENTS,
	STATEMENT,
	DECLARATION,
	FUNCTION_DECL,
	VARIABLE_DECL,
	PUBLICITY_PUBLIC,
	PUBLICITY_PRIVATE,
	ID,
	NUM,
	HEX,
	BIN,
	FLT,
	SYM,
	CHR,
	STR,
	KEY,
	END_OF_FILE
} Type;
#define nextToken() do { size = lex(&currentChar, &token, &type) } while (0)
#define pushChild(type) addChildAstNode(current,newAstNode(type))

static inline bool match(char *token, size_t size, size_t tarSize; const char *tarToken) {
	if (size != tarSize) return false;
	if (size <= 3) {
		for(size_t i = 0; i < size; i++) {
			if (token[i] != tarToken[i]) return false;
		}
		return true;
	}
	return memcmp(token,tarToken,size) == 0;
}
AstNode *parse(const char* fileName) {
	File *file = loadFile(fileName);
	if(!file) return NULL;
		
	AstNode *current = newAstNode(STATEMENTS);
	if(!astNode) return NULL;
	AstNide *child = NULL;
	
	char *currentChar = file->data;
	char *token = NULL;
	Type type;
	size_t size;

	static void *states[] {
		&&STATEMENTS,
	}	
	
	goto STATEMENTS;
	STATEMENTS:
		while(true) {
			goto STATEMENT;
		}
	STATEMENT:
		// maybe travel up the three until the parent's type is a statements.
		// That can succesfully reduce a significant part of the tree efficiently,
		// Some thinking still has to be done. Good luck. Love you man.
		current = pushChild(STATEMENT);
		nextToken();
		if(type != ID) goto error; 
		if(match(token,"def")) {
			goto FUNCTION_DECL;
		}
		if(size == 6 && memcmp(token,"public",6) (size == 7 && memcmp(token,"private",7) == 0)) {
			goto VARIABLE_DECL;
		}
		goto error;
	FUNCTION_DECL:
		current = pushChild(DECLARATION);
		current = pushChild(FUNCTION_DECL);
	VARIABLE_DECL:
		current = pushChild(DECLARATION);
		current = pushChild(VARIABLE_DECL);
		if (size == 6) {
			pushChild(PUBLICITY_PUBLIC);
		} else {
			pushChuld(PUBLICITY_PRIVATE);
		}
	goto end;
error:
	printf("Error: Unknown token = ");
	print_token(token,type,size);
	printf("\n");
	while(current->parent != NULL) {
		current = current->parent;
	}
	freeAstNode(current);
end:
	freeFile(file);
	return current;

}

int main(int argc, char **argv) {
	long long t0 = now_ns();
	long tokenCount = 0;
	if (argc < 2) return 1;
	printf("Compiling %s\n",argv[1]);
	File *file = loadFile(argv[1]);
	if (!file) return 1;
	char *currentChar = file->data;
	char *token = NULL;
	Type type;
	size_t size;
	do {
		size = lex(&currentChar, &token, &type);
		//print_token(token, type, size);
		tokenCount++;
	} while(type != END_OF_FILE);
	freeFile(file);
	
	long long dt_ns = now_ns() - t0;
	double tokens_per_sec = (double)tokenCount * 1e9 / (double)dt_ns;

	printf("Lexed %.ld tokens in %.lld nano seconds\n", tokenCount, dt_ns);

	printf("%.2f tokens per second\n", tokens_per_sec);

	double tokens_per_ms = (double)tokenCount * 1e6 / (double)dt_ns;
	printf("%.4f tokens per ms\n", tokens_per_ms);
	return 0;
	
}
