package dev.guildy.config;

/**
 * Config classes implementing this get a chance to clamp/fix values after loading.
 */
public interface Validatable {
	void validate();
}
