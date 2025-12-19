// I think C is the greatest language, for all the reasons other people hate it.
//
// In the beginning you always want Results.
// 	- First: Ugh why are there so many features? Secondly: Why doesn't my language not have the features I want?
// 	- I just want an omelet! VS. What can I make today?
// 		- Why can't this kitchen make me an omelet?
//		- What can I make with the stuff in this kitchen?
//			- Be here
// In the end all you want is control.
//
//  I spend 20 min a year looking for memory leaks,
//  the rest of the world spends all their time trying to avoid garbage collectors.
//  	- Once you need something that needs to run fast you start worrying about garbage collection
//  	- Eventually the lack of control over the garbage collector becomes the problem.
//  	- Memory leaks are a solvable problem. Garbage collection is not.
//
//  Small technology footprint
//  	- I want the simplest possible compiler to compiler my code
//  	- Therefore there are fewer things that can break
//
//  	- Few dependencies?
//  		- Can't be fucked up by other people changing things
//  		- Security vulnerabilies in your code might be exploited
//
//  Typing is not the problem.
//  	- You spend more timre readng your code then the compiler does.
//  		- We think of code as a way to communicate with computers
//  		- More important that the code communicates to us what the computer does
//  	- Ambiguity is the enemy.
//  		- Everything should be incredibly clear.
//  			- Prefer errors
//  		 - Operator overloading is stupid
//  Chrases are good
//  	- Crashes make you fix things.
//  	- Debuggers are our friends
//  	- Tools > complexity
//  Naming:
//  	- Good code is wide code.
//  	- Prefer descriptive variable names
//  	-Define words:
//  		- array -> array of data
//  		- type -> enum
//  		- node -> link to each oth
//	- Long functions are good.
//		- Sequential code is easy to read
//		- Write code that does something
//		- Alert names: manager, controller, handler
//			Code that handles other code
//	- Functions:
//		bad:
//			create_object();
//			destroy_object();
//			move_object()
//		worse:
//			create_thing();
//			remove_object();
//			move_object();
//		better:
//			object_create();
//			object_destroy();
//			object_move();
//		best:
//			module_object_create();
//			module_object_destroy();
//			module_object_move();
//		functions names in by file structure
//		organization.username.module.object.create();
//		Lang.viffx.Compiler.sys.println("Hello world");
//  API Design!
//  	- modular design in C
//  		- Awesome naming
//  		- One .h file, many .c files
//  		- file_internal.h <- for internal stuff
//  
//  Object orientation: 101
//  	Object oriented language:
//  		object thing();
//  		thing.do_something();
//  	In c:
//  		thing = object_create();
//  		object_do_something(thing);
//  	In Lang:
//  		class Object {
//			i32 x
//			str y
//
//			static val constructor(str y, i32 x) {
//				return Object.new(x,y)
//			}
//  		}
//  		Object object = Object.new(5,"str")
//  		Object object2 = Object.constructor("str",5)
//
//  Wait this is smart:
//  	typdef stuct {
//		u32 length;
//		u8 data[1];
//  	} Array;
//  	Array *array;
//  	array = malloc((sizeof *array) + sizeof(u8) * 10);
//  	array->length = 10;
//  	for (i32 i = 0; i < array->length; i++) {
//		array->data[i] = 0;
//  	}
//
//
//
//
