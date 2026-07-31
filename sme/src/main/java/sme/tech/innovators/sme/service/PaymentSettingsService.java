package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.request.UpdatePaymentSettingsRequest;
import sme.tech.innovators.sme.dto.response.PaymentSettingsDto;
import sme.tech.innovators.sme.dto.response.PaystackBankDto;
import sme.tech.innovators.sme.entity.PaystackSubaccountStatus;
import sme.tech.innovators.sme.entity.Workspace;
import sme.tech.innovators.sme.exception.InvalidBankAccountException;
import sme.tech.innovators.sme.exception.PaystackSubaccountFailedException;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.integration.paystack.PaystackClient;
import sme.tech.innovators.sme.repository.WorkspaceRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSettingsService {

    private final WorkspaceRepository workspaceRepository;
    private final PaystackClient paystackClient;

    @Transactional(readOnly = true)
    public PaymentSettingsDto getSettings(UUID workspaceId, UUID userId) {
        return toDto(loadOwnedWorkspace(workspaceId, userId));
    }

    @Transactional
    public PaymentSettingsDto updateSettings(UUID workspaceId,
                                              UUID userId,
                                              UpdatePaymentSettingsRequest request) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        if (request.getPayoutBusinessName() != null) {
            workspace.setPayoutBusinessName(blankToNull(request.getPayoutBusinessName()));
        }
        if (request.getPayoutBankCode() != null) {
            workspace.setPayoutBankCode(blankToNull(request.getPayoutBankCode()));
        }
        if (request.getPayoutAccountNumber() != null) {
            workspace.setPayoutAccountNumber(blankToNull(request.getPayoutAccountNumber()));
        }
        if (request.getPlatformFeePercent() != null) {
            workspace.setPlatformFeePercent(request.getPlatformFeePercent());
        }
        // Editing payout details after connect should require re-connect to stay accurate
        if (workspace.getPaystackSubaccountStatus() == PaystackSubaccountStatus.ACTIVE
                && (request.getPayoutBusinessName() != null
                || request.getPayoutBankCode() != null
                || request.getPayoutAccountNumber() != null)) {
            workspace.setPaystackSubaccountStatus(PaystackSubaccountStatus.PENDING);
        }
        workspaceRepository.save(workspace);
        return toDto(workspace);
    }

    @Transactional
    public PaymentSettingsDto connect(UUID workspaceId, UUID userId) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        validateBankFields(workspace);

        int feePercent = workspace.getPlatformFeePercent() != null
                ? workspace.getPlatformFeePercent()
                : paystackClient.getPlatformFeePercent();

        workspace.setPaystackSubaccountStatus(PaystackSubaccountStatus.PENDING);
        workspaceRepository.save(workspace);

        try {
            Map<String, Object> data;
            if (workspace.getPaystackSubaccountCode() != null
                    && !workspace.getPaystackSubaccountCode().isBlank()) {
                data = paystackClient.updateSubaccount(
                        workspace.getPaystackSubaccountCode(),
                        workspace.getPayoutBusinessName(),
                        workspace.getPayoutBankCode(),
                        workspace.getPayoutAccountNumber(),
                        feePercent
                );
            } else {
                data = paystackClient.createSubaccount(
                        workspace.getPayoutBusinessName(),
                        workspace.getPayoutBankCode(),
                        workspace.getPayoutAccountNumber(),
                        feePercent
                );
            }

            String code = stringVal(data.get("subaccount_code"));
            if (code == null || code.isBlank()) {
                throw new PaystackSubaccountFailedException("Paystack did not return a subaccount_code");
            }
            workspace.setPaystackSubaccountCode(code);
            String accountName = stringVal(data.get("account_name"));
            if (accountName != null) {
                workspace.setPayoutAccountName(accountName);
            }
            workspace.setPaystackSubaccountStatus(PaystackSubaccountStatus.ACTIVE);
            workspaceRepository.save(workspace);
            log.info("Paystack subaccount connected for workspace={} code={}", workspaceId, code);
            return toDto(workspace);
        } catch (PaystackSubaccountFailedException | InvalidBankAccountException ex) {
            workspace.setPaystackSubaccountStatus(PaystackSubaccountStatus.FAILED);
            workspaceRepository.save(workspace);
            throw ex;
        } catch (RuntimeException ex) {
            workspace.setPaystackSubaccountStatus(PaystackSubaccountStatus.FAILED);
            workspaceRepository.save(workspace);
            throw new PaystackSubaccountFailedException(
                    ex.getMessage() != null ? ex.getMessage() : "Failed to connect Paystack subaccount");
        }
    }

    @Transactional(readOnly = true)
    public List<PaystackBankDto> listBanks(String country) {
        return paystackClient.listBanks(country).stream()
                .map(bank -> PaystackBankDto.builder()
                        .name(stringVal(bank.get("name")))
                        .code(stringVal(bank.get("code")))
                        .country(stringVal(bank.get("country")))
                        .currency(stringVal(bank.get("currency")))
                        .build())
                .filter(b -> b.getCode() != null && b.getName() != null)
                .toList();
    }

    private void validateBankFields(Workspace workspace) {
        if (isBlank(workspace.getPayoutBusinessName())
                || isBlank(workspace.getPayoutBankCode())
                || isBlank(workspace.getPayoutAccountNumber())) {
            throw new InvalidBankAccountException(
                    "payoutBusinessName, payoutBankCode, and payoutAccountNumber are required before connect");
        }
    }

    private Workspace loadOwnedWorkspace(UUID workspaceId, UUID userId) {
        return workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or you do not have access to it"));
    }

    private PaymentSettingsDto toDto(Workspace workspace) {
        int effectiveFee = workspace.getPlatformFeePercent() != null
                ? workspace.getPlatformFeePercent()
                : paystackClient.getPlatformFeePercent();
        return PaymentSettingsDto.builder()
                .workspaceId(workspace.getId().toString())
                .payoutBusinessName(workspace.getPayoutBusinessName())
                .payoutBankCode(workspace.getPayoutBankCode())
                .payoutAccountNumber(workspace.getPayoutAccountNumber())
                .payoutAccountName(workspace.getPayoutAccountName())
                .paystackSubaccountCode(workspace.getPaystackSubaccountCode())
                .paystackSubaccountStatus(workspace.getPaystackSubaccountStatus() == null
                        ? "not_connected"
                        : workspace.getPaystackSubaccountStatus().name().toLowerCase(Locale.ROOT))
                .platformFeePercent(workspace.getPlatformFeePercent())
                .effectivePlatformFeePercent(effectiveFee)
                .publicKey(paystackClient.getPublicKey())
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }
}
