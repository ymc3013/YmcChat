import { FastifyInstance, FastifyRequest } from "fastify";
import { Server } from "socket.io";
import jwt from "jsonwebtoken";
import { database } from "../database/connection.js";

type DirectBody = { userId?: string };
type MessageBody = { content?: string };
type ConversationParams = { id: string };
type MessagesQuery = { before?: string; limit?: string };

function getUserId(request: FastifyRequest): string | null {
    const authorization = request.headers.authorization;
    const secret = process.env.JWT_SECRET;
    if (!authorization || !secret) return null;

    const [scheme, token] = authorization.split(" ");
    if (scheme !== "Bearer" || !token) return null;

    try {
        const decoded = jwt.verify(token, secret);
        return typeof decoded === "object" && typeof decoded.sub === "string"
            ? decoded.sub
            : null;
    } catch {
        return null;
    }
}

async function isMember(conversationId: string, userId: string): Promise<boolean> {
    const result = await database.query(
        `SELECT 1 FROM conversation_members
         WHERE conversation_id = $1 AND user_id = $2`,
        [conversationId, userId]
    );
    return result.rowCount > 0;
}

export async function messagesRoutes(app: FastifyInstance, io: Server) {
    // Create or retrieve a direct conversation with an accepted friend.
    app.post<{ Body: DirectBody }>("/conversations/direct", async (request, reply) => {
        const userId = getUserId(request);
        if (!userId) return reply.code(401).send({ error: "UNAUTHORIZED" });

        const targetId = request.body?.userId?.trim();
        if (!targetId || targetId === userId) {
            return reply.code(400).send({ error: "INVALID_USER" });
        }

        const friendship = await database.query(
            `SELECT 1 FROM friendships
             WHERE status = 'accepted'
               AND ((requester_id = $1 AND addressee_id = $2)
                 OR (requester_id = $2 AND addressee_id = $1))`,
            [userId, targetId]
        );
        if (friendship.rowCount === 0) {
            return reply.code(403).send({ error: "FRIENDS_ONLY" });
        }

        const pair = await database.query(
            `SELECT conversation_id FROM direct_conversations
             WHERE user_low = LEAST($1::uuid, $2::uuid)
               AND user_high = GREATEST($1::uuid, $2::uuid)`,
            [userId, targetId]
        );
        if (pair.rowCount) {
            return reply.send({ conversationId: pair.rows[0].conversation_id });
        }

        const client = await database.connect();
        try {
            await client.query("BEGIN");
            const created = await client.query(
                "INSERT INTO conversations (type, created_by) VALUES ('direct', $1) RETURNING id",
                [userId]
            );
            const conversationId = created.rows[0].id;

            await client.query(
                `INSERT INTO direct_conversations (user_low, user_high, conversation_id)
                 VALUES (LEAST($1::uuid, $2::uuid), GREATEST($1::uuid, $2::uuid), $3)`,
                [userId, targetId, conversationId]
            );
            await client.query(
                `INSERT INTO conversation_members (conversation_id, user_id)
                 VALUES ($1, $2), ($1, $3)`,
                [conversationId, userId, targetId]
            );
            await client.query("COMMIT");
            return reply.code(201).send({ conversationId });
        } catch (error: unknown) {
            await client.query("ROLLBACK");
            if (typeof error === "object" && error !== null && "code" in error && error.code === "23505") {
                const existing = await database.query(
                    `SELECT conversation_id FROM direct_conversations
                     WHERE user_low = LEAST($1::uuid, $2::uuid)
                       AND user_high = GREATEST($1::uuid, $2::uuid)`,
                    [userId, targetId]
                );
                if (existing.rowCount) return reply.send({ conversationId: existing.rows[0].conversation_id });
            }
            app.log.error(error);
            return reply.code(500).send({ error: "INTERNAL_ERROR" });
        } finally {
            client.release();
        }
    });

    // List conversations and the latest message preview.
    app.get("/conversations", async (request, reply) => {
        const userId = getUserId(request);
        if (!userId) return reply.code(401).send({ error: "UNAUTHORIZED" });

        try {
            const result = await database.query(
                `SELECT c.id, c.type, c.updated_at,
                        u.id AS friend_id, u.username AS friend_username,
                        u.display_name AS friend_display_name, u.avatar_url AS friend_avatar_url,
                        u.status AS friend_status,
                        m.content AS last_message, m.created_at AS last_message_at,
                        m.sender_id AS last_message_sender_id
                 FROM conversation_members mine
                 JOIN conversations c ON c.id = mine.conversation_id
                 JOIN conversation_members other
                   ON other.conversation_id = c.id AND other.user_id <> $1
                 JOIN users u ON u.id = other.user_id
                 LEFT JOIN LATERAL (
                     SELECT content, created_at, sender_id
                     FROM messages
                     WHERE conversation_id = c.id AND deleted_at IS NULL
                     ORDER BY created_at DESC LIMIT 1
                 ) m ON TRUE
                 WHERE mine.user_id = $1 AND c.type = 'direct'
                 ORDER BY COALESCE(m.created_at, c.updated_at) DESC
                 LIMIT 100`,
                [userId]
            );
            return reply.send({ conversations: result.rows });
        } catch (error) {
            app.log.error(error);
            return reply.code(500).send({ error: "INTERNAL_ERROR" });
        }
    });

    // Load message history. The caller must be a conversation member.
    app.get<{ Params: ConversationParams; Querystring: MessagesQuery }>(
        "/conversations/:id/messages",
        async (request, reply) => {
            const userId = getUserId(request);
            if (!userId) return reply.code(401).send({ error: "UNAUTHORIZED" });
            const { id } = request.params;

            try {
                if (!await isMember(id, userId)) return reply.code(403).send({ error: "NOT_A_MEMBER" });
                const parsedLimit = Number(request.query.limit ?? 50);
                const limit = Number.isInteger(parsedLimit) ? Math.min(Math.max(parsedLimit, 1), 100) : 50;
                const before = request.query.before;
                const result = await database.query(
                    `SELECT m.id, m.conversation_id, m.sender_id, u.username AS sender_username,
                            u.display_name AS sender_display_name, m.content, m.created_at, m.edited_at
                     FROM messages m
                     JOIN users u ON u.id = m.sender_id
                     WHERE m.conversation_id = $1 AND m.deleted_at IS NULL
                       AND ($2::timestamptz IS NULL OR m.created_at < $2::timestamptz)
                     ORDER BY m.created_at DESC
                     LIMIT $3`,
                    [id, before || null, limit]
                );
                await database.query(
                    `UPDATE conversation_members SET last_read_at = NOW()
                     WHERE conversation_id = $1 AND user_id = $2`,
                    [id, userId]
                );
                return reply.send({ messages: result.rows.reverse() });
            } catch (error) {
                app.log.error(error);
                return reply.code(500).send({ error: "INTERNAL_ERROR" });
            }
        }
    );

    // Persist and broadcast a message to every connected member.
    app.post<{ Params: ConversationParams; Body: MessageBody }>(
        "/conversations/:id/messages",
        async (request, reply) => {
            const userId = getUserId(request);
            if (!userId) return reply.code(401).send({ error: "UNAUTHORIZED" });
            const { id } = request.params;
            const content = request.body?.content?.trim();
            if (!content || content.length > 4000) {
                return reply.code(400).send({ error: "INVALID_MESSAGE" });
            }

            try {
                if (!await isMember(id, userId)) return reply.code(403).send({ error: "NOT_A_MEMBER" });
                const result = await database.query(
                    `INSERT INTO messages (conversation_id, sender_id, content)
                     VALUES ($1, $2, $3)
                     RETURNING id, conversation_id, sender_id, content, created_at`,
                    [id, userId, content]
                );
                await database.query(
                    "UPDATE conversations SET updated_at = NOW() WHERE id = $1",
                    [id]
                );
                const message = result.rows[0];
                io.to(`conversation:${id}`).emit("message:new", message);
                return reply.code(201).send({ message });
            } catch (error) {
                app.log.error(error);
                return reply.code(500).send({ error: "INTERNAL_ERROR" });
            }
        }
    );
}
