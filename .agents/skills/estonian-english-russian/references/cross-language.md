# Cross-language translation and comparison

## Contents

- Translation workflow
- Meaning dimensions to preserve
- Estonian ↔ English
- Russian ↔ English
- Estonian ↔ Russian
- Localization
- Quality assurance

## Translation workflow

1. Read the complete source before translating.
2. Identify purpose, audience, domain, register, and whether the text is informative, persuasive, legal, technical, literary, or conversational.
3. Resolve referents, ellipsis, terminology, tense, aspect, modality, negation, politeness, and information structure.
4. Draft for meaning.
5. Rewrite for idiomatic target-language syntax and collocation.
6. Compare source and target sentence by sentence for omissions, additions, reversals, and altered strength.
7. Perform a target-only read for naturalness.

Do not preserve source word order, punctuation, sentence length, or metaphors when they obstruct the same communicative effect. Do preserve intentional repetition, ambiguity, rhythm, or marked style when it matters.

## Meaning dimensions to preserve

- Who did, experienced, knew, wanted, permitted, or was obliged to do what.
- Whether the statement is factual, inferred, possible, desired, conditional, reported, ironic, or hypothetical.
- Event boundaries, duration, repetition, result, and current relevance.
- Positive versus negative meaning and the scope of negation.
- Formality, social distance, warmth, urgency, and certainty.
- Topic and focus: what is already known versus what is presented as new or contrastive.
- Terminology, units, dates, quantities, identifiers, and formatting.

## Estonian ↔ English

- Supply English articles from discourse meaning; do not derive them mechanically from Estonian word forms.
- Do not translate English articles into Estonian demonstratives unless demonstrative meaning is intended.
- Recast Estonian adessive possession idiomatically as English `have` where appropriate, and reverse the structure naturally into Estonian.
- Resolve Estonian `tema/ta` without guessing gender. Repeat a role label, use singular `they`, or ask only if the distinction is material.
- Map Estonian total/partitive object meaning to English aspect, quantity, determiner choice, or lexical phrasing as context requires.
- English prepositions may correspond to Estonian local cases, postpositions, particles, or lexical government.
- Estonian can omit contextually obvious pronouns more readily; English usually requires an overt clause subject.
- Rebuild English noun stacks as Estonian compounds, genitives, adjectives, or clauses.

## Russian ↔ English

- Supply or remove articles based on reference and countability, not by inserting demonstratives.
- Map Russian aspect to the English tense–aspect system using discourse context, not a fixed perfective = simple rule.
- Translate Russian present nominal predicates with an English copula where required.
- Do not preserve Russian negative concord as nonstandard English double negation unless voice requires it.
- Resolve subject omission and grammatical gender carefully. Use neutral restructuring or singular `they` when the source does not identify a person’s gender.
- Translate motion verbs from path, direction, repetition, mode, and aspect rather than from a single dictionary gloss.
- Preserve the distinction between informal singular and formal/plural address when English wording or context can express it; otherwise note a consequential loss only when relevant.
- Reorder topic–focus structures into natural English while preserving emphasis.

## Estonian ↔ Russian

- Do not assume similarities caused by long contact make morphology or syntax equivalent.
- Map Estonian case meanings and Russian preposition-plus-case government through meaning, not case labels.
- Compare Estonian object boundedness and Russian verbal aspect as interacting semantic systems, not direct equivalents.
- Russian requires grammatical gender agreement; Estonian generally does not encode the referent’s gender. Avoid guessing when translating into Russian; restructure or request the needed choice.
- Estonian and Russian both permit flexible word order, but their neutral patterns and focus effects differ.
- Russian has a `ты/вы` distinction; Estonian singular/plural second-person forms can also express familiarity and politeness, but conventions are not identical.
- Rebuild compounds and noun phrases according to target-language conventions.
- Present-tense copular patterns differ: translate the construction, not the missing or present surface word.

## False friends and deceptive similarity

- Treat similar-looking words as hypotheses, not evidence of identical meaning.
- Check semantic range, register, collocation, and connotation.
- Watch international vocabulary whose everyday sense differs across languages.
- Check borrowed administrative and technical terms against current target-language convention.
- Explain a false friend only when it caused an error or the user is learning.

## Localization

- Localize quotation marks, decimal and grouping separators, date and time formats, currency placement, units, dashes, nonbreaking spaces, capitalization, and address conventions.
- Preserve machine-readable formats when required, including ISO dates, placeholders, message keys, markup, and code.
- Keep product terms consistent with a supplied glossary or established interface.
- Respect character limits and plural-category behavior in software strings.
- Do not translate legal entity names, official document titles, or official personal spellings without authoritative context.
- For formal or high-impact material, require a qualified domain review even when the language is fluent.

## Quality assurance

Use this checklist:

- No source segment omitted or duplicated.
- No added fact, actor, gender, cause, certainty, or promise.
- Negation and its scope preserved.
- Tense, aspect, modality, and conditionality preserved.
- Pronoun reference and politeness preserved or consciously adapted.
- Numbers, units, dates, links, placeholders, and formatting exact.
- Terminology consistent.
- Grammar and punctuation target-native.
- Collocations and word order natural.
- Register and emotional force appropriate.
- Ambiguities retained or responsibly resolved.
