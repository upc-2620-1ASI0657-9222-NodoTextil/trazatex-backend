package com.nodotextil.trazatex.organizationaccess.infra;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.nodotextil.trazatex.organizationaccess.domain.model.Email;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.domain.model.User;
import com.nodotextil.trazatex.organizationaccess.domain.model.UserStatus;
import com.nodotextil.trazatex.organizationaccess.infrastructure.security.JwtKeyMaterial;
import com.nodotextil.trazatex.organizationaccess.infrastructure.security.NimbusJwtTokenIssuerAdapter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Ejecuta {@code infra/scripts/generate-jwt-keys.sh} contra una copia temporal de {@code .env.example} (nunca toca el .env real). */
class GenerateJwtKeysScriptTest {

    private static final Path SCRIPT = Path.of("..", "infra", "scripts", "generate-jwt-keys.sh").toAbsolutePath().normalize();
    private static final Path ENV_EXAMPLE = Path.of("..", ".env.example").toAbsolutePath().normalize();

    @TempDir
    Path tmp;

    record Run(int exit, String output) {
    }

    @BeforeAll
    static void requiresBashAndOpenssl() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(SCRIPT), "no se encontró el script");
        Assumptions.assumeTrue(run(List.of("bash", "-c", "command -v openssl")).exit() == 0, "openssl no está disponible");
    }

    private static Run run(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        return new Run(process.exitValue(), output);
    }

    private Path freshEnv() throws IOException {
        Path env = tmp.resolve(UUID.randomUUID() + ".env");
        Files.copy(ENV_EXAMPLE, env);
        return env;
    }

    private Run script(Path env, String... extra) throws Exception {
        List<String> command = new java.util.ArrayList<>(List.of("bash", SCRIPT.toString(), "--env-file", env.toString()));
        command.addAll(List.of(extra));
        return run(command);
    }

    private static Map<String, String> read(Path env) throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(env)) {
            int eq = line.indexOf('=');
            if (eq > 0 && !line.startsWith("#")) {
                values.put(line.substring(0, eq), line.substring(eq + 1));
            }
        }
        return values;
    }

    @Test
    void writesAValidRsa2048PairThatTheServiceCanLoadAndUseToSignAndVerify() throws Exception {
        Path env = freshEnv();

        Run result = script(env);

        assertThat(result.exit()).as(result.output()).isZero();
        Map<String, String> values = read(env);
        assertThat(values.get("JWT_PRIVATE_KEY")).isNotBlank().doesNotContain("\n").doesNotContain(" ");
        assertThat(values.get("JWT_KEY_ID")).matches("trazatex-\\d{8}-[0-9a-f]{8}");

        JwtKeyMaterial keys = JwtKeyMaterial.load(values.get("JWT_PRIVATE_KEY"), values.get("JWT_PUBLIC_KEY"), values.get("JWT_KEY_ID"));
        assertThat(keys.publicKey().getModulus().bitLength()).isEqualTo(2048);
        User user = User.restore(UUID.randomUUID(), null, new Email("o@nodotextil.pe"), "Rosa", "Díaz", null, "h",
                Role.LICENSE_OWNER, UserStatus.ACTIVE, Instant.now());
        SignedJWT jwt = SignedJWT.parse(new NimbusJwtTokenIssuerAdapter(keys, Duration.ofHours(8)).issue(user, Instant.now()).token());
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(values.get("JWT_KEY_ID"));
        assertThat(jwt.verify(new RSASSAVerifier(keys.publicKey()))).isTrue();
    }

    @Test
    void theKeysAreDecodedPemsInTheExpectedFormats() throws Exception {
        Path env = freshEnv();
        assertThat(script(env).exit()).isZero();
        Map<String, String> values = read(env);

        String privatePem = new String(java.util.Base64.getDecoder().decode(values.get("JWT_PRIVATE_KEY")));
        String publicPem = new String(java.util.Base64.getDecoder().decode(values.get("JWT_PUBLIC_KEY")));

        assertThat(privatePem).startsWith("-----BEGIN PRIVATE KEY-----").contains("-----END PRIVATE KEY-----");
        assertThat(publicPem).startsWith("-----BEGIN PUBLIC KEY-----").contains("-----END PUBLIC KEY-----");
    }

    @Test
    void leavesTheRestOfTheFileUntouchedAndRestrictsPermissions() throws Exception {
        Path env = freshEnv();
        List<String> before = Files.readAllLines(env).stream().filter(l -> !l.startsWith("JWT_")).toList();

        assertThat(script(env).exit()).isZero();

        assertThat(Files.readAllLines(env).stream().filter(l -> !l.startsWith("JWT_")).toList()).isEqualTo(before);
        assertThat(Files.getPosixFilePermissions(env)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        assertThat(Files.readAllLines(env).stream().filter(l -> l.startsWith("JWT_")).count()).isEqualTo(3);
    }

    @Test
    void appendsTheVariablesIfTheFileDoesNotHaveThem() throws Exception {
        Path env = tmp.resolve("minimal.env");
        Files.writeString(env, "FOO=bar\n");

        assertThat(script(env).exit()).isZero();

        assertThat(read(env)).containsKeys("FOO", "JWT_PRIVATE_KEY", "JWT_PUBLIC_KEY", "JWT_KEY_ID");
    }

    @Test
    void doesNotPrintTheKeys() throws Exception {
        Path env = freshEnv();
        Run result = script(env);

        assertThat(result.output()).doesNotContain(read(env).get("JWT_PRIVATE_KEY")).doesNotContain("PRIVATE KEY").contains("JWT_KEY_ID");
    }

    @Test
    void refusesToOverwriteAnExistingPairWithoutForce() throws Exception {
        Path env = freshEnv();
        assertThat(script(env).exit()).isZero();
        String before = Files.readString(env);

        Run second = script(env);

        assertThat(second.exit()).isNotZero();
        assertThat(second.output()).contains("--force").contains("JWT_PRIVATE_KEY");
        assertThat(Files.readString(env)).isEqualTo(before);
    }

    @Test
    void forceReplacesThePairWithANewOne() throws Exception {
        Path env = freshEnv();
        assertThat(script(env).exit()).isZero();
        Map<String, String> first = read(env);

        Run forced = script(env, "--force");

        assertThat(forced.exit()).as(forced.output()).isZero();
        Map<String, String> second = read(env);
        assertThat(second.get("JWT_PRIVATE_KEY")).isNotEqualTo(first.get("JWT_PRIVATE_KEY"));
        assertThat(second.get("JWT_KEY_ID")).isNotEqualTo(first.get("JWT_KEY_ID"));
        JwtKeyMaterial.load(second.get("JWT_PRIVATE_KEY"), second.get("JWT_PUBLIC_KEY"), second.get("JWT_KEY_ID"));
        assertThat(Files.readAllLines(env).stream().filter(l -> l.startsWith("JWT_PRIVATE_KEY=")).count()).isEqualTo(1);
    }

    @Test
    void failsWithAClearMessageIfTheEnvFileDoesNotExist() throws Exception {
        Run result = script(tmp.resolve("no-existe.env"));

        assertThat(result.exit()).isNotZero();
        assertThat(result.output()).contains("no existe").contains(".env.example");
    }

    @Test
    void leavesNoTemporaryKeyFilesBehindEvenWhenItFails() throws Exception {
        Path scratch = Files.createDirectory(tmp.resolve("scratch"));
        Path env = freshEnv();

        assertThat(exitCodeWithTmpdir(env, scratch)).isZero();
        assertThat(exitCodeWithTmpdir(env, scratch)).isNotZero();   // el segundo se niega: el par ya existe

        try (var entries = Files.list(scratch)) {
            assertThat(entries.toList()).as("el directorio temporal con las claves debe borrarse").isEmpty();
        }
    }

    private static int exitCodeWithTmpdir(Path env, Path scratch) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("bash", SCRIPT.toString(), "--env-file", env.toString()).redirectErrorStream(true);
        builder.environment().put("TMPDIR", scratch.toString());
        Process process = builder.start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }
}
