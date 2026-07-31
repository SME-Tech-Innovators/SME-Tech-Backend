package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.PaystackSubaccountFailedException;
import sme.tech.innovators.sme.dto.request.UpdatePaymentSettingsRequest;
import sme.tech.innovators.sme.dto.response.PaymentSettingsDto;
import sme.tech.innovators.sme.integration.paystack.PaystackClient;
import sme.tech.innovators.sme.repository.WorkspaceRepository;
import sme.tech.innovators.sme.service.PaymentSettingsService;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentSettingsServiceTest {

    @Mock WorkspaceRepository workspaceRepository;
    @Mock PaystackClient paystackClient;

    private PaymentSettingsService service;
    private UUID userId;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        service = new PaymentSettingsService(workspaceRepository, paystackClient);
        userId = UUID.randomUUID();
        workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .name("Bridge Labs")
                .publicSlug("bridge-labs")
                .status(WorkspaceStatus.LIVE)
                .paystackSubaccountStatus(PaystackSubaccountStatus.NOT_CONNECTED)
                .payoutBusinessName("Bridge Labs")
                .payoutBankCode("058")
                .payoutAccountNumber("0123456789")
                .build();
        when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspace.getId(), userId))
                .thenReturn(Optional.of(workspace));
        lenient().when(paystackClient.getPlatformFeePercent()).thenReturn(0);
        lenient().when(paystackClient.getPublicKey()).thenReturn("pk_test_dummy");
        lenient().when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void connectCreatesSubaccountAndStoresCode() {
        when(paystackClient.createSubaccount("Bridge Labs", "058", "0123456789", 0))
                .thenReturn(Map.of("subaccount_code", "ACCT_test123", "account_name", "BRIDGE LABS"));

        PaymentSettingsDto dto = service.connect(workspace.getId(), userId);

        assertThat(dto.getPaystackSubaccountCode()).isEqualTo("ACCT_test123");
        assertThat(dto.getPaystackSubaccountStatus()).isEqualTo("active");
        assertThat(dto.getPayoutAccountName()).isEqualTo("BRIDGE LABS");
        assertThat(dto.getPublicKey()).isEqualTo("pk_test_dummy");
        verify(paystackClient).createSubaccount(anyString(), anyString(), anyString(), anyInt());
        verify(paystackClient, never()).updateSubaccount(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void connectUpdatesExistingSubaccount() {
        workspace.setPaystackSubaccountCode("ACCT_existing");
        when(paystackClient.updateSubaccount("ACCT_existing", "Bridge Labs", "058", "0123456789", 0))
                .thenReturn(Map.of("subaccount_code", "ACCT_existing"));

        PaymentSettingsDto dto = service.connect(workspace.getId(), userId);

        assertThat(dto.getPaystackSubaccountCode()).isEqualTo("ACCT_existing");
        assertThat(dto.getPaystackSubaccountStatus()).isEqualTo("active");
        verify(paystackClient).updateSubaccount(eq("ACCT_existing"), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void connectFailureSetsFailedStatus() {
        when(paystackClient.createSubaccount(anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new PaystackSubaccountFailedException("Invalid bank"));

        assertThatThrownBy(() -> service.connect(workspace.getId(), userId))
                .isInstanceOf(PaystackSubaccountFailedException.class);
        assertThat(workspace.getPaystackSubaccountStatus()).isEqualTo(PaystackSubaccountStatus.FAILED);
    }

    @Test
    void updateSettingsSavesDraftWithoutCallingPaystack() {
        UpdatePaymentSettingsRequest req = new UpdatePaymentSettingsRequest();
        req.setPayoutBusinessName("New Name");
        req.setPayoutBankCode("632005");
        req.setPayoutAccountNumber("1111222233");

        PaymentSettingsDto dto = service.updateSettings(workspace.getId(), userId, req);

        assertThat(dto.getPayoutBusinessName()).isEqualTo("New Name");
        assertThat(dto.getPayoutBankCode()).isEqualTo("632005");
        verify(paystackClient, never()).createSubaccount(anyString(), anyString(), anyString(), anyInt());
        verify(paystackClient, never()).updateSubaccount(anyString(), anyString(), anyString(), anyString(), anyInt());
    }
}
