---
name: estonian-english-russian
description: Use when writing, rewriting, proofreading, explaining, translating, localizing, teaching, or evaluating Estonian (eesti keel), English, Russian (русский язык), or multilingual text involving these languages.
---

# Estonian, English, and Russian

## When to use

Use for grammar, spelling, punctuation, style, terminology, tone, pronunciation, transliteration, language learning, contrastive analysis, and translation or localization among Estonian, English, and Russian.

## Do not use

- Do not certify a translation or replace a qualified legal, medical, safety, or domain reviewer.
- Do not infer a person’s identity or gender when the source does not establish it.
- Do not fabricate a linguistic rule, etymology, dictionary entry, quotation, or source.
- Do not preserve source-language syntax when it makes the target text unnatural or changes the intended effect.

## Workflow

1. Identify the source language, target language, task, audience, register, and regional variety from the request.
2. Preserve meaning, intent, factual content, formatting, placeholders, numbers, units, and product terminology.
3. Write idiomatically in the target language instead of mechanically mirroring source syntax.
4. Distinguish errors from valid stylistic alternatives. Do not “correct” deliberate voice, dialect, quotations, or terminology without reason.
5. State meaningful ambiguity briefly and offer the best interpretation. Ask a question only when the unresolved choice would materially change the result.
6. Never invent an etymology, rule, citation, quotation, or dictionary entry. Verify niche, disputed, legal, medical, or publication-critical usage with an authoritative current source when tools are available.

## Load only the needed reference

- Read [references/estonian.md](references/estonian.md) for Estonian grammar, orthography, morphology, word order, usage, and pronunciation.
- Read [references/english.md](references/english.md) for English grammar, spelling varieties, punctuation, style, usage, and pronunciation.
- Read [references/russian.md](references/russian.md) for Russian grammar, orthography, morphology, aspect, stress, usage, and transliteration.
- Read [references/cross-language.md](references/cross-language.md) for translation, localization, false friends, contrastive problems, and multilingual quality checks.

Read every reference relevant to a request that spans more than one language.

## Choose the response form

### Translate

- Return the translation first.
- Preserve paragraphing and list structure unless localization requires a change.
- Retain placeholders, Markdown, tags, URLs, filenames, code, and identifiers exactly unless the user requests localization.
- Flag only consequential alternatives, such as formal versus informal address or an ambiguous source phrase.
- For a back-translation request, translate the result independently rather than reconstructing the source.

### Proofread or edit

- Return a clean corrected version first.
- Explain only the changes that teach a rule, resolve ambiguity, or alter meaning.
- Preserve the requested spelling standard and register.
- If the user asks for tracked changes but the medium does not support them, show concise before → after pairs.

### Explain

- Name the rule in plain language.
- Give one minimal correct example and, when useful, one contrasting example.
- Mark grammatical terms in the user’s preferred language where practical.
- Separate firm rules from preference, register, or frequency.

### Write

- Match audience, purpose, tone, length, and channel.
- Prefer natural collocations and information structure.
- Avoid translating an unstated source-language pattern into the output.

### Teach

- Calibrate to the learner’s level.
- Present one concept at a time, then examples, then a short exercise.
- Include an answer key only when requested or when self-study is clearly intended.
- For Russian, mark stress where pronunciation is being taught. For Estonian, explain quantity only when it matters.

## Handle uncertainty

- Use corpus or dictionary evidence for rare words, changing terminology, disputed usage, and exact collocations.
- Prefer EKI resources for normative and descriptive Estonian; established learner and editorial dictionaries for English; and authoritative contemporary dictionaries and spelling resources for Russian.
- Treat machine translation as a draft, never as authority.
- If sources disagree, identify whether the difference is regional, normative, historical, technical, or stylistic.

## Verification

- Verify semantics, negation, modality, tense, aspect, politeness, names or role labels, numbers, dates, and units.
- Check agreement, government, articles or determiners, prepositions or cases, punctuation, and natural collocations.
- Confirm the output contains only the requested language unless explanations or alternatives were requested.
- Avoid exposing private source material or adding identifying details absent from the source.

## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md` before text-bearing work. Never repeat protected identity values; use generic role labels. Run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion, then semantically review the changed text.
