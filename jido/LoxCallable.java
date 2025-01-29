package jido;

import java.util.List;

interface JidoCallable {
  int arity();
  Object call(Interpreter interpreter, List<Object> arguments);
}
