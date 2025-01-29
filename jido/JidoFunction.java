package jido;

import java.util.List;

public class JidoFunction implements JidoCallable {
    private final Stmt.Function declaration;
		private final Environment closure;

    JidoFunction(Stmt.Function declaration, Environment closure) {
			this.closure = closure;
			this.declaration = declaration;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
			/* A function encapsulates its parameters - no other code outside the function can see them
			   This means each function gets its own environment where it stores those variables */
			Environment environment = new Environment(closure);

      for (int i = 0; i < declaration.params.size(); i++) {
        environment.define(declaration.params.get(i).lexeme,
            arguments.get(i));
      }
			
			try {
				interpreter.executeBlock(declaration.body, environment);
			} catch (Return returnValue) {
				return returnValue.value;
			}

      return null;
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

		@Override
		public String toString() {
			return "<fn " + declaration.name.lexeme + ">";
		}
}
