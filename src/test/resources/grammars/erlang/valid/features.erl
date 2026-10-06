%% Hand-written sample (not from grammars-v4): records, types, binaries,
%% comprehensions, try/catch, receive/after, if and funs.
-module(features).
-export([run/1, parse/1]).
-record(point, {x = 0 :: integer(), y = 0 :: integer()}).
-type shape() :: {circle, float()} | {rect, non_neg_integer(), 1..100}.
-spec area(shape()) -> float().

area({circle, R}) -> 3.14159 * R * R;
area({rect, W, H}) -> W * H * 1.0.

parse(<<Size:16/big-unsigned, Rest/binary>>) ->
    <<Body:Size/binary, _/binary>> = Rest,
    [X * 2 || X <- binary_to_list(Body), X rem 2 =:= 0];
parse(_) ->
    << <<B>> || <<B>> <= <<"abc">> >>.

run(P = #point{x = X}) when X > 0, is_integer(X); X == -1 ->
    Q = P#point{y = X + 1},
    Y = Q#point.y,
    F = fun area/1,
    G = fun lists:reverse/1,
    try F({circle, 2.0}) of
        V when V >= 0 -> {ok, V, G([Y])}
    catch
        error:badarith:Stack -> {error, Stack};
        throw:Reason -> Reason
    after
        ok
    end,
    receive
        {msg, M} -> M
    after 1000 ->
        if
            Y > 10 -> big;
            true -> small
        end
    end,
    Z = case $a of $\n -> newline; _ -> 'other atom' end,
    not (Z == 16#FF) andalso Y bsl 2 orelse (catch erlang:error(boom)),
    [H | T] = [1, 2, 3] ++ [4] -- [2],
    begin H, T end.
