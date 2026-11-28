package br.com.adailsilva.chirpstackmanager.api.security.service;

import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import br.com.adailsilva.chirpstackmanager.api.config.SecurityProperties;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Usuario;

/**
 * Emissão do access token (JWT HS256, curta duração).
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final SecurityProperties props;

    public TokenService(JwtEncoder encoder, SecurityProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    /** {@code trocaSenha} = o usuário ainda usa a senha inicial e só pode trocá-la. */
    public String gerarAccessToken(Usuario u, String sessaoId, boolean trocaSenha) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwtIssuer())
                .issuedAt(agora)
                .expiresAt(agora.plus(props.accessTokenTtl()))
                .subject(u.getEmail())
                .claim("uid", u.getId())
                .claim("sid", sessaoId)
                .claim("nome", u.getNome())
                .claim("roles", List.of(u.getPerfil().name()))
                .claim(SenhaInicialService.CLAIM, trocaSenha)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenSeconds() {
        return props.accessTokenTtl().toSeconds();
    }
}
