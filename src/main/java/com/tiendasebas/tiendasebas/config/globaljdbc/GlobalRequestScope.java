package com.tiendasebas.tiendasebas.config.globaljdbc;

/** Ámbito interno, fijado por el filtro de rutas; nunca acepta headers ni IDs del cliente. */
final class GlobalRequestScope {
    enum Mode { AUTHENTICATION, OPERATIONS }
    private record State(Mode mode, boolean operationsRequested) {}
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private GlobalRequestScope() {}
    static Mode get() { return CURRENT.get() == null ? null : CURRENT.get().mode(); }
    static void set(Mode mode) { CURRENT.set(new State(mode, mode == Mode.OPERATIONS)); }
    static void prepareOperations() { CURRENT.set(new State(Mode.AUTHENTICATION, true)); }
    static boolean operationsRequested() {
        return CURRENT.get() != null && CURRENT.get().operationsRequested();
    }
    static void authorizeOperations() {
        if (!operationsRequested() || get() != Mode.AUTHENTICATION) {
            throw new IllegalStateException("No existe fase de autenticacion global pendiente");
        }
        CURRENT.set(new State(Mode.OPERATIONS, true));
    }
    static void clear() { CURRENT.remove(); }
}
