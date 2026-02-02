# Unifica

An Earley-style Chart Parser with Feature Unification and Defeasible Constraints for Java 21+.

## Overview

Unifica is a parsing framework that combines the power of Earley parsing with unification-based grammars and a flexible
constraint system. It handles ambiguous grammars gracefully using penalty-based optimization to select the best parse.

### Key Capabilities

- **Earley Parsing Algorithm**: Implements standard Earley operations (Predict, Scan, Complete) adapted for Unification
  Grammars. Handles left-recursive rules and arbitrary Context-Free Grammars.

- **Feature Structures**: Attach attribute-value matrices to grammar symbols (e.g., `VP[tense:past]`). Supports
  three-way unification during parsing: waiting item features, completed constituent features, and expected rule
  features.

- **Logic Variables**: Use variables in features and constraints (e.g., `agree(X, Y)`) with full unification and
  occurs-check.

- **Defeasible Constraints**: Constraints can be `REQUIRED` (hard failure) or `DEFEASIBLE` (soft failure with penalty).
  The parser tracks penalties and selects the parse with the lowest score when ambiguity exists.

- **Grammar-Driven Lexer**: Produces a token lattice supporting lexical ambiguity, which the parser resolves during
  parsing.

## Installation

### Maven

```xml

<dependency>
    <groupId>com.libdbm</groupId>
    <artifactId>unifica</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.libdbm:unifica:1.0.0'
```

## Quick Start

### 1. Define a Grammar

Create a grammar file (e.g., `grammar.ug`):

```
// Simple arithmetic expression grammar
start Expr;

Expr --> Term;
Expr --> Expr "+" Term;
Expr --> Expr "-" Term;

Term --> Factor;
Term --> Term "*" Factor;
Term --> Term "/" Factor;

Factor --> /[0-9]+/;
Factor --> "(" Expr ")";
```

### 2. Parse Input

```java
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import com.libdbm.ugf.parser.ParserFactory;
import com.libdbm.ugf.parser.ParseResult;

// Load grammar
Grammar grammar = UnificationGrammarParserFactory.parse(Path.of("grammar.ug"));

        // Create parser
        ParserFactory factory = ParserFactory.create(grammar);

        // Parse input
        ParseResult result = factory.parse("3 + 4 * 2");

if(result.

        success()){
        System.out.

        println("Parse tree: "+result.tree());
        }else{
        System.out.

        println("Parse failed: "+result.diagnostics());
        }
```

## Grammar Syntax

### Rules

```
// Basic rule
S --> NP VP;

// Rule with features
S --> NP[num:X] VP[num:X];      // Agreement via shared variable

// Alternation
Term --> "+" | "-";

// Repetition
List --> Item+;                  // One or more
List --> Item*;                  // Zero or more
List --> Item?;                  // Optional
```

### Terminals

```
// Literal strings
Keyword --> "if" | "else" | "while";

// Regular expressions
Number --> /[0-9]+/;
Identifier --> /[a-zA-Z_][a-zA-Z0-9_]*/;

// Labeled terminals (for constraint access)
Assignment --> name:/[a-z]+/ "=" value:/[0-9]+/;
```

### Features

```
// Feature annotation on nonterminals
NP[num:sing, person:3] --> "he" | "she" | "it";
NP[num:plur] --> "they" | "we";

// Variables for agreement
S --> NP[num:X, person:Y] VP[num:X, person:Y];

// Nested features
VP[aux[modal:true]] --> "can" | "could" | "may";
```

### Constraints

```
// Required constraint (hard failure if not met)
S --> NP:subject VP:predicate
    where agree(subject.num, predicate.num);

// Defeasible constraint (soft failure, adds penalty)
S --> NP:subject VP:predicate
    where ~prefer_active(subject);           // ~ marks defeasible

// Constraint with priority (higher = more penalty on failure)
S --> NP VP
    where ~[10] some_preference();           // Priority 10

// Multiple constraints
S --> A:a B:b C:c
    where equals(a.type, b.type)
      and not_equals(b.val, c.val);

// Logical operators
Rule --> X Y
    where (cond1(X) or cond2(X)) and cond3(Y);
```

### State-Based Lexing

```
// State transitions for context-sensitive tokenization
String --> '"' ==> STRING;                   // Push STRING state
{STRING} Content --> /[^"\\]+/;              // Only match in STRING state
{STRING} Escape --> /\\./;
{STRING} End --> '"' ==> _;                  // Pop state

// CDATA-style sections
CData --> "<![CDATA[" ==> CDATA;
{CDATA} Text --> /[^\]]+/;
{CDATA} End --> "]]>" ==> _;
```

### Modules

