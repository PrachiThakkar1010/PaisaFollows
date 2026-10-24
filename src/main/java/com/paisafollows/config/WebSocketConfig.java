package com.paisafollows.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Browsers connect to ws://localhost:8080/ws */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Topics the server can push to: /topic/... (everyone), /queue/... (one user)
        registry.enableSimpleBroker("/topic", "/queue");
        // Messages a browser sends to the server would start with /app (not used yet)
        registry.setApplicationDestinationPrefixes("/app");
        // /user/queue/orders = "the orders queue of the logged-in user" (Module 9)
        registry.setUserDestinationPrefix("/user");
    }
}