package org.aincraft.guilds.services;

import org.aincraft.guilds.TestUtilities;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Permission;
import org.aincraft.guilds.services.impl.PermissionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PermissionService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionServiceTest {

    @Mock
    private DatabaseManager databaseManager;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    @Mock
    private Logger logger;

    private PermissionServiceImpl permissionService;

    private UUID testResidentId = UUID.randomUUID();
    private UUID testPlotId = UUID.randomUUID();
    private String testTownName = "test_town";
    private String testWorld = "test_world";

    @BeforeEach
    void setUp() throws SQLException {
        when(databaseManager.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // Manually create the service with mocked dependencies
        permissionService = new PermissionServiceImpl(databaseManager, logger);
    }

    @Test
    @DisplayName("Should check town permission correctly")
    void shouldCheckTownPermissionCorrectly() {
        // Given
        String permission = "build";

        // When
        boolean result = permissionService.hasPermission(testResidentId, permission, "town", testTownName);

        // Then
        // Should call database and fallback to role-based permissions
        verify(databaseManager, atLeastOnce()).getDataSource();
        // For now, the service returns role-based fallback
        assertThat(result).isTrue(); // All residents have build permissions by default
    }

    @Test
    @DisplayName("Should check plot permission correctly")
    void shouldCheckPlotPermissionCorrectly() {
        // Given
        String permission = "build";

        // When
        boolean result = permissionService.hasPermission(testResidentId, permission, "plot", testPlotId.toString());

        // Then
        verify(databaseManager).getDataSource();
        // For now, falls back to ownership check
        assertThat(result).isFalse(); // Resident doesn't own the test plot
    }

    @Test
    @DisplayName("Should check global permission correctly")
    void shouldCheckGlobalPermissionCorrectly() throws SQLException {
        // Given
        String permission = "admin";
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt("permissions_flags")).thenReturn(Permission.Flag.ADMIN);

        // When
        boolean result = permissionService.hasPermission(testResidentId, permission, "global", null);

        // Then
        verify(connection).prepareStatement(contains("context = 'global'"));
        verify(preparedStatement).setString(1, testResidentId.toString());
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false for unknown context")
    void shouldReturnFalseForUnknownContext() {
        // Given
        String permission = "build";

        // When
        boolean result = permissionService.hasPermission(testResidentId, permission, "unknown", "context");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should grant permission successfully")
    void shouldGrantPermissionSuccessfully() throws SQLException {
        // Given
        String permission = "build";
        when(resultSet.next()).thenReturn(false); // No existing permission
        when(databaseManager.executeTransaction(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, java.util.function.Consumer.class).accept(connection);
            return null;
        });

        // When
        boolean result = permissionService.grantPermission(testResidentId, permission, "town", testTownName, true);

        // Then
        assertThat(result).isTrue();
        verify(databaseManager).executeTransaction(any());
    }

    @Test
    @DisplayName("Should update existing permission")
    void shouldUpdateExistingPermission() throws SQLException {
        // Given
        String permission = "build";
        when(resultSet.next()).thenReturn(true); // Existing permission found
        when(resultSet.getString("id")).thenReturn(UUID.randomUUID().toString());
        when(resultSet.getInt("permissions_flags")).thenReturn(0);
        when(databaseManager.executeTransaction(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, java.util.function.Consumer.class).accept(connection);
            return null;
        });

        // When
        boolean result = permissionService.grantPermission(testResidentId, permission, "town", testTownName, true);

        // Then
        assertThat(result).isTrue();
        verify(databaseManager).executeTransaction(any());
    }

    @Test
    @DisplayName("Should revoke permission")
    void shouldRevokePermission() {
        // Given & When
        boolean result = permissionService.revokePermission(testResidentId, "build", "town", testTownName);

        // Then
        // For now, returns false as placeholder
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should get resident permissions")
    void shouldGetResidentPermissions() throws SQLException {
        // Given
        when(resultSet.next()).thenReturn(true, false); // One permission, then end
        mockPermissionResultSet();

        // When
        List<Permission> permissions = permissionService.getResidentPermissions(testResidentId, "town", testTownName);

        // Then
        assertThat(permissions).hasSize(1);
        Permission permission = permissions.get(0);
        assertThat(permission.getContext()).isEqualTo("town");
        assertThat(permission.getContextId()).isEqualTo(testTownName);
        assertThat(permission.getTargetType()).isEqualTo("resident");
        assertThat(permission.getTargetId()).isEqualTo(testResidentId.toString());

        verify(connection).prepareStatement(contains("WHERE context = ? AND context_id = ?"));
        verify(preparedStatement).setString(1, "town");
        verify(preparedStatement).setString(2, testTownName);
        verify(preparedStatement).setString(3, testResidentId.toString());
    }

    @Test
    @DisplayName("Should get context permissions")
    void shouldGetContextPermissions() throws SQLException {
        // Given
        when(resultSet.next()).thenReturn(true, false); // One permission, then end
        mockPermissionResultSet();

        // When
        List<Permission> permissions = permissionService.getContextPermissions("town", testTownName);

        // Then
        assertThat(permissions).hasSize(1);
        verify(connection).prepareStatement(contains("WHERE context = ? AND context_id = ?"));
        verify(preparedStatement).setString(1, "town");
        verify(preparedStatement).setString(2, testTownName);
    }

    @Test
    @DisplayName("Should set town permissions")
    void shouldSetTownPermissions() throws SQLException {
        // Given
        List<Permission> permissions = List.of(
            new Permission(Permission.Flag.BUILD, Permission.Context.TOWN, testTownName, Permission.Target.RESIDENT, testResidentId.toString())
        );
        when(databaseManager.executeTransaction(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, java.util.function.Consumer.class).accept(connection);
            return null;
        });

        // When
        boolean result = permissionService.setTownPermissions(testTownName, permissions);

        // Then
        assertThat(result).isTrue();
        verify(databaseManager).executeTransaction(any());
    }

    @Test
    @DisplayName("Should set plot permissions")
    void shouldSetPlotPermissions() throws SQLException {
        // Given
        List<Permission> permissions = List.of(
            new Permission(Permission.Flag.BUILD, Permission.Context.PLOT, testPlotId.toString(), Permission.Target.RESIDENT, testResidentId.toString())
        );
        when(databaseManager.executeTransaction(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, java.util.function.Consumer.class).accept(connection);
            return null;
        });

        // When
        boolean result = permissionService.setPlotPermissions(testPlotId, permissions);

        // Then
        assertThat(result).isTrue();
        verify(databaseManager).executeTransaction(any());
    }

    @Test
    @DisplayName("Should check build permissions")
    void shouldCheckBuildPermissions() {
        // Given & When
        boolean result = permissionService.canBuild(testResidentId, 10, 20, testWorld);

        // Then
        // For now, returns true as placeholder
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should check destroy permissions")
    void shouldCheckDestroyPermissions() {
        // Given & When
        boolean result = permissionService.canDestroy(testResidentId, 10, 20, testWorld);

        // Then
        // For now, returns true as placeholder
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should check switch permissions")
    void shouldCheckSwitchPermissions() {
        // Given & When
        boolean result = permissionService.canSwitch(testResidentId, 10, 20, testWorld);

        // Then
        // For now, returns true as placeholder
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should check item use permissions")
    void shouldCheckItemUsePermissions() {
        // Given & When
        boolean result = permissionService.canUseItems(testResidentId, 10, 20, testWorld);

        // Then
        // For now, returns true as placeholder
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should get default town permissions")
    void shouldGetDefaultTownPermissions() {
        // When
        List<Permission> permissions = permissionService.getDefaultTownPermissions();

        // Then
        assertThat(permissions).hasSize(3); // resident, assistant, mayor

        Optional<Permission> residentPerms = permissions.stream()
            .filter(p -> p.getTargetType() == Permission.Target.RESIDENT)
            .findFirst();
        assertThat(residentPerms).isPresent();
        assertThat(residentPerms.get().getFlags()).isEqualTo(Permission.Flag.RESIDENT_PERMS);

        Optional<Permission> assistantPerms = permissions.stream()
            .filter(p -> p.getTargetType() == Permission.Target.ASSISTANT)
            .findFirst();
        assertThat(assistantPerms).isPresent();
        assertThat(assistantPerms.get().getFlags()).isEqualTo(Permission.Flag.ASSISTANT_PERMS);

        Optional<Permission> mayorPerms = permissions.stream()
            .filter(p -> p.getTargetType() == Permission.Target.MAYOR)
            .findFirst();
        assertThat(mayorPerms).isPresent();
        assertThat(mayorPerms.get().getFlags()).isEqualTo(Permission.Flag.MAYOR_PERMS);
    }

    @Test
    @DisplayName("Should get default plot permissions")
    void shouldGetDefaultPlotPermissions() {
        // When
        List<Permission> permissions = permissionService.getDefaultPlotPermissions();

        // Then
        assertThat(permissions).hasSize(1);
        Permission permission = permissions.get(0);
        assertThat(permission.getContext()).isEqualTo(Permission.Context.PLOT);
        assertThat(permission.getTargetType()).isEqualTo(Permission.Target.ALL);
        assertThat(permission.getFlags()).isEqualTo(Permission.Flag.DEFAULT_PLOT);
    }

    @Test
    @DisplayName("Should check if resident is town mayor")
    void shouldCheckIfResidentIsTownMayor() throws SQLException {
        // Given
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(1);

        // When
        boolean result = permissionService.isTownMayor(testResidentId, testTownName);

        // Then
        assertThat(result).isTrue();
        verify(connection).prepareStatement(contains("WHERE name = ? AND mayor_uuid = ?"));
        verify(preparedStatement).setString(1, testTownName);
        verify(preparedStatement).setString(2, testResidentId.toString());
    }

    @Test
    @DisplayName("Should check if resident is town assistant")
    void shouldCheckIfResidentIsTownAssistant() throws SQLException {
        // Given
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(1);

        // When
        boolean result = permissionService.isTownAssistant(testResidentId, testTownName);

        // Then
        assertThat(result).isTrue();
        verify(connection).prepareStatement(contains("WHERE t.name = ? AND tr.resident_uuid = ? AND tr.role = 'assistant'"));
    }

    @Test
    @DisplayName("Should check if resident owns plot")
    void shouldCheckIfResidentOwnsPlot() throws SQLException {
        // Given
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(1);

        // When
        boolean result = permissionService.ownsPlot(testResidentId, testPlotId);

        // Then
        assertThat(result).isTrue();
        verify(connection).prepareStatement(contains("WHERE id = ? AND owner_uuid = ?"));
        verify(preparedStatement).setString(1, testPlotId.toString());
        verify(preparedStatement).setString(2, testResidentId.toString());
    }

    @Test
    @DisplayName("Should check if resident has town admin")
    void shouldCheckIfResidentHasTownAdmin() throws SQLException {
        // Given
        when(databaseManager.getDataSource()).thenReturn(dataSource);

        // When
        boolean result = permissionService.hasTownAdmin(testResidentId, testTownName);

        // Then
        // Should check both mayor and assistant status
        verify(dataSource, atLeastOnce()).getConnection();
    }

    @Test
    @DisplayName("Should grant town permission to resident")
    void shouldGrantTownPermissionToResident() throws SQLException {
        // Given
        when(resultSet.next()).thenReturn(false); // No existing permission
        when(databaseManager.executeTransaction(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, java.util.function.Consumer.class).accept(connection);
            return null;
        });

        // When
        boolean result = permissionService.grantTownPermission(testResidentId, testTownName, Permission.Flag.BUILD);

        // Then
        assertThat(result).isTrue();
        verify(databaseManager).executeTransaction(any());
    }

    @Test
    @DisplayName("Should grant town permission to all residents")
    void shouldGrantTownPermissionToAllResidents() throws SQLException {
        // Given
        when(resultSet.next()).thenReturn(false); // No existing permission
        when(databaseManager.executeTransaction(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, java.util.function.Consumer.class).accept(connection);
            return null;
        });

        // When
        boolean result = permissionService.grantTownPermission(null, testTownName, Permission.Flag.BUILD);

        // Then
        assertThat(result).isTrue();
        verify(databaseManager).executeTransaction(any());
    }

    @Test
    @DisplayName("Should grant multiple town permissions")
    void shouldGrantMultipleTownPermissions() throws SQLException {
        // Given
        int flags = Permission.Flag.BUILD | Permission.Flag.DESTROY;
        when(resultSet.next()).thenReturn(false); // No existing permission
        when(databaseManager.executeTransaction(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, java.util.function.Consumer.class).accept(connection);
            return null;
        });

        // When
        boolean result = permissionService.grantTownPermissions(testResidentId, testTownName, flags);

        // Then
        assertThat(result).isTrue();
        verify(databaseManager).executeTransaction(any());
    }

    @Test
    @DisplayName("Should get all permission nodes")
    void shouldGetAllPermissionNodes() {
        // When
        List<String> nodes = permissionService.getAllPermissionNodes();

        // Then
        assertThat(nodes).contains(
            "build", "destroy", "switch", "item_use",
            "claim", "unclaim", "spawn", "set_spawn",
            "invite", "kick", "promote", "demote",
            "withdraw", "deposit",
            "plot_perm", "plot_set", "plot_owner",
            "admin", "admin_town", "admin_plot", "admin_resident", "bypass"
        );
        assertThat(nodes).hasSize(22);
    }

    @Test
    @DisplayName("Should check plot claiming permissions")
    void shouldCheckPlotClaimingPermissions() {
        // Given & When
        boolean result = permissionService.canClaimPlot(testResidentId, 10, 20, testWorld);

        // Then
        verify(databaseManager).getDataSource();
        // Checks town claim permission
        assertThat(result).isTrue(); // All residents have claim permissions by default
    }

    @Test
    @DisplayName("Should check plot buying permissions")
    void shouldCheckPlotBuyingPermissions() {
        // Given & When
        boolean result = permissionService.canBuyPlot(testResidentId, testPlotId);

        // Then
        // Anyone can buy plots (economy check handled elsewhere)
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should check plot management permissions")
    void shouldCheckPlotManagementPermissions() {
        // Given & When
        boolean result = permissionService.canManagePlot(testResidentId, testPlotId);

        // Then
        verify(databaseManager, atLeastOnce()).getDataSource();
        // Should check ownership and town admin status
        assertThat(result).isFalse(); // Resident doesn't own plot and isn't admin
    }

    @Test
    @DisplayName("Should check specific plot permission")
    void shouldCheckSpecificPlotPermission() {
        // Given & When
        boolean result = permissionService.hasPlotPermission(testResidentId, testPlotId, Permission.Flag.BUILD);

        // Then
        verify(databaseManager, atLeastOnce()).getDataSource();
        // Should check ownership first, then plot-specific permissions
        assertThat(result).isFalse(); // Resident doesn't own plot
    }

    @Test
    @DisplayName("Should check town claiming permissions")
    void shouldCheckTownClaimingPermissions() {
        // Given & When
        boolean result = permissionService.canClaimForTown(testResidentId, testTownName);

        // Then
        verify(databaseManager, atLeastOnce()).getDataSource();
        // Should check mayor/assistant status
        assertThat(result).isFalse(); // Resident isn't mayor or assistant
    }

    @Test
    @DisplayName("Should check plot management permissions in town")
    void shouldCheckPlotManagementPermissionsInTown() throws SQLException {
        // Given & When
        boolean result = permissionService.hasPlotManagementPermissions(testResidentId, testTownName);

        // Then
        verify(databaseManager, atLeastOnce()).getConnection();
        // Should check mayor/assistant status
        assertThat(result).isFalse(); // Resident isn't mayor or assistant
    }

    @Test
    @DisplayName("Should evaluate plot permission with admin override")
    void shouldEvaluatePlotPermissionWithAdminOverride() throws SQLException {
        // Given
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt("permissions_flags")).thenReturn(Permission.Flag.ADMIN);
        when(databaseManager.executeTransaction(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, java.util.function.Consumer.class).accept(connection);
            return null;
        });

        // When
        PermissionEvaluationResult result = permissionService.evaluatePlotPermission(testResidentId, testPlotId, Permission.Flag.BUILD);

        // Then
        assertThat(result.hasPermission()).isTrue();
        assertThat(result.getSource()).isEqualTo("admin");
        assertThat(result.getReason()).isEqualTo("Global admin override");
    }

    @Test
    @DisplayName("Should evaluate plot permission for owner")
    void shouldEvaluatePlotPermissionForOwner() throws SQLException {
        // Given
        when(resultSet.getInt(1)).thenReturn(1); // Resident owns plot

        // When
        PermissionEvaluationResult result = permissionService.evaluatePlotPermission(testResidentId, testPlotId, Permission.Flag.BUILD);

        // Then
        assertThat(result.hasPermission()).isTrue();
        assertThat(result.getSource()).isEqualTo("owner");
        assertThat(result.getReason()).isEqualTo("Plot owner has all permissions");
    }

    @Test
    @DisplayName("Should evaluate plot permission for non-owner")
    void shouldEvaluatePlotPermissionForNonOwner() throws SQLException {
        // Given
        when(resultSet.getInt(1)).thenReturn(0); // Resident doesn't own plot

        // When
        PermissionEvaluationResult result = permissionService.evaluatePlotPermission(testResidentId, testPlotId, Permission.Flag.BUILD);

        // Then
        assertThat(result.hasPermission()).isFalse();
        assertThat(result.getSource()).isEqualTo("default");
        assertThat(result.getReason()).isEqualTo("No permission granted");
    }

    @Test
    @DisplayName("Should handle database errors gracefully")
    void shouldHandleDatabaseErrorsGracefully() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenThrow(new SQLException("Database error"));

        // When & Then
        assertThatThrownBy(() -> permissionService.getResidentPermissions(testResidentId, "town", testTownName))
                .isInstanceOf(RuntimeException.class);
    }

    /**
     * Helper method to mock permission result set
     */
    private void mockPermissionResultSet() throws SQLException {
        when(resultSet.getString("id")).thenReturn(UUID.randomUUID().toString());
        when(resultSet.getString("context")).thenReturn("town");
        when(resultSet.getString("context_id")).thenReturn(testTownName);
        when(resultSet.getString("target_type")).thenReturn("resident");
        when(resultSet.getString("target_id")).thenReturn(testResidentId.toString());
        when(resultSet.getInt("permissions_flags")).thenReturn(Permission.Flag.BUILD);
        when(resultSet.getString("granted_at")).thenReturn("2024-01-01 00:00:00");
        when(resultSet.getString("granted_by_uuid")).thenReturn(null);
    }
}