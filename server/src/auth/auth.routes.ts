import {
    FastifyInstance,
    FastifyRequest
} from "fastify";

import argon2 from "argon2";
import jwt from "jsonwebtoken";
import { database } from "../database/connection.js";

type AuthBody = {
    username?: string;
    email?: string;
    identifier?: string;
    password?: string;
};

function createToken(userId: string): string {
    const secret = process.env.JWT_SECRET;

    if (!secret) {
        throw new Error("JWT_SECRET manquant.");
    }

    return jwt.sign(
        { sub: userId },
        secret,
        { expiresIn: "7d" }
    );
}

function getUserId(request: FastifyRequest): string | null {
    const authorization = request.headers.authorization;
    const secret = process.env.JWT_SECRET;

    if (!authorization || !secret) return null;

    const [scheme, token] = authorization.split(" ");

    if (scheme !== "Bearer" || !token) return null;

    try {
        const decoded = jwt.verify(token, secret);

        if (
            typeof decoded === "object" &&
            typeof decoded.sub === "string"
        ) {
            return decoded.sub;
        }

        return null;
    } catch {
        return null;
    }
}

export async function authRoutes(app: FastifyInstance) {

    // INSCRIPTION
    app.post<{ Body: AuthBody }>("/auth/register", async (request, reply) => {
        const body = request.body ?? {};

        const username = body.username?.trim();
        const email = body.email?.trim().toLowerCase();
        const password = body.password;

        if (
            typeof username !== "string" ||
            typeof email !== "string" ||
            typeof password !== "string"
        ) {
            return reply.code(400).send({
                error: "MISSING_FIELDS",
                message: "Tous les champs sont obligatoires."
            });
        }

        if (!/^[a-zA-Z0-9_]{3,24}$/.test(username)) {
            return reply.code(400).send({
                error: "INVALID_USERNAME",
                message: "Pseudo invalide : 3 à 24 caractères."
            });
        }

        if (
            !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ||
            email.length > 255
        ) {
            return reply.code(400).send({
                error: "INVALID_EMAIL",
                message: "Adresse email invalide."
            });
        }

        if (password.length < 12 || password.length > 128) {
            return reply.code(400).send({
                error: "INVALID_PASSWORD",
                message: "Le mot de passe doit contenir entre 12 et 128 caractères."
            });
        }

        try {
            const passwordHash = await argon2.hash(password);

            const result = await database.query(
                `INSERT INTO users (username, email, password_hash)
                 VALUES ($1, $2, $3)
                 RETURNING id, username, email, display_name,
                           avatar_url, bio, status, created_at`,
                [username, email, passwordHash]
            );

            const user = result.rows[0];
            const token = createToken(user.id);

            return reply.code(201).send({
                message: "Compte créé avec succès.",
                user,
                token
            });

        } catch (error: unknown) {
            if (
                typeof error === "object" &&
                error !== null &&
                "code" in error &&
                error.code === "23505"
            ) {
                return reply.code(409).send({
                    error: "ACCOUNT_EXISTS",
                    message: "Ce pseudo ou cet email est déjà utilisé."
                });
            }

            app.log.error(error);

            return reply.code(500).send({
                error: "INTERNAL_ERROR",
                message: "Une erreur interne est survenue."
            });
        }
    });


    // CONNEXION
    app.post<{ Body: AuthBody }>("/auth/login", async (request, reply) => {
        const body = request.body ?? {};

        const identifier =
            (body.identifier ?? body.email ?? body.username)?.trim();

        const password = body.password;

        if (
            typeof identifier !== "string" ||
            typeof password !== "string" ||
            !identifier ||
            !password
        ) {
            return reply.code(400).send({
                error: "MISSING_FIELDS",
                message: "Identifiant et mot de passe obligatoires."
            });
        }

        try {
            const result = await database.query(
                `SELECT id, username, email, password_hash,
                        display_name, avatar_url, bio, status, created_at
                 FROM users
                 WHERE lower(username) = lower($1)
                    OR lower(email) = lower($1)
                 LIMIT 1`,
                [identifier]
            );

            const user = result.rows[0];

            if (!user) {
                return reply.code(401).send({
                    error: "INVALID_CREDENTIALS",
                    message: "Identifiant ou mot de passe incorrect."
                });
            }

            const validPassword = await argon2.verify(
                user.password_hash,
                password
            );

            if (!validPassword) {
                return reply.code(401).send({
                    error: "INVALID_CREDENTIALS",
                    message: "Identifiant ou mot de passe incorrect."
                });
            }

            const token = createToken(user.id);

            delete user.password_hash;

            return reply.send({
                message: "Connexion réussie.",
                user,
                token
            });

        } catch (error) {
            app.log.error(error);

            return reply.code(500).send({
                error: "INTERNAL_ERROR",
                message: "Une erreur interne est survenue."
            });
        }
    });


    // UTILISATEUR CONNECTÉ
    app.get("/auth/me", async (request, reply) => {
        const userId = getUserId(request);

        if (!userId) {
            return reply.code(401).send({
                error: "UNAUTHORIZED",
                message: "Session invalide ou expirée."
            });
        }

        try {
            const result = await database.query(
                `SELECT id, username, email, display_name,
                        avatar_url, bio, status, created_at
                 FROM users
                 WHERE id = $1`,
                [userId]
            );

            if (result.rows.length === 0) {
                return reply.code(404).send({
                    error: "USER_NOT_FOUND",
                    message: "Utilisateur introuvable."
                });
            }

            return reply.send({
                user: result.rows[0]
            });

        } catch (error) {
            app.log.error(error);

            return reply.code(500).send({
                error: "INTERNAL_ERROR",
                message: "Une erreur interne est survenue."
            });
        }
    });
}
