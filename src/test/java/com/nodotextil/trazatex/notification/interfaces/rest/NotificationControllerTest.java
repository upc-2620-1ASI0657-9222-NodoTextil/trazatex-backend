package com.nodotextil.trazatex.notification.interfaces.rest;

import com.nodotextil.trazatex.shared.security.SecurityConfig;
import com.nodotextil.trazatex.notification.application.CountUnreadNotificationsUseCase;
import com.nodotextil.trazatex.notification.application.ListNotificationsUseCase;
import com.nodotextil.trazatex.notification.application.MarkNotificationAsReadUseCase;
import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess;
import com.nodotextil.trazatex.shared.security.JwtConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @MockitoBean private ListNotificationsUseCase listNotifications;
    @MockitoBean private MarkNotificationAsReadUseCase markAsRead;
    @MockitoBean private CountUnreadNotificationsUseCase countUnread;
    @MockitoBean private OrganizationAccess organizationAccess;
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