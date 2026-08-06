package com.localserve.realtime;

import com.localserve.config.LocalServeProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final LocalServeProperties properties;

    public WebSocketConfiguration(JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtAuthenticationConverter,
                                  LocalServeProperties properties) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.properties = properties;
    }

    @Override public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = properties.security().allowedOrigins().toArray(String[]::new);
        registry.addEndpoint("/ws").setAllowedOrigins(origins);
        registry.addEndpoint("/ws-sockjs").setAllowedOrigins(origins).withSockJS();
    }

    @Override public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new JwtStompInterceptor(jwtDecoder, jwtAuthenticationConverter));
    }

    static final class JwtStompInterceptor implements ChannelInterceptor {
        private final JwtDecoder decoder;
        private final JwtAuthenticationConverter authenticationConverter;
        JwtStompInterceptor(JwtDecoder decoder, JwtAuthenticationConverter authenticationConverter) {
            this.decoder = decoder; this.authenticationConverter = authenticationConverter;
        }

        @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (accessor == null) return message;
            if (accessor.getCommand() == StompCommand.CONNECT) {
                String authorization = accessor.getFirstNativeHeader("Authorization");
                if (authorization == null || !authorization.startsWith("Bearer ")) {
                    throw new org.springframework.security.access.AccessDeniedException("Bearer token is required");
                }
                Jwt jwt = decoder.decode(authorization.substring(7));
                accessor.setUser(authenticationConverter.convert(jwt));
            } else if ((accessor.getCommand() == StompCommand.SEND || accessor.getCommand() == StompCommand.SUBSCRIBE)
                    && accessor.getUser() == null) {
                throw new org.springframework.security.access.AccessDeniedException("Authenticated STOMP session is required");
            }
            requireAuthorizedDestination(accessor);
            return message;
        }

        private static void requireAuthorizedDestination(StompHeaderAccessor accessor) {
            String destination = accessor.getDestination();
            if (destination == null) return;
            if (accessor.getCommand() == StompCommand.SEND && !destination.startsWith("/app/")) deny();
            if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
                boolean ownQueue = destination.startsWith("/user/queue/");
                boolean adminTopic = destination.startsWith("/topic/admin/") && accessor.getUser() instanceof Authentication authentication
                        && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                if (!ownQueue && !adminTopic) deny();
            }
        }

        private static void deny() {
            throw new org.springframework.security.access.AccessDeniedException("STOMP destination is not authorized");
        }

    }
}
