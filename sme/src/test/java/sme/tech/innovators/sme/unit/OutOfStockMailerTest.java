package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.repository.ProductRepository;
import sme.tech.innovators.sme.service.EmailService;
import sme.tech.innovators.sme.service.OutOfStockMailer;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutOfStockMailerTest {

    @Mock ProductRepository productRepository;
    @Mock EmailService emailService;
    @InjectMocks OutOfStockMailer mailer;

    @Test
    void notifyIfSoldOut_skipsWhenClaimFails() {
        UUID productId = UUID.randomUUID();
        when(productRepository.claimOutOfStockNotification(productId)).thenReturn(0);

        mailer.notifyIfSoldOut(productId);

        verifyNoInteractions(emailService);
    }

    @Test
    void notifyIfSoldOut_claimsThenSends() {
        UUID productId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        User owner = User.builder()
                .id(UUID.randomUUID())
                .email("merchant@example.com")
                .fullName("Mona")
                .password("x")
                .accountStatus(AccountStatus.VERIFIED)
                .role(UserRole.OWNER)
                .build();
        Business business = Business.builder()
                .id(UUID.randomUUID())
                .name("Something Good")
                .slug("something-good")
                .publicLink("https://x")
                .owner(owner)
                .build();
        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .name("Something Good")
                .business(business)
                .build();
        Product product = Product.builder()
                .id(productId)
                .title("Classic Tee")
                .sku("TEE-1")
                .workspace(workspace)
                .quantityAvailable(0)
                .build();

        when(productRepository.claimOutOfStockNotification(productId)).thenReturn(1);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        mailer.notifyIfSoldOut(productId);

        verify(emailService).sendOutOfStockEmail(
                eq("merchant@example.com"),
                eq("Mona"),
                eq("Classic Tee"),
                eq("TEE-1"),
                eq("Something Good"),
                eq(workspaceId));
    }

    @Test
    void sendClaimed_skipsWhenNoMerchantEmail() {
        UUID productId = UUID.randomUUID();
        User owner = User.builder()
                .id(UUID.randomUUID())
                .email("  ")
                .fullName("Mona")
                .password("x")
                .accountStatus(AccountStatus.VERIFIED)
                .role(UserRole.OWNER)
                .build();
        Business business = Business.builder()
                .id(UUID.randomUUID())
                .name("Shop")
                .slug("shop")
                .publicLink("https://x")
                .owner(owner)
                .build();
        Workspace workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .name("Shop")
                .business(business)
                .build();
        Product product = Product.builder()
                .id(productId)
                .title("Tee")
                .sku("T1")
                .workspace(workspace)
                .build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        mailer.sendClaimed(productId);

        verifyNoInteractions(emailService);
    }
}
