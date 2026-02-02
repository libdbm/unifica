package com.libdbm.ugf.generator;

import com.libdbm.ugf.features.Value;

import java.util.Map;
import java.util.Optional;

/**
 * Strategy interface for generating tokens matching a TokenMatch rule element.
 *
 * <p>TokenMatch elements in unification grammars match tokens by name or features rather than
 * literal text. During generation, this strategy provides domain-specific logic for producing text
 * that would match such elements.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Lexicon-based: lookup words matching features ({@code TOKEN[pos: noun, num: pl]})
 *   <li>Random sampling: generate random words from a vocabulary
 *   <li>Feature-driven: use features to construct appropriate text
 * </ul>
 */
@FunctionalInterface
public interface TokenMatchGenerator {

    /**
     * Generate text for a TokenMatch element.
     *
     * <p>The bindings map contains any variable bindings accumulated during generation, which may
     * include constraints on the token's features. The label (if present) can be used to look up
     * specific feature requirements in the bindings.
     *
     * @param label    the label associated with this token match (may be null)
     * @param bindings variable bindings that may constrain the token generation
     * @return generated text, or empty if generation fails
     */
    Optional<String> generate(String label, Map<String, Value> bindings);
}
