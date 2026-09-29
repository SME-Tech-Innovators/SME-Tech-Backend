package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sme.tech.innovators.sme.dto.request.CreateDeliveryRequest;
import sme.tech.innovators.sme.dto.request.UpdateDeliveryRequest;
import sme.tech.innovators.sme.dto.response.DeliveryDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.InvalidOrderStatusTransitionException;
import sme.tech.innovators.sme.repository.DeliveryEventRepository;
import sme.tech.innovators.sme.repository.DeliveryRepository;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;
import sme.tech.innovators.sme.service.DeliveryService;
import sme.tech.innovators.sme.service.PublicStoreResolver;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock WorkspaceRepository workspaceRepository;
    @Mock OrderRepository orderRepository;
    @Mock DeliveryRepository deliveryRepository;
    @Mock DeliveryEventRepository deliveryEventRepository;
    @Mock PublicStoreResolver publicStoreResolver;

    private DeliveryService service;
    private UUID workspaceId;
    private UUID userId;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new DeliveryService(
                workspaceRepository, orderRepository, deliveryRepository,
                deliveryEventRepository, publicStoreResolver);
        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        Workspace workspace = Workspace.builder().id(workspaceId).name("Store").build();
        order = Order.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .orderNumber("ORD-1")
                .customerName("Ada")
                .customerEmail("ada@example.com")
                .customerPhone("+2700")
                .subtotalAmount(new BigDecimal("10.00"))
                .shippingAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("10.00"))
                .currency("ZAR")
                .status(OrderStatus.PAID)
                .paymentStatus(PaymentStatus.PAID)
                .items(List.of())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        lenient().when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.of(workspace));
        lenient().when(orderRepository.findByIdAndWorkspaceId(order.getId(), workspaceId))
                .thenReturn(Optional.of(order));
        lenient().when(deliveryRepository.save(any(Delivery.class)))
                .thenAnswer(invocation -> {
                    Delivery delivery = invocation.getArgument(0);
                    if (delivery.getId() == null) delivery.setId(UUID.randomUUID());
                    return delivery;
                });
        lenient().when(deliveryEventRepository.findAllByDeliveryIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
    }

    @Test
    void createsPendingDeliveryAndRecordsInitialEvent() {
        CreateDeliveryRequest request = new CreateDeliveryRequest();
        request.setCarrierName("Courier");
        request.setTrackingNumber("TRACK-1");

        DeliveryDto result = service.create(workspaceId, userId, order.getId(), request);

        assertThat(result.getStatus()).isEqualTo("pending");
        assertThat(result.getCarrierName()).isEqualTo("Courier");
        verify(deliveryEventRepository).save(any(DeliveryEvent.class));
    }

    @Test
    void rejectsSecondDeliveryForOrder() {
        when(deliveryRepository.existsByOrderId(order.getId())).thenReturn(true);
        CreateDeliveryRequest request = new CreateDeliveryRequest();

        assertThatThrownBy(() -> service.create(workspaceId, userId, order.getId(), request))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        verify(deliveryRepository, never()).save(any(Delivery.class));
    }

    @Test
    void updatesAllMetadataFieldsInOneRequest() {
        Delivery delivery = Delivery.builder()
                .id(UUID.randomUUID())
                .order(order)
                .status(DeliveryStatus.ASSIGNED)
                .build();
        when(deliveryRepository.findByOrderId(order.getId())).thenReturn(Optional.of(delivery));

        UpdateDeliveryRequest request = new UpdateDeliveryRequest();
        request.setStatus("dispatched");
        request.setCarrierName("Courier");
        request.setTrackingNumber("TRACK-2");
        request.setPublicNotes("Leave at reception");

        DeliveryDto result = service.update(workspaceId, userId, order.getId(), request);

        assertThat(result.getStatus()).isEqualTo("dispatched");
        assertThat(result.getCarrierName()).isEqualTo("Courier");
        assertThat(result.getTrackingNumber()).isEqualTo("TRACK-2");
        assertThat(result.getPublicNotes()).isEqualTo("Leave at reception");
        verify(deliveryEventRepository).save(any(DeliveryEvent.class));
    }

    @Test
    void repeatedIdenticalUpdateDoesNotCreateAnotherEvent() {
        Delivery delivery = Delivery.builder()
                .id(UUID.randomUUID())
                .order(order)
                .status(DeliveryStatus.ASSIGNED)
                .carrierName("Courier")
                .build();
        when(deliveryRepository.findByOrderId(order.getId())).thenReturn(Optional.of(delivery));

        UpdateDeliveryRequest request = new UpdateDeliveryRequest();
        request.setStatus("assigned");
        request.setCarrierName("Courier");

        service.update(workspaceId, userId, order.getId(), request);

        verify(deliveryRepository, never()).save(any(Delivery.class));
        verify(deliveryEventRepository, never()).save(any(DeliveryEvent.class));
    }

    @Test
    void terminalDeliveryCannotMoveAgain() {
        assertThat(DeliveryService.isAllowedTransition(DeliveryStatus.DELIVERED, DeliveryStatus.FAILED))
                .isFalse();
        assertThat(DeliveryService.isAllowedTransition(DeliveryStatus.CANCELLED, DeliveryStatus.ASSIGNED))
                .isFalse();
        assertThat(DeliveryService.isAllowedTransition(DeliveryStatus.IN_TRANSIT, DeliveryStatus.DELIVERED))
                .isTrue();
    }
}
