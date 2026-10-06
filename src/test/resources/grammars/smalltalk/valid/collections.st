| numbers total squares |
"Sum and square a collection."
numbers := #(1 2 3 4 5).
total := numbers inject: 0 into: [:sum :each | sum + each].
squares := numbers collect: [:x | x * x].
(total > 10) ifTrue: [Transcript show: 'big'] ifFalse: [Transcript show: 'small'].
Transcript show: total printString; cr; show: squares printString; cr.
^ squares
