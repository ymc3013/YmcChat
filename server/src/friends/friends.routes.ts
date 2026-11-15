import { FastifyInstance, FastifyRequest } from "fastify";
import jwt from "jsonwebtoken";
import { database } from "../database/connection.js";

type FriendsBody = {
    username?: string;
};

type FriendsQuery = {
    q?: string;
};

type FriendParams = {
    id: string;
    userId: string;
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

export async function friendsRoutes(app: FastifyInstance) {

    // RECHERCHER DES UTILISATEURS
    app.get<{ Querystring: FriendsQuery }>(
        "/friends/search",
        async (request, reply) => {
            const userId = getUserId(request);

            if (!userId) {
                return reply.code(401).send({ error: "UNAUTHORIZED" });
            }

            const query = request.query.q?.trim();

            if (!query || query.length < 2 || query.length > 50) {
                return reply.code(400).send({
                    error: "INVALID_SEARCH",
                    message: "Entre au moins 2 caractères."
                });
            }

            try {
                const result = await database.query(
                    `SELECT id, username, display_name, avatar_url, bio, status
                     FROM users u
                     WHERE u.id <> $1
                       AND u.profile_visibility = 'public'
                       AND (
                           u.username ILIKE $2
                           OR u.display_name ILIKE $2
                       )
                       AND NOT EXISTS (
                           SELECT 1
                           FROM friendships f
                           WHERE (
                               f.requester_id = $1 AND f.addressee_id = u.id
                           ) OR (
                               f.requester_id = u.id AND f.addressee_id = $1
                           )
                       )
                     ORDER BY username
                     LIMIT 20`,
                    [userId, `%${query}%`]
                );

                return reply.send({ users: result.rows });

            } catch (error) {
                app.log.error(error);
                return reply.code(500).send({ error: "INTERNAL_ERROR" });
            }
        }
    );

    // ENVOYER UNE DEMANDE D'AMI
    app.post<{ Body: FriendsBody }>(
        "/friends/requests",
        async (request, reply) => {
            const userId = getUserId(request);

            if (!userId) {
                return reply.code(401).send({ error: "UNAUTHORIZED" });
            }

            const username = request.body?.username?.trim();

            if (!username) {
                return reply.code(400).send({
                    error: "MISSING_USERNAME"
                });
            }

            try {
                const target = await database.query(
                    `SELECT id
                     FROM users
                     WHERE lower(username) = lower($1)
                       AND profile_visibility = 'public'
                     LIMIT 1`,
                    [username]
                );

                if (target.rowCount === 0) {
                    return reply.code(404).send({
                        error: "USER_NOT_FOUND"
                    });
                }

                const targetId = target.rows[0].id;

                if (targetId === userId) {
                    return reply.code(400).send({
                        error: "CANNOT_ADD_SELF"
                    });
                }

                const result = await database.query(
                    `INSERT INTO friendships (requester_id, addressee_id)
                     VALUES ($1, $2)
                     RETURNING id, requester_id, addressee_id, status, created_at`,
                    [userId, targetId]
                );

                return reply.code(201).send({
                    message: "Demande d'ami envoyée.",
                    request: result.rows[0]
                });

            } catch (error: unknown) {
                if (
                    typeof error === "object" &&
                    error !== null &&
                    "code" in error &&
                    error.code === "23505"
                ) {
                    return reply.code(409).send({
                        error: "RELATIONSHIP_EXISTS",
                        message: "Une demande ou une amitié existe déjà."
                    });
                }

                app.log.error(error);
                return reply.code(500).send({ error: "INTERNAL_ERROR" });
            }
        }
    );

    // VOIR LES DEMANDES ENTRANTES ET SORTANTES
    app.get("/friends/requests", async (request, reply) => {
        const userId = getUserId(request);

        if (!userId) {
            return reply.code(401).send({ error: "UNAUTHORIZED" });
        }

        try {
            const result = await database.query(
                `SELECT
                    f.id,
                    f.requester_id,
                    f.addressee_id,
                    f.status,
                    f.created_at,
                    CASE
                        WHEN f.requester_id = $1 THEN 'outgoing'
                        ELSE 'incoming'
                    END AS direction,
                    u.username,
                    u.display_name,
                    u.avatar_url,
                    u.status AS user_status
                 FROM friendships f
                 JOIN users u
                   ON u.id = CASE
                       WHEN f.requester_id = $1 THEN f.addressee_id
                       ELSE f.requester_id
                   END
                 WHERE (
                     f.requester_id = $1 OR f.addressee_id = $1
                 )
                 AND f.status = 'pending'
                 ORDER BY f.created_at DESC`,
                [userId]
            );

            return reply.send({ requests: result.rows });

        } catch (error) {
            app.log.error(error);
            return reply.code(500).send({ error: "INTERNAL_ERROR" });
        }
    });

    // ACCEPTER UNE DEMANDE
    app.post<{ Params: FriendParams }>(
        "/friends/requests/:id/accept",
        async (request, reply) => {
            const userId = getUserId(request);

            if (!userId) {
                return reply.code(401).send({ error: "UNAUTHORIZED" });
            }

            try {
                const result = await database.query(
                    `UPDATE friendships
                     SET status = 'accepted',
                         updated_at = NOW()
                     WHERE id = $1
                       AND addressee_id = $2
                       AND status = 'pending'
                     RETURNING id, requester_id, addressee_id, status`,
                    [request.params.id, userId]
                );

                if (result.rowCount === 0) {
                    return reply.code(404).send({
                        error: "REQUEST_NOT_FOUND"
                    });
                }

                return reply.send({
                    message: "Demande acceptée.",
                    friendship: result.rows[0]
                });

            } catch (error) {
                app.log.error(error);
                return reply.code(500).send({ error: "INTERNAL_ERROR" });
            }
        }
    );

    // REFUSER UNE DEMANDE ENTRANTE
    app.delete<{ Params: FriendParams }>(
        "/friends/requests/:id",
        async (request, reply) => {
            const userId = getUserId(request);

            if (!userId) {
                return reply.code(401).send({ error: "UNAUTHORIZED" });
            }

            try {
                const result = await database.query(
                    `DELETE FROM friendships
                     WHERE id = $1
                       AND addressee_id = $2
                       AND status = 'pending'
                     RETURNING id`,
                    [request.params.id, userId]
                );

                if (result.rowCount === 0) {
                    return reply.code(404).send({
                        error: "REQUEST_NOT_FOUND"
                    });
                }

                return reply.send({
                    message: "Demande refusée."
                });

            } catch (error) {
                app.log.error(error);
                return reply.code(500).send({ error: "INTERNAL_ERROR" });
            }
        }
    );

    // LISTE DES AMIS
    app.get("/friends", async (request, reply) => {
        const userId = getUserId(request);

        if (!userId) {
            return reply.code(401).send({ error: "UNAUTHORIZED" });
        }

        try {
            const result = await database.query(
                `SELECT
                    u.id,
                    u.username,
                    u.display_name,
                    u.avatar_url,
                    u.bio,
                    u.status,
                    u.status_message,
                    u.last_seen,
                    f.created_at AS friends_since
                 FROM friendships f
                 JOIN users u
                   ON u.id = CASE
                       WHEN f.requester_id = $1 THEN f.addressee_id
                       ELSE f.requester_id
                   END
                 WHERE (
                     f.requester_id = $1 OR f.addressee_id = $1
                 )
                 AND f.status = 'accepted'
                 ORDER BY u.username`,
                [userId]
            );

            return reply.send({ friends: result.rows });

        } catch (error) {
            app.log.error(error);
            return reply.code(500).send({ error: "INTERNAL_ERROR" });
        }
    });

    // SUPPRIMER UN AMI
    app.delete<{ Params: FriendParams }>(
        "/friends/:userId",
        async (request, reply) => {
            const userId = getUserId(request);

            if (!userId) {
                return reply.code(401).send({ error: "UNAUTHORIZED" });
            }

            try {
                const result = await database.query(
                    `DELETE FROM friendships
                     WHERE status = 'accepted'
                       AND (
                           (requester_id = $1 AND addressee_id = $2)
                           OR
                           (requester_id = $2 AND addressee_id = $1)
                       )
                     RETURNING id`,
                    [userId, request.params.userId]
                );

                if (result.rowCount === 0) {
                    return reply.code(404).send({
                        error: "FRIEND_NOT_FOUND"
                    });
                }

                return reply.send({
                    message: "Amité supprimée."
                });

            } catch (error) {
                app.log.error(error);
                return reply.code(500).send({ error: "INTERNAL_ERROR" });
            }
        }
    );
}

