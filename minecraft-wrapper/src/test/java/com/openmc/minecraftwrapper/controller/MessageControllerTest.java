package com.openmc.minecraftwrapper.controller;

import com.openmc.minecraftwrapper.exception.MessageDeliveryException;
import com.openmc.minecraftwrapper.service.MessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessageController.class)
@DisplayName("MessageController Tests")
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageService messageService;

    @Test
    @DisplayName("Should send message successfully")
    void shouldSendMessageSuccessfully() throws Exception {
        mockMvc.perform(post("/api/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Hello World\",\"destination\":\"MINECRAFT\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Message sent successfully"));

        verify(messageService).sendMessage("Hello World", "MINECRAFT");
    }

    @Test
    @DisplayName("Should default destination to MINECRAFT when not provided")
    void shouldDefaultDestinationToMinecraft() throws Exception {
        mockMvc.perform(post("/api/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Hello World\"}"))
                .andExpect(status().isOk());

        verify(messageService).sendMessage("Hello World", "MINECRAFT");
    }

    @Test
    @DisplayName("Should return 400 when message text is blank")
    void shouldReturn400WhenMessageTextIsBlank() throws Exception {
        mockMvc.perform(post("/api/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"\",\"destination\":\"MINECRAFT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when message text is missing")
    void shouldReturn400WhenMessageTextIsMissing() throws Exception {
        mockMvc.perform(post("/api/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\":\"MINECRAFT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 502 when the message could not be handed to the alert manager")
    void shouldReturn502WhenDeliveryFails() throws Exception {
        // The wrapper does not deliver messages itself. If the forward to the
        // alert manager fails, nothing was broadcast, and reporting 200 here
        // would tell the caller otherwise.
        doThrow(new MessageDeliveryException(
                "The message was not delivered: the alert manager refused it with HTTP 400."))
                .when(messageService).sendMessage("Hello World", "MINECRAFT");

        mockMvc.perform(post("/api/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Hello World\",\"destination\":\"MINECRAFT\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value(
                        "The message was not delivered: the alert manager refused it with HTTP 400."));
    }

    @Test
    @DisplayName("Should return 400 for a destination the alert manager does not know")
    void shouldReturn400ForUnknownDestination() throws Exception {
        doThrow(new IllegalArgumentException("Unknown message destination 'SLACK'. Use one of: DISCORD, MINECRAFT."))
                .when(messageService).sendMessage("Hello World", "SLACK");

        mockMvc.perform(post("/api/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Hello World\",\"destination\":\"SLACK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
