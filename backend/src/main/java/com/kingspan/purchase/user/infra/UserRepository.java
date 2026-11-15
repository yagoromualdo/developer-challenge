package com.kingspan.purchase.user.infra;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    // lower() e não o IgnoreCase derivado (que gera upper()): assim a consulta usa o índice uq_app_user_email.
    @Query("select u from UserEntity u where lower(u.email) = lower(:email)")
    Optional<UserEntity> findByEmail(String email);
}
