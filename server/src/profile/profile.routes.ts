
import { FastifyInstance, FastifyRequest } from "fastify";
import jwt from "jsonwebtoken";
import { database } from "../database/connection.js";

type ProfileBody = {
    display_name?: string | null;
    avatar_url?: string | null;
    bio?: string | null;
    status_message?: string | null;
    profile_visibility?: "public" | "private";
};

function getUserId(request: FastifyRequest): string | null {
    const authorization = request.headers.authorization;
    const secret = process.env.JWT_SECRET;

    if (!authorization || !secret) return null;

    const [scheme, token] = authorization.split(" ");

    if (scheme !== "Bearer" || !token) return null;

    try {
        const decoded = jwt.verify(token, secret);

        if (typeof decoded === "object" && typeof decoded.sub === "string") {
            return decoded.sub;
        }

        return null;
    } catch {
        return null;
    }
}

export async function profileRoutes(app: FastifyInstance) {

    // RÉCUPÉRER SON PROFIL
    app.get("/profile/me", async (request, reply) => {
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
                        avatar_url, bio, status, status_message,
                        profile_visibility, last_seen, created_at,
                        updated_at
                 FROM users
                 WHERE id = $1`,
                [userId]
            );

            if (result.rowCount === 0) {
                return reply.code(404).send({
                    error: "USER_NOT_FOUND"
                });
            }

            return reply.send({ user: result.rows[0] });

        } catch (error) {
            app.log.error(error);

            return reply.code(500).send({
                error: "INTERNAL_ERROR"
            });
        }
    });

    // MODIFIER SON PROFIL
    app.patch<{ Body: ProfileBody }>("/profile/me", async (request, reply) => {
        const userId = getUserId(request);

        if (!userId) {
            return reply.code(401).send({
                error: "UNAUTHORIZED",
                message: "Session invalide ou expirée."
            });
        }

        const body = request.body ?? {};

        const allowedFields: Record<string, number> = {
            display_name: 50,
            avatar_url: 2048,
            bio: 300,
            status_message: 80
        };

        const updates: string[] = [];
        const values: unknown[] = [];

        for (const [field, maxLength] of Object.entries(allowedFields)) {
            if (!Object.prototype.hasOwnProperty.call(body, field)) {
                continue;
            }

            const value = body[field as keyof ProfileBody];

            if (value !== null && typeof value !== "string") {
                return reply.code(400).send({
                    error: "INVALID_FIELD",
                    message: `Valeur invalide pour ${field}.`
                });
            }

            if (typeof value === "string" && value.length > maxLength) {
                return reply.code(400).send({
                    error: "FIELD_TOO_LONG",
                    message: `${field} est trop long.`
                });
            }

            values.push(typeof value === "string" && value.trim() === ""
                ? null
                : value);

            updates.push(`${field} = $${values.length}`);
        }

        if (Object.prototype.hasOwnProperty.call(body, "profile_visibility")) {
            if (
                body.profile_visibility !== "public" &&
                body.profile_visibility !== "private"
            ) {
                return reply.code(400).send({
                    error: "INVALID_VISIBILITY",
                    message: "Visibilité invalide."
                });
            }

            values.push(body.profile_visibility);
            updates.push(`profile_visibility = $${values.length}`);
        }

        if (updates.length === 0) {
            return reply.code(400).send({
                error: "NO_CHANGES",
                message: "Aucune modification fournie."
            });
        }

        updates.push("updated_at = NOW()");
        values.push(userId);

        try {
            const result = await database.query(
                `UPDATE users
                 SET ${updates.join(", ")}
                 WHERE id = $${values.length}
                 RETURNING id, username, email, display_name,
                           avatar_url, bio, status, status_message,
                           profile_visibility, last_seen, created_at,
                           updated_at`,
                values
            );

            if (result.rowCount === 0) {
                return reply.code(404).send({
                    error: "USER_NOT_FOUND"
                });
            }

            return reply.send({
                message: "Profil mis à jour.",
                user: result.rows[0]
            });

        } catch (error) {
            app.log.error(error);

            return reply.code(500).send({
                error: "INTERNAL_ERROR"
            });
        }
    });
}

