package sme.tech.innovators.sme.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import sme.tech.innovators.sme.entity.OrderReturn;
import java.util.UUID;
public interface OrderReturnRepository extends JpaRepository<OrderReturn, UUID> {}
