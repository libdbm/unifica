-module(bad).

f(X) ->
    case X of
        1 -> one;
        _ -> other
.
