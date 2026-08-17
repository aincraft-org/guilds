package dev.mintychochip.sql;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SQL text with {@code :name} placeholders rewritten to JDBC {@code ?} in appearance order.
 */
public final class ParsedSql {

    private final List<Token> tokens;

    private ParsedSql(List<Token> tokens) {
        this.tokens = List.copyOf(tokens);
    }

    public static ParsedSql parse(String source) {
        Objects.requireNonNull(source, "source");
        return new ParsedSql(tokenize(source));
    }

    public String jdbcSql(Map<String, ?> params) {
        Objects.requireNonNull(params, "params");
        StringBuilder sql = new StringBuilder();
        for (Token token : tokens) {
            if (token instanceof Literal literal) {
                sql.append(literal.text);
            } else if (token instanceof NamedParameter named) {
                Object value = params.get(named.name);
                if (value instanceof Collection<?> collection) {
                    appendPlaceholders(sql, collection.size());
                } else {
                    sql.append('?');
                }
            }
        }
        return sql.toString().strip();
    }

    public List<String> parameterNames(Map<String, ?> params) {
        Objects.requireNonNull(params, "params");
        List<String> names = new ArrayList<>();
        for (Token token : tokens) {
            if (token instanceof NamedParameter named) {
                Object value = params.get(named.name);
                if (value instanceof Collection<?> collection) {
                    for (int i = 0; i < collection.size(); i++) {
                        names.add(named.name);
                    }
                } else {
                    names.add(named.name);
                }
            }
        }
        return List.copyOf(names);
    }

    public void bind(PreparedStatement statement, Map<String, ?> params) throws SQLException {
        Objects.requireNonNull(statement, "statement");
        Objects.requireNonNull(params, "params");
        int index = 1;
        for (Token token : tokens) {
            if (!(token instanceof NamedParameter named)) {
                continue;
            }
            Object value = requireValue(params, named.name);
            if (value instanceof Collection<?> collection) {
                for (Object element : collection) {
                    statement.setObject(index++, element);
                }
            } else {
                statement.setObject(index++, value);
            }
        }
    }

    public ParsedSql withIdentifiers(Map<String, String> identifiers) {
        Objects.requireNonNull(identifiers, "identifiers");
        List<Token> rewritten = new ArrayList<>();
        for (Token token : tokens) {
            if (token instanceof Literal literal) {
                String text = literal.text;
                for (Map.Entry<String, String> entry : identifiers.entrySet()) {
                    text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
                }
                rewritten.add(new Literal(text));
            } else {
                rewritten.add(token);
            }
        }
        return new ParsedSql(rewritten);
    }

    private static Object requireValue(Map<String, ?> params, String name) {
        if (!params.containsKey(name)) {
            throw new IllegalArgumentException("Missing SQL parameter: " + name);
        }
        return params.get(name);
    }

    private static void appendPlaceholders(StringBuilder sql, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("IN-list parameter must contain at least one value");
        }
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
    }

    private static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\'') {
                int end = consumeQuoted(source, i, '\'');
                literal.append(source, i, end);
                i = end;
                continue;
            }
            if (c == '"') {
                int end = consumeQuoted(source, i, '"');
                literal.append(source, i, end);
                i = end;
                continue;
            }
            if (c == '-' && i + 1 < source.length() && source.charAt(i + 1) == '-') {
                int end = source.indexOf('\n', i);
                end = end < 0 ? source.length() : end + 1;
                literal.append(source, i, end);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                end = end < 0 ? source.length() : end + 2;
                literal.append(source, i, end);
                i = end;
                continue;
            }
            if (c == ':' && i + 1 < source.length() && source.charAt(i + 1) == ':') {
                literal.append("::");
                i += 2;
                continue;
            }
            if (c == ':' && i + 1 < source.length() && isNameStart(source.charAt(i + 1))) {
                flushLiteral(tokens, literal);
                int start = i + 1;
                int end = start + 1;
                while (end < source.length() && isNamePart(source.charAt(end))) {
                    end++;
                }
                tokens.add(new NamedParameter(source.substring(start, end)));
                i = end;
                continue;
            }
            literal.append(c);
            i++;
        }
        flushLiteral(tokens, literal);
        return tokens;
    }

    private static int consumeQuoted(String source, int start, char quote) {
        int i = start + 1;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == quote) {
                if (i + 1 < source.length() && source.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return source.length();
    }

    private static void flushLiteral(List<Token> tokens, StringBuilder literal) {
        if (literal.length() == 0) {
            return;
        }
        tokens.add(new Literal(literal.toString()));
        literal.setLength(0);
    }

    private static boolean isNameStart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
    }

    private static boolean isNamePart(char c) {
        return isNameStart(c) || (c >= '0' && c <= '9');
    }

    private sealed interface Token permits Literal, NamedParameter {
    }

    private record Literal(String text) implements Token {
    }

    private record NamedParameter(String name) implements Token {
    }
}
