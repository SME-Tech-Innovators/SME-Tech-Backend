package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.repository.*;
import sme.tech.innovators.sme.service.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerOrderAccessServiceTest {
    PublicStoreResolver stores = mock(PublicStoreResolver.class);
    OrderRepository orders = mock(OrderRepository.class);
    EmailService emails = mock(EmailService.class);
    CustomerOrderAccessService service = new CustomerOrderAccessService(stores, orders, emails);
    UUID w = UUID.randomUUID(), id = UUID.randomUUID();
    Order order;
    @BeforeEach void setup() {
        var workspace = Workspace.builder().id(w).publicSlug("shop").build();
        order = Order.builder().id(id).workspace(workspace).customerEmail("buyer@example.com").build();
        when(stores.requireLiveWorkspace("shop")).thenReturn(workspace);
        when(orders.findByWorkspaceIdAndOrderNumberIgnoreCaseAndCustomerEmailIgnoreCase(w, "ORD-1", "buyer@example.com"))
                .thenReturn(Optional.of(order));
        when(orders.lockForReturn(id, w)).thenReturn(Optional.of(order));
        when(orders.findByIdAndWorkspaceId(id, w)).thenReturn(Optional.of(order));
    }
    String issue() {
        service.sendAccessLink("shop", " ORD-1 ", " buyer@example.com ");
        var token = ArgumentCaptor.forClass(String.class);
        verify(emails).sendOrderAccessEmail(eq("buyer@example.com"), eq("shop"), eq(id), token.capture());
        return token.getValue();
    }
    @Test void tokenIsRandomHashedAndAuthorizesOnlyTheOrder() {
        String token = issue();
        assertThat(token).hasSize(43);
        assertThat(order.getCustomerAccessTokenHash()).hasSize(64).isNotEqualTo(token);
        assertThat(order.getCustomerAccessExpiresAt()).isAfter(LocalDateTime.now().plusHours(23));
        assertThat(service.authorize("shop", id, token)).isSameAs(order);
    }
    @Test void wrongMissingAndExpiredTokensAreRejected() {
        String token = issue();
        assertThatThrownBy(() -> service.authorize("shop", id, null)).hasMessageContaining("invalid or expired");
        assertThatThrownBy(() -> service.authorize("shop", id, "x".repeat(43))).hasMessageContaining("invalid or expired");
        order.setCustomerAccessExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(() -> service.authorize("shop", id, token)).hasMessageContaining("invalid or expired");
    }
    @Test void tokenCannotAccessAnotherOrderOrStore() {
        String token = issue();
        assertThatThrownBy(() -> service.authorize("shop", UUID.randomUUID(), token)).hasMessageContaining("invalid or expired");
        when(stores.requireLiveWorkspace("other")).thenReturn(Workspace.builder().id(UUID.randomUUID()).build());
        assertThatThrownBy(() -> service.authorize("other", id, token)).hasMessageContaining("invalid or expired");
    }
    @Test void incorrectEmailReturnsValidationMessageWithoutSendingMail() {
        assertThatThrownBy(() -> service.sendAccessLink("shop", "ORD-1", "other@example.com"))
                .isInstanceOf(sme.tech.innovators.sme.exception.OrderNotFoundException.class)
                .hasMessage("Incorrect order number or email. Check the details in your order confirmation.");
        verifyNoInteractions(emails);
        verify(orders, never()).save(any());
    }
    @Test void incorrectOrderNumberReturnsTheSameMessageWithoutSendingMail() {
        assertThatThrownBy(() -> service.sendAccessLink("shop", "BAD-NUMBER", "buyer@example.com"))
                .isInstanceOf(sme.tech.innovators.sme.exception.OrderNotFoundException.class)
                .hasMessage("Incorrect order number or email. Check the details in your order confirmation.");
        verifyNoInteractions(emails);
        verify(orders, never()).save(any());
    }
    @Test void repeatedRequestsWithinAMinuteDoNotFloodEmail() {
        issue();
        service.sendAccessLink("shop", "ORD-1", "buyer@example.com");
        verify(emails, times(1)).sendOrderAccessEmail(any(), any(), any(), any());
    }
    @Test void newLinkRevokesPreviousToken() {
        String old = issue();
        order.setCustomerAccessSentAt(LocalDateTime.now().minusMinutes(2));
        service.sendAccessLink("shop", "ORD-1", "buyer@example.com");
        assertThatThrownBy(() -> service.authorize("shop", id, old)).hasMessageContaining("invalid or expired");
    }
}
