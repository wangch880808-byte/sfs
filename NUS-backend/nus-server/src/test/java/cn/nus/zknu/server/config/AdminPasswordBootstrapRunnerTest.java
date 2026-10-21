package cn.nus.zknu.server.config;

import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AdminPasswordBootstrapRunnerTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    @TempDir
    Path tempDir;

    private final AdminUserMapper userMapper = mock(AdminUserMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private AdminBootstrapProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AdminBootstrapProperties();
        properties.setEnabled(true);
        properties.setUsername("admin");
    }

    @Test
    void updatesOnlyTrustedBaselinePassword() throws Exception {
        Path passwordFile = passwordFile("Strong!Pass123");
        properties.setPasswordFile(passwordFile.toString());
        AdminUserDO admin = new AdminUserDO().setId(1L)
                .setUsername("admin")
                .setPassword(AdminPasswordBootstrapRunner.BASELINE_ADMIN_PASSWORD_HASH);
        when(userMapper.selectByUsername("admin")).thenReturn(admin);
        when(passwordEncoder.encode("Strong!Pass123")).thenReturn("encoded-password");
        when(userMapper.updatePasswordIfCurrent(1L,
                AdminPasswordBootstrapRunner.BASELINE_ADMIN_PASSWORD_HASH, "encoded-password")).thenReturn(1);

        runner().run(NO_ARGS);

        verify(userMapper).updatePasswordIfCurrent(1L,
                AdminPasswordBootstrapRunner.BASELINE_ADMIN_PASSWORD_HASH, "encoded-password");
    }

    @Test
    void rejectsWeakPassword() throws Exception {
        properties.setPasswordFile(passwordFile("admin123456").toString());

        assertThatThrownBy(() -> runner().run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("length must be between 12 and 16");
        verifyNoInteractions(userMapper, passwordEncoder);
    }

    @Test
    void rejectsEmptyPasswordFile() throws Exception {
        properties.setPasswordFile(passwordFile("").toString());

        assertThatThrownBy(() -> runner().run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("length must be between 12 and 16");
        verifyNoInteractions(userMapper, passwordEncoder);
    }

    @Test
    void rejectsPredictablePasswordContainingUsername() throws Exception {
        properties.setPasswordFile(passwordFile("Admin!Pass1234").toString());

        assertThatThrownBy(() -> runner().run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too predictable");
        verifyNoInteractions(userMapper, passwordEncoder);
    }

    @Test
    void rejectsPasswordFileWithBroadPermissions() throws Exception {
        Path passwordFile = passwordFile("Strong!Pass123");
        Files.setPosixFilePermissions(passwordFile, PosixFilePermissions.fromString("rw-r--r--"));
        properties.setPasswordFile(passwordFile.toString());

        assertThatThrownBy(() -> runner().run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("permissions must be 0600");
        verifyNoInteractions(userMapper, passwordEncoder);
    }

    @Test
    void rejectsAlreadyChangedPassword() throws Exception {
        properties.setPasswordFile(passwordFile("Strong!Pass123").toString());
        when(userMapper.selectByUsername("admin")).thenReturn(new AdminUserDO().setId(1L)
                .setUsername("admin").setPassword("another-hash"));

        assertThatThrownBy(() -> runner().run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already changed");
        verify(userMapper, never()).updatePasswordIfCurrent(anyLong(), anyString(), anyString());
    }

    @Test
    void rejectsConcurrentPasswordChange() throws Exception {
        properties.setPasswordFile(passwordFile("Strong!Pass123").toString());
        when(userMapper.selectByUsername("admin")).thenReturn(new AdminUserDO().setId(1L)
                .setUsername("admin").setPassword(AdminPasswordBootstrapRunner.BASELINE_ADMIN_PASSWORD_HASH));
        when(passwordEncoder.encode("Strong!Pass123")).thenReturn("encoded-password");
        when(userMapper.updatePasswordIfCurrent(anyLong(), anyString(), anyString())).thenReturn(0);

        assertThatThrownBy(() -> runner().run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed concurrently");
    }

    private AdminPasswordBootstrapRunner runner() {
        return new AdminPasswordBootstrapRunner(properties, userMapper, passwordEncoder);
    }

    private Path passwordFile(String password) throws Exception {
        Path path = tempDir.resolve("admin-password");
        Files.writeString(path, password + "\n");
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        return path;
    }
}
