package com.nodotextil.trazatex.organizationaccess.application.port.in;

import com.nodotextil.trazatex.organizationaccess.application.dto.LoginCommand;
import com.nodotextil.trazatex.organizationaccess.application.dto.LoginResult;

public interface AuthenticateUseCase {

    /** US05. */
    LoginResult login(LoginCommand command);
}