```
// Import from another grammar file
import "./lexical.ug";
import "./expressions.ug" (Expr, Term);      // Selective import

// Module declaration
module math.expressions;
export Expr, Term, Factor;
```

## Built-in Predicates

### Equality and Comparison

| Predicate             | Description               |
|-----------------------|---------------------------|
| `equals(a, b)`        | Values are equal          |
| `not_equals(a, b)`    | Values are not equal      |
| `less_than(a, b)`     | Numeric comparison a < b  |
| `greater_than(a, b)`  | Numeric comparison a > b  |
| `less_equal(a, b)`    | Numeric comparison a <= b |
| `greater_equal(a, b)` | Numeric comparison a >= b |

### String Predicates

| Predicate                | Description                  |
|--------------------------|------------------------------|
| `starts_with(s, prefix)` | String starts with prefix    |
| `ends_with(s, suffix)`   | String ends with suffix      |
| `contains(s, substr)`    | String contains substring    |
| `matches(s, regex)`      | String matches regex pattern |
| `length(s, n)`           | String has length n          |

### Type Checking

| Predicate         | Description                  |
|-------------------|------------------------------|
| `is_string(v)`    | Value is a string            |
| `is_number(v)`    | Value is a number            |
| `is_structure(v)` | Value is a feature structure |
| `is_bound(v)`     | Variable is bound            |

### Feature Access

| Predicate                        | Description           |
|----------------------------------|-----------------------|
| `has_feature(struct, name)`      | Structure has feature |
| `get_feature(struct, name, val)` | Get feature value     |
| `agree(a, b)`                    | Features unify        |
| `feature_eq(s1, s2, name)`       | Same feature value    |

### Position Predicates

| Predicate        | Description          |
|------------------|----------------------|
| `at_start()`     | At position 0        |
| `at_position(n)` | At specific position |

## API Reference

### Core Classes

#### `ParserFactory`

Main entry point for parsing.

```java
// Create from grammar
ParserFactory factory = ParserFactory.create(grammar);

// Parse with default options
ParseResult result = factory.parse("input text");

// Parse with custom context (for external predicates)
Context context = Context.create()
        .withPredicate("custom_check", args -> Result.ok());
ParseResult result = factory.parse(context, "input text");
```

#### `ParseResult`

Result of a parse operation.

```java
result.success()      // true if parse succeeded
result.

tree()         // ParseTree (Node or Leaf)
result.

penalty()      // Total penalty score
result.

diagnostics()  // Detailed failure information
```

#### `Grammar`

Grammar representation loaded from `.ug` files.

```java
// Load from file
Grammar grammar = UnificationGrammarParserFactory.parse(path);

// Load from string
Grammar grammar = UnificationGrammarParserFactory.parseString(grammarText);

// Programmatic building
Grammar grammar = Grammar.builder()
        .start("S")
        .rule("S", List.of(nt("NP"), nt("VP")))
        .rule("NP", List.of(terminal("the"), nt("N")))
        .build();
```

#### `ParseTree`

Immutable parse tree with two variants:

```java
switch(tree){
        case
ParseTree.Node node ->{
String symbol = node.symbol();           // Nonterminal name
List<ParseTree> children = node.children();
Structure features = node.features();
    }
            case
ParseTree.Leaf leaf ->{
String text = leaf.text();               // Matched text
int start = leaf.start();                // Start position
int end = leaf.end();                    // End position
    }
            }
```

### Generation

Generate text from a grammar:

```java
GrammarGenerator generator = GrammarGenerator.builder()
        .grammar(grammar)
        .terminalGenerator((name, features) -> Optional.of("word"))
        .build();

// Generate with target features
Structure features = Structure.builder()
        .set("num", new StringConstant("sing"))
        .build();

List<String> texts = generator.generate("NP", features, 10);
```

### Parse Observer

Monitor parsing for debugging or visualization:

```java
LoggingObserver observer = new LoggingObserver();
ParserFactory factory = ParserFactory.create(grammar, observer);
factory.

parse("input");

// Access recorded events
observer.

events().

forEach(event ->{
        switch(event){
        case
ParseEvents.Predict p ->System.out.

println("Predict: "+p.rule());
        case
ParseEvents.Scan s ->System.out.

println("Scan: "+s.token());
        case
ParseEvents.Complete c ->System.out.

println("Complete: "+c.item());
        // ... other events
        }
        });
```

## Building from Source

```bash
# Clone repository
git clone https://github.com/libdbm/unifica.git
cd unifica

# Build and test
mvn clean verify

# Build JAR
mvn clean package

# Generate coverage report (target/site/jacoco/index.html)
mvn clean test
```

### Requirements

- Java 21 or later
- Maven 3.8+

## License

BSD 3-Clause License. See [LICENSE](LICENSE) for details.
