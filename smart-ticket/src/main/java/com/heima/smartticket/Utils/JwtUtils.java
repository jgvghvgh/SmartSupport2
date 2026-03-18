package com.heima.smartticket.Utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

import static com.heima.smartcommon.Constant.JWTConstant.JWT_EXPIRE;
import static com.heima.smartcommon.Constant.JWTConstant.JWT_SECRET;

@Component
public class JwtUtils {




    public String generateToken(String username, String role) {
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        Date now = new Date();
        Date expiry = new Date(now.getTime() + JWT_EXPIRE);
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsername(String token) {
        return Jwts.parserBuilder().setSigningKey(JWT_SECRET.getBytes()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }
    // ✅ 从 Token 中解析出角色
    public  String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }
    private  Claims parseToken(String token) {
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(JWT_SECRET.getBytes()).build().parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}

