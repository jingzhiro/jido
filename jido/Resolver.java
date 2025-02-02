// package jido;

// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.Stack;

// public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
// 	private final Interpreter interpreter;
// 	private final Stack<Map<String, Boolean>> scopes = new Stack<>();

// 	Resolver(Interpreter interpreter) {
// 		this.interpreter = interpreter;
// 	}

// 	void resolve(List<Stmt> statements) {
// 		for (Stmt statement: statements) {
// 			resolve(statement);
// 		}
// 	}

// 	private void resolve(Stmt stmt) {
// 		stmt.accept(this);
// 	}

// 	private void beginScope() {
// 		scopes.push(new HashMap<String, Boolean>());
// 	}

// 	private void endScope() {
// 		scopes.pop();
// 	}

	
// 	@Override
// 	public Void visitBlockStmt(Stmt.Block stmt) {
// 		beginScope();
// 		resolve(stmt.statements);
// 		endScope();
// 		return null;
// 	}

// 	@Override
// 	public Void visitVarStmt(Stmt.Var stmt) {
// 		declare(stmt.name);
// 		if (stmt.initializer != null) {
// 			resolve(stmt.initializer);
// 		}
// 		define(stmt.name);
// 		return null;
// 	}

// 	@Override
// 	public Void visitVariableExpr(Expr.Variable expr) {
// 		if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
// 			Jido.error(expr.name, "Can't read local variable in its own initialiser");
// 		}
		
// 		resolveLocal(expr, expr.name);
// 		return null;
// 	}
	
// 	// Adds the variable to the innermost scope
// 	private void declare(Token name) {
// 		if (scopes.isEmpty()) return;

// 		Map<String, Boolean> scope = scopes.peek();
// 		scope.put(name.lexeme, false);
// 	}

// 	// After
// 	private void define (Token name) {
// 		if (scopes.isEmpty()) return;
// 		scopes.peek().put(name.lexeme, true);
// 	}
// }
