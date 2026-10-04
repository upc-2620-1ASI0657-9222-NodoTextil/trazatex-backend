package com.nodotextil.trazatex.notification.interfaces.rest;

import com.nodotextil.trazatex.infrastructure.security.SecurityConfig;
import com.nodotextil.trazatex.notification.application.CountUnreadNotificationsUseCase;
import com.nodotextil.trazatex.notification.application.ListNotificationsUseCase;
import com.nodotextil.trazatex.notification.application.MarkNotificationAsReadUseCase;
import com.nodotextil.trazatex.organization.application.contract.OrganizationAccess;
import com.nodotextil.trazatex.organization.infrastructure.security.JwtConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, JwtConfiguration.class})
class NotificationControllerTest {

    @MockBean private ListNotificationsUseCase listNotifications;
    @MockBean private MarkNotificationAsReadUseCase markAsRead;
    @MockBean private CountUnreadNotificationsUseCase countUnread;
    @MockBean private OrganizationAccess organizationAccess;
    @Autowired private MockMvc mockMvc;

    @Test
    void unreadCountRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
    }

    @Test
    void unreadCountReturnsValueForAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(countUnread.count(userId)).thenReturn(3L);
        mockMvc.perform(get("/api/notifications/unread-count")
                        .with(jwt().jwt(j -> j.claim("userId", userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(content().string("3"));
    }
}