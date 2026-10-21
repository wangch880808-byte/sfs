package cn.nus.zknu.server.config;

import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "nus.admin-bootstrap", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AdminBootstrapProperties.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class AdminPasswordBootstrapRunner implements ApplicationRunner {

    static final String BASELINE_ADMIN_PASSWORD_HASH =
            "$2a$04$KljJDa/LK7QfDm0lF5OhuePhlPfjRH3tB2Wu351Uidz.oQGJXevPi";
    private static final Set<PosixFilePermission> REQUIRED_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    private static final Set<String> FORBIDDEN_PASSWORDS = Set.of(
            "123456", "admin", "admin123", "admin123456", "password", "password123", "nus123456");

    private final AdminBootstrapProperties properties;
    private final AdminUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminPasswordBootstrapRunner(AdminBootstrapProperties properties, AdminUserMapper userMapper,
                                        PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = requireUsername();
        char[] password = readPassword();
        try {
            validatePassword(username, password);
            replaceBaselinePassword(username, password);
            log.info("Administrator bootstrap completed for account '{}'", username);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private String requireUsername() {
        String username = properties.getUsername();
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("administrator bootstrap username must not be blank");
        }
        return username.trim();
    }

    private char[] readPassword() {
        String passwordFile = properties.getPasswordFile();
        if (passwordFile == null || passwordFile.isBlank()) {
            throw new IllegalStateException("administrator bootstrap password file is required");
        }
        Path path = Path.of(passwordFile).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("administrator bootstrap password file must be a regular file");
        }
        try {
            if (!Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).equals(REQUIRED_PERMISSIONS)) {
                throw new IllegalStateException("administrator bootstrap password file permissions must be 0600");
            }
            long size = Files.size(path);
            if (size == 0 || size > 256) {
                throw new IllegalStateException("administrator bootstrap password file size is invalid");
            }
            byte[] bytes = Files.readAllBytes(path);
            try {
                String value = new String(bytes, StandardCharsets.UTF_8);
                if (value.endsWith("\r\n")) {
                    value = value.substring(0, value.length() - 2);
                } else if (value.endsWith("\n")) {
                    value = value.substring(0, value.length() - 1);
                }
                return value.toCharArray();
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read administrator bootstrap password file", ex);
        }
    }

    private void validatePassword(String username, char[] password) {
        String value = new String(password);
        if (value.length() < 12 || value.length() > 16) {
            throw new IllegalStateException("administrator password length must be between 12 and 16 characters");
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (char character : password) {
            hasUpper |= Character.isUpperCase(character);
            hasLower |= Character.isLowerCase(character);
            hasDigit |= Character.isDigit(character);
            hasSpecial |= !Character.isLetterOrDigit(character) && !Character.isWhitespace(character);
            if (Character.isWhitespace(character) || character > 0x7f) {
                throw new IllegalStateException("administrator password must use non-whitespace ASCII characters");
            }
        }
        if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
            throw new IllegalStateException(
                    "administrator password must contain uppercase, lowercase, digit and special characters");
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_PASSWORDS.contains(normalized) || normalized.contains(username.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("administrator password is too predictable");
        }
    }

    private void replaceBaselinePassword(String username, char[] password) {
        AdminUserDO user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new IllegalStateException("administrator bootstrap account does not exist");
        }
        if (!BASELINE_ADMIN_PASSWORD_HASH.equals(user.getPassword())) {
            throw new IllegalStateException("administrator password was already changed or is not the trusted baseline");
        }
        String encodedPassword = passwordEncoder.encode(new String(password));
        int updated = userMapper.updatePasswordIfCurrent(user.getId(), BASELINE_ADMIN_PASSWORD_HASH, encodedPassword);
        if (updated != 1) {
            throw new IllegalStateException("administrator password changed concurrently; bootstrap was not applied");
        }
    }
}
