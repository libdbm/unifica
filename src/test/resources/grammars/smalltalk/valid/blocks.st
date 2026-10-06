"Blocks, temporaries and non-local return."
| counter block |
counter := 0.
block := [:n | | doubled | doubled := n * 2. counter := counter + doubled. doubled].
1 to: 10 do: [:i | block value: i].
[counter < 100] whileTrue: [counter := counter + 1].
[:x :y | x max: y] value: 3 value: 4.
[] value.
[:unused] value: nil.
counter>50 ifTrue: [^ counter].
^self
