package com.fin.billage.util;

import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.security.Keys;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.DatatypeConverter;
import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtUtil {
    private final Key key;
    private final long accessExpired;
    private final long refreshExpired;

    @Value("${8fin.authentication.scope}")
    private String scope;

    // 서버 측 secret key
    public JwtUtil(@Value("${jwt.secret}") String secretKey,
                   @Value("${jwt.access-expired-seconds}") long accessExpired,
                   @Value("${jwt.refresh-expired-seconds}") long refreshExpired) {
        byte[] secretByteKey = DatatypeConverter.parseBase64Binary(secretKey);
        this.key = Keys.hmacShaKeyFor(secretByteKey);
        this.accessExpired = accessExpired * 1000;
        this.refreshExpired = refreshExpired * 1000;
    }

    // 토큰 발급
    public JwtToken createToken(Authentication authentication) {
        // 역할을 설정하는 것을 추출하기
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        String accessToken = Jwts.builder()
                .setSubject(authentication.getName()) // 토큰의 이름 설정
                .claim("auth", authorities) // 권한 넣기
                .claim("type", "ACCESS")
                .claim("userPk", authentication.getCredentials()) // pk 값 넣기
                .claim("userCellNo", authentication.getName()) // 핸드폰 번호 값 넣기
                .setExpiration(new Date(System.currentTimeMillis() + accessExpired)) // 만료기간 30분 설정
                .signWith(SignatureAlgorithm.HS256, key)
                .compact();

        String refreshToken = Jwts.builder()
                .setSubject(authentication.getName()) // 토큰의 이름 설정
                .claim("auth", authorities) // 권한 넣기
                .claim("type", "REFRESH")
                .claim("userPk", authentication.getCredentials()) // pk 값 넣기
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpired))
                .signWith(SignatureAlgorithm.HS256, key)
                .compact();

        return JwtToken.builder()
                .grantType("Bearer") // 이거는 그냥 jwt라는 것을 알려주기 위한 이름 같은 것
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // 헤더에서 토큰 추출
    public String resolveToken(HttpServletRequest httpServletRequest){
        String bearerToken = httpServletRequest.getHeader("Authorization");

        if(StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer")){
            // "Bearer" 다음에 오는 부분 문자열 추출 = token
            return bearerToken.substring(7);
        }

        // 조건에 부합하지 않으면 그냥 return null
        return null;
    }

    // 토큰 복호화
    public Authentication getAuthentication(String accessToken) {
        Claims claims = parseClaims(accessToken);

        if (!claims.get("auth").equals(scope)) {
            throw new RuntimeException("권한 정보가 없는 토큰");
        }

        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get("auth").toString().split(","))
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        UserDetails principal = new User(claims.getSubject(), "", authorities);

        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    // Access 토큰 유효성 검사
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .setSigningKey(key)
                    .parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("Invalid JWT Token", e);
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT Token", e);
        } catch (UnsupportedJwtException e) {
            log.info("Unsurported JWT Token", e);
        } catch (IllegalArgumentException e) {
            log.info("JWT claims string is empty", e);
        }
        return false;
    }

    // token 내용 parsing하는 함수
    public Claims parseClaims(String token) {
        return Jwts.parser().setSigningKey(key)
                .parseClaimsJws(token)
                .getBody();
    }

    public JwtToken refreshAccessToken(String refreshToken) {
        Claims token = parseClaims(refreshToken);

        String newToken = Jwts.builder()
                .setSubject(token.getSubject()) // 토큰의 이름 설정
                .claim("auth", scope) // 권한 넣기
                .claim("type", "ACCESS")
                .claim("userPk", token.get("userPk")) // pk 값 넣기
                .claim("userCellNo", token.get("userCellNo"))
                .setExpiration(new Date(System.currentTimeMillis() + accessExpired)) // 만료기간 30분 설정
                .signWith(SignatureAlgorithm.HS256, key)
                .compact();

        return JwtToken.builder()
                .grantType("Bearer")
                .accessToken(newToken)
                .build();
    }

    public Long extractUserPkFromToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        String token = bearerToken.substring(7);

        return Long.parseLong(parseClaims(token).get("userPk").toString());
    }
}

