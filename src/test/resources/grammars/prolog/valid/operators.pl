:- dynamic counter/1.
:- discontiguous fact/2.

counter(0).

increment :-
    retract(counter(N)),
    N1 is N + 1,
    assertz(counter(N1)).

classify(X, small) :- X =< 10, !.
classify(X, medium) :- X > 10, X =< 100, !.
classify(_, large).

max(X, Y, Z) :- ( X >= Y -> Z = X ; Z = Y ).

not_member(X, L) :- \+ member(X, L).

bits(X, Y) :- Y is (X /\ 0xff) \/ (0b1010 << 2) >> 1.

same(X, Y) :- X == Y ; X =:= Y.

expr(T) :- T =.. [F|Args], atom(F), is_list(Args).

greeting --> [hello], name.
name --> [world].
