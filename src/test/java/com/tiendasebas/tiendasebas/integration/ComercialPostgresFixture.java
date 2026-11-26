package com.tiendasebas.tiendasebas.integration;

import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Clúster PostgreSQL privado: no usa servicios existentes ni scripts SQL del proyecto. */
final class ComercialPostgresFixture implements AutoCloseable {
    private final boolean windows = System.getProperty("os.name").startsWith("Windows");
    private final Path binaries = Path.of(System.getenv().getOrDefault("POSTGRES_TEST_BIN",
            windows ? "C:/Program Files/PostgreSQL/17/bin" : "/usr/lib/postgresql/17/bin"));
    private final String username = "comercial_fixture";
    private final String password = UUID.randomUUID().toString();
    private Path base, root, data;
    private int port;
    private boolean started;

    void start() throws Exception {
        PostgresTestSafety.rejectExternalConfiguration(System.getenv());
        PostgresTestSafety.rejectExternalConfiguration(System.getProperties());
        for (String exe : List.of("initdb", "pg_ctl", "postgres"))
            if (!Files.isRegularFile(binario(exe))) throw new IllegalStateException("Se requiere PostgreSQL 17 local para esta fixture");
        base = Path.of("target/comercial-postgres").toAbsolutePath().normalize();
        Files.createDirectories(base);
        base = base.toRealPath();
        root = Files.createTempDirectory(base, "fixture-").toRealPath();
        data = root.resolve("data");
        try (var socket = new java.net.ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { port = socket.getLocalPort(); }
        Path passwordFile = root.resolve("init-password.txt");
        try {
            Files.writeString(passwordFile, password, StandardOpenOption.CREATE_NEW);
            run("initdb", "-D", data.toString(), "--username=" + username,
                    "--auth=scram-sha-256", "--pwfile=" + passwordFile, "--encoding=UTF8", "--locale=C");
        } finally { Files.deleteIfExists(passwordFile); }
        Path serverLog = root.resolve("postgres.log");
        var server = new ProcessBuilder(binario("postgres").toString(), "-D", data.toString(), "-h", "127.0.0.1",
                "-p", Integer.toString(port), "-c", "unix_socket_directories=")
                .redirectErrorStream(true).redirectOutput(serverLog.toFile());
        server.environment().keySet().removeIf(k -> k.toUpperCase(Locale.ROOT).startsWith("PG"));
        var process = server.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (true) {
            try (var connection = DriverManager.getConnection(url("postgres"), username, password)) { checkDataDirectory(connection); break; }
            catch (java.sql.SQLException ex) {
                if (!process.isAlive() || System.nanoTime() > deadline) {
                    String startupLog = Files.readString(serverLog).strip();
                    throw new IllegalStateException("No arrancó PostgreSQL privado en loopback: " + startupLog, ex);
                }
                Thread.sleep(200);
            }
        }
        started = true;
        try (var connection = DriverManager.getConnection(url("postgres"), username, password)) {
            checkDataDirectory(connection);
            connection.createStatement().execute("CREATE DATABASE comercial_aislado");
        }
        verifiedUrl();
        System.out.printf("PostgreSQL aislado verificado: servidor=127.0.0.1 puerto=%d base=comercial_aislado usuario=%s%n",
                port, username);
    }
    String getUsername() { return username; }
    String getPassword() { return password; }
    private String url(String database) { return "jdbc:postgresql://127.0.0.1:" + port + "/" + database; }
    String verifiedUrl() {
        if (!started || root == null || !Files.isRegularFile(data.resolve("postmaster.pid")))
            throw new IllegalStateException("PostgreSQL privado no está iniciado");
        String url = url("comercial_aislado");
        URI address = URI.create(url.substring("jdbc:".length()));
        if (!"127.0.0.1".equals(address.getHost()) || address.getPort() != port || !"/comercial_aislado".equals(address.getPath()))
            throw new IllegalStateException("Destino PostgreSQL no permitido");
        try (var connection = DriverManager.getConnection(url, username, password)) {
            checkDataDirectory(connection);
            try (var result = connection.createStatement().executeQuery("SELECT current_database()")) {
                if (!result.next() || !"comercial_aislado".equals(result.getString(1))) throw new IllegalStateException("Base externa rechazada");
            }
        } catch (Exception ex) { throw new IllegalStateException("No se pudo verificar PostgreSQL privado", ex); }
        return url;
    }
    private void checkDataDirectory(java.sql.Connection connection) throws Exception {
        try (var result = connection.createStatement().executeQuery("SHOW data_directory")) {
            if (!result.next() || !Path.of(result.getString(1)).toRealPath().equals(data.toRealPath()))
                throw new IllegalStateException("El directorio de PostgreSQL no pertenece a esta fixture");
        }
    }
    private Path binario(String command) {
        return binaries.resolve(windows ? command + ".exe" : command);
    }

    private void run(String executable, String... args) throws Exception {
        var command = new ArrayList<String>(); command.add(binario(executable).toString()); command.addAll(List.of(args));
        var builder = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(root.resolve("tools.log").toFile()));
        // Nunca heredar credenciales ni direccionamiento PostgreSQL de otro entorno.
        builder.environment().keySet().removeIf(k -> k.toUpperCase(Locale.ROOT).startsWith("PG"));
        var process = builder.start();
        if (!process.waitFor(180, TimeUnit.SECONDS)) {
            process.descendants().forEach(p->p.destroyForcibly()); process.destroyForcibly(); process.waitFor(10,TimeUnit.SECONDS); throw new IllegalStateException("Tiempo agotado en la herramienta PostgreSQL de prueba");
        }
        if (process.exitValue() != 0) {
            for (String log : List.of("tools.log", "postgres.log")) {
                Path source = root.resolve(log);
                if (Files.exists(source)) Files.copy(source, base.resolve("ultimo-" + log), StandardCopyOption.REPLACE_EXISTING);
            }
            throw new IllegalStateException("La herramienta PostgreSQL de prueba falló: " + executable);
        }
    }
    @Override public void close() throws Exception {
        if (root == null) return;
        // Antes de parar o borrar, verificar el destino absoluto y que es hijo directo del área de fixtures.
        Path owned = root.toRealPath();
        if (!owned.startsWith(base) || !owned.getParent().equals(base) || !owned.getFileName().toString().startsWith("fixture-"))
            throw new IllegalStateException("Se rechazó limpiar un directorio ajeno a la fixture");
        if (Files.exists(data.resolve("postmaster.pid")))
            run("pg_ctl", "-D", data.toString(), "-m", "fast", "-w", "-t", "30", "stop");
        started = false;
        try (var files = Files.walk(owned)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
