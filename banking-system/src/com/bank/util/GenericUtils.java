package com.bank.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * GENERICS demo: reusable, type-safe helper methods that work for ANY element
 * type (Transaction, Account, String, ...). The compiler checks types, so no
 * casts are needed at the call site.
 */
public final class GenericUtils {

    private GenericUtils() { }

    /**
     * Groups items by a key extracted with {@code keyFn}.
     *
     * {@code <T, K>} declares two type parameters.
     * {@code ? super T} / {@code ? extends K} are bounded wildcards (PECS rule:
     * Producer Extends, Consumer Super) which make the method more flexible.
     */
    public static <T, K> Map<K, List<T>> groupBy(Collection<? extends T> items,
                                                 Function<? super T, ? extends K> keyFn) {
        Map<K, List<T>> result = new LinkedHashMap<>();
        for (T item : items) {
            result.computeIfAbsent(keyFn.apply(item), k -> new ArrayList<>()).add(item);
        }
        return result;
    }

    /** Returns the first {@code n} items according to the given ordering. */
    public static <T> List<T> topN(Collection<T> items, Comparator<? super T> ordering, int n) {
        return items.stream()
                .sorted(ordering)
                .limit(Math.max(0, n))
                .collect(Collectors.toList());
    }
}
