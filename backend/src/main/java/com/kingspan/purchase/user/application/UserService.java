package com.kingspan.purchase.user.application;

import com.kingspan.purchase.costcenter.application.CostCenterService;
import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.shared.error.BusinessRuleException;
import com.kingspan.purchase.shared.error.ErrorCode;
import com.kingspan.purchase.shared.error.ResourceNotFoundException;
import com.kingspan.purchase.user.domain.UserProfile;
import com.kingspan.purchase.user.infra.UserEntity;
import com.kingspan.purchase.user.infra.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão de usuários pelo ADMIN; também é o único caminho de criação, usado pelo cadastro público. */
@Service
public class UserService {

    private final UserRepository users;
    private final CostCenterService costCenters;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(
            UserRepository users, CostCenterService costCenters, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.costCenters = costCenters;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserAccount> list() {
        return users.findAll(Sort.by("id")).stream().map(UserAccount::from).toList();
    }

    @Transactional
    public UserAccount create(String name, String email, String password, UserProfile profile) {
        String normalizedEmail = normalizeEmail(email);
        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw emailAlreadyRegistered();
        }
        requireExistingCostCenter(profile.costCenterId());
        UserEntity user = new UserEntity(
                name.trim(),
                normalizedEmail,
                passwordEncoder.encode(password),
                profile.role(),
                profile.approverLevel(),
                profile.costCenterId(),
                clock.instant());
        try {
            return UserAccount.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            // Dois cadastros simultâneos com o mesmo e-mail: o índice único decide e o perdedor recebe o 422.
            throw emailAlreadyRegistered();
        }
    }

    /** Edição parcial de papel, nível e centro de custo; o novo perfil vale a partir do próximo login (ADR 007). */
    @Transactional
    public UserAccount changeProfile(Long userId, Role role, ApproverLevel approverLevel, Long costCenterId) {
        UserEntity user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário " + userId + " não encontrado."));
        UserProfile current = new UserProfile(user.getRole(), user.getApproverLevel(), user.getCostCenterId());
        UserProfile changed = current.change(role, approverLevel, costCenterId);
        if (!Objects.equals(changed.costCenterId(), current.costCenterId())) {
            requireExistingCostCenter(changed.costCenterId());
        }
        user.changeProfile(changed.role(), changed.approverLevel(), changed.costCenterId(), clock.instant());
        return UserAccount.from(user);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void requireExistingCostCenter(Long costCenterId) {
        if (costCenterId != null && !costCenters.exists(costCenterId)) {
            throw new BusinessRuleException(
                    ErrorCode.COST_CENTER_NOT_FOUND,
                    "Não é possível salvar o usuário: o centro de custo " + costCenterId + " não existe.");
        }
    }

    private static BusinessRuleException emailAlreadyRegistered() {
        return new BusinessRuleException(
                ErrorCode.EMAIL_ALREADY_REGISTERED, "Não é possível cadastrar: este e-mail já está em uso.");
    }
}
