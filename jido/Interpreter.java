package jido;

import java.util.ArrayList;
import java.util.List;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
	final Environment globals = new Environment();
	private Environment environment = globals;

	Interpreter() {
		globals.define("clock", new JidoCallable() {
			@Override
			public int arity() { return 0; }

			@Override
			public Object call(Interpreter interpreter,
								List<Object> arguments) {
			return (double)System.currentTimeMillis() / 1000.0;
			}

			@Override
			public String toString() { return "<native fn>"; }
		});
	}

  void interpret(List<Stmt> statements) {
    try {
      for (Stmt statement : statements) {
        execute(statement);
      }
    } catch (RuntimeError error) {
      Jido.runtimeError(error);
    }
  }

	@Override
	public Object visitLiteralExpr(Expr.Literal expr) {
		return expr.value;
	}

  @Override
  public Object visitLogicalExpr(Expr.Logical expr) {
    Object left = evaluate(expr.left);

    if (expr.operator.type == TokenType.OR) {
      if (isTruthy(left)) return left;
    } else {
      if (!isTruthy(left)) return left;
    }

    return evaluate(expr.right);
  }

	@Override
	public Object visitGroupingExpr(Expr.Grouping expr) {
		return evaluate(expr.expression);
	}

	@Override
	public Object visitUnaryExpr(Expr.Unary expr) {
		Object right = evaluate(expr.right);

		switch (expr.operator.type) {
			case BANG:
				return !isTruthy(right);
			case MINUS:
				return -(double)right;
			default:
				return null;
		}
	}

	@Override
	public Object visitPostfixExpr(Expr.Postfix expr) {
		Object left = evaluate(expr.left);
		if (!(expr.left instanceof Expr.Variable)) {
			throw new RuntimeError(expr.operator, "Can only apply '++' or '--' to variables.");
		}

		if (!(left instanceof Double)) {
			throw new RuntimeError(expr.operator, "Operand must be a number");
		}
		
		double newValue = (double)left + (expr.operator.type == TokenType.INCREMENT ? 1 : -1);
	  
		// Cast expr as variable, then get name as token
		environment.assign(((Expr.Variable)expr.left).name, (Object)newValue);
		return (double)left;
	}

	@Override
	public Object visitVariableExpr(Expr.Variable expr) {
		return environment.get(expr.name);
	}

	@Override
	public Object visitBinaryExpr(Expr.Binary expr) {
		Object left = evaluate(expr.left);
		Object right = evaluate(expr.right); 

		switch (expr.operator.type) {
			case GREATER:
				checkNumberOperands(expr.operator, left, right);
				return (double)left > (double)right;
			case GREATER_EQUAL:
				checkNumberOperands(expr.operator, left, right);
				return (double)left >= (double)right;
			case LESS:
				checkNumberOperands(expr.operator, left, right);
				return (double)left < (double)right;
			case LESS_EQUAL:
				checkNumberOperands(expr.operator, left, right);
				return (double)left <= (double)right;
			case MINUS:
				checkNumberOperands(expr.operator, left, right);
				return (double)left - (double)right;
			case PLUS:
				if (left instanceof Double && right instanceof Double) {
					return (double)left + (double)right;
				} 

				if (left instanceof String && right instanceof String) {
					return (String)left + (String)right;
				}

				if (left instanceof String) {
					return (String)left + right.toString();
				} else if (right instanceof String) {
					return left.toString() + (String)right;
				}

				throw new RuntimeError(expr.operator,
				"Operands must contain either two numbers for addition, or at least one string for concatenation.");
			case SLASH:
				checkNumberOperands(expr.operator, left, right);
				if ((double)left == 0 && (double)right == 0) throw new RuntimeError(expr.operator, "Cannot divide zero by zero.");
				return (double)left / (double)right;
			case STAR:
				checkNumberOperands(expr.operator, left, right);
				return (double)left * (double)right;
			case BANG_EQUAL: return !isEqual(left, right);
			case EQUAL_EQUAL: return isEqual(left, right);
			default: return null;
		}
	}

	@Override
	public Object visitTernaryExpr(Expr.Ternary expr) {
		Object conditionBoolean = evaluate(expr.condition);

		if (isTruthy(conditionBoolean)) return evaluate(expr.then);
		return evaluate(expr.otherwise);
	}

	@Override
	public Void visitExpressionStmt(Stmt.Expression stmt) {
		evaluate(stmt.expression);
		return null;
	}

	@Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
		// When creating a JidoFunction, the current environment is captured and parameterised.
    JidoFunction function = new JidoFunction(stmt, environment);
    environment.define(stmt.name.lexeme, function);
    return null;
  }

	@Override
	public Void visitPrintStmt(Stmt.Print stmt) {
		Object value = evaluate(stmt.expression);
		System.out.println(stringify(value));
		return null;
	}

	@Override
	public Void visitReturnStmt(Stmt.Return stmt) {
		Object value = null;
		if (stmt.value != null) value = evaluate(stmt.value);
		throw new Return(value);
	}

	@Override
	public Void visitVarStmt(Stmt.Var stmt) {
		Object value = null;
		if (stmt.initializer != null) {
			value = evaluate(stmt.initializer);
		}

		environment.define(stmt.name.lexeme, value);
		return null;
	}

	@Override
	public Object visitAssignExpr(Expr.Assign expr) {
		Object value = evaluate(expr.value);
		environment.assign(expr.name, value);
		return value;
	}

	@Override
	public Void visitBreakStmt(Stmt.Break stmt) {
		throw new BreakException();
	}

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    if (isTruthy(evaluate(stmt.condition))) {
      execute(stmt.thenBranch);
    } else if (stmt.elseBranch != null) {
      execute(stmt.elseBranch);
    }
    return null;
  }

	@Override
	public Void visitWhileStmt(Stmt.While stmt) {
		try {
			while (isTruthy(evaluate(stmt.condition))) {
				execute(stmt.body);
			}
		} catch (BreakException e) {}
		
		return null;
	}

	@Override
	public Object visitCallExpr(Expr.Call expr) {
		Object callee = evaluate(expr.callee);
		
		List<Object> arguments = new ArrayList<>();
		for (Expr argument : expr.arguments) {
			arguments.add(evaluate(argument));
		}

		// Ensures the callee can be called.
		if (!(callee instanceof JidoCallable)) {
			throw new RuntimeError(expr.paren, "Can only call functions and classes.");
		}

		JidoCallable function = (JidoCallable)callee;
		if (arguments.size() != function.arity()) {
			throw new RuntimeError(expr.paren, "Expected " + function.arity() + " arguments bot got " + arguments.size() + ".");
		}
		return function.call(this, arguments);
	}

	private Object evaluate(Expr expr) {
		return expr.accept(this);
	}

	private void execute(Stmt stmt) {
		stmt.accept(this);
	}

	void executeBlock(List<Stmt> statements, Environment environment) {
		Environment previous = this.environment;
		try {
			this.environment = environment;

			for (Stmt statement : statements) {
				execute(statement);
			}
		} finally {
			this.environment = previous;
		}
	}

	@Override
	public Void visitBlockStmt(Stmt.Block stmt) {
		executeBlock(stmt.statements, new Environment(environment));
		return null;
	}

	// False and nil are falsey, everything else is truthy.
	private boolean isTruthy(Object object) {
		if (object == null) return false;
		if (object instanceof Boolean) return (boolean)object;
		return true;
	}

	private boolean isEqual(Object a, Object b) {
		if (a == null && b == null) return true;
		if (a == null) return false;

		return a.equals(b);
	}

	private void checkNumberOperands(Token operator, Object left, Object right) {
		if (left instanceof Double && right instanceof Double) return;
		throw new RuntimeError(operator, "Operands must be numbers.");
	}

	private String stringify(Object object) {
		if (object == null) return "nil";

		if (object instanceof Double) {
			String text = object.toString();
			if (text.endsWith(".0")) {
				text = text.substring(0, text.length() - 2);
			}
			return text;
		}

		return object.toString();
	}
}
