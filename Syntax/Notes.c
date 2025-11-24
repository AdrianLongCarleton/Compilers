int* make_heap_value() {
	int *x = malloc(sizeof(int));
	*x = 42;
	return x;
}
int problem1() {
	int *p = make_heap_value();
	printf("%d\n", *p);
	// forgot to free(p)
	return 0;
}
int problem2() {
	int *p = make_heap_value();
	int *q = p; // q is an alias for p
	// forgot to free(p) 
}
int problem3() {
	int *p = make_heap_value();
	int *q = make_heap_value();
	// forgot to free(p)
	p = q // the pointer p is reasigned to q
	// forgot to free(p) which is really free(q)
}
// rules to solve problems 1, 2, 3
// 1. When an pointer goes out of scope the heap it points to is freed
// 2. An alias is not a pointer
// 	a) an alias will never free the heap of the pointer it is aliasing
// 3. When a "original pointer = replacement pointer" event occurs,
//	then the heap that the original pointer pointed to is freed,
//	and the replacement pointer is now an alias to the original pointer
//example.class
private val make_heap_value() {
	i32 *x = 42 // allocate 42 on the heap
	i32 *y = x  // alias y to x
	return y    // return pointer to heap value
}
problem1() {
	int *p = make_heap_value();
	printf("%d\n", *p);
	return 0;
	// implicit free(p) freeing the value of 42 on the heap
}
problem2() {
	int *p = make_heap_value();
	int *q = p; // never free aliases
	// implicit free(p) freeing the value of 42 on the heap
}
problem3() {
	int *p = make_heap_value();
	int *q = make_heap_value();
	// implicit free(p) freeing the value of 42 on the heap
	p = q // reassign ownership to q
	// implicit free(p) freeing the value of 42 on the heap
	
	// q is now an alias for p
}

void rules() {
	int *a = 42   // allocate 42 on the heap
	int b = 43    // allocate 43 on the stack

	int *p = a    // allocate 42 on the heap
	int *q = b    // q is an alias for b
	int **r = a   // r is an alias for a, specifically r points the pointer a in this scope

	*p = 44       // mutate the heap poined to by p
	*q = 45       // mutate stack variable b through alias 
	*r = 46	      // mutate the heap pointed to by a
	
	// free(a)
	// free(p)
}

void rulesForAliases() {
	int pointer a = 42   // allocate 42 on the heap
	int b = 43    // allocate 43 on the stack

	int pointer p = a    // p is an alias for a
	int pointer q = b    // q is an alias for b

	deref p = 44       // mutate the heap pointed to by a, through alias p
	deref q = 45       // mutate stack variable b through alias q

	// break alias status by:
	p = 46        // allocate 46 on the heap (new ownership for p)
	// or
	int c = 5
	q = addressof c        // reassign q to alias new stack variable c
}
// fuck it we support both











#include <stdio.h>
#include <stdlib.h>
#include <string.h>

void pointer_chaos() {
    int *a = malloc(sizeof(int));
    *a = 10;

    // (1) DOUBLE FREE
    int *b = a;       // b and a both point to the same heap location
    free(a);
    free(b);           // 💣 double free – undefined behavior
    // (1) WORKS FINE -- THIS IS LOGICAL ERROR HOWEVER
    int *b = a 
    // is equivalent to:
    int *b = malloc(sizeof(int))
    b = *a
    // (1) LOGICAL ERROR FIX
    int **b = &a; 
    free(a); // free the value 10 from the heap, a now points to the special nothing value
    print(*b); // prints the address of a 
    print(**n); // Exception cannot dereference nothing

    // (2) USE AFTER FREE
    int *c = malloc(sizeof(int));
    *c = 20;
    free(c);
    printf("%d\n", *c);  // 💀 use-after-free
    // Exception cannot dereference nothing.

    // (3) LEAK
    int *d = malloc(sizeof(int));
    *d = 30;
    d = malloc(sizeof(int));  // ⚠️ previous allocation lost (leak)
    *d = 31;
    // (3) Is compiled as:
    int *d = malloc(sizeof(int));
    *d = 30;
    free(d);
    int *d = malloc(sizeof(int));
    *d = 31;

    // (4) RETURNING ADDRESS OF LOCAL VARIABLE
    int *e;
    {
        int local = 40;
        e = &local;    // ⚠️ e points to stack memory that will go out of scope
    }
    printf("%d\n", *e);  // 💀 invalid read – dangling stack pointer
    // (4) Is compied as
    int *e;
    {
	int *e;
        int local = 40;
        e = &local;    // ⚠️ e points to stack memory that will go out of scope
	// if you want to modify the e from the higher scope use explicit syntax that only lets you assign higher scope e and address of a higher scope variable
    }
    printf("%d\n", *e);  // Exception, e is uninitialized
    
    // (5) INVALID WRITE (wild pointer)
    int *f;
    *f = 50;  // 💀 uninitialized pointer, writing to random address
    // (5) Is compiled as:
    int *f = malloc(sizeof(int));
    *f = 50

    // (6) BUFFER OVERFLOW
    int *g = malloc(2 * sizeof(int));
    g[0] = 1;
    g[1] = 2;
    g[2] = 3; // 💣 writes past allocated memory
	      // Array out of bounds exception

    // (7) STRAY POINTER (free then reuse)
    int *h = malloc(sizeof(int));
    *h = 60;
    free(h);
    h = NULL;
    *h = 61; // Exception cannot assign a value to a null dereference

    // (8) MIXED HEAP AND STACK
    int stack_var = 70;
    int *i = malloc(sizeof(int));
    *i = 71;
    i = &stack_var;  // ⚠️ overwrites heap pointer (leak)
    free(i);         // 💣 attempt to free stack memory
    // (8) Is compiled as:
    int stack_var = 70;
    int *i = malloc(sizeof(int));
    *i = 71;
    free(i);
    i = &stack_var;
    free(i);         // Exception cannot free a stack variable
}

int main() {
    pointer_chaos();
    return 0;
}
