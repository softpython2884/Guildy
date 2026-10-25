package dev.guildy.builds;

/**
 * A schematic that cannot be used: the message is a lang key ("build.error.too_big"...), shown with its arguments in
 * the menus. Never thrown out of the loader (the worker turns it into an error entry).
 */
public final class SchematicException extends Exception {
	private final transient Object[] args;

	public SchematicException(String key, Object... args) {
		super(key);
		this.args = args == null ? new Object[0] : args.clone();
	}

	public String key() {
		return getMessage();
	}

	public Object[] args() {
		return args.clone();
	}
}
