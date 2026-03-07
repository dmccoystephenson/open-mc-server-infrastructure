package com.openmc.minecraftwrapper.controller;

import com.openmc.minecraftwrapper.model.MessageRequest;
import com.openmc.minecraftwrapper.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<String> sendMessage(@RequestBody MessageRequest request) {
        log.info("Received message request: {}", request.getText());
        
        String destination = request.getDestination() != null ? request.getDestination() : "MINECRAFT";
        messageService.sendMessage(request.getText(), destination);
        
        return ResponseEntity.ok("Message sent successfully");
    }
}
