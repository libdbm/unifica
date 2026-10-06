| a b c |
a := #(1 2.5 $a 'text' #sym #at:put: foo bar: + nil true false (nested 1 2) #(inner)).
b := {1 + 2. 3 * 4. self size}.
c := #{1. 2}.
a := 16rFF + 1e10 + 3.14e2.
b := #+ , #'quoted symbol' , #with:with:.
c := super initialize; yourself.
^ a = b | (b ~= c) & true
