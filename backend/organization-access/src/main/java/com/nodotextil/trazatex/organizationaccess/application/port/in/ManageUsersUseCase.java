package com.nodotextil.trazatex.organizationaccess.application.port.in;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.UserResult;
import com.nodotextil.trazatex.organizationaccess.domain.model.UserStatus;

import java.util.List;
import java.util.UUID;

public interface ManageUsersUseCase {

    List<UserResult> list(Actor actor);

    /** US09. */
    UserResult changeStatus(UUID userId, UserStatus status, Actor actor);
}
