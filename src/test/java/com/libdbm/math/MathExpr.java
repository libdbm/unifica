package com.libdbm.math;

import java.util.List;

/** AST nodes for mathematical expressions */
public sealed interface MathExpr {

  /** Binary operators supported in expressions */
  enum BinaryOperator {
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    MODULO("%"),
    POWER("^");

    private final String symbol;

    BinaryOperator(final String symbol) {
      this.symbol = symbol;
    }

    public static BinaryOperator fromSymbol(final String symbol) {
      return switch (symbol) {
        case "+" -> ADD;
        case "-" -> SUBTRACT;
        case "*" -> MULTIPLY;
        case "/" -> DIVIDE;
        case "%" -> MODULO;
        case "^" -> POWER;
        default -> throw new IllegalArgumentException("Unknown binary operator symbol: " + symbol);
      };
    }

    public String symbol() {
      return symbol;
    }
  }

  /** Unary operators supported in expressions */
  enum UnaryOperator {
    PLUS("+"),
    MINUS("-");

    private final String symbol;

    UnaryOperator(final String symbol) {
      this.symbol = symbol;
    }

    public static UnaryOperator fromSymbol(final String symbol) {
      return switch (symbol) {
        case "+" -> PLUS;
        case "-" -> MINUS;
        default -> throw new IllegalArgumentException("Unknown unary operator symbol: " + symbol);
      };
    }

    public String symbol() {
      return symbol;
    }
  }

  /** Binary operation: left op right */
  record BinaryOp(MathExpr left, BinaryOperator operator, MathExpr right) implements MathExpr {}

  /** Unary operation: op expr */
  record UnaryOp(UnaryOperator operator, MathExpr expr) implements MathExpr {}

  /** Numeric literal */
  record Constant(double value) implements MathExpr {}

  /** Variable reference */
  record Variable(String name) implements MathExpr {}

  /** Function call: func(arg1, arg2, ...) */
  record FunctionCall(String name, List<MathExpr> args) implements MathExpr {}
}
