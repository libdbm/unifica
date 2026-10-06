quote('It''s here').
quote('tab\there\n').
codes("abc", 0'a).
back(`back quoted`).
float(3.14).
float(1.0e+10).
numbers(0o17, 0x1F, 0b101, 42).
neg(X) :- X is - 1 * 2 ** 3 mod 5 rem 2.
graphic(X) :- X = '+', Y = '=', Z = #, write(Y-Z).
curly({a, b, c}).
empty([], {}).
module:qualified(goal).
