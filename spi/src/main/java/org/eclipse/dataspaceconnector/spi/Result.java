package org.eclipse.dataspaceconnector.spi;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static java.util.Collections.emptyList;

public class Result<T> {

    public static <T> Result<T> success(T content) {
        return new Result<>(content, emptyList());
    }

    public static <T> Result<T> failure(String error) {
        return new Result<>(null, List.of(error));
    }

    private final T content;
    private final List<String> errors;

    private Result(T content, @NotNull List<String> errors) {
        this.content = content;
        this.errors = errors;
    }

    public T getContent() {
        return content;
    }

    public boolean invalid() {
        return !errors.isEmpty();
    }

    public String getInvalidMessage() {
        return errors.stream().findFirst().orElseThrow(() -> new EdcException("This result is successful"));
    }
}
