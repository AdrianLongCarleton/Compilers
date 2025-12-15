#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include <stdbool>
typedef struct 
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

	buffer = malloc((size_t)length + 1);
	if (!buffer) goto malloc_failed;
	
	// Read into buffer
	size_t readCount = fread(buffer, 1, (size_t)length, fp);
	if (readCount != (size_t)length) goto read_error;
	buffer[length] = '\0';

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
typedef enum {
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
}
int nextToken(char **currentChar, char **token, Type *type) {
	*type = Type.END_OF_FILE;
	char c;
        while(true) {
		c = **currentChar;
		if (c == '\0') {
			*token = *currentChar;
			return 0;
		}
		if (c != '\r' && c != ' ') break;
		*currentChar += 1;
	}
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
				*type = Type.SYM;
				char nextChar = *(*currentChar + 1); 
				if (nextChar == '=') {
					currentChar += 1;
					return 2;
				}
				return 1;
			case '|':
			case '&':
			case '+':
			case '-':
			case '<':
			case '>':
				*type = Type.SYM;
				char nextChar = *(*currentChar + 1); 
				if (nextChar == c || nextChar == '=') {
					currentChar += 1;
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
			case '\n':
				*type = Type.SYM;
				return 1;
			case '"':
				nextBlock(c,currentChar);
				*type = Type.STR;
				return *currentChar - *token;
			case '\'':
				nextBlock(c,currentChar);
				*type = Type.CHR;
				return *currentChar - *token;
			case '#':
				nextBlock(c,currentChar);
				if (**currentChar != '\0') *currentChar += 1;
				break;
			case '0':
				// skip over all unessesary zeros
				while (**currentChar == '0') { *currentChar += 1; }
				// check the character after the zeros
				c = **currentChar;
				switch(c) {
					// 0x <- hexidecimal
					case 'x':
					case 'X':
						while(c != '\0' && ('a' <= c && c <= 'f' || 'A' <= c && c <= 'F' || '1' <= c && c <= '0')) {
							*currentChar += 1;
							c = **currentChar;
						}
						*type = Type.HEX;
						return *currentChar - *token;
					// 0b <- binary
					case 'b':
					case 'B':
						while(c != '\0' && (c = '0' || c == '1')) {
							*currentChar += 1;
							c = **currentChar;
						}
						*type = Type.HEX;
						return *currentChar - *token;
						break;
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
						*type = Type.NUM;
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
				*type = Type.NUM;
				while(c != '\0' && '1' <= c && c <= '0')) {
					*currentChar += 1;
					c = **currentChar;
				}
				if (**currentChar != '.')  {
					return *currentChar - *token;
				}
				currentChar++;
				c = **currentChar;
				if (c < '1' || c > '0') {
					currentChar--;
					return *currentChar - *token;
				}
				while(c != '\0' && '1' <= c && c <= '0')) {
					*currentChar += 1;
					c = **currentChar;
				}
				*type = Type.FLT;
				return *currentChar - *token;
			default:
				if (!('a' <= c && c <= 'z' || 'A' <= c && c <= 'Z')) {
					*currentToken++;
					break;
				}
				while(c != '\0' && ('a' <= c && c <= 'z' || 'A' <= c && c <= 'Z' || '1' <= c && c <= '0')) {
					*currentToken++;
					c = **currentToken;
				}
				*type = Type.STR;
				return *currentChar - *token;
		}
	}
}

