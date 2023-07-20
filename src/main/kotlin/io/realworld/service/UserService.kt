package io.realworld.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.realworld.exception.InvalidLoginException
import io.realworld.model.User
import io.realworld.model.inout.Login
import io.realworld.repository.UserRepository
import org.mindrot.jbcrypt.BCrypt
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey




@Service
class UserService(val userRepository: UserRepository,
                  @Value("\${jwt.secret}") val jwtSecret: String,
                  @Value("\${jwt.issuer}") val jwtIssuer: String) {

    val currentUser = ThreadLocal<User>()

    fun findByToken(token: String) = userRepository.findByToken(token)

    fun clearCurrentUser() = currentUser.remove()

    fun setCurrentUser(user: User): User {
        currentUser.set(user)
        return user
    }

    fun currentUser(): User = currentUser.get()

    fun newToken(user: User): String {
        val secretKey: SecretKey = KeyGenerator.getInstance(jwtSecret).generateKey()
        return Jwts.builder()
                .setIssuedAt(Date())
                .setSubject(user.email)
                .setIssuer(jwtIssuer)
                .setExpiration(Date(System.currentTimeMillis() + 10 * 24 * 60 * 60 * 1000)) // 10 days
                .signWith(secretKey, SignatureAlgorithm.HS256).compact()
    }


    fun validToken(token: String, user: User): Boolean {
        val claims = Jwts.parser().setSigningKey(jwtSecret)
                .parseClaimsJws(token).body
        return claims.subject == user.email && claims.issuer == jwtIssuer
                && Date().before(claims.expiration)
    }

    fun updateToken(user: User): User {
        user.token = newToken(user)
        return userRepository.save(user)
    }

    fun login(login: Login): User? {
        userRepository.findByEmail(login.email!!)?.let {
            if (BCrypt.checkpw(login.password!!, it.password)) {
                return updateToken(it)
            }
            throw InvalidLoginException("password", "invalid password")
        }
        throw InvalidLoginException("email", "unknown email")
    }

}
