% List utilities.
append([], L, L).
append([H|T], L, [H|R]) :- append(T, L, R).

member(X, [X|_]).
member(X, [_|T]) :- member(X, T).

/* Naive reverse. */
nrev([], []).
nrev([H|T], R) :- nrev(T, RT), append(RT, [H], R).

length_([], 0).
length_([_|T], N) :- length_(T, N0), N is N0 + 1.
