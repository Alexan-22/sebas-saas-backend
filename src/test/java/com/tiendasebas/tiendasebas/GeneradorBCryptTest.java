package com.tiendasebas.tiendasebas;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.Console;
import java.util.Arrays;
import java.util.Scanner;

public class GeneradorBCryptTest {

    @Test
    void generarHash() {

        Console console = System.console();

        if (console == null) {
            throw new IllegalStateException(
                "Ejecutar en una consola Java interactiva. " +
                "No introducir contraseñas como argumentos."
            );
        }

        char[] password = console.readPassword(
            "Nueva contraseña Super Admin: "
        );

        try {
            if (password == null || password.length < 12) {
                throw new IllegalArgumentException(
                    "La contraseña debe tener al menos 12 caracteres"
                );
            }

            BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder(12);

            String hash = encoder.encode(new String(password));

            System.out.println("HASH BCrypt:");
            System.out.println(hash);

        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }
}
