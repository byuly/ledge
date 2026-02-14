package io.ledge.tenant.domain

import io.ledge.TestFixtures
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals

class TenantTest {

    private fun activeTenant() = Tenant(
        id = TestFixtures.tenantId(),
        name = "Acme Corp",
        apiKeyHash = "hashed-key"
    )

    @Test
    fun `initial status is ACTIVE`() {
        val tenant = activeTenant()
        assertEquals(TenantStatus.ACTIVE, tenant.status)
    }

    @Test
    fun `suspend from ACTIVE transitions to SUSPENDED`() {
        val tenant = activeTenant()
        tenant.suspend()
        assertEquals(TenantStatus.SUSPENDED, tenant.status)
    }

    @Test
    fun `suspend from SUSPENDED throws IllegalStateException`() {
        val tenant = activeTenant()
        tenant.suspend()
        assertThrows<IllegalStateException> { tenant.suspend() }
    }

    @Test
    fun `suspend from DELETED throws IllegalStateException`() {
        val tenant = activeTenant()
        tenant.delete()
        assertThrows<IllegalStateException> { tenant.suspend() }
    }

    @Test
    fun `delete from ACTIVE transitions to DELETED`() {
        val tenant = activeTenant()
        tenant.delete()
        assertEquals(TenantStatus.DELETED, tenant.status)
    }

    @Test
    fun `delete from SUSPENDED transitions to DELETED`() {
        val tenant = activeTenant()
        tenant.suspend()
        tenant.delete()
        assertEquals(TenantStatus.DELETED, tenant.status)
    }

    @Test
    fun `delete from DELETED throws IllegalStateException`() {
        val tenant = activeTenant()
        tenant.delete()
        assertThrows<IllegalStateException> { tenant.delete() }
    }
}
