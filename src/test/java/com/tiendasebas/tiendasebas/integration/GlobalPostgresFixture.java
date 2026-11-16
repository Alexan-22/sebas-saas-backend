package com.tiendasebas.tiendasebas.integration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.json.JsonMapper;

/** Fixture nueva: Node verifica rutas canonicas y el JDBC comprueba el servidor propio antes de DDL. */
final class GlobalPostgresFixture implements AutoCloseable {
    private Process process;
    private int port;
    private String password;
    private String dataDirectory;
    private String clusterId;
    private static final String USER = "global_node_fixture";

    void start() throws Exception {
        PostgresTestSafety.rejectExternalConfiguration(System.getenv());
        PostgresTestSafety.rejectExternalConfiguration(System.getProperties());
        Path helper = Path.of("src/test/resources/global-postgres-fixture.cjs").toAbsolutePath();
        if (!Files.isRegularFile(helper)) throw new IllegalStateException("Falta auxiliar local");
        var builder = new ProcessBuilder(System.getProperty("test.node.executable", "node"), helper.toString());
        builder.environment().keySet().removeIf(k -> !k.matches("(?i)(SystemRoot|WINDIR|ComSpec|USERPROFILE|HOMEDRIVE|HOMEPATH|Path|TEMP|TMP|APPDATA|LOCALAPPDATA|ProgramFiles|ProgramData|USERNAME|USERDOMAIN)"));
        Files.createDirectories(Path.of("target"));
        builder.redirectError(Path.of("target/global-node-fixture-error.log").toFile());
        process = builder.start();
        String phase = "handshake";
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8));
            String line = CompletableFuture.supplyAsync(() -> {
                try { return reader.readLine(); } catch (java.io.IOException ex) { throw new IllegalStateException("Canal de fixture cerrado"); }
            }).get(45,TimeUnit.SECONDS);
            if (line == null) throw new IllegalStateException("Fixture no devolvio handshake");
            phase = "parse-handshake";
            var state = JsonMapper.builder().build().readTree(line);
            phase = "campos-handshake";
            port = state.get("port").asInt();
            password = state.get("password").asText();
            dataDirectory = state.get("dataDirectory").asText();
            clusterId = state.get("clusterId").asText();
            if (!USER.equals(state.get("username").asText()) || port<1 || port>65535
                    || !password.matches("[0-9a-f-]{36}") || !clusterId.matches("[0-9a-f-]{36}")) {
                throw new IllegalStateException("Handshake local no valido");
            }
            phase = "verificacion-jdbc";
            verifiedUrl();
        } catch (Exception ex) {
            close();
            // No adjuntar handshake ni causa de parser: contienen contrasena efimera.
            throw new IllegalStateException("Fixture local fallo en " + phase + ": " + ex.getClass().getSimpleName()
                    + (ex instanceof java.sql.SQLException sql ? " SQLState=" + sql.getSQLState() : ""));
        }
    }

    private String url() { return "jdbc:postgresql://127.0.0.1:"+port+"/postgres?connectTimeout=5&socketTimeout=15"; }
    String verifiedUrl() throws Exception {
        if (process == null || !process.isAlive()) throw new IllegalStateException("Fixture no activa");
        try (var c = DriverManager.getConnection(url(),USER,password);
             var rs = c.createStatement().executeQuery("SELECT current_setting('data_directory'),current_setting('cluster_name'),host(inet_server_addr()),current_user")) {
            if (!rs.next() || !normalized(dataDirectory).equals(normalized(rs.getString(1)))
                    || !clusterId.equals(rs.getString(2)) || !"127.0.0.1".equals(rs.getString(3)) || !USER.equals(rs.getString(4))) {
                throw new IllegalStateException("Servidor no corresponde al cluster privado");
            }
        }
        return url();
    }
    private static String normalized(String p) { return Path.of(p).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT); }
    String getUsername() { return USER; }
    String getPassword() { return password; }

    @Override
    public void close() throws Exception {
        if (process != null && process.isAlive()) {
            process.getOutputStream().write("STOP\n".getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            if (!process.waitFor(20,TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(10,TimeUnit.SECONDS)) process.destroyForcibly();
                throw new IllegalStateException("Fixture no confirmo apagado");
            }
            if (process.exitValue()!=0) throw new IllegalStateException("Fallo al detener cluster privado");
        }
    }
}
