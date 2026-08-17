package com.azoth.territory.decree;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based, in-process English → {@link DecreeEffects} transcriber for tests and offline use.
 * <p>
 * Recognizes tax-on-goods phrases such as:
 * <ul>
 *   <li>"all goods that are vegetables are taxed by 15%"</li>
 *   <li>"vegetables are taxed by 15%"</li>
 *   <li>"tax vegetables by 15%"</li>
 *   <li>"iron ore is taxed by 10%"</li>
 * </ul>
 * Resolves category/name labels through the provided {@link GoodsCatalog} so good ids
 * match the catalog the interpreter uses — never hardcodes vegetable ids outside the catalog.
 */
public final class DeterministicDecreeTranscriber implements DecreeTranscriber {
    /** Creates a deterministic transcriber. */
    public DeterministicDecreeTranscriber() {
    }

    /**
     * Grouped patterns that capture a goods label and a percentage.
     * Order: more specific multi-word forms first where helpful.
     */
    private static final List<Pattern> TAX_PATTERNS = List.of(
            // all goods that are <label> are taxed by N%
            Pattern.compile(
                    "(?i)all\\s+goods\\s+that\\s+are\\s+([a-z][a-z\\s_-]*?)\\s+are\\s+taxed\\s+by\\s+(\\d+(?:\\.\\d+)?)\\s*%"
            ),
            // <label> are/is taxed by N%
            Pattern.compile(
                    "(?i)\\b([a-z][a-z\\s_-]*?)\\s+(?:are|is)\\s+taxed\\s+by\\s+(\\d+(?:\\.\\d+)?)\\s*%"
            ),
            // tax <label> by N%
            Pattern.compile(
                    "(?i)\\btax\\s+([a-z][a-z\\s_-]*?)\\s+by\\s+(\\d+(?:\\.\\d+)?)\\s*%"
            ),
            // N% tax on <label>
            Pattern.compile(
                    "(?i)(\\d+(?:\\.\\d+)?)\\s*%\\s+tax\\s+on\\s+([a-z][a-z\\s_-]+)"
            )
    );

    /**
     * {@inheritDoc}
     *
     * @param english English decree text
     * @param catalog goods catalog
     * @return structured decree effects
     */
    @Override
    public DecreeEffects transcribe(String english, GoodsCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        if (english == null || english.isBlank()) {
            return DecreeEffects.empty();
        }
        String text = english.trim();
        List<TaxEffect> taxes = new ArrayList<>();

        for (Pattern p : TAX_PATTERNS) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                String label;
                double percent;
                if (p.pattern().startsWith("(?i)(\\d+")) {
                    // N% tax on <label>
                    percent = Double.parseDouble(m.group(1));
                    label = m.group(2).trim();
                } else {
                    label = m.group(1).trim();
                    percent = Double.parseDouble(m.group(2));
                }
                label = scrubLabel(label);
                List<String> goodIds = catalog.resolveLabel(label);
                if (!goodIds.isEmpty()) {
                    taxes.add(new TaxEffect(goodIds, percent));
                }
            }
            if (!taxes.isEmpty()) {
                // first matching pattern family wins (avoid double-counting same sentence)
                break;
            }
        }
        return taxes.isEmpty() ? DecreeEffects.empty() : DecreeEffects.ofTaxes(taxes);
    }

    private static String scrubLabel(String label) {
        String s = label.trim().toLowerCase(Locale.ROOT);
        // drop leading articles / filler from capture groups
        s = s.replaceFirst("^(?:the|all|any)\\s+", "");
        s = s.replaceFirst("\\s+(?:goods?|items?|products?)$", "");
        return s.trim();
    }
}
