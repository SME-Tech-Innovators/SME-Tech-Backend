package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.service.EmailService;
import sme.tech.innovators.sme.service.OrderConfirmationMailer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderConfirmationMailerTest {

    @Mock OrderRepository orderRepository;
    @Mock EmailService emailService;
    @InjectMocks OrderConfirmationMailer mailer;

    @Test
    void sendIfNeeded_skipsWhenNoEmail() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("ORD-1")
                .customerName("Ada")
                .customerEmail("  ")
                .customerPhone("+2700")
                .subtotalAmount(new java.math.BigDecimal("1.00"))
                .totalAmount(new java.math.BigDecimal("1.00"))
                .currency("ZAR")
                .build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        mailer.sendIfNeeded(order.getId());

        verifyNoInteractions(emailService);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void sendIfNeeded_sendsOnceAndClaimsFlag() {
        Workspace workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .name("Bridge Labs")
                .publicSlug("bridge-labs")
                .build();
        OrderItem item = OrderItem.builder()
                .title("Tee")
                .quantity(1)
                .totalAmount(new java.math.BigDecimal("25.00"))
                .currency("ZAR")
                .build();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .orderNumber("ORD-9")
                .customerName("Ada")
                .customerEmail("ada@example.com")
                .customerPhone("+2700")
                .subtotalAmount(new java.math.BigDecimal("25.00"))
                .totalAmount(new java.math.BigDecimal("25.00"))
                .currency("ZAR")
                .confirmationEmailSent(false)
                .items(List.of(item))
                .build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        mailer.sendIfNeeded(order.getId());

        assertThat(order.isConfirmationEmailSent()).isTrue();
        verify(emailService).sendOrderConfirmationEmail(
                eq("ada@example.com"),
                eq("Ada"),
                eq("Bridge Labs"),
                eq("bridge-labs"),
                eq("ORD-9"),
                anyList(),
                eq(new java.math.BigDecimal("25.00")),
                eq("ZAR"));
    }

    @Test
    void sendIfNeeded_skipsWhenAlreadySent() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("ORD-1")
                .customerName("Ada")
                .customerEmail("ada@example.com")
                .customerPhone("+2700")
                .subtotalAmount(new java.math.BigDecimal("1.00"))
                .totalAmount(new java.math.BigDecimal("1.00"))
                .currency("ZAR")
                .confirmationEmailSent(true)
                .build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        mailer.sendIfNeeded(order.getId());

        verifyNoInteractions(emailService);
        verify(orderRepository, never()).save(any());
    }
}
